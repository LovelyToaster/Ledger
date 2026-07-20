package com.verdantgem.ledger.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 同步元数据清单，存储在 WebDAV 上的 manifest.json
 */
@Serializable
data class SyncManifest(
    @SerialName("v") val version: Int = 2,
    @SerialName("snapshotSeq") val snapshotSeq: Int = 0,
    @SerialName("devices") val devices: Map<String, DeviceState> = emptyMap()
)

@Serializable
data class DeviceState(
    @SerialName("batch") val batch: Int = 0,   // 该设备最后上传的批次号
    @SerialName("seq") val seq: Int = 0         // 该设备最后上传的 seq 号
)
