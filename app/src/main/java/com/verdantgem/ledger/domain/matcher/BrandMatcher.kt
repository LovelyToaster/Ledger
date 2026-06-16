package com.verdantgem.ledger.domain.matcher

import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Category

/**
 * 统一品牌匹配引擎
 * 消除 DashboardScreen 和 AddRecordScreen 中重复的 matchIn() 逻辑
 *
 * 匹配优先级（从高到低）：
 *   1. 名称精确匹配
 *   2. prompts 精确匹配（优先于品牌映射，避免自学习错误覆盖）
 *   3. 品牌映射精确匹配（仅 confirmCount >= 3 的映射）
 *   4. 品牌映射包含匹配（仅 confirmCount >= 3 的映射）
 *   5. prompts 包含匹配（双向）
 *   6. 名称包含匹配（双向）
 */
object BrandMatcher {

    /** 品牌映射参与匹配所需的最低确认次数 */
    private const val MIN_CONFIRM_COUNT = 3

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
        // 仅筛选已充分确认的品牌映射参与匹配
        val activeMappings = brandMappings.filter { it.confirmCount >= MIN_CONFIRM_COUNT }
        val sortedBrandMappings = activeMappings.sortedWith(
            compareByDescending<BrandMapping> { it.brandName.length }
                .thenByDescending { it.hitCount }
        )
        // 分类列表也按名称稳定排序，避免 firstOrNull 结果抖动
        val sortedCategories = categories.sortedBy { it.name }

        // 第 1 层：精确名称匹配
        val exact = sortedCategories.firstOrNull { it.name == note }
        if (exact != null) return exact

        // 第 2 层：prompts 精确匹配（优先于品牌映射）
        val promptExact = sortedCategories.firstOrNull {
            it.prompts.split(",", "，").any { p -> p.trim() == note }
        }
        if (promptExact != null) return promptExact

        // 第 3 层：品牌映射精确匹配（确认次数充足的映射）
        val exactBm = sortedBrandMappings.firstOrNull { it.brandName == note }
        if (exactBm != null) {
            return sortedCategories.firstOrNull { it.id == exactBm.categoryId }
        }

        // 第 4 层：品牌映射包含匹配（如"蜜雪冰城 柠檬水"包含品牌"蜜雪冰城"）
        val containBm = sortedBrandMappings.firstOrNull { bm ->
            note.contains(bm.brandName)
        }
        if (containBm != null) {
            return sortedCategories.firstOrNull { it.id == containBm.categoryId }
        }

        // 第 5 层：prompts 包含匹配（双向）
        val promptContain = sortedCategories.firstOrNull {
            it.prompts.split(",", "，").any { p ->
                note.contains(p.trim()) || p.trim().contains(note)
            }
        }
        if (promptContain != null) return promptContain

        // 第 6 层：名称包含匹配（双向）
        return sortedCategories.firstOrNull {
            note.contains(it.name) || it.name.contains(note)
        }
    }
}
