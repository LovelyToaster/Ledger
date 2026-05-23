package com.verdantgem.ledger.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verdantgem.ledger.ui.theme.dimens
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateToSettings: () -> Unit) {
    val d = MaterialTheme.dimens
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets.statusBars
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(d.spacingMd))
            Text(text = "简记账", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = "Version 1.3.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            
            Spacer(modifier = Modifier.height(d.spacingXl))

            // 设置入口
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                ListItem(
                    headlineContent = { Text("设置", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigateToSettings() }
                )
            }

        }
    }
}

@Composable
fun WebDavSettingsSection() {
    // 简化后的配置项
    var webDavUrl by remember { mutableStateOf("") }
    
    OutlinedTextField(
        value = webDavUrl,
        onValueChange = { webDavUrl = it },
        label = { Text("服务器地址", fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
    
    Spacer(modifier = Modifier.height(12.dp))
    
    Button(
        onClick = { /* 保存逻辑 */ },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Text("验证并保存同步配置")
    }
}

// 辅助字体大小
// 注意：上面代码中 Text("服务器地址", fontSize = 12.sp) 是笔误，应该是 12.sp 的单位。
// 在实际实现中，我直接用 MaterialTheme.typography。
