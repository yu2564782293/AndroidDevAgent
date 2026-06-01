package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.llm.LlmConstants
import com.example.androiddevagent.agent.security.SecurityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKey by remember { mutableStateOf(uiState.apiKey) }
    var baseUrl by remember { mutableStateOf(uiState.baseUrl) }
    var modelName by remember { mutableStateOf(uiState.modelName) }
    var showApiKey by remember { mutableStateOf(false) }
    var projectPath by remember { mutableStateOf(uiState.projectPath) }
    var securityLevel by remember { mutableStateOf(uiState.securityLevel) }
    var savedVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        apiKey = uiState.apiKey
        baseUrl = uiState.baseUrl
        modelName = uiState.modelName
        projectPath = uiState.projectPath
        securityLevel = uiState.securityLevel
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("模型配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API 密钥") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showApiKey) "隐藏" else "显示"
                        )
                    }
                },
                singleLine = true
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("接口地址") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(LlmConstants.DEFAULT_BASE_URL) },
                singleLine = true
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(LlmConstants.DEFAULT_MODEL) },
                singleLine = true
            )

            Divider()

            Text("项目", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = projectPath,
                onValueChange = { projectPath = it },
                label = { Text("项目路径") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("/sdcard/MyProject") },
                singleLine = true
            )

            Text(
                "输入设备上 Android 项目目录的绝对路径",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text("安全策略", style = MaterialTheme.typography.titleMedium)

            Text(
                "控制 Agent 执行操作前是否需要您的确认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SecurityLevelSelector(
                selected = securityLevel,
                onSelected = { securityLevel = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveSettings(apiKey, baseUrl, modelName, projectPath, securityLevel)
                    savedVisible = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }

            if (savedVisible && uiState.saved) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        "设置已保存！",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SecurityLevelSelector(
    selected: SecurityLevel,
    onSelected: (SecurityLevel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecurityLevelOption(
            level = SecurityLevel.AUTO_CONFIRM,
            title = "自动确认",
            description = "Agent 自动执行所有操作（最快，安全性最低）",
            selected = selected == SecurityLevel.AUTO_CONFIRM,
            onSelect = { onSelected(SecurityLevel.AUTO_CONFIRM) }
        )
        SecurityLevelOption(
            level = SecurityLevel.DANGEROUS_CONFIRM,
            title = "危险操作确认",
            description = "仅确认删除、构建等危险操作（推荐）",
            selected = selected == SecurityLevel.DANGEROUS_CONFIRM,
            onSelect = { onSelected(SecurityLevel.DANGEROUS_CONFIRM) }
        )
        SecurityLevelOption(
            level = SecurityLevel.ALL_CONFIRM,
            title = "全部确认",
            description = "执行任何操作前都需要确认（最安全，最慢）",
            selected = selected == SecurityLevel.ALL_CONFIRM,
            onSelect = { onSelected(SecurityLevel.ALL_CONFIRM) }
        )
    }
}

@Composable
private fun SecurityLevelOption(
    level: SecurityLevel,
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
