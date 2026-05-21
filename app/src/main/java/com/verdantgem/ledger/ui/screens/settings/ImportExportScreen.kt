package com.verdantgem.ledger.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val isParsing by viewModel::isParsing
    val isImporting by viewModel::isImporting
    val importProgress by viewModel::importProgress
    val preview by viewModel::preview
    val result by viewModel::result
    val error by viewModel::error

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.parseFile(context.contentResolver, uri)
        }
    }

    // 导入结果弹窗
    if (result != null) {
        AlertDialog(
            onDismissRequest = { viewModel.reset() },
            title = { Text("导入完成") },
            text = {
                Text("成功导入 ${result!!.successCount} 条记录" +
                        if (result!!.skippedCount > 0) "\n跳过 ${result!!.skippedCount} 条（分类不匹配）" else "")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.reset() }) {
                    Text("确定")
                }
            }
        )
    }

    // 错误弹窗
    if (error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.reset() },
            title = { Text("导入失败") },
            text = { Text(error!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset() }) {
                    Text("确定")
                }
            }
        )
    }

    // 解析预览弹窗
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) viewModel.reset() },
            title = { Text(if (isImporting) "导入中" else "导入预览") },
            text = {
                Column {
                    if (isImporting && importProgress != null) {
                        Text("导入中 ${importProgress!!.current} / ${importProgress!!.total}",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                val p = importProgress
                                if (p != null) p.current.toFloat() / p.total.toFloat() else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("文件中共 ${preview!!.totalRows} 条记录")
                        Spacer(Modifier.height(8.dp))
                        Text("支出: ${preview!!.expenseCount} 条")
                        Text("收入: ${preview!!.incomeCount} 条")
                        Spacer(Modifier.height(4.dp))
                        Text("分类匹配成功: ${preview!!.matchedRows} 条",
                            color = MaterialTheme.colorScheme.primary)
                        if (preview!!.unmatchedRows > 0) {
                            Text("无法匹配: ${preview!!.unmatchedRows} 条（将跳过）",
                                color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("导入后数据将自动同步到 WebDAV（如已配置）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                if (!isImporting) {
                    TextButton(onClick = { viewModel.confirmImport() }) {
                        Text("确认导入")
                    }
                }
            },
            dismissButton = {
                if (!isImporting) {
                    TextButton(onClick = { viewModel.reset() }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 导入卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("从文件导入", style = MaterialTheme.typography.titleMedium)
                            Text("支持 .xls 格式的账单文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 导入按钮
                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isParsing && !isImporting
                    ) {
                        if (isParsing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("解析中...")
                        } else {
                            Text("选择文件")
                        }
                    }
                }
            }

            // 导出卡片（禁用）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("导出数据", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("开发中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }

            // 说明文字
            Text(
                "导入的账单数据将根据分类名称自动匹配已有分类。\n" +
                        "若分类不匹配，该条记录将被跳过。\n" +
                        "导入完成后数据会自动同步至 WebDAV（如已配置）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
