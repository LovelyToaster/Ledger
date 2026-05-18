package com.verdantgem.ledger.data.remote

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class AddressResult(
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val errorMessage: String? = null
)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getAddress(): AddressResult = withContext(Dispatchers.Main) {
        val client = AMapLocationClient(context.applicationContext)
        client.setLocationOption(
            AMapLocationClientOption().apply {
                isOnceLocation = true
                isNeedAddress = true
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                httpTimeOut = 15000
            }
        )

        try {
            withTimeout(20000L) {
                suspendCancellableCoroutine<AddressResult> { cont ->
                    client.setLocationListener(object : AMapLocationListener {
                        override fun onLocationChanged(location: AMapLocation?) {
                            if (cont.isCancelled) return
                            client.stopLocation()
                            client.onDestroy()
                            if (location != null && location.errorCode == 0) {
                                cont.resume(
                                    AddressResult(
                                        address = extractAddress(location),
                                        latitude = location.latitude,
                                        longitude = location.longitude
                                    )
                                )
                            } else {
                                cont.resume(
                                    AddressResult(
                                        errorMessage = "定位失败[${location?.errorCode ?: -1}]: ${location?.errorInfo ?: "未知错误"}"
                                    )
                                )
                            }
                        }
                    })
                    client.startLocation()
                    cont.invokeOnCancellation {
                        client.stopLocation()
                        client.onDestroy()
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            client.stopLocation()
            client.onDestroy()
            AddressResult(errorMessage = "定位超时(20s)，请检查网络或GPS")
        }
    }

    private fun extractAddress(location: AMapLocation): String {
        return location.poiName
            ?: listOfNotNull(location.street, location.streetNum)
                .joinToString("")
                .ifEmpty { "${location.district}${location.street}" }
                .ifEmpty { location.address }
                .ifEmpty { "${location.province}${location.city}${location.district}${location.street}${location.streetNum}" }
    }
}

class LocationDelegate(private val locationProvider: LocationProvider) {
    private var _address: AddressResult? = null
    private var locationJob: Job? = null

    val address: AddressResult? get() = _address

    fun startLocation(scope: CoroutineScope) {
        val current = _address
        if (current != null && current.errorMessage == null && current.address.isNotEmpty()) return
        if (locationJob?.isActive == true) return
        locationJob = scope.launch {
            try {
                _address = locationProvider.getAddress()
            } catch (_: CancellationException) {
            }
        }
    }

    fun stopLocation() {
        locationJob?.cancel()
    }

    suspend fun joinLocation() {
        locationJob?.join()
    }
}
