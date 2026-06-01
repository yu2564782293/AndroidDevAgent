package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.events.EventStream
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.tools.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val agentEngine: AgentEngine,
    private val eventStream: EventStream,
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null

    private fun loadInitialState(): AgentChatUiState {
        val projectPath = prefs.getString("project_path", "") ?: ""
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        return AgentChatUiState(projectPath = projectPath)
    }

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
        prefs.edit().putString("project_path", path).apply()
        agentEngine.setProjectPath(path)
        _uiState.value = _uiState.value.copy(projectPath = path)
    }
}
