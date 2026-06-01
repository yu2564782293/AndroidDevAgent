package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.AndroidDevAgent
import com.example.androiddevagent.agent.AgentResponse
import com.example.androiddevagent.models.ProgrammingLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeExplanationScreen(
    viewModel: CodeExplanationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var codeInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(ProgrammingLanguage.KOTLIN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "代码解释",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "选择编程语言:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            ProgrammingLanguage.entries.forEach { language ->
                FilterChip(
                    selected = language == selectedLanguage,
                    onClick = { selectedLanguage = language },
                    label = { Text(language.displayName) }
                )
            }
        }

        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("粘贴需要解释的代码...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 8
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (codeInput.isNotBlank()) {
                    viewModel.explainCode(codeInput, selectedLanguage)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = codeInput.isNotBlank() && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("分析中...")
            } else {
                Text("解释代码")
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

        if (uiState.explanation.isNotBlank()) {
            Text(
                text = "解释结果:",
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
                        text = uiState.explanation,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (uiState.suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "优化建议:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                uiState.suggestions.forEach { suggestion ->
                    Text(
                        text = "• $suggestion",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@HiltViewModel
class CodeExplanationViewModel @Inject constructor(
    private val agent: AndroidDevAgent
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeExplanationUiState())
    val uiState: StateFlow<CodeExplanationUiState> = _uiState.asStateFlow()

    fun explainCode(code: String, language: ProgrammingLanguage) {
        viewModelScope.launch {
            agent.explainCode(code, language).collect { response ->
                when (response) {
                    is AgentResponse.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is AgentResponse.Success -> {
                        val result = response.data as? com.example.androiddevagent.models.CodeExplanationResult
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            explanation = result?.explanation ?: response.data.toString(),
                            suggestions = result?.suggestions ?: emptyList(),
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

data class CodeExplanationUiState(
    val explanation: String = "",
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
