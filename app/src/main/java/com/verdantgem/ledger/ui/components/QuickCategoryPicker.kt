package com.verdantgem.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verdantgem.ledger.data.model.Category

/**
 * 快速类别选择器，显示父类别为标签头、子类别为平铺网格（4列）
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
    val pickerParents = categories.filter { it.isIncome == pickerIsIncome && it.parentName == null }
    val gridColumns = 4
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !pickerIsIncome,
                onClick = { pickerIsIncome = false },
                label = { Text("支出") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = pickerIsIncome,
                onClick = { pickerIsIncome = true },
                label = { Text("收入") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState())
        ) {
            pickerParents.forEach { parent ->
                val subs = categories.filter { it.parentName == parent.name }

                Text(
                    text = parent.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                )

                if (subs.isNotEmpty()) {
                    val subChunked = subs.chunked(gridColumns)
                    subChunked.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { sub ->
                                Box(modifier = Modifier.weight(1f)) {
                                    PickerCategoryItem(
                                        label = sub.name,
                                        isSelected = pickerEffectiveName == sub.name,
                                        onClick = { onCategoryChange(sub.name) }
                                    )
                                }
                            }
                            repeat(gridColumns - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PickerCategoryItem(
                                label = parent.name,
                                isSelected = pickerEffectiveName == parent.name,
                                onClick = { onCategoryChange(parent.name) }
                            )
                        }
                        repeat(gridColumns - 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PickerCategoryItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(label.take(1), color = if(isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
