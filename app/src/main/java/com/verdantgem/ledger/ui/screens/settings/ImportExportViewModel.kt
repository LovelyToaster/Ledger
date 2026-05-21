package com.verdantgem.ledger.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdantgem.ledger.data.importer.XlsImporter
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportPreview(
    val totalRows: Int,
    val matchedRows: Int,
    val unmatchedRows: Int,
    val expenseCount: Int,
    val incomeCount: Int,
    val skippedCount: Int
)

data class ImportProgress(
    val current: Int,
    val total: Int
)

data class ImportResult(
    val successCount: Int,
    val skippedCount: Int
)

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val xlsImporter: XlsImporter
) : ViewModel() {

    var isParsing by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set
    var preview by mutableStateOf<ImportPreview?>(null)
        private set
    var result by mutableStateOf<ImportResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var pendingMatchedRows: List<XlsImporter.MatchedRow> = emptyList()

    /**
     * 解析用户选中的 XLS 文件，生成导入预览
     */
    fun parseFile(contentResolver: ContentResolver, uri: Uri) {
        if (isParsing || isImporting) return
        isParsing = true
        preview = null
        result = null
        error = null
        pendingMatchedRows = emptyList()

        viewModelScope.launch {
            try {
                val inputStream = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)
                } ?: throw IllegalStateException("无法打开文件")

                try {
                    val rows = withContext(Dispatchers.IO) {
                        xlsImporter.parse(inputStream)
                    }

                    val categories = withContext(Dispatchers.IO) {
                        ledgerRepository.getAllCategoriesList()
                    }

                    val matchedRows = xlsImporter.matchCategories(rows, categories)
                    pendingMatchedRows = matchedRows

                    val matched = matchedRows.filter { it.matched }
                    val unmatched = matchedRows.filter { !it.matched }
                    val expenseCount = matchedRows.count { !it.xlsRow.isIncome }
                    val incomeCount = matchedRows.count { it.xlsRow.isIncome }

                    preview = ImportPreview(
                        totalRows = rows.size,
                        matchedRows = matched.size,
                        unmatchedRows = unmatched.size,
                        expenseCount = expenseCount,
                        incomeCount = incomeCount,
                        skippedCount = unmatched.size
                    )
                } finally {
                    try {
                        inputStream.close()
                    } catch (_: Throwable) { }
                }
            } catch (e: Throwable) {
                error = "解析失败: ${e.localizedMessage ?: e.message}"
            } finally {
                isParsing = false
            }
        }
    }

    /**
     * 确认导入，将已匹配的记录批量写入数据库
     */
    fun confirmImport() {
        if (isImporting || pendingMatchedRows.isEmpty()) return
        isImporting = true

        viewModelScope.launch {
            try {
                val records = pendingMatchedRows
                    .filter { it.matched }
                    .mapNotNull { it.toRecord() }
                val total = records.size
                importProgress = ImportProgress(0, total)

                val successCount = withContext(Dispatchers.IO) {
                    ledgerRepository.insertRecords(records) { current ->
                        importProgress = ImportProgress(current, total)
                    }
                }
                val skippedCount = pendingMatchedRows.size - successCount

                result = ImportResult(
                    successCount = successCount,
                    skippedCount = skippedCount
                )
                // 清空预览态
                preview = null
                pendingMatchedRows = emptyList()
            } catch (e: Throwable) {
                error = "导入失败: ${e.localizedMessage ?: e.message}"
            } finally {
                isImporting = false
                importProgress = null
            }
        }
    }

    /**
     * 重置状态（关闭预览/清空结果）
     */
    fun reset() {
        preview = null
        result = null
        error = null
        importProgress = null
        pendingMatchedRows = emptyList()
    }
}
