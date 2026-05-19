package com.verdantgem.ledger.ui.screens.record

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.ui.components.DateTimePickerDialog

import com.verdantgem.ledger.ui.theme.WindowWidth
import com.verdantgem.ledger.ui.theme.dimens
import com.verdantgem.ledger.ui.theme.windowSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    onBack: () -> Unit,
    onNavigateToCategoryEdit: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val allCategories by viewModel.allCategories.collectAsState()
    val d = MaterialTheme.dimens
    val windowSize = MaterialTheme.windowSize
    val gridColumns = when (windowSize.width) {
        WindowWidth.COMPACT -> 4
        WindowWidth.MEDIUM -> 5
        WindowWidth.EXPANDED -> 6
    }
    var isIncome by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("0.00") }
    var note by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            viewModel.startLocation()
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLocation() }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startLocation()
        } else {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val currentCategories = remember(allCategories, isIncome) {
        allCategories.filter { it.isIncome == isIncome }
    }
    
    val parentCategories = remember(currentCategories) {
        currentCategories.filter { it.parentName == null }
    }
    
    var selectedSub by remember { mutableStateOf("") }
    var expandedParent by remember { mutableStateOf("") }
    var billDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var userTouchedCategory by remember { mutableStateOf(false) }
    val noteMatchedCategory = remember(note, allCategories) {
        val parsedNote = note.trim().takeIf { it.isNotEmpty() } ?: return@remember null
        fun matchIn(list: List<Category>): Category? {
            val exact = list.firstOrNull { it.name == parsedNote }
            if (exact != null) return exact
            val promptExact = list.firstOrNull {
                it.prompts.split(",", "，").any { p -> p.trim() == parsedNote }
            }
            if (promptExact != null) return promptExact
            val promptContain = list.firstOrNull {
                it.prompts.split(",", "，").any { p -> parsedNote.contains(p.trim()) || p.trim().contains(parsedNote) }
            }
            if (promptContain != null) return promptContain
            return list.firstOrNull { parsedNote.contains(it.name) || it.name.contains(parsedNote) }
        }
        val incomeCats = allCategories.filter { it.isIncome }
        val expenseCats = allCategories.filter { !it.isIncome }
        val incomeMatch = matchIn(incomeCats)
        val expenseMatch = matchIn(expenseCats)
        val note2 = parsedNote
        when {
            incomeMatch != null && expenseMatch != null -> {
                if (incomeMatch.name == note2 || incomeMatch.prompts.contains(note2)) incomeMatch
                else if (expenseMatch.name == note2 || expenseMatch.prompts.contains(note2)) expenseMatch
                else expenseMatch
            }
            incomeMatch != null -> incomeMatch
            expenseMatch != null -> expenseMatch
            else -> null
        }
    }
    LaunchedEffect(noteMatchedCategory) {
        if (!userTouchedCategory && noteMatchedCategory != null) {
            selectedSub = noteMatchedCategory.name
            isIncome = noteMatchedCategory.isIncome
            if (noteMatchedCategory.parentName != null) {
                expandedParent = noteMatchedCategory.parentName
            }
        }
    }

    fun save() {
        if (selectedSub.isEmpty()) {
            Toast.makeText(context, "请选择分类", Toast.LENGTH_SHORT).show()
            return
        }
        val amt = amount.toDoubleOrNull() ?: 0.0
        if (amt <= 0.0) {
            Toast.makeText(context, "请输入有效金额", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val success = viewModel.saveRecord(
                amount = amt,
                note = note,
                categoryName = selectedSub,
                isIncome = isIncome,
                billDate = billDate
            )
            if (success) onBack()
        }
    }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    val navBarDp = with(density) { navBarPx.toDp() }
    val customKeyboardHeight = 260.dp + navBarDp + 16.dp
    val actualKeyboardDp = maxOf(0.dp, customKeyboardHeight - imeBottomDp)
    val keyboardAlpha = (actualKeyboardDp / customKeyboardHeight).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = imeBottomDp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    .background(if (!isIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isIncome = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("支出", color = if (!isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isIncome = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("收入", color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onNavigateToCategoryEdit) {
                            Text("编辑", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    if (parentCategories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("暂无类别，点击右上角编辑", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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
                                        val hasSelectedSub = subs.any { it.name == selectedSub }
                                        Box(modifier = Modifier.weight(1f)) {
                                            CategoryItem(
                                                label = parent.name,
                                                isSelected = expandedParent == parent.name || hasSelectedSub,
                                                onClick = {
                                                    userTouchedCategory = true
                                                    if (subs.isNotEmpty()) {
                                                        if (expandedParent != parent.name) selectedSub = ""
                                                        expandedParent = if (expandedParent == parent.name) "" else parent.name
                                                    } else {
                                                        selectedSub = parent.name
                                                    }
                                                }
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
                                                .padding(12.dp),
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
                                                                isSelected = selectedSub == sub.name,
                                                                onClick = { userTouchedCategory = true; selectedSub = sub.name }
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("备注", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 6.dp))
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.widthIn(min = 80.dp),
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actualKeyboardDp)
                    .alpha(keyboardAlpha)
                    .clipToBounds()
            ) {
                Surface(
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                ) {
                    Column {
                        CompactNumberKeyboard(
                            onNumberClick = { num ->
                                when {
                                    amount == "0.00" && num == "." -> amount = "0."
                                    amount == "0.00" && num == "0" -> return@CompactNumberKeyboard
                                    amount == "0.00" -> amount = num
                                    amount.contains(".") && amount.substringAfter(".").length >= 2 -> return@CompactNumberKeyboard
                                    num == "." && amount.contains(".") -> return@CompactNumberKeyboard
                                    else -> amount += num
                                }
                            },
                            onDeleteClick = {
                                if (amount.length > 1) amount = amount.dropLast(1)
                                else amount = "0.00"
                            },
                            onDoneClick = { save() }
                        )
                        if (navBarPx > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(navBarDp)
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DateTimePickerDialog(
            initialDate = billDate,
            onConfirm = { billDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun CategoryItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
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
fun CompactNumberKeyboard(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp, top = 4.dp)
    ) {
        Column(modifier = Modifier.weight(3f)) {
            keys.forEach { row ->
                Row(modifier = Modifier.weight(1f)) {
                    row.forEach { key ->
                        KeyButton(key, Modifier.weight(1f)) { onNumberClick(key) }
                    }
                }
            }
            Row(modifier = Modifier.weight(1f)) {
                KeyButton(".", Modifier.weight(1f)) { onNumberClick(".") }
                KeyButton("0", Modifier.weight(1f)) { onNumberClick("0") }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            KeyButton(null, Modifier.weight(1f).fillMaxWidth(), Icons.AutoMirrored.Filled.Backspace) { onDeleteClick() }
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxWidth()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        )
                    )
                    .clickable { onDoneClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "确定", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun KeyButton(text: String?, modifier: Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        onClick = onClick,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            } else if (text != null) {
                Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
