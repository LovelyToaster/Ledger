package com.verdantgem.ledger.data.remote

import android.content.Context
import android.net.Uri
import com.verdantgem.ledger.data.DataChangeNotifier
import com.verdantgem.ledger.data.local.SyncChangeLog
import com.verdantgem.ledger.data.local.SyncChangeLogDao
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

sealed class SyncResult {
    data object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavClient: WebDavClient,
    private val cryptoManager: CryptoManager,
    private val repository: LedgerRepository,
    private val changeNotifier: DataChangeNotifier,
    private val syncChangeLogDao: SyncChangeLogDao
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)
    private val syncFile = "ledger_sync.json"
    private var debounceJob: Job? = null
    private var dirty: Boolean
        get() = _dirty.get()
        set(value) {
            _dirty.set(value)
            prefs.edit().putBoolean(KEY_SYNC_DIRTY, value).apply()
        }
    private val _dirty = AtomicBoolean(prefs.getBoolean(KEY_SYNC_DIRTY, false))

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var backgroundedAt = 0L

    fun markDirty() { dirty = true }

    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            changeNotifier.changes.collect {
                dirty = true
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(5_000L)
                    val cfg = readConfig() ?: return@launch
                    if (fullSync(cfg.url, cfg.user, cfg.pass, cfg.cryptoPw) is SyncResult.Success) {
                        dirty = false
                    }
                }
            }
        }
    }

    suspend fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
        val cfg = readConfig()
        if (cfg != null) {
            if (dirty) {
                if (cfg.autoSync) {
                    debounceJob?.cancel()
                    if (fullSync(cfg.url, cfg.user, cfg.pass, cfg.cryptoPw) is SyncResult.Success) {
                        dirty = false
                    }
                }
            }
            if (cfg.autoBackup) {
                backupDatabase(cfg.url, cfg.user, cfg.pass)
            }
        }
    }

    suspend fun onAppForegrounded() {
        if (backgroundedAt == 0L) return
        val elapsed = System.currentTimeMillis() - backgroundedAt
        backgroundedAt = 0L
        if (elapsed < 30_000L) return
        val cfg = readConfig() ?: return
        if (!cfg.autoSync) return
        fullSync(cfg.url, cfg.user, cfg.pass, cfg.cryptoPw)
    }

    suspend fun syncIfConfigured(ignoreAutoSync: Boolean = false): SyncResult? {
        val cfg = readConfig() ?: return null
        if (!ignoreAutoSync && !cfg.autoSync) return null
        return fullSync(cfg.url, cfg.user, cfg.pass, cfg.cryptoPw)
    }

    suspend fun backupDatabase(
        url: String,
        user: String,
        pass: String
    ): SyncResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "backup_upload_temp")
        try {
            val username = prefs.getString("sync_username", "") ?: ""
            if (username.isBlank()) return@withContext SyncResult.Error("Sync username not configured")

            val dbFile = context.getDatabasePath("ledger_db")
            if (!dbFile.exists()) return@withContext SyncResult.Error("Database not found")

            // Gzip 压缩数据库文件
            GZIPOutputStream(tempFile.outputStream()).use { gzip ->
                dbFile.inputStream().use { input -> input.copyTo(gzip) }
                gzip.finish()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFilename = "ledger_backup_$timestamp.db.gz"
            val remoteDir = ensureSubPath(url, username)
            val remoteUrl = remoteDir + backupFilename

            webDavClient.mkdir(remoteDir, user, pass)
            val result = webDavClient.backup(remoteUrl, user, pass, tempFile)
            if (result.isFailure) {
                return@withContext SyncResult.Error(result.exceptionOrNull()?.message ?: "Backup upload failed")
            }

            val baseUrl = ensureSubPath(url, username)
            val listResult = webDavClient.listFiles(baseUrl, user, pass)
            if (listResult.isSuccess) {
                val backups = listResult.getOrThrow()
                    .map { it.extractName() }
                    .filter { it.startsWith("ledger_backup_") && it.endsWith(".db.gz") }
                    .sorted()
                if (backups.size > MAX_BACKUPS) {
                    backups.take(backups.size - MAX_BACKUPS).forEach { old ->
                        webDavClient.deleteFile("$baseUrl$old", user, pass)
                    }
                }
            }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Backup failed")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun readConfig(): SyncConfig? {
        val url = prefs.getString("webdav_url", "") ?: ""
        val user = prefs.getString("webdav_user", "") ?: ""
        val pass = prefs.getString("webdav_pass", "") ?: ""
        val syncUsername = prefs.getString("sync_username", "") ?: ""
        if (url.isBlank() || user.isBlank() || pass.isBlank() || syncUsername.isBlank()) return null
        return SyncConfig(
            url = url, user = user, pass = pass,
            syncUsername = syncUsername,
            autoSync = prefs.getBoolean("webdav_auto_sync", false),
            autoBackup = prefs.getBoolean("webdav_auto_backup", false),
            cryptoPw = prefs.getString("webdav_crypto_password", null)
        )
    }

    private data class SyncConfig(
        val url: String, val user: String, val pass: String,
        val syncUsername: String,
        val autoSync: Boolean, val autoBackup: Boolean, val cryptoPw: String?
    )

    private suspend fun ensureDir(url: String, user: String, pass: String, username: String) {
        val dirUrl = ensureSubPath(url, username)
        webDavClient.mkdir(dirUrl, user, pass)
    }

    private fun ensureSubPath(url: String, username: String): String {
        val base = if (url.endsWith("/")) url else "$url/"
        return "$base${Uri.encode("简记账")}/${Uri.encode(username)}/"
    }

    fun getDeviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    suspend fun fullSync(
        url: String,
        user: String,
        pass: String,
        cryptoPassword: String?,
        dispatcher: CoroutineContext = Dispatchers.IO
    ): SyncResult {
        _isSyncing.value = true
        return try {
            mutex.withLock {
                val syncUsername = prefs.getString("sync_username", "") ?: ""
                if (syncUsername.isBlank()) {
                    return@withLock SyncResult.Error("Sync username not configured")
                }
                withContext(dispatcher) {
                    try {
                        ensureDir(url, user, pass, syncUsername)

                        // 1. 断裂升级：清理旧 v2 残留（manifest.json → 已废弃）
                        migrateFromV2(url, user, pass, cryptoPassword, syncUsername, dispatcher)

                        // 2. 新设备加入：变更日志为空时，从本地数据种子初始变更
                        seedChangeLogFromLocalData()

                        // 3. 拉取远程变更
                        pullChanges(url, user, pass, cryptoPassword, syncUsername, dispatcher)

                        // 4. 推送本地变更
                        val pushResult = pushChanges(url, user, pass, cryptoPassword, syncUsername, dispatcher)

                        // 5. 压缩检查
                        compactCheck(url, user, pass, cryptoPassword, syncUsername, dispatcher)

                        if (pushResult is SyncResult.Error) pushResult else SyncResult.Success
                    } catch (e: Exception) {
                        SyncResult.Error(e.message ?: "Unknown sync error")
                    }
                }
            }
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * 断裂升级：删除旧 v2 的 manifest.json 和旧格式 snapshot.json.gz，
     * 并将旧 snapshot 重命名为新命名格式。清除本地旧的 manifest 相关偏好。
     * 仅执行一次。
     */
    private suspend fun migrateFromV2(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ) {
        if (prefs.getBoolean("migrated_to_v3", false)) return
        val baseDir = ensureSubPath(url, username)
        // 删除旧 manifest.json（v2 残留）
        try { webDavClient.deleteFile(baseDir + "manifest.json", user, pass) } catch (_: Exception) {}
        // 重命名旧 snapshot.json.gz → snapshot_1.json.gz
        try {
            val oldSnapshotUrl = baseDir + "snapshot.json.gz"
            val headResult = webDavClient.head(oldSnapshotUrl, user, pass)
            if (headResult.isSuccess) {
                // 下载旧快照 → 上传为新文件名 → 删除旧文件
                val tempFile = File(context.cacheDir, "snapshot_migrate_temp")
                try {
                    val dl = webDavClient.restore(oldSnapshotUrl, user, pass, tempFile)
                    if (dl.isSuccess) {
                        webDavClient.backup(baseDir + "snapshot_1.json.gz", user, pass, tempFile)
                    }
                } catch (_: Exception) {} finally {
                    if (tempFile.exists()) tempFile.delete()
                }
                try { webDavClient.deleteFile(oldSnapshotUrl, user, pass) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        // 清除/迁移本地旧的 manifest 相关偏好
        val edit = prefs.edit()
        for (key in prefs.all.keys) {
            when {
                // 迁移 remote_known_batch_* → known_batch_*
                key.startsWith("remote_known_batch_") -> {
                    val deviceId = key.removePrefix("remote_known_batch_")
                    val value = prefs.getInt(key, 0)
                    edit.putInt("known_batch_$deviceId", value)
                    edit.remove(key)
                }
                // 删除不再需要的旧键
                key.startsWith("remote_known_seq_") ||
                key == "my_last_seq" ||
                key == "last_compaction_time" ||
                key == "manifest_etag" ||
                key == "last_remote_etag" -> edit.remove(key)
            }
        }
        // 重置 last_snapshot_seq（旧值可能大于新命名快照的 seq）
        edit.putInt("last_snapshot_seq", 0)
        edit.putBoolean("migrated_to_v3", true)
        edit.apply()
    }

    /**
     * 新设备加入已有同步组时，将本地全部数据写入变更日志，确保被 pushChanges 推送
     */
    private suspend fun seedChangeLogFromLocalData() {
        // 仅在变更日志为空时写入（避免与正常操作产生的条目重复）
        val totalCount = syncChangeLogDao.getTotalCount()
        if (totalCount > 0) return
        val deviceId = getDeviceId()
        // Records
        val records = repository.getAllRecordsForSync()
        for (record in records) {
            try {
                val jsonStr = json.encodeToString(SyncRecord.serializer(), record.toSync())
                syncChangeLogDao.appendNext(
                    SyncChangeLog(
                        seq = 0, entity = "record", uuid = record.syncUuid,
                        operation = "upsert", data = jsonStr,
                        changedAt = record.updatedAt, deviceId = deviceId
                    )
                )
            } catch (_: Exception) {}
        }
        // Categories
        val categories = repository.getAllCategoriesForSync()
        for (cat in categories) {
            try {
                val jsonStr = json.encodeToString(SyncCategory.serializer(), cat.toSync())
                syncChangeLogDao.appendNext(
                    SyncChangeLog(
                        seq = 0, entity = "category", uuid = cat.syncUuid,
                        operation = "upsert", data = jsonStr,
                        changedAt = cat.updatedAt, deviceId = deviceId
                    )
                )
            } catch (_: Exception) {}
        }
        // Budget
        val budget = repository.getBudgetForSync()
        if (budget != null) {
            try {
                val jsonStr = json.encodeToString(SyncBudget.serializer(), budget.toSync())
                syncChangeLogDao.appendNext(
                    SyncChangeLog(
                        seq = 0, entity = "budget", uuid = budget.syncUuid,
                        operation = "upsert", data = jsonStr,
                        changedAt = budget.updatedAt, deviceId = deviceId
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private suspend fun pushChanges(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ): SyncResult = withContext(dispatcher) {
        val unsynced = syncChangeLogDao.getUnsyncedChanges()
        if (unsynced.isEmpty()) return@withContext SyncResult.Success

        val baseDir = ensureSubPath(url, username)
        val changesDir = deviceChangesUrl(baseDir, getDeviceId())

        try {
            // 确保 changes/{deviceId}/ 目录存在
            webDavClient.mkdir(baseDir + "changes/", user, pass)
            webDavClient.mkdir(changesDir, user, pass)

            // PROPFIND 当前目录，取最大批次号
            val maxBatch = discoverMaxBatch(changesDir, user, pass)
            val nextBatch = maxBatch + 1
            val maxSeq = unsynced.maxOf { it.seq }

            // 从变更日志反序列化实体
            val records = mutableListOf<SyncRecord>()
            val categories = mutableListOf<SyncCategory>()
            val budgets = mutableListOf<SyncBudget>()
            val batchJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            for (entry in unsynced) {
                when (entry.entity) {
                    "record" -> records.add(batchJson.decodeFromString<SyncRecord>(entry.data))
                    "category" -> categories.add(batchJson.decodeFromString<SyncCategory>(entry.data))
                    "budget" -> budgets.add(batchJson.decodeFromString<SyncBudget>(entry.data))
                }
            }

            val batch = SyncChangeBatch(
                deviceId = getDeviceId(),
                batchNum = nextBatch,
                records = records,
                categories = categories,
                budgets = budgets
            )

            val batchJsonStr = batchJson.encodeToString(SyncChangeBatch.serializer(), batch)

            // 批次加密/压缩包装
            val batchFile = if (!cryptoPassword.isNullOrBlank()) {
                val compressedInner = compress(batchJsonStr)
                val compressedBase64 = Base64.getEncoder().encodeToString(compressedInner)
                val ciphertext = cryptoManager.encrypt(compressedBase64, cryptoPassword)
                BatchFile(encrypted = true, ciphertext = ciphertext)
            } else {
                BatchFile(data = batchJsonStr)
            }
            val outputJson = json.encodeToString(BatchFile.serializer(), batchFile)
            val compressed = compress(outputJson)

            val tempFile = File(context.cacheDir, "sync_batch_temp")
            try {
                tempFile.writeBytes(compressed)
                // 上传批次文件
                val batchUrl = batchFileUrl(baseDir, getDeviceId(), nextBatch)
                val uploadResult = webDavClient.backup(batchUrl, user, pass, tempFile)
                if (uploadResult.isFailure) {
                    return@withContext SyncResult.Error(uploadResult.exceptionOrNull()?.message ?: "Batch upload failed")
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }

            // 标记已推送
            syncChangeLogDao.markSynced(maxSeq)
            dirty = false

            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Push changes failed")
        }
    }

    private suspend fun pullChanges(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ): SyncResult = withContext(dispatcher) {
        val baseDir = ensureSubPath(url, username)
        var anyApplied = false
        val myDeviceId = getDeviceId()

        // 1. 先发现远程设备（供快照合并后更新 known_batch 使用）
        val remoteDevices = discoverRemoteDevices(baseDir, user, pass)

        // 2. 检查是否有新快照
        val snapshotSeq = discoverSnapshotSeq(baseDir, user, pass)
        val lastSnapshotSeq = prefs.getInt("last_snapshot_seq", 0)
        if (snapshotSeq > lastSnapshotSeq) {
            val snapshotUrl = baseDir + SNAPSHOT_PREFIX + snapshotSeq + SNAPSHOT_SUFFIX
            val tempFile = File(context.cacheDir, "sync_snapshot_pull_temp")
            try {
                val downloadResult = webDavClient.restore(snapshotUrl, user, pass, tempFile)
                if (downloadResult.isSuccess) {
                    val rawBytes = tempFile.readBytes()
                    val rawJson = decompress(rawBytes)
                    val syncFileWrapper = json.decodeFromString<SyncFile>(rawJson)
                    val snapshot: SyncSnapshot? = if (syncFileWrapper.encrypted) {
                        if (!cryptoPassword.isNullOrBlank()) {
                            val decrypted = cryptoManager.decrypt(syncFileWrapper.ciphertext, cryptoPassword)
                            val snapshotJson = if (syncFileWrapper.compressed) {
                                val compressed2 = Base64.getDecoder().decode(decrypted)
                                decompress(compressed2)
                            } else decrypted
                            json.decodeFromString<SyncSnapshot>(snapshotJson)
                        } else null
                    } else syncFileWrapper.data
                    if (snapshot != null) {
                        mergeSnapshot(snapshot)
                        // 快照已覆盖全量历史数据，将所有已知设备的 known_batch 归零
                        val edit = prefs.edit()
                        edit.putInt("last_snapshot_seq", snapshotSeq)
                        for ((deviceId, _) in remoteDevices) {
                            if (deviceId != myDeviceId) edit.putInt("known_batch_$deviceId", 0)
                        }
                        edit.apply()
                        anyApplied = true
                    }
                }
            } catch (_: Exception) {
                // 快照下载失败不阻断
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

        // 3. 一次性加载全量数据用于合并（含快照合并后的新数据）
        val allRecords = repository.getAllRecordsForSync()
        val recordsByUuid = allRecords.associateBy { it.syncUuid }
        val recordsById = allRecords.associateBy { it.id }
        val allCategories = repository.getAllCategoriesForSync()
        val categoriesByUuid = allCategories.associateBy { it.syncUuid }
        val categoriesById = allCategories.associateBy { it.id }
        val categoriesByKey = mutableMapOf<Triple<String, String?, Boolean>, com.verdantgem.ledger.data.model.Category>()
        for (cat in allCategories) {
            val key = Triple(cat.name, cat.parentName, cat.isIncome)
            val existing = categoriesByKey[key]
            if (existing == null || (existing.deleted && !cat.deleted)) {
                categoriesByKey[key] = cat
            }
        }
        val localBudget = repository.getBudgetForSync()

        // 4. 拉取各设备的新批次
        for ((deviceId, maxBatch) in remoteDevices) {
            if (deviceId == myDeviceId) continue

            var knownBatch = prefs.getInt("known_batch_$deviceId", 0)

            // 仅在 PROPFIND 确实看到批次文件时才触发重置检测（maxBatch=0 可能是网络失败）
            if (maxBatch > 0 && maxBatch < knownBatch) {
                knownBatch = 0
                prefs.edit()
                    .putInt("known_batch_$deviceId", 0)
                    .putInt("last_snapshot_seq", 0)
                    .apply()
            }

            if (maxBatch <= knownBatch) continue

            val changesDir = deviceChangesUrl(baseDir, deviceId)
            val batchJson = Json { ignoreUnknownKeys = true }
            var appliedMaxBatch = knownBatch

            for (batchNum in (knownBatch + 1)..maxBatch) {
                val batchUrl = batchFileUrl(baseDir, deviceId, batchNum)
                val tempFile = File(context.cacheDir, "sync_pull_batch_temp")
                try {
                    val downloadResult = webDavClient.restore(batchUrl, user, pass, tempFile)
                    if (downloadResult.isFailure) break
                    val compressed = tempFile.readBytes()
                    val rawJson = decompress(compressed)
                    val batchFile = batchJson.decodeFromString<BatchFile>(rawJson)
                    val batchJsonStr = if (batchFile.encrypted) {
                        if (cryptoPassword.isNullOrBlank()) break
                        val decrypted = cryptoManager.decrypt(batchFile.ciphertext, cryptoPassword)
                        val compressedInner = Base64.getDecoder().decode(decrypted)
                        decompress(compressedInner)
                    } else {
                        batchFile.data ?: break
                    }
                    val batch = batchJson.decodeFromString<SyncChangeBatch>(batchJsonStr)
                    mergeChangeBatch(
                        batch, recordsByUuid, recordsById,
                        categoriesByUuid, categoriesById, categoriesByKey, localBudget
                    )
                    appliedMaxBatch = batchNum
                    anyApplied = true
                } catch (e: Exception) {
                    break
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            if (appliedMaxBatch > knownBatch) {
                prefs.edit().putInt("known_batch_$deviceId", appliedMaxBatch).apply()
            }
        }

        if (anyApplied) {
            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
        }
        SyncResult.Success
    }

    private suspend fun mergeChangeBatch(
        batch: SyncChangeBatch,
        recordsByUuid: Map<String, com.verdantgem.ledger.data.model.Record>,
        recordsById: Map<Long, com.verdantgem.ledger.data.model.Record>,
        categoriesByUuid: Map<String, com.verdantgem.ledger.data.model.Category>,
        categoriesById: Map<Long, com.verdantgem.ledger.data.model.Category>,
        categoriesByKey: Map<Triple<String, String?, Boolean>, com.verdantgem.ledger.data.model.Category>,
        localBudget: com.verdantgem.ledger.data.model.Budget?
    ) {
        // 合并 records
        for (remote in batch.records) {
            var local: com.verdantgem.ledger.data.model.Record? = null
            if (remote.syncUuid.isNotBlank()) {
                local = recordsByUuid[remote.syncUuid]
            }
            if (local == null) {
                val byId = recordsById[remote.id]
                if (byId != null && (byId.syncUuid.isBlank() || byId.syncUuid == remote.syncUuid)) {
                    local = byId
                }
            }
            if (local == null || remote.updatedAt > local.updatedAt || (local.deleted && !remote.deleted)) {
                val record = remote.toRecord().copy(updatedAt = remote.updatedAt)
                val syncUuid = when {
                    record.syncUuid.isNotBlank() -> record.syncUuid
                    local != null && local.syncUuid.isNotBlank() -> local.syncUuid
                    else -> record.syncUuid
                }
                val finalId = local?.id ?: 0L
                repository.insertRecordForSync(record.copy(id = finalId, syncUuid = syncUuid))
            }
        }

        // 合并 categories
        for (remote in batch.categories) {
            var local: com.verdantgem.ledger.data.model.Category? = null
            if (remote.syncUuid.isNotBlank()) {
                local = categoriesByUuid[remote.syncUuid]
            }
            if (local == null) {
                val byId = categoriesById[remote.id]
                if (byId != null && (byId.syncUuid.isBlank() || byId.syncUuid == remote.syncUuid)) {
                    local = byId
                }
            }
            if (local == null) {
                local = categoriesByKey[Triple(remote.name, remote.parentName, remote.isIncome)]
            }
            if (local == null || remote.updatedAt > local.updatedAt || (local.deleted && !remote.deleted)) {
                val cat = remote.toCategory().copy(updatedAt = remote.updatedAt)
                val syncUuid = when {
                    cat.syncUuid.isNotBlank() -> cat.syncUuid
                    local != null && local.syncUuid.isNotBlank() -> local.syncUuid
                    else -> cat.syncUuid
                }
                val finalId = local?.id ?: 0L
                repository.upsertCategoryForSync(cat.copy(id = finalId, syncUuid = syncUuid))
            }
        }

        // 合并 budgets
        for (remote in batch.budgets) {
            if (localBudget == null || remote.updatedAt > localBudget.updatedAt) {
                val b = remote.toBudget().copy(updatedAt = remote.updatedAt)
                val syncUuid = when {
                    b.syncUuid.isNotBlank() -> b.syncUuid
                    localBudget != null && localBudget.syncUuid.isNotBlank() -> localBudget.syncUuid
                    else -> b.syncUuid
                }
                repository.upsertBudgetForSync(b.copy(syncUuid = syncUuid))
            }
        }

        // 更新同步时间戳
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    private suspend fun compactCheck(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ) {
        val totalChanges = syncChangeLogDao.getTotalCount()
        if (totalChanges > COMPACTION_THRESHOLD) {
            compact(url, user, pass, cryptoPassword, username, dispatcher)
        }
    }

    private suspend fun compact(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ) = withContext(dispatcher) {
        val baseDir = ensureSubPath(url, username)

        try {
            // 1. 构建并上传全量快照（seq 递增）
            val records = repository.getAllRecordsForSync().map { it.toSync() }
            val categories = repository.getAllCategoriesForSync().map { it.toSync() }
            val budgets = repository.getBudgetForSync()?.let { listOf(it.toSync()) } ?: emptyList()

            val newSeq = (prefs.getInt("last_snapshot_seq", 0)) + 1
            val snapshot = SyncSnapshot(
                deviceId = getDeviceId(),
                syncTimestamp = System.currentTimeMillis(),
                records = records,
                categories = categories,
                budgets = budgets
            )
            uploadSnapshot(snapshot, cryptoPassword, baseDir, user, pass, newSeq)

            // 2. 只删除自己的批次文件（不影响其他设备）
            val myDir = deviceChangesUrl(baseDir, getDeviceId())
            val batchFiles = webDavClient.listFiles(myDir, user, pass).getOrNull()
            if (batchFiles != null) {
                for (path in batchFiles) {
                    val name = path.extractName()
                    if (name.endsWith(".json.gz")) {
                        try { webDavClient.deleteFile(myDir + name, user, pass) } catch (_: Exception) {}
                    }
                }
            }

            // 3. 清理本地变更日志 + 更新快照序号
            syncChangeLogDao.deleteAll()
            prefs.edit().putInt("last_snapshot_seq", newSeq).apply()
        } catch (e: Exception) {
            // 压缩失败不应阻断正常同步
        }
    }

    /**
     * 重置同步：清除本设备在远程的批次文件，从本机全量重新上传。
     * 不影响其他设备的数据（快照基线和其他设备的批次文件均保留）。
     * 用于修复本设备同步异常状态，或在新设备上初始化同步组。
     */
    suspend fun resetSync(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String,
        dispatcher: CoroutineContext = Dispatchers.IO
    ): SyncResult = withContext(dispatcher) {
        try {
            _isSyncing.value = true
            mutex.withLock {
                val baseDir = ensureSubPath(url, username)

                // 1. 删除旧格式残留文件（v1/v2 遗留，不影响其他设备）
                try { webDavClient.deleteFile(baseDir + "manifest.json", user, pass) } catch (_: Exception) {}
                try { webDavClient.deleteFile(baseDir + syncFile, user, pass) } catch (_: Exception) {}
                // 注意：不删除快照文件（snapshot_*.json.gz），它们属于所有设备共享的基线

                // 2. 只删除自己的批次文件（不影响其他设备）
                val myDir = deviceChangesUrl(baseDir, getDeviceId())
                try {
                    val batchFiles = webDavClient.listFiles(myDir, user, pass).getOrNull()
                    if (batchFiles != null) {
                        for (path in batchFiles) {
                            val name = path.extractName()
                            if (name.endsWith(".json.gz")) {
                                try { webDavClient.deleteFile(myDir + name, user, pass) } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 3. 清空本地变更日志
                syncChangeLogDao.deleteAll()

                // 4. 清除所有同步状态
                val edit = prefs.edit()
                for (key in prefs.all.keys) {
                    if (key.startsWith("known_batch_") || key.startsWith("remote_known_") ||
                        key == "last_snapshot_seq" || key == "my_last_seq" ||
                        key == "last_compaction_time" || key == "manifest_etag" ||
                        key == "last_remote_etag"
                    ) {
                        edit.remove(key)
                    }
                }
                edit.apply()
                dirty = false

                // 5. 种子全量本地数据 → pushChanges 自动推送
                seedChangeLogFromLocalData()
                val pushResult = pushChanges(url, user, pass, cryptoPassword, username, dispatcher)
                if (pushResult is SyncResult.Error) return@withLock pushResult

                prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
                SyncResult.Success
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Reset sync failed")
        } finally {
            _isSyncing.value = false
        }
    }

    // ---- PROPFIND 辅助方法 ----

    /** 从 PROPFIND href 提取最后一段文件名/目录名，兼容带/不带尾随 / */
    private fun String.extractName(): String = this.trim('/').substringAfterLast('/')

    /** 构建设备批次目录的绝对 URL */
    private fun deviceChangesUrl(baseDir: String, deviceId: String) =
        baseDir + "changes/" + deviceId + "/"

    /** 构建批次文件的绝对 URL */
    private fun batchFileUrl(baseDir: String, deviceId: String, batchNum: Int) =
        deviceChangesUrl(baseDir, deviceId) + String.format("%06d", batchNum) + ".json.gz"

    /** PROPFIND changes/ 目录，返回 Map<deviceId, maxBatchNum> */
    private suspend fun discoverRemoteDevices(baseDir: String, user: String, pass: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val changesBase = baseDir + "changes/"
        val deviceDirs = webDavClient.listFiles(changesBase, user, pass).getOrNull() ?: return result
        for (dirPath in deviceDirs) {
            val deviceId = dirPath.extractName()
            if (deviceId.isBlank() || deviceId == "changes") continue
            val maxBatch = discoverMaxBatch(deviceChangesUrl(baseDir, deviceId), user, pass)
            result[deviceId] = maxBatch
        }
        return result
    }

    /** PROPFIND 根目录，匹配 snapshot_{seq}.json.gz，返回最大 seq */
    private suspend fun discoverSnapshotSeq(baseDir: String, user: String, pass: String): Int {
        val files = webDavClient.listFiles(baseDir, user, pass).getOrNull() ?: return 0
        var maxSeq = 0
        for (path in files) {
            val name = path.extractName()
            if (name.startsWith(SNAPSHOT_PREFIX) && name.endsWith(SNAPSHOT_SUFFIX)) {
                val seqStr = name.removePrefix(SNAPSHOT_PREFIX).removeSuffix(SNAPSHOT_SUFFIX)
                val seq = seqStr.toIntOrNull() ?: continue
                if (seq > maxSeq) maxSeq = seq
            }
        }
        return maxSeq
    }

    /** PROPFIND 指定目录，返回最大批次号（从文件名 000xxx.json.gz 提取） */
    private suspend fun discoverMaxBatch(dirUrl: String, user: String, pass: String): Int {
        val files = webDavClient.listFiles(dirUrl, user, pass).getOrNull() ?: return 0
        var maxBatch = 0
        for (path in files) {
            val name = path.extractName()
            if (name.endsWith(".json.gz")) {
                val numStr = name.removeSuffix(".json.gz")
                val num = numStr.toIntOrNull() ?: continue
                if (num > maxBatch) maxBatch = num
            }
        }
        return maxBatch
    }

    // ---- 文件上传/下载 ----

    private suspend fun uploadSnapshot(
        snapshot: SyncSnapshot, cryptoPassword: String?,
        baseDir: String, user: String, pass: String,
        seq: Int = 1
    ): Boolean {
        val tempFile = File(context.cacheDir, "snapshot_upload_temp")
        try {
            val syncFileWrapper = if (!cryptoPassword.isNullOrBlank()) {
                val snapshotJson = json.encodeToString(SyncSnapshot.serializer(), snapshot)
                val compressed = compress(snapshotJson)
                val compressedBase64 = Base64.getEncoder().encodeToString(compressed)
                val ciphertext = cryptoManager.encrypt(compressedBase64, cryptoPassword)
                SyncFile(encrypted = true, compressed = true, ciphertext = ciphertext)
            } else {
                SyncFile(data = snapshot)
            }
            val outputJson = json.encodeToString(SyncFile.serializer(), syncFileWrapper)
            val compressed = compress(outputJson)
            tempFile.writeBytes(compressed)
            val snapshotUrl = baseDir + SNAPSHOT_PREFIX + seq + SNAPSHOT_SUFFIX
            val result = webDavClient.backup(snapshotUrl, user, pass, tempFile)
            return result.isSuccess
        } catch (_: Exception) {
            return false
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun mergeSnapshot(snapshot: SyncSnapshot) {
        val allRecords = repository.getAllRecordsForSync()
        val localRecordsByUuid = allRecords.associateBy { it.syncUuid }
        val localRecordsById = allRecords.associateBy { it.id }
        val allCategories = repository.getAllCategoriesForSync()
        val localCategoriesByUuid = allCategories.associateBy { it.syncUuid }
        val localCategoriesById = allCategories.associateBy { it.id }
        // 语义匹配 Map：按 (name, parentName, isIncome) 索引，优先保留 active 行
        val localCategoriesByKey = mutableMapOf<Triple<String, String?, Boolean>, com.verdantgem.ledger.data.model.Category>()
        for (cat in allCategories) {
            val key = Triple(cat.name, cat.parentName, cat.isIncome)
            val existing = localCategoriesByKey[key]
            if (existing == null || (existing.deleted && !cat.deleted)) {
                localCategoriesByKey[key] = cat
            }
        }
        val localBudget = repository.getBudgetForSync()

        for (remote in snapshot.records) {
            // 优先 UUID 匹配；失败则回落 id 匹配（兼容旧格式 / 迁移后 UUID 不一致）
            var local: com.verdantgem.ledger.data.model.Record? = null
            if (remote.syncUuid.isNotBlank()) {
                local = localRecordsByUuid[remote.syncUuid]
            }
            if (local == null) {
                val byId = localRecordsById[remote.id]
                if (byId != null && (byId.syncUuid.isBlank() || byId.syncUuid == remote.syncUuid)) {
                    local = byId
                }
            }

            if (local == null || remote.updatedAt > local.updatedAt || (local.deleted && !remote.deleted)) {
                val record = remote.toRecord().copy(updatedAt = remote.updatedAt)
                // UUID 收敛策略：远程有 UUID → 采纳；否则保留本地 UUID
                val syncUuid = when {
                    record.syncUuid.isNotBlank()        -> record.syncUuid
                    local != null && local.syncUuid.isNotBlank() -> local.syncUuid
                    else                                -> record.syncUuid
                }
                // 若按 id 找到本地记录，传入本地 id 更新而非新建
                val finalId = local?.id ?: 0L
                repository.insertRecordForSync(record.copy(id = finalId, syncUuid = syncUuid))
            }
        }

        for (remote in snapshot.categories) {
            var local: com.verdantgem.ledger.data.model.Category? = null
            if (remote.syncUuid.isNotBlank()) {
                local = localCategoriesByUuid[remote.syncUuid]
            }
            if (local == null) {
                val byId = localCategoriesById[remote.id]
                if (byId != null && (byId.syncUuid.isBlank() || byId.syncUuid == remote.syncUuid)) {
                    local = byId
                }
            }
            // 第三回退：按 (name, parentName, isIncome) 语义匹配，处理跨设备 ID 漂移
            if (local == null) {
                local = localCategoriesByKey[Triple(remote.name, remote.parentName, remote.isIncome)]
            }

            if (local == null || remote.updatedAt > local.updatedAt || (local.deleted && !remote.deleted)) {
                val cat = remote.toCategory().copy(updatedAt = remote.updatedAt)
                val syncUuid = when {
                    cat.syncUuid.isNotBlank()            -> cat.syncUuid
                    local != null && local.syncUuid.isNotBlank() -> local.syncUuid
                    else                                 -> cat.syncUuid
                }
                // 若按 id 找到本地记录，传入本地 id 更新而非新建，同时收敛 UUID
                val finalId = local?.id ?: 0L
                repository.upsertCategoryForSync(cat.copy(id = finalId, syncUuid = syncUuid))
            }
        }

        val remoteBudget = snapshot.budgets.firstOrNull()
        if (remoteBudget != null) {
            if (localBudget == null || remoteBudget.updatedAt > localBudget.updatedAt) {
                val b = remoteBudget.toBudget().copy(updatedAt = remoteBudget.updatedAt)
                val syncUuid = when {
                    b.syncUuid.isNotBlank()              -> b.syncUuid
                    localBudget != null && localBudget.syncUuid.isNotBlank() -> localBudget.syncUuid
                    else -> b.syncUuid
                }
                repository.upsertBudgetForSync(b.copy(syncUuid = syncUuid))
            }
        }

        if (snapshot.syncTimestamp > prefs.getLong("last_sync_time", 0)) {
            prefs.edit().putLong("last_sync_time", snapshot.syncTimestamp).apply()
        }
    }

    /**
     * Gzip 压缩
     */
    private fun compress(text: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(text.toByteArray(Charsets.UTF_8))
            gzip.finish()
        }
        return bos.toByteArray()
    }

    /**
     * Gzip 解压
     */
    private fun decompress(bytes: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val MAX_BACKUPS = 5
        private const val COMPACTION_THRESHOLD = 200
        private const val KEY_SYNC_DIRTY = "sync_dirty"
        private const val SNAPSHOT_PREFIX = "snapshot_"
        private const val SNAPSHOT_SUFFIX = ".json.gz"
    }
}
