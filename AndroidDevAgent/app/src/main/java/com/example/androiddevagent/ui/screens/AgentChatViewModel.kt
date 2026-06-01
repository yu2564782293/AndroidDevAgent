package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.events.EventStream
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.tools.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentChatUiState(
    val events: List<AgentEvent> = emptyList(),
    val isRunning: Boolean = false,
    val projectPath: String = "",
    val awaitingConfirmation: AgentEvent.AwaitingConfirmationEvent? = null
)

@HiltViewModel
class AgentChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val eventStream: EventStream,
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            eventStream.events.collect { event ->
                _uiState.value = _uiState.value.copy(
                    events = _uiState.value.events + event
                )
            }
        }
    }

    fun sendTask(task: String) {
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true)
            agentEngine.run(task).collect { event ->
            }
            _uiState.value = _uiState.value.copy(isRunning = false)
        }
    }

    fun stopAgent() {
        currentJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun confirmAction() {
        _uiState.value = _uiState.value.copy(awaitingConfirmation = null)
    }

    fun denyAction() {
        _uiState.value = _uiState.value.copy(awaitingConfirmation = null)
    }

    fun setProjectPath(path: String) {
        agentEngine.setProjectPath(path)
        _uiState.value = _uiState.value.copy(projectPath = path)
    }
}
