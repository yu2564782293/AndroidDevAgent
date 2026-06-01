package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.AndroidDevAgent
import com.example.androiddevagent.agent.AgentResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggingScreen(
    viewModel: DebuggingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var errorDescription by remember { mutableStateOf("") }
    var codeSnippet by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "调试助手",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = errorDescription,
            onValueChange = { errorDescription = it },
            label = { Text("描述你遇到的错误...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = codeSnippet,
            onValueChange = { codeSnippet = it },
            label = { Text("相关代码片段（可选）") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (errorDescription.isNotBlank()) {
                    viewModel.debugError(errorDescription, codeSnippet.ifBlank { null })
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = errorDescription.isNotBlank() && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("分析中...")
            } else {
                Text("开始调试")
            }
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.suggestedSolution.isNotBlank()) {
            Text(
                text = "解决方案:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = uiState.suggestedSolution,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (uiState.alternativeSolutions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "备选方案:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                uiState.alternativeSolutions.forEach { solution ->
                    Text(
                        text = "• $solution",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            if (uiState.confidence > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = uiState.confidence.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "置信度: ${(uiState.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@HiltViewModel
class DebuggingViewModel @Inject constructor(
    private val agent: AndroidDevAgent
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebuggingUiState())
    val uiState: StateFlow<DebuggingUiState> = _uiState.asStateFlow()

    fun debugError(errorDescription: String, codeSnippet: String?) {
        viewModelScope.launch {
            agent.debugError(errorDescription, codeSnippet).collect { response ->
                when (response) {
                    is AgentResponse.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is AgentResponse.Success -> {
                        val result = response.data as? com.example.androiddevagent.models.DebugResult
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            suggestedSolution = result?.suggestedSolution ?: response.data.toString(),
                            confidence = result?.confidence ?: 0.0,
                            alternativeSolutions = result?.alternativeSolutions ?: emptyList(),
                            error = null
                        )
                    }
                    is AgentResponse.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = response.message
                        )
                    }
                }
            }
        }
    }
}

data class DebuggingUiState(
    val suggestedSolution: String = "",
    val confidence: Double = 0.0,
    val alternativeSolutions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
