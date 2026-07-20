package com.verdantgem.ledger.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单次同步推送的变更批次
 * 存储在 changes/{deviceId}/{batchNum}.json.gz
 */
@Serializable
data class SyncChangeBatch(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("batchNum") val batchNum: Int,
    @SerialName("records") val records: List<SyncRecord> = emptyList(),
    @SerialName("categories") val categories: List<SyncCategory> = emptyList(),
    @SerialName("budgets") val budgets: List<SyncBudget> = emptyList()
)

/**
 * 批次文件外层包装，支持可选加密（与 SyncFile 同模式）
 */
@Serializable
data class BatchFile(
    @SerialName("e") val encrypted: Boolean = false,
    @SerialName("d") val data: String? = null,
    @SerialName("c") val ciphertext: String = ""
)
