package com.verdantgem.ledger.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 品牌→分类映射实体，用于快速记账自动匹配未知品牌
 * @param id 本地自增主键
 * @param brandName 品牌名称（如"星巴克"）
 * @param categoryId 指向 categories.id
 * @param source 来源："default"（预置）/ "user"（用户自学习）
 * @param hitCount 命中次数（用于热度排序）
 * @param confirmCount 确认次数，>=3 才参与匹配（自学习需用户多次确认）
 * @param missCount 命中但未被用户采纳的次数，>=3 自动删除
 */
@Entity(
    tableName = "brand_mappings",
    indices = [Index(value = ["brandName"], unique = true)]
)
data class BrandMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandName: String,
    val categoryId: Long,
    val source: String = "default",
    val hitCount: Int = 0,
    val confirmCount: Int = 0,
    val missCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
