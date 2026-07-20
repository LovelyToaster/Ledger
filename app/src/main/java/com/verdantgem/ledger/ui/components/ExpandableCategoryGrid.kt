package com.verdantgem.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verdantgem.ledger.data.model.Category

/**
 * 可展开的类别网格选择器——与 AddRecordScreen 使用相同的交互模型。
 *
 * 所有父类别统一显示为等大网格项（默认 4 列）：
 * - 有子类别的父类别：点击展开/收起子类别区域
 * - 无子类别的父类别（仅大类）：点击直接选中
 *
 * 调用方负责管理 isIncome 状态（外部传入）。
 */
@Composable
fun ExpandableCategoryGrid(
    categories: List<Category>,
    selectedCategoryName: String,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isIncome: Boolean = false,
    onIsIncomeChange: ((Boolean) -> Unit)? = null,
    showIncomeToggle: Boolean = true,
    gridColumns: Int = 4,
    maxHeight: Dp? = null,
) {
    val currentCategories = remember(categories, isIncome) {
        categories.filter { it.isIncome == isIncome }
    }
    val parentCategories = remember(currentCategories) {
        currentCategories.filter { it.parentName == null }
    }
    var expandedParent by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showIncomeToggle && onIsIncomeChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isIncome,
                    onClick = { onIsIncomeChange(false) },
                    label = { Text("支出") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isIncome,
                    onClick = { onIsIncomeChange(true) },
                    label = { Text("收入") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val scrollModifier = if (maxHeight != null) {
            Modifier.heightIn(max = maxHeight)
        } else {
            Modifier
        }

        Column(
            modifier = scrollModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val parentChunked = parentCategories.chunked(gridColumns)
            parentChunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { parent ->
                        val subs = currentCategories.filter { it.parentName == parent.name }
                        val hasSelectedSub = subs.any { it.name == selectedCategoryName }
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                label = parent.name,
                                isSelected = expandedParent == parent.name ||
                                    hasSelectedSub ||
                                    (subs.isEmpty() && selectedCategoryName == parent.name),
                                onClick = {
                                    if (subs.isNotEmpty()) {
                                        expandedParent = if (expandedParent == parent.name) "" else parent.name
                                    } else {
                                        onCategoryClick(parent.name)
                                    }
                                },
                                icon = parent.icon
                            )
                        }
                    }
                    repeat(gridColumns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                val expandedInRow = row.firstOrNull { it.name == expandedParent }
                if (expandedInRow != null) {
                    val subs = currentCategories.filter { it.parentName == expandedInRow.name }
                    if (subs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val subChunked = subs.chunked(gridColumns)
                            subChunked.forEach { subRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    subRow.forEach { sub ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            CategoryItem(
                                                label = sub.name,
                                                isSelected = selectedCategoryName == sub.name,
                                                onClick = { onCategoryClick(sub.name) },
                                                icon = sub.icon
                                            )
                                        }
                                    }
                                    repeat(gridColumns - subRow.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(label: String, isSelected: Boolean, onClick: () -> Unit, icon: String = "default_icon") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(8.dp)
    ) {
        CategoryIcon(
            icon = icon,
            name = label,
            size = 44.dp,
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
