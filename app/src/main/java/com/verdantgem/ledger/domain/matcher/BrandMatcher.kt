package com.verdantgem.ledger.domain.matcher

import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Category

/**
 * 统一品牌匹配引擎
 * 消除 DashboardScreen 和 AddRecordScreen 中重复的 matchIn() 逻辑
 *
 * 匹配优先级（从高到低）：
 *   0. 品牌映射精确匹配（brand_mappings 表直查）
 *   1. 品牌映射模糊匹配（Levenshtein 编辑距离 ≤ 2）
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
        // 第 0 层：品牌映射精确匹配
        val exactBm = brandMappings.firstOrNull { it.brandName == note }
        if (exactBm != null) {
            return categories.firstOrNull { it.id == exactBm.categoryId }
        }

        // 第 1 层：品牌映射模糊匹配（编辑距离 ≤ 2）
        val fuzzyBm = brandMappings.firstOrNull { bm ->
            levenshteinDistance(bm.brandName, note) <= 2
        }
        if (fuzzyBm != null) {
            return categories.firstOrNull { it.id == fuzzyBm.categoryId }
        }

        // 第 2-5 层：原有匹配逻辑
        return legacyMatchIn(note, categories)
    }

    /**
     * 编辑距离（Levenshtein Distance）
     * 用于容忍用户输入中的拼写错误，如"星八克"→"星巴克"
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // 删除
                    dp[i][j - 1] + 1,     // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }
        return dp[len1][len2]
    }

    /**
     * 原有的 4 层匹配逻辑
     * 与改造前两处 matchIn() 行为完全一致
     */
    internal fun legacyMatchIn(note: String, list: List<Category>): Category? {
        // 第 2 层：精确名称匹配
        val exact = list.firstOrNull { it.name == note }
        if (exact != null) return exact

        // 第 3 层：prompts 精确匹配
        val promptExact = list.firstOrNull {
            it.prompts.split(",", "，").any { p -> p.trim() == note }
        }
        if (promptExact != null) return promptExact

        // 第 4 层：prompts 包含匹配（双向）
        val promptContain = list.firstOrNull {
            it.prompts.split(",", "，").any { p ->
                note.contains(p.trim()) || p.trim().contains(note)
            }
        }
        if (promptContain != null) return promptContain

        // 第 5 层：名称包含匹配（双向）
        return list.firstOrNull {
            note.contains(it.name) || it.name.contains(note)
        }
    }
}
