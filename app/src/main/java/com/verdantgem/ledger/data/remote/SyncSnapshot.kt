package com.verdantgem.ledger.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncFile(
    @SerialName("v") val version: Int = 1,
    @SerialName("e") val encrypted: Boolean = false,
    @SerialName("z") val compressed: Boolean = false,
    @SerialName("d") val data: SyncSnapshot? = null,
    @SerialName("s") val salt: String = "",
    @SerialName("i") val iv: String = "",
    @SerialName("c") val ciphertext: String = ""
)

@Serializable
data class SyncSnapshot(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("syncTimestamp") val syncTimestamp: Long,
    @SerialName("records") val records: List<SyncRecord>,
    @SerialName("categories") val categories: List<SyncCategory>,
    @SerialName("budgets") val budgets: List<SyncBudget>
)

@Serializable
data class SyncRecord(
    @SerialName("id") val id: Long,
    @SerialName("suuid") val syncUuid: String = "",
    @SerialName("amount") val amount: Double,
    @SerialName("categoryId") val categoryId: Long,
    @SerialName("categoryName") val categoryName: String,
    @SerialName("note") val note: String,
    @SerialName("date") val date: Long,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long,
    @SerialName("deleted") val deleted: Boolean = false,
    @SerialName("exf") val excludeFromBudget: Boolean = false,
    @SerialName("address") val address: String = "",
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null
)

@Serializable
data class SyncCategory(
    @SerialName("id") val id: Long,
    @SerialName("suuid") val syncUuid: String = "",
    @SerialName("name") val name: String,
    @SerialName("parentName") val parentName: String? = null,
    @SerialName("icon") val icon: String = "default_icon",
    @SerialName("isIncome") val isIncome: Boolean = false,
    @SerialName("prompts") val prompts: String = "",
    @SerialName("updatedAt") val updatedAt: Long,
    @SerialName("deleted") val deleted: Boolean = false
)

@Serializable
data class SyncBudget(
    @SerialName("id") val id: Int = 1,
    @SerialName("suuid") val syncUuid: String = "",
    @SerialName("monthlyAmount") val monthlyAmount: Double,
    @SerialName("updatedAt") val updatedAt: Long,
    @SerialName("deleted") val deleted: Boolean = false
)

fun SyncRecord.toRecord() = com.verdantgem.ledger.data.model.Record(
    id = id, syncUuid = syncUuid, amount = amount, categoryId = categoryId,
    categoryName = categoryName, note = note, date = date,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
    excludeFromBudget = excludeFromBudget,
    address = address, latitude = latitude, longitude = longitude
)

fun com.verdantgem.ledger.data.model.Record.toSync() = SyncRecord(
    id = id, syncUuid = syncUuid, amount = amount, categoryId = categoryId,
    categoryName = categoryName, note = note, date = date,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
    excludeFromBudget = excludeFromBudget,
    address = address, latitude = latitude, longitude = longitude
)

fun SyncCategory.toCategory() = com.verdantgem.ledger.data.model.Category(
    id = id, syncUuid = syncUuid, name = name, parentName = parentName, icon = icon,
    isIncome = isIncome, prompts = prompts, updatedAt = updatedAt, deleted = deleted
)

fun com.verdantgem.ledger.data.model.Category.toSync() = SyncCategory(
    id = id, syncUuid = syncUuid, name = name, parentName = parentName, icon = icon,
    isIncome = isIncome, prompts = prompts, updatedAt = updatedAt, deleted = deleted
)

fun SyncBudget.toBudget() = com.verdantgem.ledger.data.model.Budget(
    id = id, syncUuid = syncUuid, monthlyAmount = monthlyAmount, updatedAt = updatedAt, deleted = deleted
)

fun com.verdantgem.ledger.data.model.Budget.toSync() = SyncBudget(
    id = id, syncUuid = syncUuid, monthlyAmount = monthlyAmount, updatedAt = updatedAt, deleted = deleted
)
