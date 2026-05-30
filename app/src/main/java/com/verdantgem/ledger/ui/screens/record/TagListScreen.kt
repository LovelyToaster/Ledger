package com.verdantgem.ledger.ui.screens.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一标签项数据模型
 * @param id 唯一标识（用于 Compose key）
 * @param label 显示文字
 * @param subtitle 副标签（品牌模式下显示"预置"/"用户"等来源信息）
 */
data class TagItem(
    val id: String,
    val label: String,
    val subtitle: String? = null,
)

/**
 * 统一的标签管理界面
 * 用于提示词管理和关联品牌管理两个场景
 *
 * @param title 页面标题
 * @param subtitle 底部说明文字
 * @param placeholder 输入框占位提示
 * @param items 当前标签列表
 * @param onAdd 添加回调（返回输入的文字）
 * @param onDelete 删除回调
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagListScreen(
    title: String,
    subtitle: String,
    placeholder: String,
    items: List<TagItem>,
    onAdd: (String) -> Unit,
    onDelete: (TagItem) -> Unit,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val isDuplicate = title == "关联品牌" && inputText.isNotBlank() &&
        items.any { it.label.equals(inputText.trim(), ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 顶部输入区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(placeholder) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = isDuplicate
                    )
                    FilledTonalIconButton(
                        onClick = {
                            val text = inputText.trim()
                            if (text.isNotEmpty() && !isDuplicate) {
                                onAdd(text)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        enabled = inputText.isNotBlank() && !isDuplicate
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
                if (isDuplicate) {
                    Text(
                        "该品牌已存在",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }

            // 标签列表
            if (items.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无数据",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach { item ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.subtitle != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (item.subtitle == "预置")
                                                            Color(0xFF42A5F5)
                                                        else
                                                            Color(0xFF66BB6A)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(item.label, fontWeight = FontWeight.Medium)
                                    }
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onDelete(item) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除 ${item.label}",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部说明
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
