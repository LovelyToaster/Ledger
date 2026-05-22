package com.verdantgem.ledger.data.remote

import android.content.Context
import android.net.Uri
import com.verdantgem.ledger.data.DataChangeNotifier
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
    private val changeNotifier: DataChangeNotifier
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)
    private val syncFile = "ledger_sync.json"
    private var debounceJob: Job? = null
    private val dirty = AtomicBoolean(false)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var backgroundedAt = 0L

    fun markDirty() { dirty.set(true) }

    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            changeNotifier.changes.collect {
                dirty.set(true)
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(30_000L)
                    val cfg = readConfig() ?: return@launch
                    if (fullSync(cfg.url, cfg.user, cfg.pass, cfg.cryptoPw) is SyncResult.Success) {
                        dirty.set(false)
                    }
                }
            }
        }
    }

    suspend fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
        val cfg = readConfig()
        if (cfg != null) {
            if (dirty.get()) {
                if (cfg.autoSync) {
                    debounceJob?.cancel()
                    if (fullSync(cfg.url, cfg.user, cfg.pass, cfg.cryptoPw) is SyncResult.Success) {
                        dirty.set(false)
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
        try {
            val username = prefs.getString("sync_username", "") ?: ""
            if (username.isBlank()) return@withContext SyncResult.Error("Sync username not configured")

            val dbFile = context.getDatabasePath("ledger_db")
            if (!dbFile.exists()) return@withContext SyncResult.Error("Database not found")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFilename = "ledger_backup_$timestamp.db"
            val remoteDir = ensureSubPath(url, username)
            val remoteUrl = remoteDir + backupFilename

            webDavClient.mkdir(remoteDir, user, pass)
            val result = webDavClient.backup(remoteUrl, user, pass, dbFile)
            if (result.isFailure) {
                return@withContext SyncResult.Error(result.exceptionOrNull()?.message ?: "Backup upload failed")
            }

            val baseUrl = ensureSubPath(url, username)
            val listResult = webDavClient.listFiles(baseUrl, user, pass)
            if (listResult.isSuccess) {
                val backups = listResult.getOrThrow()
                    .map { it.trimStart('/').substringAfterLast('/') }
                    .filter { it.startsWith("ledger_backup_") && it.endsWith(".db") }
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
                        val pullResult = pullAndMerge(url, user, pass, cryptoPassword, syncUsername, dispatcher)
                        if (pullResult is SyncResult.Error && pullResult.message != "Remote file not found") {
                            return@withContext pullResult
                        }
                        val pushResult = push(url, user, pass, cryptoPassword, syncUsername, dispatcher)
                        if (pushResult is SyncResult.Success) {
                            dirty.set(false)
                        }
                        pushResult
                    } catch (e: Exception) {
                        SyncResult.Error(e.message ?: "Unknown sync error")
                    }
                }
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun pullAndMerge(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ): SyncResult = withContext(dispatcher) {
        val tempFile = File(context.cacheDir, "sync_download_temp")
        try {
            val remoteUrl = ensureSubPath(url, username) + syncFile
            val downloadResult = webDavClient.restore(remoteUrl, user, pass, tempFile)
            if (downloadResult.isFailure) {
                val error = downloadResult.exceptionOrNull()
                return@withContext if (error?.message?.contains("404") == true || error?.message?.contains("403") == true) {
                    SyncResult.Error("Remote file not found")
                } else {
                    SyncResult.Error(error?.message ?: "Download failed")
                }
            }

            val rawJson = tempFile.readText()
            val syncFileWrapper = json.decodeFromString<SyncFile>(rawJson)
            val snapshot = if (syncFileWrapper.encrypted) {
                if (cryptoPassword.isNullOrBlank()) {
                    return@withContext SyncResult.Error("Encryption password required")
                }
                val decrypted = cryptoManager.decrypt(syncFileWrapper.ciphertext, cryptoPassword)
                json.decodeFromString<SyncSnapshot>(decrypted)
            } else {
                syncFileWrapper.data ?: return@withContext SyncResult.Error("Empty sync data")
            }

            mergeSnapshot(snapshot)
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Merge failed")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun mergeSnapshot(snapshot: SyncSnapshot) {
        val localRecords = repository.getAllRecordsForSync().associateBy { it.id }
        val localCategories = repository.getAllCategoriesForSync().associateBy { it.id }
        val localBudget = repository.getBudgetForSync()

        for (remote in snapshot.records) {
            val local = localRecords[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                repository.insertRecordForSync(remote.toRecord().copy(updatedAt = remote.updatedAt))
            } else if (local.deleted && !remote.deleted && remote.updatedAt > local.updatedAt) {
                repository.insertRecordForSync(remote.toRecord().copy(updatedAt = remote.updatedAt))
            }
        }

        for (remote in snapshot.categories) {
            val local = localCategories[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                repository.upsertCategoryForSync(remote.toCategory().copy(updatedAt = remote.updatedAt))
            }
        }

        val remoteBudget = snapshot.budgets.firstOrNull()
        if (remoteBudget != null) {
            if (localBudget == null || remoteBudget.updatedAt > localBudget.updatedAt) {
                repository.upsertBudgetForSync(remoteBudget.toBudget().copy(updatedAt = remoteBudget.updatedAt))
            }
        }

        if (snapshot.syncTimestamp > prefs.getLong("last_sync_time", 0)) {
            prefs.edit().putLong("last_sync_time", snapshot.syncTimestamp).apply()
        }
    }

    private suspend fun push(
        url: String, user: String, pass: String,
        cryptoPassword: String?, username: String, dispatcher: CoroutineContext
    ): SyncResult = withContext(dispatcher) {
        val tempFile = File(context.cacheDir, "sync_upload_temp")
        try {
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

            val syncFileWrapper = if (!cryptoPassword.isNullOrBlank()) {
                val snapshotJson = json.encodeToString(SyncSnapshot.serializer(), snapshot)
                val ciphertext = cryptoManager.encrypt(snapshotJson, cryptoPassword)
                SyncFile(encrypted = true, ciphertext = ciphertext)
            } else {
                SyncFile(data = snapshot)
            }

            val outputJson = json.encodeToString(SyncFile.serializer(), syncFileWrapper)
            tempFile.writeText(outputJson)

            val remoteUrl = ensureSubPath(url, username) + syncFile
            val uploadResult = webDavClient.backup(remoteUrl, user, pass, tempFile)
            if (uploadResult.isFailure) {
                return@withContext SyncResult.Error(
                    uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                )
            }

            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Push failed")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    companion object {
        private const val MAX_BACKUPS = 5
    }
}
