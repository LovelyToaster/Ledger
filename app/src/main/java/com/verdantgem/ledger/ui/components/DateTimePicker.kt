package com.verdantgem.ledger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialDate: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)

    val initialCal = Calendar.getInstance().apply { timeInMillis = initialDate }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择时间", fontWeight = FontWeight.Bold) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: initialDate
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = selectedMillis
                    }
                    val localCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(localCal.timeInMillis)
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("上一步") }
            }
        )
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTimePicker = true }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

fun formatDateTime(millis: Long, pattern: String = "yyyy-MM-dd HH:mm"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(millis))
}
