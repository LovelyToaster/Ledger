package com.verdantgem.ledger.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdantgem.ledger.data.remote.SyncManager
import com.verdantgem.ledger.data.remote.SyncResult
import com.verdantgem.ledger.data.remote.WebDavClient
import com.verdantgem.ledger.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SyncStatus {
    IDLE, SYNCING, SUCCESS, ERROR
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavClient: WebDavClient,
    private val syncManager: SyncManager
) : ViewModel() {
    private val prefs = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)

    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    var webDavUrl by mutableStateOf("")
        private set
    var webDavUser by mutableStateOf("")
        private set
    var webDavPass by mutableStateOf("")
        private set
    var connectionTestSuccess by mutableStateOf(false)
        private set
    var autoSyncEnabled by mutableStateOf(false)
        private set
    var testResultMessage by mutableStateOf<String?>(null)
        private set
    var isTestingConnection by mutableStateOf(false)
        private set

    var syncUsername by mutableStateOf("")
        private set
    var syncUsernameSet by mutableStateOf(false)
        private set
    var encryptionPassword by mutableStateOf("")
        private set
    var encryptionPasswordSet by mutableStateOf(false)
        private set

    var autoBackupEnabled by mutableStateOf(false)
        private set

    var syncStatus by mutableStateOf(SyncStatus.IDLE)
        private set
    var syncStatusMessage by mutableStateOf<String?>(null)
        private set
    var lastSyncTime by mutableStateOf<String?>(null)
        private set

    init {
        loadConfig()
    }

    fun loadConfig() {
        webDavUrl = prefs.getString("webdav_url", "") ?: ""
        webDavUser = prefs.getString("webdav_user", "") ?: ""
        webDavPass = prefs.getString("webdav_pass", "") ?: ""
        connectionTestSuccess = prefs.getBoolean("webdav_test_success", false)
        autoSyncEnabled = prefs.getBoolean("webdav_auto_sync", false)
        autoBackupEnabled = prefs.getBoolean("webdav_auto_backup", false)
        syncUsername = prefs.getString("sync_username", "") ?: ""
        syncUsernameSet = syncUsername.isNotEmpty()
        encryptionPassword = prefs.getString("webdav_crypto_password", "") ?: ""
        encryptionPasswordSet = encryptionPassword.isNotEmpty()

        val lastSync = prefs.getLong("last_sync_time", 0)
        lastSyncTime = if (lastSync > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(lastSync))
        } else null
    }

    fun saveConfig(url: String, user: String, pass: String) {
        val urlChanged = url != webDavUrl || user != webDavUser || pass != webDavPass
        prefs.edit().apply {
            putString("webdav_url", url)
            putString("webdav_user", user)
            putString("webdav_pass", pass)
            if (urlChanged) putBoolean("webdav_test_success", false)
            apply()
        }
        webDavUrl = url
        webDavUser = user
        webDavPass = pass
        if (urlChanged) connectionTestSuccess = false
        testResultMessage = "已保存"
    }

    fun testConnection(url: String, user: String, pass: String) {
        if (isTestingConnection) return
        isTestingConnection = true
        testResultMessage = null

        viewModelScope.launch {
            val result = webDavClient.testConnection(url, user, pass)
            if (result.isSuccess) {
                prefs.edit().apply {
                    putString("webdav_url", url)
                    putString("webdav_user", user)
                    putString("webdav_pass", pass)
                    putBoolean("webdav_test_success", true)
                    apply()
                }
                webDavUrl = url
                webDavUser = user
                webDavPass = pass
                connectionTestSuccess = true
                testResultMessage = "连接成功"
            } else {
                connectionTestSuccess = false
                testResultMessage = "连接失败: ${result.exceptionOrNull()?.message}"
            }
            isTestingConnection = false
        }
    }

    fun setAutoSync(enabled: Boolean) {
        autoSyncEnabled = enabled
        prefs.edit().putBoolean("webdav_auto_sync", enabled).apply()
    }

    fun updateSyncIdentity(username: String, password: String) {
        syncUsername = username
        syncUsernameSet = username.isNotEmpty()
        encryptionPassword = password
        encryptionPasswordSet = password.isNotEmpty()
        prefs.edit().apply {
            putString("sync_username", username)
            putString("webdav_crypto_password", password)
            apply()
        }
    }

    fun clearSyncIdentity() {
        syncUsername = ""
        syncUsernameSet = false
        encryptionPassword = ""
        encryptionPasswordSet = false
        prefs.edit().apply {
            remove("sync_username")
            remove("webdav_crypto_password")
            apply()
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        autoBackupEnabled = enabled
        prefs.edit().putBoolean("webdav_auto_backup", enabled).apply()
    }

    fun triggerSync() {
        if (syncStatus == SyncStatus.SYNCING) return
        if (webDavUrl.isBlank() || webDavUser.isBlank() || webDavPass.isBlank()) return

        syncStatus = SyncStatus.SYNCING
        syncStatusMessage = "同步中..."

        viewModelScope.launch {
            val cryptoPw = if (encryptionPasswordSet) encryptionPassword else null
            val result = syncManager.fullSync(webDavUrl, webDavUser, webDavPass, cryptoPw)
            when (result) {
                is SyncResult.Success -> {
                    syncStatus = SyncStatus.SUCCESS
                    syncStatusMessage = "同步成功"
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    lastSyncTime = sdf.format(java.util.Date())
                }
                is SyncResult.Error -> {
                    syncStatus = SyncStatus.ERROR
                    syncStatusMessage = "同步失败: ${result.message}"
                }
            }
        }
    }

    fun resetSync() {
        if (syncStatus == SyncStatus.SYNCING) return
        if (webDavUrl.isBlank() || webDavUser.isBlank() || webDavPass.isBlank()) return
        if (syncUsername.isBlank()) return

        syncStatus = SyncStatus.SYNCING
        syncStatusMessage = "重置同步中..."

        viewModelScope.launch {
            val cryptoPw = if (encryptionPasswordSet) encryptionPassword else null
            val result = syncManager.resetSync(webDavUrl, webDavUser, webDavPass, cryptoPw, syncUsername)
            when (result) {
                is SyncResult.Success -> {
                    syncStatus = SyncStatus.SUCCESS
                    syncStatusMessage = "同步重置成功"
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    lastSyncTime = sdf.format(java.util.Date())
                }
                is SyncResult.Error -> {
                    syncStatus = SyncStatus.ERROR
                    syncStatusMessage = "重置失败: ${result.message}"
                }
            }
        }
    }

    fun clearTestResult() {
        testResultMessage = null
    }
}
