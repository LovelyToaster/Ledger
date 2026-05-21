package com.verdantgem.ledger.data.importer

import android.util.Log
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XLS 账单导入解析器
 *
 * 预期格式（列索引 0-based）：
 *   idx 0: 时间 (yyyy-MM-dd HH:mm)
 *   idx 1: 收支类型 (支出/收入)
 *   idx 2: 金额 (负数=支出, 正数=收入)
 *   idx 3: 一级分类
 *   idx 4: 二级分类（可选）
 *   idx 9: 备注（可选）
 *   idx 15: 地址（可选）
 */
@Singleton
class XlsImporter @Inject constructor() {

    companion object {
        private const val TAG = "XlsImporter"

        // 期望列索引
        const val COL_DATE = 0
        const val COL_TYPE = 1
        const val COL_AMOUNT = 2
        const val COL_PARENT_CAT = 3
        const val COL_SUB_CAT = 4
        const val COL_NOTE = 9
        const val COL_ADDRESS = 15

        // 尝试多种日期格式（按优先级排列）
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
            SimpleDateFormat("yyyy-M-d HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-M-d", Locale.getDefault())
        )
    }

    /**
     * XLS 中一行的原始解析结果
     */
    data class XlsRow(
        val date: Long,
        val isIncome: Boolean,
        val amount: Double,
        val parentCategory: String,
        val subCategory: String?,
        val note: String,
        val address: String?,
        val rowIndex: Int
    )

    /**
     * 分类匹配后的一行数据
     */
    data class MatchedRow(
        val xlsRow: XlsRow,
        val matched: Boolean,
        val categoryId: Long = 0,
        val categoryName: String = "",
        val isIncome: Boolean = false,
        val skipReason: String? = null
    ) {
        fun toRecord(): Record? {
            if (!matched) return null
            return Record(
                amount = xlsRow.amount,
                categoryId = categoryId,
                categoryName = categoryName,
                note = xlsRow.note,
                date = xlsRow.date,
                address = xlsRow.address ?: ""
            )
        }
    }

    /**
     * 解析 XLS 文件为原始行数据
     */
    fun parse(inputStream: InputStream): List<XlsRow> {
        // 将流预读到字节数组，避免 Android ContentResolver 返回的特殊 InputStream 与 POI 不兼容
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read input stream: ${e.javaClass.name}: ${e.message}")
            return emptyList()
        }
        val workbook = try {
            HSSFWorkbook(java.io.ByteArrayInputStream(bytes))
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open workbook", e)
            return emptyList()
        }

        val sheet = try {
            workbook.getSheetAt(0)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get sheet: ${e.javaClass.name}: ${e.message}")
            workbook.close()
            return emptyList()
        }

        Log.d(TAG, "Sheet '${sheet.sheetName}': lastRowNum=${sheet.lastRowNum}, physicalRows=${sheet.physicalNumberOfRows}")

        val rows = mutableListOf<XlsRow>()

        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            try {
                // 日期（必填）
                val dateCell = row.getCell(COL_DATE)
                val date = parseDateCell(dateCell) ?: continue

                // 收支类型（必填）
                val typeCell = row.getCell(COL_TYPE)
                val typeStr = parseStringCell(typeCell) ?: continue
                val isIncome = typeStr == "收入"

                // 金额（必填）
                val amountCell = row.getCell(COL_AMOUNT)
                val amountRaw = parseNumericCell(amountCell) ?: continue
                val amount = kotlin.math.abs(amountRaw)

                // 一级分类
                val parentCat = parseStringCell(row.getCell(COL_PARENT_CAT)) ?: ""

                // 二级分类（可选）
                val subCat = parseStringCell(row.getCell(COL_SUB_CAT))

                // 备注
                val note = parseStringCell(row.getCell(COL_NOTE)) ?: ""

                // 地址（可选）
                val address = parseStringCell(row.getCell(COL_ADDRESS))

                rows.add(
                    XlsRow(
                        date = date,
                        isIncome = isIncome,
                        amount = amount,
                        parentCategory = parentCat,
                        subCategory = subCat,
                        note = note,
                        address = address,
                        rowIndex = i
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Row $i: parse error: ${e.message}")
            }
        }

        Log.d(TAG, "Parsed ${rows.size} rows from sheet")
        try {
            workbook.close()
        } catch (_: Throwable) { }
        return rows
    }

    /**
     * 将解析的行与数据库分类匹配
     */
    fun matchCategories(
        rows: List<XlsRow>,
        categories: List<Category>
    ): List<MatchedRow> {
        // 建立索引：父分类 Map、子分类列表
        val subCats = categories.filter { it.parentName != null }
        val parentCatsByName = categories
            .filter { it.parentName == null }
            .associateBy { it.name }

        return rows.map { row ->
            var matched = false
            var categoryId = 0L
            var categoryName = ""
            var isIncome = row.isIncome

            // 策略1：子分类名精确匹配
            val subMatch = if (row.subCategory != null) {
                subCats.find { it.name == row.subCategory }
            } else null

            if (subMatch != null) {
                matched = true
                categoryId = subMatch.id
                categoryName = subMatch.name
                isIncome = subMatch.isIncome
            } else {
                // 策略2：一级分类名精确匹配
                val parentMatch = parentCatsByName[row.parentCategory]
                if (parentMatch != null) {
                    matched = true
                    categoryId = parentMatch.id
                    categoryName = parentMatch.name
                    isIncome = parentMatch.isIncome
                } else {
                    // 策略3：按收支类型兜底
                    val fallback = if (isIncome) {
                        categories.find { it.name == "收入" && it.isIncome }
                            ?: categories.find { it.isIncome }
                    } else {
                        categories.find { it.name == "其他" && !it.isIncome }
                            ?: categories.find { !it.isIncome }
                    }

                    if (fallback != null) {
                        matched = true
                        categoryId = fallback.id
                        categoryName = fallback.name
                        isIncome = fallback.isIncome
                    } else {
                        // 策略4：任意可用分类
                        val anyCat = categories.firstOrNull()
                        if (anyCat != null) {
                            matched = true
                            categoryId = anyCat.id
                            categoryName = anyCat.name
                            isIncome = anyCat.isIncome
                        }
                    }
                }
            }

            val skipReason = if (!matched) {
                "分类不匹配: ${row.parentCategory}${row.subCategory?.let { "/$it" } ?: ""}"
            } else null

            MatchedRow(
                xlsRow = row,
                matched = matched,
                categoryId = categoryId,
                categoryName = categoryName,
                isIncome = isIncome,
                skipReason = skipReason
            )
        }
    }

    // ---- 单元格解析辅助方法 ----

    /**
     * 解析日期单元格，支持多种格式和存储方式：
     * 1. 如果是 NUMERIC 格式的 Excel 日期 → DateUtil + getDateCellValue
     * 2. 如果是 STRING 格式 → 尝试多种 SimpleDateFormat 模式
     * 3. 如果 NUMERIC 但未被识别为日期格式 → 尝试作为 Excel 序列号处理
     * 4. 兜底直接用 getDateCellValue (POI 原生解析)
     */
    private fun parseDateCell(cell: org.apache.poi.ss.usermodel.Cell?): Long? {
        if (cell == null) return null
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // 标准 Excel 日期格式
                        cell.dateCellValue.time
                    } else {
                        // 可能是 Excel 日期序列号（数值）
                        val raw = cell.numericCellValue
                        if (DateUtil.isValidExcelDate(raw)) {
                            DateUtil.getJavaDate(raw).time
                        } else {
                            null
                        }
                    }
                }
                CellType.STRING -> {
                    val str = cell.stringCellValue.trim()
                    if (str.isEmpty()) return null
                    // 尝试多种字符串日期格式
                    for (fmt in DATE_FORMATS) {
                        val parsed = fmt.parse(str)
                        if (parsed != null) return parsed.time
                    }
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Date parse error: ${e.message}")
            // 终极兜底：尝试 POI 原生日期解析
            try {
                cell.dateCellValue?.time
            } catch (_: Exception) { null }
        }
    }

    private fun parseNumericCell(cell: org.apache.poi.ss.usermodel.Cell?): Double? {
        if (cell == null) return null
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue
                CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull()
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Numeric parse error: ${e.message}")
            null
        }
    }

    private fun parseStringCell(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        if (cell == null) return null
        return try {
            when (cell.cellType) {
                CellType.STRING -> {
                    val v = cell.stringCellValue.trim()
                    v.ifEmpty { null }
                }
                CellType.NUMERIC -> {
                    val v = cell.numericCellValue
                    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "String parse error: ${e.message}")
            null
        }
    }
}
