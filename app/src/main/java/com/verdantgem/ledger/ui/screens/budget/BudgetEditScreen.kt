package com.verdantgem.ledger.ui.screens.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.ui.theme.dimens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditScreen(
    onBack: () -> Unit,
    viewModel: BudgetEditViewModel = hiltViewModel()
) {
    val budget by viewModel.budget.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()
    val scope = rememberCoroutineScope()
    val d = MaterialTheme.dimens

    var inputAmount by remember(budget) { mutableStateOf(if (budget != null) budget!!.monthlyAmount.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("月度预算", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = d.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            if (budget != null) {
                Text(
                    text = "当前预算",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\uFFE5${String.format("%.2f", budget!!.monthlyAmount)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "尚未设置月度预算",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = inputAmount,
                onValueChange = {
                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        inputAmount = it
                        error = null
                    }
                },
                label = { Text("预算金额") },
                prefix = { Text("\uFFE5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "本月已支出 \uFFE5${String.format("%.2f", monthlyExpense)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amount = inputAmount.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        error = "请输入有效的预算金额"
                    } else {
                        scope.launch {
                            viewModel.saveBudget(amount)
                            onBack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = inputAmount.isNotBlank()
            ) {
                Text(
                    text = if (budget != null) "更新预算" else "设置预算",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (budget != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.clearBudget()
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清除预算", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
