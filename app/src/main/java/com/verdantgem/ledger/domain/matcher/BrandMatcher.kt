package com.verdantgem.ledger.domain.matcher

import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Category

/**
 * 统一品牌匹配引擎
 * 消除 DashboardScreen 和 AddRecordScreen 中重复的 matchIn() 逻辑
 *
 * 匹配优先级（从高到低，level 数字越小越精确）：
 *   1. 名称精确匹配
 *   2. 品牌映射精确匹配（仅 confirmCount >= MIN_CONFIRM_COUNT 的映射）
 *   3. prompts 精确匹配
 *   4. 品牌映射包含匹配（仅 confirmCount >= MIN_CONFIRM_COUNT 的映射）
 *   5. prompts 单向包含匹配（note.contains(prompt)）
 *
 * 相较旧版：
 * - 将品牌映射精确匹配提到 prompts 精确之前，用户维护的品牌享有更高信任
 * - 移除 Category.name 双向包含匹配（易误伤短名称）
 * - prompts 包含匹配改为单向，避免短 note 反向命中长 prompt
 */
object BrandMatcher {

    /** 品牌映射参与匹配所需的最低确认次数 */
    private const val MIN_CONFIRM_COUNT = 3

    /** 匹配结果，携带命中层级，用于跨调用方比较优先级 */
    data class MatchResult(val category: Category, val level: Int)

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
    ): Category? = matchNoteInternal(note, categories, brandMappings)?.category

    /**
     * 在全量分类中同时对收入/支出候选进行匹配，返回命中层级更精确的一方。
     * 若两侧层级相同，优先返回支出候选（沿用原始默认策略）。
     *
     * 调用方无需再手写 incomeMatch/expenseMatch + when 消歧。
     *
     * @param note 用户输入的备注文本
     * @param allCategories 全量分类（内部自动按 isIncome 拆分）
     * @param brandMappings 全量品牌映射列表
     * @return 最优匹配分类，无匹配则返回 null
     */
    fun matchBest(
        note: String,
        allCategories: List<Category>,
        brandMappings: List<BrandMapping>
    ): Category? {
        if (note.isBlank()) return null
        val incomeCats = allCategories.filter { it.isIncome }
        val expenseCats = allCategories.filter { !it.isIncome }
        val incomeMatch = matchNoteInternal(note, incomeCats, brandMappings)
        val expenseMatch = matchNoteInternal(note, expenseCats, brandMappings)
        return when {
            incomeMatch == null && expenseMatch == null -> null
            incomeMatch == null -> expenseMatch?.category
            expenseMatch == null -> incomeMatch.category
            incomeMatch.level < expenseMatch.level -> incomeMatch.category
            expenseMatch.level < incomeMatch.level -> expenseMatch.category
            // 层级相同时优先支出（沿用原始行为）
            else -> expenseMatch.category
        }
    }

    private fun matchNoteInternal(
        note: String,
        categories: List<Category>,
        brandMappings: List<BrandMapping>
    ): MatchResult? {
        if (note.isBlank() || categories.isEmpty()) return null

        val activeMappings = brandMappings.filter { it.confirmCount >= MIN_CONFIRM_COUNT }
        val sortedBrandMappings = activeMappings.sortedWith(
            compareByDescending<BrandMapping> { it.brandName.length }
                .thenByDescending { it.hitCount }
        )
        // 分类按名称稳定排序，避免 firstOrNull 结果抖动
        val sortedCategories = categories.sortedBy { it.name }

        // 第 1 层：名称精确匹配
        sortedCategories.firstOrNull { it.name == note }?.let {
            return MatchResult(it, 1)
        }

        // 第 2 层：品牌映射精确匹配（用户维护品牌优先于内置 prompts）
        sortedBrandMappings.firstOrNull { it.brandName == note }?.let { bm ->
            sortedCategories.firstOrNull { it.id == bm.categoryId }?.let {
                return MatchResult(it, 2)
            }
        }

        // 第 3 层：prompts 精确匹配
        sortedCategories.firstOrNull { cat ->
            cat.prompts.split(",", "，").any { it.trim() == note }
        }?.let { return MatchResult(it, 3) }

        // 第 4 层：品牌映射包含匹配（如 "蜜雪冰城 柠檬水" 包含品牌 "蜜雪冰城"）
        sortedBrandMappings.firstOrNull { bm ->
            note.contains(bm.brandName)
        }?.let { bm ->
            sortedCategories.firstOrNull { it.id == bm.categoryId }?.let {
                return MatchResult(it, 4)
            }
        }

        // 第 5 层：prompts 单向包含匹配（note.contains(prompt)）
        sortedCategories.firstOrNull { cat ->
            cat.prompts.split(",", "，").any { p ->
                val trimmed = p.trim()
                trimmed.isNotEmpty() && note.contains(trimmed)
            }
        }?.let { return MatchResult(it, 5) }

        return null
    }
}
