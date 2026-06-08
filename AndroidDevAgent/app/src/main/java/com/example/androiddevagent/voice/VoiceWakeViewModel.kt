package com.example.androiddevagent.voice

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 语音唤醒设置页面的 ViewModel
 * 管理唤醒词注册、服务启停等状态
 */
data class VoiceWakeUiState(
    val isServiceRunning: Boolean = false,
    val isEnrolled: Boolean = false,
    val wakeWordName: String = "",
    val enrollmentProgress: Int = 0,       // 当前注册次数（0-3）
    val isEnrolling: Boolean = false,
    val enrollmentStepText: String = "",   // 注册步骤提示文字
    val isDetecting: Boolean = false,      // 是否检测到唤醒词
    val errorMessage: String = "",
    val vadMode: String = "unknown"        // "onnx" 或 "energy"
)

@HiltViewModel
class VoiceWakeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("voice_wake_prefs", Context.MODE_PRIVATE)
    }

    private val featureExtractor = PersonalWakeFeatureExtractor()
    private val enrollment = PersonalWakeEnrollment(context, featureExtractor)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<VoiceWakeUiState> = _uiState.asStateFlow()

    init {
        // 监听服务运行状态
        viewModelScope.launch {
            VoiceWakeService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isServiceRunning = running)
            }
        }
        // 监听唤醒词检测事件
        viewModelScope.launch {
            VoiceWakeService.wakeWordDetected.collect { detected ->
                _uiState.value = _uiState.value.copy(isDetecting = detected)
            }
        }
    }

    /**
     * 加载初始状态
     */
    private fun loadInitialState(): VoiceWakeUiState {
        val isEnrolled = enrollment.isEnrolled()
        val wakeWordName = enrollment.getWakeWordName()
        return VoiceWakeUiState(
            isEnrolled = isEnrolled,
            wakeWordName = wakeWordName,
            enrollmentProgress = if (isEnrolled) 3 else 0
        )
    }

    /**
     * 开始注册唤醒词
     */
    fun startEnrollment() {
        _uiState.value = _uiState.value.copy(
            isEnrolling = true,
            enrollmentProgress = 0,
            enrollmentStepText = "请说唤醒词（第1次/共3次）",
            errorMessage = ""
        )

        // 启动服务进入注册模式
        val intent = Intent(context, VoiceWakeService::class.java).apply {
            action = VoiceWakeService.ACTION_ENROLL_START
        }
        context.startForegroundService(intent)
    }

    /**
     * 完成一次注册
     * @param progress 当前注册次数
     */
    fun onEnrollmentSampleReady(progress: Int) {
        val stepText = if (progress < 3) {
            "请说唤醒词（第${progress + 1}次/共3次）"
        } else {
            "注册完成！"
        }
        _uiState.value = _uiState.value.copy(
            enrollmentProgress = progress,
            enrollmentStepText = stepText
        )

        if (progress >= 3) {
            _uiState.value = _uiState.value.copy(
                isEnrolling = false,
                isEnrolled = true
            )
        }
    }

    /**
     * 取消注册
     */
    fun cancelEnrollment() {
        val intent = Intent(context, VoiceWakeService::class.java).apply {
            action = VoiceWakeService.ACTION_ENROLL_STOP
        }
        context.startService(intent)

        _uiState.value = _uiState.value.copy(
            isEnrolling = false,
            enrollmentProgress = 0,
            enrollmentStepText = ""
        )
    }

    /**
     * 保存唤醒词名称
     */
    fun saveWakeWordName(name: String) {
        enrollment.saveWakeWordName(name)
        _uiState.value = _uiState.value.copy(wakeWordName = name)
    }

    /**
     * 启动唤醒词监听服务
     */
    fun startWakeService() {
        val wakeWord = _uiState.value.wakeWordName.ifBlank { "小助手" }
        val intent = Intent(context, VoiceWakeService::class.java).apply {
            action = VoiceWakeService.ACTION_START
            putExtra(VoiceWakeService.EXTRA_WAKE_WORD, wakeWord)
        }
        context.startForegroundService(intent)
    }

    /**
     * 停止唤醒词监听服务
     */
    fun stopWakeService() {
        val intent = Intent(context, VoiceWakeService::class.java).apply {
            action = VoiceWakeService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * 清除注册数据
     */
    fun clearEnrollment() {
        enrollment.clearEnrollment()
        _uiState.value = _uiState.value.copy(
            isEnrolled = false,
            wakeWordName = "",
            enrollmentProgress = 0
        )
    }

    /**
     * 设置 VAD 模式显示
     */
    fun updateVadMode(mode: String) {
        _uiState.value = _uiState.value.copy(vadMode = mode)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
