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

        return if (matcher.find()) {
            val amountStr = matcher.group(1)
            val amount = amountStr?.toDoubleOrNull() ?: return null

            // 提取金额之外的部分作为备注
            // 例如 "打水0.01" -> "打水"
            val note = trimmedInput.replace(amountStr, "").trim()

            ParseResult(
                amount = amount,
                note = if (note.isEmpty()) "未命名支出" else note,
                rawText = trimmedInput
            )
        } else {
            null
        }
    }
}
