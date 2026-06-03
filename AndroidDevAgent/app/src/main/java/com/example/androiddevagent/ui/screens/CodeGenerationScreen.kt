package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.AgentResponse
import com.example.androiddevagent.agent.AndroidDevAgent
import com.example.androiddevagent.models.CodeGenerationRequest
import com.example.androiddevagent.models.CodeGenerationResult
import com.example.androiddevagent.models.ProgrammingLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeGenerationScreen(
    modifier: Modifier = Modifier,
    viewModel: CodeGenerationViewModel = hiltViewModel()
) {
    var userInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(ProgrammingLanguage.KOTLIN) }
    val uiState by viewModel.uiState.collectAsState()

    val languages = listOf(
        ProgrammingLanguage.KOTLIN,
        ProgrammingLanguage.JAVA,
        ProgrammingLanguage.PYTHON,
        ProgrammingLanguage.JAVASCRIPT
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "描述需求并选择语言，Agent 会生成可参考的代码片段。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            languages.forEach { language ->
                FilterChip(
                    selected = language == selectedLanguage,
                    onClick = { selectedLanguage = language },
                    label = { Text(language.displayName) }
                )
            }
        }

        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("描述你想要的代码功能...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (userInput.isNotBlank()) {
                    viewModel.generateCode(userInput, selectedLanguage)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = userInput.isNotBlank() && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("生成中...")
            } else {
                Text("生成代码")
            }
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.generatedCode.isNotBlank()) {
            Text(
                text = "生成的代码:",
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
                        text = uiState.generatedCode,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* 复制到剪贴板 */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制代码")
                }

                OutlinedButton(
                    onClick = {
                        userInput = ""
                        viewModel.clearResult()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空")
                }
            }
        }
    }
}

@HiltViewModel
class CodeGenerationViewModel @Inject constructor(
    private val agent: AndroidDevAgent
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeGenerationUiState())
    val uiState: StateFlow<CodeGenerationUiState> = _uiState.asStateFlow()

    fun generateCode(description: String, language: ProgrammingLanguage) {
        viewModelScope.launch {
            val request = CodeGenerationRequest(
                description = description,
                language = language
            )

            agent.generateCode(request).collect { response ->
                when (response) {
                    is AgentResponse.Success -> {
                        val code = (response.data as? CodeGenerationResult)?.code
                            ?: response.data.toString()
                        _uiState.update {
                            it.copy(
                                generatedCode = code,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    is AgentResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message
                            )
                        }
                    }
                    is AgentResponse.Loading -> {
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                error = null
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearResult() {
        _uiState.value = CodeGenerationUiState()
    }
}

data class CodeGenerationUiState(
    val generatedCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
