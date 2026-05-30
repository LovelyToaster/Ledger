package com.verdantgem.ledger.ui.screens.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.verdantgem.ledger.ui.theme.WindowWidth
import com.verdantgem.ledger.ui.theme.dimens
import com.verdantgem.ledger.ui.theme.windowSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onBack: () -> Unit,
    categories: List<Category>,
    viewModel: CategoryViewModel,
    onAdd: (String, String?, Boolean) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onDelete: (Category) -> Unit,
    onReset: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var currentTabIsIncome by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val d = MaterialTheme.dimens
    val windowSize = MaterialTheme.windowSize
    val gridColumns = when (windowSize.width) {
        WindowWidth.COMPACT -> 4
        WindowWidth.MEDIUM -> 5
        WindowWidth.EXPANDED -> 6
    }

    val filteredCategories = remember(categories, currentTabIsIncome) {
        categories.filter { it.isIncome == currentTabIsIncome }
    }

    val parentCategories = remember(filteredCategories) {
        filteredCategories.filter { it.parentName == null }
    }

    // 展开状态托管在 ViewModel 中，Navigation 跳转回来不丢失
    val expandedParentIds by viewModel.expandedParentIds.collectAsState()

    // 注意：展开状态不在此处重置，由外部导航进入时由调用方主动 resetExpandState()
    // 从子页面（详情/提示词/品牌）返回时，状态保持不丢失

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Row(
                    modifier = Modifier
                        .widthIn(min = 130.dp, max = 200.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (!currentTabIsIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { currentTabIsIncome = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("支出", color = if (!currentTabIsIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (currentTabIsIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { currentTabIsIncome = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("收入", color = if (currentTabIsIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showResetConfirm = true }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "重置",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    onClick = {
                        newName = ""
                        showAddDialog = true
                    },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加分类", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(parentCategories, key = { "parent_${it.id}" }) { parent ->
                val subs = filteredCategories.filter { it.parentName == parent.name }
                CategoryGroup(
                    parent = parent,
                    subs = subs,
                    gridColumns = gridColumns,
                    expanded = parent.id in expandedParentIds,
                    onToggle = { viewModel.toggleExpand(parent.id) },
                    onAddSub = { subName -> onAdd(subName, parent.name, currentTabIsIncome) },
                    onNavigateToDetail = onNavigateToDetail,
                    onDelete = onDelete
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加分类") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("分类名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onAdd(newName, null, currentTabIsIncome)
                        newName = ""
                        showAddDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置分类") },
            text = { Text("将重置所有分类到默认设置，确定要重置吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onReset()
                }) {
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun CategoryGroup(
    parent: Category,
    subs: List<Category>,
    gridColumns: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddSub: (String) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onDelete: (Category) -> Unit = {}
) {
    var showAddSubDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var subName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
                Icon(
                    Icons.Default.Category,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = parent.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onNavigateToDetail(parent.id) }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val items = subs + null
                val chunked = items.chunked(gridColumns)
                chunked.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { sub ->
                            if (sub != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CategoryItem(
                                        label = sub.name,
                                        isSelected = false,
                                        onClick = { onNavigateToDetail(sub.id) }
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f)) {
                                    AddCategoryItem(onClick = { showAddSubDialog = true })
                                }
                            }
                        }
                        repeat(gridColumns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showAddSubDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubDialog = false },
            title = { Text("添加子分类到 ${parent.name}") },
            text = {
                TextField(
                    value = subName,
                    onValueChange = { subName = it },
                    placeholder = { Text("如：公交、电费...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (subName.isNotBlank()) {
                        onAddSub(subName)
                        subName = ""
                        showAddSubDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除分类") },
            text = { Text("确定要删除「${parent.name}」及其所有子分类吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(parent)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun AddCategoryItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("添加", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}
