package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.events.EventStream
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.share.ShareManager
import com.example.androiddevagent.agent.tools.ToolExecutor
import com.example.androiddevagent.data.TaskRecordDao
import com.example.androiddevagent.data.TaskRecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
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
    private val toolExecutor: ToolExecutor,
    private val taskRecordDao: TaskRecordDao,
    private val shareManager: ShareManager
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null
    private var taskStartTime: Long = 0
    private var currentTaskDescription: String = ""

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
        currentTaskDescription = task
        taskStartTime = System.currentTimeMillis()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true)
            var finalEvent: AgentEvent? = null
            agentEngine.run(task).collect { event ->
                finalEvent = event
            }
            val durationMs = System.currentTimeMillis() - taskStartTime
            saveTaskRecord(task, finalEvent, durationMs)
            _uiState.value = _uiState.value.copy(isRunning = false)
        }
    }

    fun stopAgent() {
        currentJob?.cancel()
        val durationMs = System.currentTimeMillis() - taskStartTime
        saveTaskRecord(currentTaskDescription, null, durationMs, "INTERRUPTED")
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun confirmAction() {
        _uiState.value = _uiState.value.copy(awaitingConfirmation = null)
    }

    fun denyAction() {
        _uiState.value = _uiState.value.copy(awaitingConfirmation = null)
    }

    fun setProjectPath(path: String) {
        try {
            prefs.edit().putString("project_path", path).apply()
            agentEngine.setProjectPath(path)
            _uiState.value = _uiState.value.copy(projectPath = path)
        } catch (e: Exception) {
            prefs.edit().putString("project_path", path).apply()
            _uiState.value = _uiState.value.copy(projectPath = path)
        }
    }

    fun triggerBuild(task: String = "assembleDebug") {
        if (_uiState.value.projectPath.isEmpty()) return
        sendTask("执行 Gradle 构建: $task")
    }

    fun triggerInstallApk() {
        if (_uiState.value.projectPath.isEmpty()) return
        sendTask("安装最新的 debug APK")
    }

    fun triggerRunTests() {
        if (_uiState.value.projectPath.isEmpty()) return
        sendTask("运行项目测试")
    }

    fun shareReport(context: Context) {
        try {
            val reportFile = shareManager.exportTaskReport(
                _uiState.value.events,
                currentTaskDescription.ifBlank { "任务报告" }
            )
            shareManager.shareFile(context, reportFile, "text/markdown")
        } catch (_: Exception) {
        }
    }

    private fun saveTaskRecord(
        task: String,
        finalEvent: AgentEvent?,
        durationMs: Long,
        overrideStatus: String? = null
    ) {
        viewModelScope.launch {
            val status = overrideStatus ?: when (finalEvent) {
                is AgentEvent.TaskCompleteEvent -> "COMPLETED"
                is AgentEvent.StuckDetectedEvent -> "FAILED"
                is AgentEvent.ErrorEvent -> "FAILED"
                else -> "INTERRUPTED"
            }
            val summary = when (finalEvent) {
                is AgentEvent.TaskCompleteEvent -> finalEvent.summary
                is AgentEvent.StuckDetectedEvent -> finalEvent.reason
                is AgentEvent.ErrorEvent -> finalEvent.message
                else -> ""
            }
            val filesChanged = when (finalEvent) {
                is AgentEvent.TaskCompleteEvent -> finalEvent.filesChanged
                else -> emptyList()
            }
            val record = TaskRecordEntity(
                id = UUID.randomUUID().toString(),
                task = task,
                status = status,
                filesChanged = filesChanged,
                summary = summary.take(500),
                tokenUsage = 0,
                createdAt = taskStartTime,
                durationMs = durationMs,
                projectPath = _uiState.value.projectPath
            )
            try {
                taskRecordDao.insert(record)
            } catch (_: Exception) {
            }
        }
    }
}
