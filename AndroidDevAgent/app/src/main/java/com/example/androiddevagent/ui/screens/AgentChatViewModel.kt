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
import com.example.androiddevagent.agent.vcs.GitIntegration
import com.example.androiddevagent.data.ChatMessageDao
import com.example.androiddevagent.data.ChatMessageEntity
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
    val awaitingConfirmation: AgentEvent.AwaitingConfirmationEvent? = null,
    val sessionId: String = "",
    val gitStatus: String = ""
)

@HiltViewModel
class AgentChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentEngine: AgentEngine,
    private val eventStream: EventStream,
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor,
    private val taskRecordDao: TaskRecordDao,
    private val shareManager: ShareManager,
    private val chatMessageDao: ChatMessageDao,
    private val gitIntegration: GitIntegration
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
        val savedSessionId = prefs.getString("session_id", "") ?: ""
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        return AgentChatUiState(
            projectPath = projectPath,
            sessionId = if (savedSessionId.isNotEmpty()) savedSessionId else UUID.randomUUID().toString()
        )
    }

    init {
        viewModelScope.launch {
            loadChatHistory()
        }

        viewModelScope.launch {
            eventStream.events.collect { event ->
                _uiState.value = _uiState.value.copy(
                    events = _uiState.value.events + event
                )
                saveEventToDb(event)
            }
        }
    }

    private suspend fun loadChatHistory() {
        val sessionId = _uiState.value.sessionId
        try {
            val entities = chatMessageDao.getBySession(sessionId)
            if (entities.isNotEmpty()) {
                val events = entities.map { entity ->
                    AgentEvent.fromJson(entity.eventType, entity.contentJson)
                }
                _uiState.value = _uiState.value.copy(events = events)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveEventToDb(event: AgentEvent) {
        viewModelScope.launch {
            try {
                val entity = ChatMessageEntity(
                    sessionId = _uiState.value.sessionId,
                    eventType = event.eventType(),
                    contentJson = event.toJson(),
                    timestamp = System.currentTimeMillis(),
                    projectPath = _uiState.value.projectPath
                )
                chatMessageDao.insert(entity)
            } catch (_: Exception) {
            }
        }
    }

    fun startNewSession() {
        val newSessionId = UUID.randomUUID().toString()
        prefs.edit().putString("session_id", newSessionId).apply()
        eventStream.clear()
        _uiState.value = _uiState.value.copy(
            events = emptyList(),
            sessionId = newSessionId,
            awaitingConfirmation = null,
            gitStatus = ""
        )
    }

    fun clearCurrentChat() {
        viewModelScope.launch {
            try {
                chatMessageDao.deleteBySession(_uiState.value.sessionId)
            } catch (_: Exception) {
            }
            startNewSession()
        }
    }

    fun sendTask(task: String) {
        currentTaskDescription = task
        taskStartTime = System.currentTimeMillis()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true)
            var finalEvent: AgentEvent? = null
            try {
                agentEngine.run(task).collect { event ->
                    finalEvent = event
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                finalEvent = AgentEvent.StuckDetectedEvent("任务已被用户中断")
            } catch (e: Exception) {
                finalEvent = AgentEvent.ErrorEvent("任务执行异常: ${e.message}")
                eventStream.emitSync(finalEvent as AgentEvent.ErrorEvent)
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
        prefs.edit().putString("project_path", path).apply()
        try {
            agentEngine.setProjectPath(path)
        } catch (_: Exception) {
        }
        _uiState.value = _uiState.value.copy(projectPath = path)
    }

    fun cloneRepo(url: String, directory: String) {
        sendTask("克隆仓库 $url 到 $directory")
    }

    fun gitPush() {
        sendTask("将当前更改推送到远程仓库")
    }

    fun gitPull() {
        sendTask("从远程仓库拉取最新更改")
    }

    fun refreshGitStatus() {
        viewModelScope.launch {
            try {
                val status = gitIntegration.getStatus()
                val branch = gitIntegration.getCurrentBranch()
                val statusText = buildString {
                    if (branch.success) append("分支: ${branch.output}\n")
                    if (status.success) append(status.output)
                    else append("未初始化 Git 仓库")
                }
                _uiState.value = _uiState.value.copy(gitStatus = statusText)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(gitStatus = "Git 不可用")
            }
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
