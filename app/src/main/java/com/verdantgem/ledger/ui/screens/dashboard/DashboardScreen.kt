package com.verdantgem.ledger.ui.screens.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.domain.parser.SmartParser
import com.verdantgem.ledger.ui.components.DateTimePickerDialog
import com.verdantgem.ledger.ui.theme.dimens
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToAddRecord: () -> Unit,
    onNavigateToRecordDetail: (Long) -> Unit,
    onNavigateToBudget: () -> Unit,
    innerPadding: PaddingValues
) {
    val pagingItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val d = MaterialTheme.dimens

    var isSearchExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isSheetOpen by remember { mutableStateOf(false) }
    var quickCategoryName by remember { mutableStateOf("") }
    var quickBillDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            viewModel.startLocation()
        }
    }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.setSearchQuery("")
    }
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("已选中 ${selectedIds.size} 项") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "取消")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                } else if (isSearchExpanded) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("搜索备注或类别...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                isSearchExpanded = false
                                viewModel.setSearchQuery("")
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("我的账本", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                        },
                        windowInsets = WindowInsets.statusBars
                    )
                }
            },
            floatingActionButton = {
                if (!isSheetOpen) {
                    FloatingActionButton(
                        onClick = { isSheetOpen = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "记账")
                    }
                }
            },
            bottomBar = {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                val monthlyExpense by viewModel.monthlyExpense.collectAsState()
                val monthlyIncome by viewModel.monthlyIncome.collectAsState()
                val monthlySurplus by viewModel.monthlySurplus.collectAsState()
                val budget by viewModel.budget.collectAsState()
                DashboardCard(
                    expense = monthlyExpense,
                    income = monthlyIncome,
                    surplus = monthlySurplus,
                    budget = budget,
                    onClick = onNavigateToBudget
                )
                Spacer(modifier = Modifier.height(d.spacingLg))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(d.spacingSm),
                    contentPadding = PaddingValues(bottom = d.spacingMd)
                ) {
                    items(pagingItems.itemCount) { index ->
                        val item = pagingItems[index]
                        if (item != null) {
                            when (item) {
                                is DashboardItem.Header -> {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(vertical = d.spacingSm, horizontal = d.spacingXs)
                                    )
                                }
                                is DashboardItem.Record -> {
                                    RecordItem(
                                        record = item.record,
                                        categories = allCategories,
                                        isSelected = selectedIds.contains(item.record.id),
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(item.record.id)
                                            } else {
                                                onNavigateToRecordDetail(item.record.id)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                viewModel.enterSelectionMode(item.record.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }

                    if (pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        if (isSheetOpen) {
            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startLocation()
                } else {
                    permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            DisposableEffect(Unit) {
                onDispose { viewModel.stopLocation() }
            }
            Popup(
                onDismissRequest = { isSheetOpen = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnClickOutside = false,
                    clippingEnabled = false
                )
            ) {
                QuickRecordOverlay(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = { categoryName, isIncome, billDate ->
                        viewModel.quickRecord(inputText, categoryName, isIncome, billDate = billDate)
                        inputText = ""
                        isSheetOpen = false
                    },
                    onExpand = {
                        isSheetOpen = false
                        onNavigateToAddRecord()
                    },
                    onDismiss = { isSheetOpen = false },
                    focusRequester = focusRequester,
                    categories = allCategories,
                    selectedCategory = quickCategoryName,
                    onCategoryChange = { quickCategoryName = it },
                    billDate = quickBillDate,
                    onDateClick = { showDatePicker = true },
                )
            }
        }
    }

    if (showDatePicker) {
        DateTimePickerDialog(
            initialDate = quickBillDate,
            onConfirm = { quickBillDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun QuickRecordOverlay(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (categoryName: String?, isIncome: Boolean, billDate: Long) -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    categories: List<Category>,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    billDate: Long,
    onDateClick: () -> Unit,
) {
    val d = MaterialTheme.dimens
    var showCategoryPicker by remember { mutableStateOf(false) }
    val parseResult = remember(text) { SmartParser.parse(text) }
    val parsedNote = parseResult?.note?.takeIf { it != "未命名支出" }
    val parsedAmount = parseResult?.amount

    val matchedCategory = remember(parsedNote, categories) {
        parsedNote?.let { note ->
            fun matchIn(list: List<Category>): Category? {
                val exact = list.firstOrNull { it.name == note }
                if (exact != null) return exact
                val promptExact = list.firstOrNull {
                    it.prompts.split(",", "，").any { p -> p.trim() == note }
                }
                if (promptExact != null) return promptExact
                val promptContain = list.firstOrNull {
                    it.prompts.split(",", "，").any { p -> note.contains(p.trim()) || p.trim().contains(note) }
                }
                if (promptContain != null) return promptContain
                return list.firstOrNull { note.contains(it.name) || it.name.contains(note) }
            }
            val incomeCats = categories.filter { it.isIncome }
            val expenseCats = categories.filter { !it.isIncome }
            val incomeMatch = matchIn(incomeCats)
            val expenseMatch = matchIn(expenseCats)
            when {
                incomeMatch != null && expenseMatch != null -> {
                    if (incomeMatch.name == note || incomeMatch.prompts.contains(note)) incomeMatch
                    else if (expenseMatch.name == note || expenseMatch.prompts.contains(note)) expenseMatch
                    else expenseMatch
                }
                incomeMatch != null -> incomeMatch
                expenseMatch != null -> expenseMatch
                else -> null
            }
        }
    }
    val isIncomeCat = matchedCategory?.isIncome == true
    val displayCatName = matchedCategory?.name ?: ""

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )

        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val bottomOffset = with(density) { imeBottom.toDp() }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = bottomOffset)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                QuickInputBar(
                    text = text,
                    onTextChange = onTextChange,
                    onSend = {
                        val finalCategory = selectedCategory.ifBlank { matchedCategory?.name }
                        onSend(finalCategory, isIncomeCat, billDate)
                    },
                    onExpand = onExpand,
                    focusRequester = focusRequester
                )

                Spacer(modifier = Modifier.height(d.spacingSm))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showCategoryPicker = true },
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (matchedCategory != null) displayCatName.take(1) else "类",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (matchedCategory != null) displayCatName else "类别",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Surface(
                        onClick = onDateClick,
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (parsedAmount != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${if (isIncomeCat) "+ " else "- "}\uFFE5${String.format("%.2f", parsedAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncomeCat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }

    if (showCategoryPicker) {
        var pickerExpandedParent by remember { mutableStateOf("") }
        val expenseParents = categories.filter { !it.isIncome && it.parentName == null }
        val gridColumns = 4

        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("选择分类") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    expenseParents.forEach { parent ->
                        val subs = categories.filter { it.parentName == parent.name }
                        val isExpanded = pickerExpandedParent == parent.name

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                                    else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Surface(
                                onClick = {
                                    pickerExpandedParent = if (isExpanded) "" else parent.name
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                if (subs.any { it.name == selectedCategory }) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            parent.name.take(1),
                                            color = if (subs.any { it.name == selectedCategory }) Color.White
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = parent.name,
                                        fontWeight = if (subs.any { it.name == selectedCategory }) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (subs.isNotEmpty()) {
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            if (isExpanded && subs.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val chunked = subs.chunked(gridColumns)
                                    chunked.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            row.forEach { sub ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    PickerCategoryItem(
                                                        label = sub.name,
                                                        isSelected = selectedCategory == sub.name,
                                                        onClick = {
                                                            onCategoryChange(sub.name)
                                                            showCategoryPicker = false
                                                        }
                                                    )
                                                }
                                            }
                                            if (row.size < gridColumns) {
                                                repeat(gridColumns - row.size) {
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
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text("取消") }
            }
        )
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
private fun PickerCategoryItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
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

@Composable
fun DashboardCard(
    expense: Double,
    income: Double,
    surplus: Double,
    budget: Budget?,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val tertiary = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.dimens.spacingLg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("本月收入", color = onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format("%.2f", income),
                            color = onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("本月结余", color = onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format("%.2f", surplus),
                            color = if (surplus >= 0) primary else error,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (budget != null) {
                    val percentage = if (budget.monthlyAmount > 0) (expense / budget.monthlyAmount).toFloat() else 0f
                    val progressColor = when {
                        percentage > 1f -> error
                        percentage > 0.8f -> tertiary
                        percentage > 0.5f -> MaterialTheme.colorScheme.secondary
                        else -> primary
                    }
                    val now = LocalDate.now()
                    val yearMonth = YearMonth.from(now)
                    val daysInMonth = yearMonth.lengthOfMonth()
                    val dayOfMonth = now.dayOfMonth
                    val dailyExpense = if (dayOfMonth > 0) expense / dayOfMonth else 0.0
                    val remaining = budget.monthlyAmount - expense
                    val remainingDays = daysInMonth - dayOfMonth + 1
                    val dailyRemaining = if (remaining > 0 && remainingDays > 0) remaining / remainingDays else 0.0

                    Spacer(Modifier.height(MaterialTheme.dimens.spacingLg))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("支出", color = onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(MaterialTheme.dimens.spacingSm))
                            Text(
                                text = String.format("%.2f", expense),
                                color = onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("预算", color = onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(MaterialTheme.dimens.spacingSm))
                            Text(
                                text = String.format("%.2f", budget.monthlyAmount),
                                color = onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(MaterialTheme.dimens.spacingSm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { percentage.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = progressColor,
                            trackColor = onSurfaceVariant.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.width(MaterialTheme.dimens.spacingSm))
                        Text(
                            text = "${(percentage * 100).toInt()}%",
                            color = onSurfaceVariant,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(MaterialTheme.dimens.spacingMd))

                    Text(
                        text = if (remaining >= 0) "剩余 ${String.format("%.2f", remaining)}" else "超支 ${String.format("%.2f", -remaining)}",
                        color = if (remaining >= 0) onSurfaceVariant else error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(MaterialTheme.dimens.spacingXs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "日均支出 ${String.format("%.2f", dailyExpense)}",
                            color = onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "日均预算 ${String.format("%.2f", dailyRemaining)}",
                            color = onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Spacer(Modifier.height(MaterialTheme.dimens.spacingXl))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ 设置月度预算",
                            color = primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordItem(
    record: Record,
    categories: List<Category>,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Column {
                    Text(text = record.note, fontWeight = FontWeight.Medium)
                    val cat = categories.find { it.name == record.categoryName }
                    val displayName = if (cat?.parentName != null) "${cat.parentName}-${cat.name}" else record.categoryName
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
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

@Composable
fun QuickInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onExpand: () -> Unit,
    focusRequester: FocusRequester
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExpand) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "展开",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                placeholder = {
                    Text(
                        "记一笔，如：早餐 15",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}
