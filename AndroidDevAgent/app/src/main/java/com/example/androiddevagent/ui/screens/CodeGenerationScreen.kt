package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.R
import com.example.androiddevagent.agent.LLMProvider
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
import com.example.androiddevagent.ui.components.ErrorCard
import com.example.androiddevagent.ui.components.LoadingIndicator
import com.example.androiddevagent.ui.theme.DevAgentTheme
import com.example.androiddevagent.models.ProgrammingLanguage
import com.example.androiddevagent.utils.InputValidator
import com.example.androiddevagent.utils.RateLimitResult
import com.example.androiddevagent.utils.RateLimiter
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
            text = stringResource(R.string.screen_code_generation_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = stringResource(R.string.label_select_language),
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
            label = { Text(stringResource(R.string.label_code_requirement)) },
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
            Text(stringResource(R.string.action_generate_code))
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

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingIndicator(
                statusMessage = uiState.loadingMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.generatedCode.isNotBlank()) {
            Text(
                text = stringResource(R.string.title_generated_code_colon),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = DevAgentTheme.colors.codeBlockContainer
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
                        fontFamily = FontFamily.Monospace,
                        color = DevAgentTheme.colors.onCodeBlockContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        copyTextToClipboard(
                            context,
                            context.getString(R.string.clipboard_generated_code),
                            uiState.generatedCode
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_copy_code))
                }

                OutlinedButton(
                    onClick = {
                        userInput = ""
                        viewModel.clearResult()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_clear))
                }
            }
        }
    }
}

@HiltViewModel
class CodeGenerationViewModel @Inject constructor(
    private val llmProvider: LLMProvider,
    private val conversationDao: ConversationDao,
    private val rateLimiter: RateLimiter
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeGenerationUiState())
    val uiState: StateFlow<CodeGenerationUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    fun generateCode(description: String, language: ProgrammingLanguage) {
        val sanitizedInput = InputValidator.sanitizeUserInput(description)
        val trimmedDescription = sanitizedInput.value
        if (trimmedDescription.isBlank()) {
            _uiState.update { it.copy(error = "请先描述你想要生成的代码") }
            return
        }

        val rateLimitWarning = when (val rateLimitResult = rateLimiter.tryAcquire(ACTION_KEY)) {
            is RateLimitResult.Allowed -> rateLimitResult.warningMessage
            is RateLimitResult.Blocked -> {
                _uiState.update { it.copy(error = rateLimitResult.message) }
                return
            }
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = CodeGenerationUiState(
                isLoading = true,
                loadingMessage = if (sanitizedInput.wasTruncated) {
                    "输入内容已截断至 ${sanitizedInput.maxLength} 字符，正在生成代码..."
                } else {
                    "正在生成代码..."
                },
                error = rateLimitWarning
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
                    it.copy(
                        isLoading = false,
                        loadingMessage = null
                    )
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

    private companion object {
        const val ACTION_KEY = "code_generation"
    }
}

data class CodeGenerationUiState(
    val generatedCode: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val error: String? = null
)
