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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.LLMProvider
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
import com.example.androiddevagent.ui.components.ErrorCard
import com.example.androiddevagent.models.ProgrammingLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    val context = LocalContext.current

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
            ErrorCard(
                message = error,
                onRetry = {
                    viewModel.generateCode(userInput, selectedLanguage)
                },
                retryEnabled = userInput.isNotBlank() && !uiState.isLoading
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
                    onClick = {
                        copyTextToClipboard(context, "生成的代码", uiState.generatedCode)
                    },
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
    private val llmProvider: LLMProvider,
    private val conversationDao: ConversationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeGenerationUiState())
    val uiState: StateFlow<CodeGenerationUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    fun generateCode(description: String, language: ProgrammingLanguage) {
        val trimmedDescription = description.trim()
        if (trimmedDescription.isBlank()) {
            _uiState.update { it.copy(error = "请先描述你想要生成的代码") }
            return
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = CodeGenerationUiState(
                isLoading = true
            )

            try {
                val responseBuilder = StringBuilder()

                llmProvider.streamCompletion(
                    buildCodeGenerationPrompt(trimmedDescription, language)
                ).collect { token ->
                    responseBuilder.append(token)
                    _uiState.update {
                        it.copy(
                            generatedCode = responseBuilder.toString(),
                            error = null
                        )
                    }
                }

                val response = responseBuilder.toString()
                if (response.isNotBlank()) {
                    saveConversation(trimmedDescription, response, language)
                }

                _uiState.update {
                    it.copy(isLoading = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "代码生成失败"
                    )
                }
            }
        }
    }

    private suspend fun saveConversation(
        description: String,
        response: String,
        language: ProgrammingLanguage
    ) {
        runCatching {
            conversationDao.insert(
                Conversation(
                    screenType = "code_gen",
                    userMessage = description,
                    aiResponse = response,
                    language = language.displayName
                )
            )
        }
    }

    fun clearResult() {
        generationJob?.cancel()
        _uiState.value = CodeGenerationUiState()
    }

    private fun buildCodeGenerationPrompt(
        description: String,
        language: ProgrammingLanguage
    ): String {
        return """
            系统指令：你是 AndroidDevAgent，专注帮助 Android 开发者生成可靠、可维护的代码。请根据用户需求生成完整、可参考的 ${language.displayName} 代码。

            请提供：
            1. 可直接参考的完整实现代码，使用 Markdown fenced code block。
            2. 必要的错误处理、边界条件和生命周期注意事项。
            3. 关键实现说明和 Android 最佳实践建议。
            4. 如需求不完整，请先基于明确假设生成方案，并列出假设。

            用户需求：
            ```text
            $description
            ```
        """.trimIndent()
    }
}

data class CodeGenerationUiState(
    val generatedCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
