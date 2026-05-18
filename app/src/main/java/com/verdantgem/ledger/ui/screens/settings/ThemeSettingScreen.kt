package com.verdantgem.ledger.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verdantgem.ledger.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingScreen(
    onBack: () -> Unit,
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ThemeOptionItem("跟随系统", ThemeMode.SYSTEM, currentMode, onModeChange)
            ThemeOptionItem("浅色模式", ThemeMode.LIGHT, currentMode, onModeChange)
            ThemeOptionItem("深色模式", ThemeMode.DARK, currentMode, onModeChange)
        }
    }
}

@Composable
fun ThemeOptionItem(
    label: String,
    mode: ThemeMode,
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            RadioButton(selected = mode == currentMode, onClick = null)
        },
        modifier = Modifier.clickable { onSelect(mode) }
    )
}
