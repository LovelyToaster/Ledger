package com.verdantgem.ledger.domain.parser

import java.util.regex.Pattern

/**
 * 智能记账解析结果
 */
data class ParseResult(
    val amount: Double,
    val note: String,
    val rawText: String
)

/**
 * 快速记账解析引擎
 */
object SmartParser {
    // 匹配金额的正则表达式：支持整数、浮点数
    private val AMOUNT_REGEX = Pattern.compile("(\\d+(\\.\\d+)?)")

    /**
     * 解析输入文本（如：打水0.01）
     * @return 解析成功返回 ParseResult，失败返回 null
     */
    fun parse(input: String): ParseResult? {
        val trimmedInput = input.trim()
        val matcher = AMOUNT_REGEX.matcher(trimmedInput)

        // 查找所有数字，取最后一个作为金额
        // 例如 "S5 700" → 最后一个"700"是金额，"S5"是备注
        var amountStr: String? = null
        var lastStart = -1
        var lastEnd = -1
        while (matcher.find()) {
            amountStr = matcher.group(1)
            lastStart = matcher.start()
            lastEnd = matcher.end()
        }

        if (amountStr == null) return null
        val amount = amountStr.toDoubleOrNull() ?: return null

        // 精确移除最后一个数字的位置（避免误删其他同名数字）
        val note = (trimmedInput.substring(0, lastStart) +
                    trimmedInput.substring(lastEnd)).trim()

        return ParseResult(
            amount = amount,
            note = note.ifEmpty { "未命名支出" },
            rawText = trimmedInput
        )
    }
}
