package com.verdantgem.ledger.domain.matcher

import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Category

/**
 * 统一品牌匹配引擎
 * 消除 DashboardScreen 和 AddRecordScreen 中重复的 matchIn() 逻辑
 *
 * 匹配优先级（从高到低）：
 *   0. 品牌映射精确匹配（备注文本完全等于品牌名）
 *   1. 品牌映射包含匹配（备注文本中包含品牌名）
 *   2. 原 4 层匹配：名称精确 → prompts精确 → prompts包含 → 名称包含
 */
object BrandMatcher {

    /**
     * 在给定分类列表中查找匹配的分类
     * @param note 用户输入的备注文本
     * @param categories 候选分类列表（已按 isIncome 过滤）
     * @param brandMappings 全量品牌映射列表
     * @return 匹配到的分类，无匹配则返回 null
     */
    fun matchNote(
        note: String,
        categories: List<Category>,
        brandMappings: List<BrandMapping>
    ): Category? {
        // 第 0 层：品牌映射精确匹配（备注文本完全等于品牌名）
        val exactBm = brandMappings.firstOrNull { it.brandName == note }
        if (exactBm != null) {
            return categories.firstOrNull { it.id == exactBm.categoryId }
        }

        // 第 1 层：品牌映射包含匹配（备注文本中包含品牌名）
        // 如"蜜雪冰城 柠檬水"包含品牌"蜜雪冰城"，匹配到饮料酒水
        val containBm = brandMappings.firstOrNull { bm ->
            note.contains(bm.brandName)
        }
        if (containBm != null) {
            return categories.firstOrNull { it.id == containBm.categoryId }
        }

        // 第 2-5 层：原有匹配逻辑（名称精确 → prompts精确 → prompts包含 → 名称包含）
        return legacyMatchIn(note, categories)
    }

    /**
     * 原有的 4 层匹配逻辑
     */
    internal fun legacyMatchIn(note: String, list: List<Category>): Category? {
        // 第 1 层：精确名称匹配
        val exact = list.firstOrNull { it.name == note }
        if (exact != null) return exact

        // 第 2 层：prompts 精确匹配
        val promptExact = list.firstOrNull {
            it.prompts.split(",", "，").any { p -> p.trim() == note }
        }
        if (promptExact != null) return promptExact

        // 第 3 层：prompts 包含匹配（双向）
        val promptContain = list.firstOrNull {
            it.prompts.split(",", "，").any { p ->
                note.contains(p.trim()) || p.trim().contains(note)
            }
        }
        if (promptContain != null) return promptContain

        // 第 4 层：名称包含匹配（双向）
        return list.firstOrNull {
            note.contains(it.name) || it.name.contains(note)
        }
    }
}
