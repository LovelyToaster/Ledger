package com.verdantgem.ledger.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_change_log",
    indices = [
        Index("seq", unique = true),
        Index("synced")
    ]
)
data class SyncChangeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seq: Int,
    val entity: String,        // "record" / "category" / "budget"
    val uuid: String,          // 实体的 syncUuid
    val operation: String,     // "upsert" / "delete"
    val data: String,          // 变更时实体的完整 JSON（SyncRecord/SyncCategory/SyncBudget 序列化）
    val changedAt: Long,       // updatedAt 时间戳
    val deviceId: String,
    val synced: Boolean = false  // false=待推送, true=已推送
)
