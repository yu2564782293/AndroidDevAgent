package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import com.example.androiddevagent.agent.llm.LlmProviderConfig
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
    var tokenBudget by remember { mutableStateOf(uiState.tokenBudget.toString()) }
    var gitToken by remember { mutableStateOf(uiState.gitToken) }
    var showGitToken by remember { mutableStateOf(false) }
    var maxIterations by remember { mutableStateOf(uiState.maxIterations.toString()) }

    LaunchedEffect(uiState) {
        apiKey = uiState.apiKey
        baseUrl = uiState.baseUrl
        modelName = uiState.modelName
        projectPath = uiState.projectPath
        securityLevel = uiState.securityLevel
        tokenBudget = uiState.tokenBudget.toString()
        gitToken = uiState.gitToken
        maxIterations = uiState.maxIterations.toString()
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
            Text("模型服务商", style = MaterialTheme.typography.titleMedium)

            LlmProviderSelector(
                providers = uiState.providers,
                selectedProvider = uiState.selectedProvider,
                onProviderSelected = { viewModel.selectProvider(it) }
            )

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

            Text("Token 用量", style = MaterialTheme.typography.titleMedium)

            TokenUsageCard(
                totalTokens = uiState.totalTokens,
                totalCost = uiState.totalCost,
                todayTokens = uiState.todayTokens,
                todayCost = uiState.todayCost
            )

            OutlinedTextField(
                value = tokenBudget,
                onValueChange = {
                    tokenBudget = it
                    it.toIntOrNull()?.let { budget -> viewModel.saveTokenBudget(budget) }
                },
                label = { Text("Token 预算上限") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0 = 无限制") },
                singleLine = true
            )

            Text(
                "每次任务消耗的 Token 达到上限时将自动停止",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Text("Git 配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = gitToken,
                onValueChange = {
                    gitToken = it
                    viewModel.saveGitToken(it)
                },
                label = { Text("GitHub Token") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showGitToken) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showGitToken = !showGitToken }) {
                        Icon(
                            if (showGitToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showGitToken) "隐藏" else "显示"
                        )
                    }
                },
                singleLine = true
            )

            Text(
                "用于 push/pull 等远程 Git 操作（需要 repo 权限）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text("Agent 配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = maxIterations,
                onValueChange = {
                    maxIterations = it
                    it.toIntOrNull()?.let { max -> viewModel.saveMaxIterations(max) }
                },
                label = { Text("最大迭代次数") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("50") },
                singleLine = true
            )

            Text(
                "Agent 执行复杂任务时的最大循环次数（5-500，默认50）",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmProviderSelector(
    providers: List<LlmProviderConfig>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        providers.forEach { provider ->
            FilterChip(
                selected = provider.id == selectedProvider,
                onClick = { onProviderSelected(provider.id) },
                label = { Text(provider.name, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun TokenUsageCard(
    totalTokens: Long,
    totalCost: Double,
    todayTokens: Long,
    todayCost: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${todayTokens / 1000}K", style = MaterialTheme.typography.titleMedium)
                Text("¥${String.format("%.3f", todayCost * 7.2)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("累计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${totalTokens / 1000}K", style = MaterialTheme.typography.titleMedium)
                Text("¥${String.format("%.3f", totalCost * 7.2)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
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
