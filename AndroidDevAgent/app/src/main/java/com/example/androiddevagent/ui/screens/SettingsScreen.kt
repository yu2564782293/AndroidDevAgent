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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.llm.LlmConstants

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

    LaunchedEffect(uiState) {
        apiKey = uiState.apiKey
        baseUrl = uiState.baseUrl
        modelName = uiState.modelName
        projectPath = uiState.projectPath
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveSettings(apiKey, baseUrl, modelName, projectPath)
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
