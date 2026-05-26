package com.verdantgem.ledger.ui.screens.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.data.repository.LedgerRepository
import com.verdantgem.ledger.ui.components.DateTimePickerDialog
import com.verdantgem.ledger.ui.components.QuickCategoryPicker
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

    private val _showNoteEditDialog = MutableStateFlow(false)
    val showNoteEditDialog: StateFlow<Boolean> = _showNoteEditDialog

    private val _showCategoryPicker = MutableStateFlow(false)
    val showCategoryPicker: StateFlow<Boolean> = _showCategoryPicker

    private val _showAmountEditDialog = MutableStateFlow(false)
    val showAmountEditDialog: StateFlow<Boolean> = _showAmountEditDialog

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

    fun showNoteEditDialog() { _showNoteEditDialog.value = true }
    fun dismissNoteEditDialog() { _showNoteEditDialog.value = false }

    fun showCategoryPickerDialog() { _showCategoryPicker.value = true }
    fun dismissCategoryPickerDialog() { _showCategoryPicker.value = false }

    fun showAmountEditDialog() { _showAmountEditDialog.value = true }
    fun dismissAmountEditDialog() { _showAmountEditDialog.value = false }

    fun updateBillDate(newDate: Long) {
        viewModelScope.launch {
            repository.updateRecordBillDate(currentRecordId, newDate)
            _record.value = repository.getRecordById(currentRecordId)
            _showDatePicker.value = false
        }
    }

    fun updateNote(newNote: String) {
        viewModelScope.launch {
            repository.updateRecordNote(currentRecordId, newNote)
            _record.value = repository.getRecordById(currentRecordId)
            _showNoteEditDialog.value = false
        }
    }

    fun updateCategory(categoryName: String) {
        viewModelScope.launch {
            val cat = allCategories.value.find { it.name == categoryName } ?: return@launch
            repository.updateRecordCategory(currentRecordId, cat.id, cat.name)
            _record.value = repository.getRecordById(currentRecordId)
            _showCategoryPicker.value = false
        }
    }

    fun updateAmount(newAmount: Double) {
        viewModelScope.launch {
            repository.updateRecordAmount(currentRecordId, newAmount)
            _record.value = repository.getRecordById(currentRecordId)
            _showAmountEditDialog.value = false
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
    val showNoteEditDialog by viewModel.showNoteEditDialog.collectAsState()
    val showCategoryPicker by viewModel.showCategoryPicker.collectAsState()
    val showAmountEditDialog by viewModel.showAmountEditDialog.collectAsState()
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // 类别
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val cat = allCategories.find { it.name == currentRecord.categoryName }
                            val displayName = if (cat?.parentName != null) "${cat.parentName}-${cat.name}" else currentRecord.categoryName
                            DetailItem(
                                icon = Icons.Default.ListIcon,
                                label = "类别",
                                value = displayName,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.showCategoryPickerDialog() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "修改类别",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

                        // 金额
                        val isIncome = allCategories.find { it.name == currentRecord.categoryName }?.isIncome == true
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailItem(
                                icon = Icons.Default.ShoppingCart,
                                label = "金额",
                                value = "${if (isIncome) "+ " else "- "}\uFFE5${String.format("%.2f", currentRecord.amount)}",
                                valueColor = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.showAmountEditDialog() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "修改金额",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

                        // 账单时间
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailItem(
                                icon = Icons.Default.Notes,
                                label = "备注",
                                value = currentRecord.note.ifEmpty { "无备注" },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.showNoteEditDialog() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "修改备注",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
    }

    if (showAmountEditDialog && currentRecord != null) {
        var amountText by remember(currentRecord) { mutableStateOf(currentRecord.amount.toString()) }
        var amountError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAmountEditDialog() },
            title = { Text("编辑金额") },
            text = {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = it.toDoubleOrNull() == null || (it.toDoubleOrNull() ?: 0.0) <= 0
                    },
                    label = { Text("金额") },
                    supportingText = { if (amountError) Text("请输入有效的正数金额") },
                    isError = amountError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newAmount = amountText.toDoubleOrNull()
                        if (newAmount != null && newAmount > 0) {
                            viewModel.updateAmount(newAmount)
                        }
                    },
                    enabled = !amountError && amountText.toDoubleOrNull() != null && (amountText.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAmountEditDialog() }) {
                    Text("取消")
                }
            }
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

    if (showNoteEditDialog && currentRecord != null) {
        var noteText by remember(currentRecord) { mutableStateOf(currentRecord.note) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoteEditDialog() },
            title = { Text("编辑备注") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateNote(noteText) }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissNoteEditDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCategoryPicker && currentRecord != null) {
        val currentCat = allCategories.find { it.name == currentRecord.categoryName }
        Dialog(
            onDismissRequest = { viewModel.dismissCategoryPickerDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    QuickCategoryPicker(
                        categories = allCategories,
                        selectedCategory = currentRecord.categoryName,
                        effectiveCategory = currentCat,
                        onCategoryChange = { name ->
                            viewModel.updateCategory(name)
                        },
                        onDismiss = { viewModel.dismissCategoryPickerDialog() }
                    )
                }
            }
        }
    }

    if (showAmountEditDialog && currentRecord != null) {
        var amountText by remember(currentRecord) { mutableStateOf(currentRecord.amount.toString()) }
        var amountError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAmountEditDialog() },
            title = { Text("编辑金额") },
            text = {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = it.toDoubleOrNull() == null || (it.toDoubleOrNull() ?: 0.0) <= 0
                    },
                    label = { Text("金额") },
                    supportingText = { if (amountError) Text("请输入有效的正数金额") },
                    isError = amountError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newAmount = amountText.toDoubleOrNull()
                        if (newAmount != null && newAmount > 0) {
                            viewModel.updateAmount(newAmount)
                        }
                    },
                    enabled = !amountError && amountText.toDoubleOrNull() != null && (amountText.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAmountEditDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = Color.Unspecified,
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
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (valueColor != Color.Unspecified) valueColor else Color.Unspecified
            )
        }
    }
}
