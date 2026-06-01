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
fun ArchitectureScreen(
    viewModel: ArchitectureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var projectDescription by remember { mutableStateOf("") }
    var requirementsText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "架构设计",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = projectDescription,
            onValueChange = { projectDescription = it },
            label = { Text("描述你的项目...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = requirementsText,
            onValueChange = { requirementsText = it },
            label = { Text("项目需求（每行一个）") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (projectDescription.isNotBlank()) {
                    val requirements = requirementsText.lines().filter { it.isNotBlank() }
                    viewModel.designArchitecture(projectDescription, requirements)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = projectDescription.isNotBlank() && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("设计中...")
            } else {
                Text("设计架构")
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

        if (uiState.suggestedArchitecture.isNotBlank()) {
            Text(
                text = "架构建议:",
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
                        text = uiState.suggestedArchitecture,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (uiState.components.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "核心组件:",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    uiState.components.forEach { component ->
                        AssistChip(
                            onClick = { },
                            label = { Text(component, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (uiState.patterns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "设计模式:",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    uiState.patterns.forEach { pattern ->
                        AssistChip(
                            onClick = { },
                            label = { Text(pattern, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (uiState.bestPractices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "最佳实践:",
                    style = MaterialTheme.typography.titleSmall
                )
                uiState.bestPractices.forEach { practice ->
                    Text(
                        text = "• $practice",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@HiltViewModel
class ArchitectureViewModel @Inject constructor(
    private val agent: AndroidDevAgent
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchitectureUiState())
    val uiState: StateFlow<ArchitectureUiState> = _uiState.asStateFlow()

    fun designArchitecture(projectDescription: String, requirements: List<String>) {
        viewModelScope.launch {
            agent.designArchitecture(projectDescription, requirements).collect { response ->
                when (response) {
                    is AgentResponse.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is AgentResponse.Success -> {
                        val result = response.data as? com.example.androiddevagent.models.ArchitectureResult
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            suggestedArchitecture = result?.suggestedArchitecture ?: response.data.toString(),
                            components = result?.components ?: emptyList(),
                            patterns = result?.patterns ?: emptyList(),
                            bestPractices = result?.bestPractices ?: emptyList(),
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

data class ArchitectureUiState(
    val suggestedArchitecture: String = "",
    val components: List<String> = emptyList(),
    val patterns: List<String> = emptyList(),
    val bestPractices: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
