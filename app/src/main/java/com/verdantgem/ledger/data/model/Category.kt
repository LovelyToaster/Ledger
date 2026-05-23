package com.verdantgem.ledger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 分类实体
 * @param id 唯一标识（本地自增主键）
 * @param syncUuid 同步用全局唯一标识
 * @param name 分类名称（如：水费）
 * @param parentName 父分类名称（如：居家生活）
 * @param icon 图标名称
 * @param isIncome 是否为收入分类
 * @param prompts 提示词（逗号分隔），用于快速记账自动匹配
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String = UUID.randomUUID().toString(),
    val name: String,
    val parentName: String? = null,
    val icon: String = "default_icon",
    val isIncome: Boolean = false,
    val prompts: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)
