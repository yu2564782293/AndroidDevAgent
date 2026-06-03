package com.example.androiddevagent.floating

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.events.EventStream
import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.llm.LlmProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FloatingChatUiState(
    val messages: List<FloatingChatMessage> = emptyList(),
    val inputText: String = "",
    val isRunning: Boolean = false,
    val projectPath: String = ""
)

data class FloatingChatMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class FloatingChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentEngine: AgentEngine,
    private val eventStream: EventStream,
    private val llmProvider: LlmProvider
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(FloatingChatUiState())
    val uiState: StateFlow<FloatingChatUiState> = _uiState.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null

    init {
        val projectPath = prefs.getString("project_path", "") ?: ""
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        _uiState.value = _uiState.value.copy(projectPath = projectPath)

        viewModelScope.launch {
            eventStream.events.collect { event ->
                when (event) {
                    is AgentEvent.UserMessage -> {
                        addMessage(FloatingChatMessage(
                            content = event.content,
                            isUser = true
                        ))
                    }
                    is AgentEvent.AssistantThought -> {
                        addMessage(FloatingChatMessage(
                            content = event.content,
                            isUser = false
                        ))
                    }
                    is AgentEvent.TaskCompleteEvent -> {
                        addMessage(FloatingChatMessage(
                            content = "✅ ${event.summary}",
                            isUser = false
                        ))
                        _uiState.value = _uiState.value.copy(isRunning = false)
                    }
                    is AgentEvent.StuckDetectedEvent -> {
                        addMessage(FloatingChatMessage(
                            content = "⚠️ ${event.reason}",
                            isUser = false
                        ))
                        _uiState.value = _uiState.value.copy(isRunning = false)
                    }
                    is AgentEvent.ErrorEvent -> {
                        addMessage(FloatingChatMessage(
                            content = "❌ ${event.message}",
                            isUser = false
                        ))
                        _uiState.value = _uiState.value.copy(isRunning = false)
                    }
                    is AgentEvent.ToolCallEvent -> {
                        addMessage(FloatingChatMessage(
                            content = "🔧 ${event.name}(${event.args.entries.take(3).joinToString { "${it.key}=${it.value}" }})",
                            isUser = false
                        ))
                    }
                    else -> { }
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isRunning) return

        _uiState.value = _uiState.value.copy(inputText = "", isRunning = true)

        currentJob = viewModelScope.launch {
            try {
                agentEngine.run(text, buildHistoryMessages()).collect { }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(isRunning = false)
            } catch (e: Exception) {
                addMessage(FloatingChatMessage(
                    content = "❌ 任务执行异常: ${e.message}",
                    isUser = false
                ))
                _uiState.value = _uiState.value.copy(isRunning = false)
            }
        }
    }

    fun stopAgent() {
        currentJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    private fun addMessage(message: FloatingChatMessage) {
        val current = _uiState.value.messages
        val updated = (current + message).takeLast(50)
        _uiState.value = _uiState.value.copy(messages = updated)
    }

    private fun buildHistoryMessages(): List<ChatCompletionRequest.Message> {
        val messages = mutableListOf<ChatCompletionRequest.Message>()
        for (msg in _uiState.value.messages.takeLast(20)) {
            messages.add(ChatCompletionRequest.Message(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content
            ))
        }
        return messages
    }
}
