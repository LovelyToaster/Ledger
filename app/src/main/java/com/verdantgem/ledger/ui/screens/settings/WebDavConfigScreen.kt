package com.verdantgem.ledger.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.verdantgem.ledger.ui.theme.dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavConfigScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val d = MaterialTheme.dimens
    val vm = settingsViewModel
    var showDialog by remember { mutableStateOf(false) }
    var showIdentityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据同步设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ListItem(
                headlineContent = { Text("WebDAV参数配置") },
                supportingContent = {
                    Text(if (vm.webDavUrl.isNotEmpty()) "已配置" else "未配置")
                },
                leadingContent = {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { showDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = d.spacingMd, vertical = d.spacingSm), thickness = 0.5.dp)

            ListItem(
                headlineContent = { Text("同步身份") },
                supportingContent = {
                    Text(
                        if (vm.syncUsernameSet) "${vm.syncUsername}${if (vm.encryptionPasswordSet) " · 已加密" else " · 未加密"}"
                        else "未设置"
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { showIdentityDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = d.spacingMd, vertical = d.spacingSm), thickness = 0.5.dp)

            SwitchSettingRow(
                title = "自动同步",
                subtitle = when {
                    vm.webDavUrl.isEmpty() -> "请先配置 WebDAV 参数"
                    !vm.connectionTestSuccess -> "请先测试连接"
                    !vm.syncUsernameSet -> "请先设置同步身份"
                    vm.autoSyncEnabled -> "已开启"
                    else -> "已关闭"
                },
                checked = vm.autoSyncEnabled,
                enabled = vm.webDavUrl.isNotEmpty() && vm.connectionTestSuccess && vm.syncUsernameSet,
                onCheckedChange = { vm.setAutoSync(it) }
            )

            SwitchSettingRow(
                title = "自动备份（SQLite数据库）",
                subtitle = if (vm.autoBackupEnabled) "已开启" else "已关闭",
                checked = vm.autoBackupEnabled,
                enabled = vm.webDavUrl.isNotEmpty() && vm.connectionTestSuccess && vm.syncUsernameSet,
                onCheckedChange = { vm.setAutoBackup(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = d.spacingMd, vertical = d.spacingSm), thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("同步状态", style = MaterialTheme.typography.bodyLarge)
                    vm.lastSyncTime?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("上次同步: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vm.syncStatusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = when (vm.syncStatus) {
                                SyncStatus.ERROR -> MaterialTheme.colorScheme.error
                                SyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                    }
                }
                if (vm.syncStatus == SyncStatus.SYNCING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Button(
                        onClick = { vm.triggerSync() },
                        enabled = vm.webDavUrl.isNotEmpty() && vm.connectionTestSuccess && vm.syncUsernameSet
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即同步")
                    }
                }
            }
        }
    }

    if (showDialog) {
        WebDavConfigDialog(
            initialUrl = vm.webDavUrl,
            initialUser = vm.webDavUser,
            initialPass = vm.webDavPass,
            testResultMessage = vm.testResultMessage,
            isTesting = vm.isTestingConnection,
            onSave = { url, user, pass -> vm.saveConfig(url, user, pass) },
            onTest = { url, user, pass -> vm.testConnection(url, user, pass) },
            onDismiss = {
                showDialog = false
                vm.clearTestResult()
            }
        )
    }

    if (showIdentityDialog) {
        SyncIdentityDialog(
            currentUsername = vm.syncUsername,
            currentPassword = vm.encryptionPassword,
            onSave = { username, password -> vm.updateSyncIdentity(username, password) },
            onClear = { vm.clearSyncIdentity() },
            onDismiss = { showIdentityDialog = false }
        )
    }
}

@Composable
private fun SyncIdentityDialog(
    currentUsername: String,
    currentPassword: String,
    onSave: (username: String, password: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(currentUsername) }
    var password by remember { mutableStateOf(currentPassword) }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth()
            ) {
                Text("同步身份", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = { Text("同步用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如: alice") }
                )
                Text(
                    "在多台设备上使用相同的用户名，数据将隔离到同一目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("加密密码（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("确认密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onClear()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = currentUsername.isNotEmpty() || currentPassword.isNotEmpty()
                    ) {
                        Text("清除")
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (username.isBlank()) {
                                error = "请输入同步用户名"
                            } else if (password.length > 0 && password.length < 4) {
                                error = "密码至少4位"
                            } else if (password != confirmPassword) {
                                error = "两次输入的密码不一致"
                            } else {
                                onSave(username.trim(), password)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun WebDavConfigDialog(
    initialUrl: String,
    initialUser: String,
    initialPass: String,
    testResultMessage: String?,
    isTesting: Boolean,
    onSave: (url: String, user: String, pass: String) -> Unit,
    onTest: (url: String, user: String, pass: String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth()
            ) {
                Text("WebDAV参数配置", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/dav") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )

                if (testResultMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = testResultMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testResultMessage.contains("成功"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                if (isTesting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                    OutlinedButton(
                        onClick = { onSave(url, user, pass) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                    Button(
                        onClick = { onTest(url, user, pass) },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting
                    ) {
                        Text("测试")
                    }
                }
            }
        }
    }
}
