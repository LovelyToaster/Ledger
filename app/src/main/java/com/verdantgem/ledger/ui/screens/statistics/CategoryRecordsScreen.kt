package com.verdantgem.ledger.ui.screens.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.ui.components.CategoryIcon
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRecordsScreen(
    viewModel: CategoryRecordsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToRecordDetail: (Long) -> Unit
) {
    val pagingItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val allCategories by viewModel.allCategories.collectAsState()
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.categoryName,
                        fontWeight = FontWeight.Bold
                    )
                },
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
        if (pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (pagingItems.itemCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无记录", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(pagingItems.itemCount) { index ->
                    val record = pagingItems[index]
                    if (record != null) {
                        CategoryRecordItem(
                            record = record,
                            categories = allCategories,
                            dateFormat = dateFormat,
                            onClick = { onNavigateToRecordDetail(record.id) }
                        )
                    }
                }

                if (pagingItems.loadState.append is LoadState.Loading) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRecordItem(
    record: Record,
    categories: List<Category>,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cat = categories.firstOrNull { it.name == record.categoryName }
            if (cat != null) {
                CategoryIcon(icon = cat.icon, name = cat.name, size = 28.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.note.ifBlank { "无备注" },
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = dateFormat.format(Date(record.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            val isIncome = categories.find { it.name == record.categoryName }?.isIncome == true
            Text(
                text = "${if (isIncome) "+ " else "- "}\uFFE5${String.format("%.2f", record.amount)}",
                fontWeight = FontWeight.Bold,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
