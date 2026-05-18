package com.verdantgem.ledger

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.amap.api.location.AMapLocationClient
import com.verdantgem.ledger.data.repository.LedgerRepository
import com.verdantgem.ledger.data.remote.SyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LedgerApplication : Application() {

    @Inject lateinit var repository: LedgerRepository
    @Inject lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        val appScope = CoroutineScope(Dispatchers.IO)
        syncManager.startObserving(appScope)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                appScope.launch {
                    syncManager.onAppBackgrounded()
                }
            }
        })

        appScope.launch {
            repository.seedDefaultCategories()

            val prefs = getSharedPreferences("ledger_settings", MODE_PRIVATE)
            val url = prefs.getString("webdav_url", "") ?: ""
            val user = prefs.getString("webdav_user", "") ?: ""
            val pass = prefs.getString("webdav_pass", "") ?: ""
            val autoSync = prefs.getBoolean("webdav_auto_sync", false)
            val cryptoPw = prefs.getString("webdav_crypto_password", null)
            if (url.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && autoSync) {
                syncManager.fullSync(url, user, pass, cryptoPw)
            }
        }
    }
}
