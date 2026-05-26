package com.verdantgem.ledger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * 账目记录实体
 * @param id 唯一标识（本地自增主键）
 * @param syncUuid 同步用全局唯一标识
 * @param amount 金额（正数为支出或收入，由 category 决定）
 * @param categoryId 关联分类 ID
 * @param note 备注或事由
 * @param date 记账日期
 * @param createdAt 创建时间戳
 * @param address 地点地址文本
 * @param latitude 纬度
 * @param longitude 经度
 */
@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String = UUID.randomUUID().toString(),
    val amount: Double,
    val categoryId: Long,
    val categoryName: String,
    val note: String,
    val date: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)
