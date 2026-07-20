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
                    .map { it.trimStart('/').substringAfterLast('/') }
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

                        // 1. 升级检测：v1 → v2 迁移
                        val upgradeResult = upgradeToIncremental(url, user, pass, cryptoPassword, syncUsername, dispatcher)
                        if (upgradeResult is SyncResult.Error) return@withContext upgradeResult

                        // 1.5 新设备加入：变更日志为空时，从本地数据种子初始变更
                        seedChangeLogFromLocalData()

                        // 2. 拉取远程变更
                        val pullResult = pullChanges(url, user, pass, cryptoPassword, syncUsername, dispatcher)
                        if (pullResult is SyncResult.Error && pullResult.message != "Remote manifest not found") {
                            return@withContext pullResult
                        }

                        // 3. 推送本地变更
                        val pushResult = pushChanges(url, user, pass, cryptoPassword, syncUsername, dispatcher)

                        // 4. 压缩检查
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

    private suspend fun upgradeToIncremental(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ): SyncResult = withContext(dispatcher) {
        val baseDir = ensureSubPath(url, username)
        val manifestUrl = baseDir + MANIFEST_FILE

        // 检查 manifest 是否已存在
        val headResult = webDavClient.head(manifestUrl, user, pass)
        if (headResult.isSuccess) return@withContext SyncResult.Success // v2 已初始化

        // 升级路径：检查旧 ledger_sync.json
        val oldSyncUrl = baseDir + syncFile
        val oldHeadResult = webDavClient.head(oldSyncUrl, user, pass)

        if (oldHeadResult.isSuccess) {
            // 存在旧格式文件：下载并合并作为种子数据
            val tempFile = File(context.cacheDir, "sync_upgrade_temp")
            try {
                val downloadResult = webDavClient.restore(oldSyncUrl, user, pass, tempFile)
                if (downloadResult.isSuccess) {
                    val rawBytes = tempFile.readBytes()
                    val rawJson = decompress(rawBytes)
                    val syncFileWrapper = json.decodeFromString<SyncFile>(rawJson)
                    val snapshot: SyncSnapshot? = if (syncFileWrapper.encrypted) {
                        if (!cryptoPassword.isNullOrBlank()) {
                            val decrypted = cryptoManager.decrypt(syncFileWrapper.ciphertext, cryptoPassword)
                            val snapshotJson = if (syncFileWrapper.compressed) {
                                val compressed = Base64.getDecoder().decode(decrypted)
                                decompress(compressed)
                            } else decrypted
                            json.decodeFromString<SyncSnapshot>(snapshotJson)
                        } else null
                    } else syncFileWrapper.data

                    if (snapshot != null) {
                        mergeSnapshot(snapshot) // 复用旧的全量合并逻辑
                    }
                }
            } catch (_: Exception) {
                // 旧文件解析失败，忽略，从本地 DB 构建基线
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }

            // 删除旧的 ledger_sync.json
            try { webDavClient.deleteFile(oldSyncUrl, user, pass) } catch (_: Exception) {}
        }

        // 从本地数据库构建初始快照
        val records = repository.getAllRecordsForSync().map { it.toSync() }
        val categories = repository.getAllCategoriesForSync().map { it.toSync() }
        val budgets = repository.getBudgetForSync()?.let { listOf(it.toSync()) } ?: emptyList()

        val snapshot = SyncSnapshot(
            deviceId = getDeviceId(),
            syncTimestamp = System.currentTimeMillis(),
            records = records,
            categories = categories,
            budgets = budgets
        )

        // 上传初始快照
        if (!uploadSnapshot(snapshot, cryptoPassword, baseDir, user, pass)) {
            return@withContext SyncResult.Error("Failed to upload initial snapshot")
        }

        // 创建并上传 manifest
        val manifest = SyncManifest(
            version = 2,
            snapshotSeq = 1,
            devices = mapOf(getDeviceId() to DeviceState(batch = 0, seq = 0))
        )
        if (!uploadManifest(manifest, manifestUrl, user, pass)) {
            return@withContext SyncResult.Error("Failed to upload initial manifest")
        }

        // 保存本地状态
        saveLocalSeqKeys(manifest)
        prefs.edit().putString(KEY_MANIFEST_ETAG, null).apply()

        SyncResult.Success
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
        val changesDir = baseDir + "changes/${getDeviceId()}/"
        val manifestUrl = baseDir + MANIFEST_FILE

        try {
            // 确保 changes/{deviceId}/ 目录存在
            webDavClient.mkdir(baseDir + "changes/", user, pass)
            webDavClient.mkdir(changesDir, user, pass)

            // 读取当前 manifest 获取下一个批次号
            var currentManifest = downloadManifest(manifestUrl, user, pass)
            if (currentManifest == null) {
                // manifest 不存在（首次同步或远程被清空）：创建初始 manifest
                val records = repository.getAllRecordsForSync().map { it.toSync() }
                val categories = repository.getAllCategoriesForSync().map { it.toSync() }
                val budgets = repository.getBudgetForSync()?.let { listOf(it.toSync()) } ?: emptyList()
                val snapshot = SyncSnapshot(
                    deviceId = getDeviceId(), syncTimestamp = System.currentTimeMillis(),
                    records = records, categories = categories, budgets = budgets
                )
                uploadSnapshot(snapshot, cryptoPassword, baseDir, user, pass)
                currentManifest = SyncManifest(
                    version = 2, snapshotSeq = 1,
                    devices = mapOf(getDeviceId() to DeviceState(batch = 0, seq = 0))
                )
                if (!uploadManifest(currentManifest!!, manifestUrl, user, pass)) {
                    return@withContext SyncResult.Error("Failed to create initial manifest")
                }
            }
            val myState = currentManifest.devices[getDeviceId()] ?: DeviceState()
            val nextBatch = myState.batch + 1
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
            val compressed = compress(batchJsonStr)

            val tempFile = File(context.cacheDir, "sync_batch_temp")
            try {
                tempFile.writeBytes(compressed)

                // 上传批次文件
                val batchUrl = "${changesDir}${String.format("%06d", nextBatch)}.json.gz"
                val uploadResult = webDavClient.backup(batchUrl, user, pass, tempFile)
                if (uploadResult.isFailure) {
                    return@withContext SyncResult.Error(uploadResult.exceptionOrNull()?.message ?: "Batch upload failed")
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }

            // 乐观锁更新 manifest（使用读取-修改-写入窗口 + 重试）
            var manifestUpdated = false
            var retries = 0
            while (!manifestUpdated && retries < MAX_MANIFEST_RETRIES) {
                val manifest = downloadManifest(manifestUrl, user, pass)
                if (manifest == null) {
                    retries++
                    continue
                }
                val devices = manifest.devices.toMutableMap()
                devices[getDeviceId()] = DeviceState(batch = nextBatch, seq = maxSeq)
                val newManifest = manifest.copy(devices = devices)

                val success = uploadManifest(newManifest, manifestUrl, user, pass)
                if (success) {
                    manifestUpdated = true
                    // 保存 ETag 用于后续
                    val headRes = webDavClient.head(manifestUrl, user, pass)
                    if (headRes.isSuccess) {
                        prefs.edit().putString(KEY_MANIFEST_ETAG, headRes.getOrThrow()["etag"]).apply()
                    }
                }
                retries++
            }

            if (!manifestUpdated) {
                return@withContext SyncResult.Error("Failed to update manifest after $MAX_MANIFEST_RETRIES retries")
            }

            // 仅在 manifest 成功更新后标记已推送
            syncChangeLogDao.markSynced(maxSeq)
            dirty = false

            // 保存本地同步位置
            saveLocalSeqKeys(newManifest = null, myMaxSeq = maxSeq)

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
        val manifestUrl = baseDir + MANIFEST_FILE

        val manifest = downloadManifest(manifestUrl, user, pass)
            ?: return@withContext SyncResult.Error("Remote manifest not found")

        var anyApplied = false
        val myDeviceId = getDeviceId()

        // 检查是否需要拉取新的基线快照
        val lastSnapshotSeq = prefs.getInt("last_snapshot_seq", 0)
        if (manifest.snapshotSeq > lastSnapshotSeq) {
            val snapshotUrl = baseDir + SNAPSHOT_FILE
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
                        prefs.edit().putInt("last_snapshot_seq", manifest.snapshotSeq).apply()
                        anyApplied = true
                    }
                }
            } catch (_: Exception) {
                // 快照下载失败不阻断，继续拉取批次
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

        // 一次性加载全量数据用于合并（避免每个批次重复加载）
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

        for ((deviceId, deviceState) in manifest.devices) {
            if (deviceId == myDeviceId) continue // 跳过自己的

            val knownSeq = prefs.getInt("remote_known_seq_$deviceId", 0)
            val knownBatch = prefs.getInt("remote_known_batch_$deviceId", 0)

            if (deviceState.seq <= knownSeq) continue // 无新变更

            val changesDir = baseDir + "changes/$deviceId/"
            val batchJson = Json { ignoreUnknownKeys = true }

            // 逐个下载新批次
            var appliedMaxBatch = knownBatch
            for (batchNum in (knownBatch + 1)..deviceState.batch) {
                val batchUrl = "${changesDir}${String.format("%06d", batchNum)}.json.gz"
                val tempFile = File(context.cacheDir, "sync_pull_batch_temp")
                try {
                    val downloadResult = webDavClient.restore(batchUrl, user, pass, tempFile)
                    if (downloadResult.isFailure) {
                        break // 批次不可用则停止，不跳过继续
                    }

                    val compressed = tempFile.readBytes()
                    val batchJsonStr = decompress(compressed)
                    val batch = batchJson.decodeFromString<SyncChangeBatch>(batchJsonStr)

                    mergeChangeBatch(
                        batch, recordsByUuid, recordsById,
                        categoriesByUuid, categoriesById, categoriesByKey, localBudget
                    )
                    appliedMaxBatch = batchNum
                    anyApplied = true
                } catch (e: Exception) {
                    break // 解析失败也停止，不跳过
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            // 仅推进到成功处理的批次
            if (appliedMaxBatch > knownBatch) {
                prefs.edit()
                    .putInt("remote_known_seq_$deviceId", deviceState.seq)
                    .putInt("remote_known_batch_$deviceId", appliedMaxBatch)
                    .apply()
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
        val lastCompact = prefs.getLong(KEY_LAST_COMPACTION, 0L)
        val hoursSinceCompact = (System.currentTimeMillis() - lastCompact) / 3600_000L

        // 条件：变更日志 > 200 条 或 距上次压缩 > 24 小时
        if (totalChanges > COMPACTION_THRESHOLD || (hoursSinceCompact > 24 && totalChanges > 0)) {
            compact(url, user, pass, cryptoPassword, username, dispatcher)
        }
    }

    private suspend fun compact(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ) = withContext(dispatcher) {
        val baseDir = ensureSubPath(url, username)
        val manifestUrl = baseDir + MANIFEST_FILE

        try {
            // 1. 构建并上传全量快照
            val records = repository.getAllRecordsForSync().map { it.toSync() }
            val categories = repository.getAllCategoriesForSync().map { it.toSync() }
            val budgets = repository.getBudgetForSync()?.let { listOf(it.toSync()) } ?: emptyList()

            val snapshot = SyncSnapshot(
                deviceId = getDeviceId(),
                syncTimestamp = System.currentTimeMillis(),
                records = records,
                categories = categories,
                budgets = budgets
            )
            uploadSnapshot(snapshot, cryptoPassword, baseDir, user, pass)

            // 2. 更新 manifest：递增 snapshotSeq，保留各设备 seq/batch
            val manifest = downloadManifest(manifestUrl, user, pass) ?: return@withContext
            val newManifest = manifest.copy(snapshotSeq = manifest.snapshotSeq + 1)
            uploadManifest(newManifest, manifestUrl, user, pass)

            // 3. 清理远程旧的批次文件
            for (deviceId in manifest.devices.keys) {
                val deviceChangesDir = baseDir + "changes/$deviceId/"
                try {
                    val files = webDavClient.listFiles(deviceChangesDir, user, pass)
                    files.getOrNull()?.forEach { filePath ->
                        val fileName = filePath.trimStart('/').substringAfterLast('/')
                        if (fileName.endsWith(".json.gz")) {
                            try { webDavClient.deleteFile(deviceChangesDir + fileName, user, pass) } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. 清理本地变更日志中已同步的条目
            val maxSeq = syncChangeLogDao.getMaxSeq()
            syncChangeLogDao.deleteSyncedUpTo(maxSeq)

            // 5. 保存压缩时间（不重置设备 knownSeq/batch，由 pullChanges 消费 snapshotSeq）
            prefs.edit()
                .putLong(KEY_LAST_COMPACTION, System.currentTimeMillis())
                .putInt("last_snapshot_seq", newManifest.snapshotSeq)
                .apply()
        } catch (e: Exception) {
            // 压缩失败不应阻断正常同步
        }
    }

    private suspend fun downloadManifest(manifestUrl: String, user: String, pass: String): SyncManifest? {
        val tempFile = File(context.cacheDir, "manifest_download_temp")
        try {
            val result = webDavClient.restore(manifestUrl, user, pass, tempFile)
            if (result.isFailure) return null
            val content = tempFile.readText(Charsets.UTF_8)
            return try {
                json.decodeFromString<SyncManifest>(content)
            } catch (_: Exception) { null }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun uploadManifest(manifest: SyncManifest, manifestUrl: String, user: String, pass: String): Boolean {
        val tempFile = File(context.cacheDir, "manifest_upload_temp")
        try {
            tempFile.writeText(json.encodeToString(SyncManifest.serializer(), manifest))
            val result = webDavClient.backup(manifestUrl, user, pass, tempFile)
            return result.isSuccess
        } catch (_: Exception) {
            return false
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun uploadSnapshot(
        snapshot: SyncSnapshot, cryptoPassword: String?,
        baseDir: String, user: String, pass: String
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
            val result = webDavClient.backup(baseDir + SNAPSHOT_FILE, user, pass, tempFile)
            return result.isSuccess
        } catch (_: Exception) {
            return false
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun saveLocalSeqKeys(newManifest: SyncManifest? = null, myMaxSeq: Int? = null) {
        val edit = prefs.edit()
        val myDeviceId = getDeviceId()
        if (myMaxSeq != null) {
            edit.putInt("my_last_seq", myMaxSeq)
        }
        if (newManifest != null) {
            for ((deviceId, state) in newManifest.devices) {
                if (deviceId != myDeviceId) {
                    edit.putInt("remote_known_seq_$deviceId", state.seq)
                    edit.putInt("remote_known_batch_$deviceId", state.batch)
                }
            }
        }
        edit.apply()
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
        private const val MAX_MANIFEST_RETRIES = 3
        private const val COMPACTION_THRESHOLD = 200
        private const val KEY_LAST_REMOTE_ETAG = "last_remote_etag"
        private const val KEY_SYNC_DIRTY = "sync_dirty"
        private const val KEY_LAST_COMPACTION = "last_compaction_time"
        private const val KEY_MANIFEST_ETAG = "manifest_etag"
        private const val MANIFEST_FILE = "manifest.json"
        private const val SNAPSHOT_FILE = "snapshot.json.gz"
    }
}
