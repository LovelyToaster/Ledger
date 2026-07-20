package com.verdantgem.ledger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verdantgem.ledger.data.model.Category

/**
 * 快速类别选择器——基于 ExpandableCategoryGrid 的展开/收起交互模型。
 *
 * 所有父类别统一显示为等大网格项（4 列）：
 * - 有子类别的父类别：点击展开子类别区域
 * - 无子类别的父类别（仅大类）：点击直接选中
 */
@Composable
fun QuickCategoryPicker(
    categories: List<Category>,
    selectedCategory: String,
    effectiveCategory: Category?,
    onCategoryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pickerIsIncome by remember { mutableStateOf(effectiveCategory?.isIncome ?: false) }
    val pickerEffectiveName = selectedCategory.ifBlank { effectiveCategory?.name ?: "" }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("选择分类", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDismiss) { Text("完成") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ExpandableCategoryGrid(
            categories = categories,
            selectedCategoryName = pickerEffectiveName,
            onCategoryClick = onCategoryChange,
            isIncome = pickerIsIncome,
            onIsIncomeChange = { pickerIsIncome = it },
            showIncomeToggle = true,
            gridColumns = 4,
            maxHeight = 350.dp,
        )
    }
}
