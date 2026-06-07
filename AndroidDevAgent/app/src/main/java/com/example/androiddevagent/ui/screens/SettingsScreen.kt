package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.R
import com.example.androiddevagent.settings.LLMProvider
import java.util.Locale
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
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderDropdown(
            selectedProvider = uiState.selectedProvider,
            onProviderSelected = viewModel::onProviderSelected
        )

        OutlinedTextField(
            value = uiState.config.apiKey,
            onValueChange = viewModel::onApiKeyChanged,
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            singleLine = true,
            visualTransformation = if (uiState.isApiKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                    Icon(
                        imageVector = if (uiState.isApiKeyVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (uiState.isApiKeyVisible) {
                            stringResource(R.string.settings_hide_api_key)
                        } else {
                            stringResource(R.string.settings_show_api_key)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.config.baseUrl,
            onValueChange = viewModel::onBaseUrlChanged,
            label = { Text(stringResource(R.string.settings_base_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.config.modelName,
            onValueChange = viewModel::onModelNameChanged,
            label = { Text(stringResource(R.string.settings_model_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        ModelSuggestions(
            provider = uiState.selectedProvider,
            onModelSelected = viewModel::onModelNameChanged
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_temperature_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = String.format(Locale.US, "%.1f", uiState.config.temperature),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = uiState.config.temperature.toFloat(),
                onValueChange = viewModel::onTemperatureChanged,
                valueRange = 0f..2f,
                steps = 19
            )
        }

        OutlinedTextField(
            value = uiState.maxTokensInput,
            onValueChange = viewModel::onMaxTokensChanged,
            label = { Text(stringResource(R.string.settings_max_tokens_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        uiState.successMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_testing))
                } else {
                    Text(stringResource(R.string.btn_test_connection))
                }
            }

            Button(
                onClick = viewModel::saveConfig,
                enabled = !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_saving))
                } else {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
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

    LaunchedEffect(uiState.selectedProvider, uiState.saved) {
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

            if (uiState.saveError.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        uiState.saveError,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selectedProvider: LLMProvider,
    onProviderSelected: (LLMProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_provider_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LLMProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        expanded = false
                        onProviderSelected(provider)
                    }
                )
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
private fun ModelSuggestions(
    provider: LLMProvider,
    onModelSelected: (String) -> Unit
) {
    if (provider.modelSuggestions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_common_models),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            provider.modelSuggestions.forEach { modelName ->
                AssistChip(
                    onClick = { onModelSelected(modelName) },
                    label = { Text(modelName) }
                )
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
