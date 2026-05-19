package com.verdantgem.ledger.ui.screens.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.data.repository.LedgerRepository
import com.verdantgem.ledger.ui.components.DateTimePickerDialog
import com.verdantgem.ledger.ui.components.formatDateTime
import com.verdantgem.ledger.ui.theme.dimens
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {
    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record

    private var currentRecordId: Long = 0

    private val _showDatePicker = MutableStateFlow(false)
    val showDatePicker: StateFlow<Boolean> = _showDatePicker

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadRecord(id: Long) {
        currentRecordId = id
        viewModelScope.launch {
            _record.value = repository.getRecordById(id)
        }
    }

    fun showDatePickerDialog() { _showDatePicker.value = true }
    fun dismissDatePicker() { _showDatePicker.value = false }

    fun updateBillDate(newDate: Long) {
        viewModelScope.launch {
            repository.updateRecordBillDate(currentRecordId, newDate)
            _record.value = repository.getRecordById(currentRecordId)
            _showDatePicker.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val showDatePicker by viewModel.showDatePicker.collectAsState()
    val d = MaterialTheme.dimens

    LaunchedEffect(recordId) {
        viewModel.loadRecord(recordId)
    }

    val currentRecord = record

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (currentRecord != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val cat = allCategories.find { it.name == currentRecord.categoryName }
                    val displayName = if (cat?.parentName != null) "${cat.parentName}-${cat.name}" else currentRecord.categoryName
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "金额",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        val isIncome = allCategories.find { it.name == currentRecord.categoryName }?.isIncome == true
                        Text(
                            text = "${if (isIncome) "+ " else "- "}\uFFE5${String.format("%.2f", currentRecord.amount)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(d.spacingXl))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailItem(
                                icon = Icons.Default.CalendarToday,
                                label = "账单时间",
                                value = formatDateTime(currentRecord.date),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.showDatePickerDialog() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "修改账单时间",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)
                        DetailItem(
                            icon = Icons.Default.CalendarToday,
                            label = "记录时间",
                            value = formatDateTime(currentRecord.createdAt),
                            iconTint = MaterialTheme.colorScheme.outline
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)
                        DetailItem(
                            icon = Icons.Default.LocationOn,
                            label = "记录地点",
                            value = currentRecord.address.ifEmpty { "暂无位置数据" }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)
                        DetailItem(
                            icon = Icons.Default.Notes,
                            label = "备注",
                            value = currentRecord.note.ifEmpty { "无备注" }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到该账单记录")
            }
        }
    }

    if (showDatePicker && currentRecord != null) {
        DateTimePickerDialog(
            initialDate = currentRecord.date,
            onConfirm = { viewModel.updateBillDate(it) },
            onDismiss = { viewModel.dismissDatePicker() }
        )
    }
}

@Composable
fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
