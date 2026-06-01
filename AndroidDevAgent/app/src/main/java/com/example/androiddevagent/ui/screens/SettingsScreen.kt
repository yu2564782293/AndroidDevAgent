package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.llm.LlmConstants
import com.example.androiddevagent.agent.security.SecurityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKey by remember { mutableStateOf(uiState.apiKey) }
    var baseUrl by remember { mutableStateOf(uiState.baseUrl) }
    var modelName by remember { mutableStateOf(uiState.modelName) }
    var showApiKey by remember { mutableStateOf(false) }
    var projectPath by remember { mutableStateOf(uiState.projectPath) }
    var securityLevel by remember { mutableStateOf(uiState.securityLevel) }

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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("LLM Configuration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showApiKey) "Hide" else "Show"
                        )
                    }
                },
                singleLine = true
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(LlmConstants.DEFAULT_BASE_URL) },
                singleLine = true
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(LlmConstants.DEFAULT_MODEL) },
                singleLine = true
            )

            Divider()

            Text("Project", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = projectPath,
                onValueChange = { projectPath = it },
                label = { Text("Project Path") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("/sdcard/MyProject") },
                singleLine = true
            )

            Text(
                "Enter the absolute path to your Android project directory on the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text("Security", style = MaterialTheme.typography.titleMedium)

            Text(
                "Control when the Agent needs your confirmation before executing actions.",
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
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            if (uiState.saved) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        "Settings saved!",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
            title = "Auto Confirm",
            description = "Agent executes all actions automatically (fastest, least safe)",
            selected = selected == SecurityLevel.AUTO_CONFIRM,
            onSelect = { onSelected(SecurityLevel.AUTO_CONFIRM) }
        )
        SecurityLevelOption(
            level = SecurityLevel.DANGEROUS_CONFIRM,
            title = "Confirm Dangerous",
            description = "Only confirm dangerous actions like delete and build (recommended)",
            selected = selected == SecurityLevel.DANGEROUS_CONFIRM,
            onSelect = { onSelected(SecurityLevel.DANGEROUS_CONFIRM) }
        )
        SecurityLevelOption(
            level = SecurityLevel.ALL_CONFIRM,
            title = "Confirm All",
            description = "Confirm every action before execution (safest, slowest)",
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
