package com.example.androiddevagent.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.androiddevagent.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音唤醒前台服务
 * 在后台持续监听麦克风，检测唤醒词
 * 参考 Operit 的 VoiceWakeService 实现
 */
class VoiceWakeService : Service() {

    companion object {
        const val ACTION_START = "com.example.androiddevagent.VOICE_WAKE_START"
        const val ACTION_STOP = "com.example.androiddevagent.VOICE_WAKE_STOP"
        const val ACTION_ENROLL_START = "com.example.androiddevagent.VOICE_WAKE_ENROLL_START"
        const val ACTION_ENROLL_STOP = "com.example.androiddevagent.VOICE_WAKE_ENROLL_STOP"
        const val EXTRA_WAKE_WORD = "extra_wake_word"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "voice_wake_channel"

        // 音频参数
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 512    // 每帧样本数（32ms @ 16kHz）
        private const val HOP_SIZE = 256      // 帧移

        // 服务运行状态
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        // 唤醒词检测事件
        private val _wakeWordDetected = MutableStateFlow(false)
        val wakeWordDetected: StateFlow<Boolean> = _wakeWordDetected.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 核心组件
    private lateinit var vad: OnnxSileroVad
    private lateinit var featureExtractor: PersonalWakeFeatureExtractor
    private lateinit var wakeListener: PersonalWakeListener
    private lateinit var enrollment: PersonalWakeEnrollment

    // 音频录制
    private var audioRecorder: android.media.AudioRecord? = null
    private var isRecording = false

    // 注册模式
    private var isEnrolling = false
    private var enrollmentAudioBuffer = mutableListOf<Float>()

    // 唤醒回调
    var onWakeWordDetected: (() -> Unit)? = null
    var onEnrollmentSampleReady: ((Int) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 初始化组件
        vad = OnnxSileroVad(this)
        featureExtractor = PersonalWakeFeatureExtractor()
        wakeListener = PersonalWakeListener(featureExtractor)
        enrollment = PersonalWakeEnrollment(this, featureExtractor)

        // 初始化 VAD
        serviceScope.launch {
            vad.init()
        }

        // 加载已保存的模板
        val templates = enrollment.loadTemplates()
        if (templates.isNotEmpty()) {
            wakeListener.setTemplatesFromFlat(templates)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val wakeWord = intent.getStringExtra(EXTRA_WAKE_WORD) ?: ""
                startListening(wakeWord)
            }
            ACTION_STOP -> {
                stopListening()
                stopSelf()
            }
            ACTION_ENROLL_START -> {
                startEnrollment()
            }
            ACTION_ENROLL_STOP -> {
                stopEnrollment()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        vad.release()
        serviceScope.cancel()
        _isRunning.value = false
    }

    /**
     * 开始监听唤醒词
     */
    private fun startListening(wakeWord: String) {
        if (isRecording) return

        _isRunning.value = true
        showNotification("正在监听唤醒词: $wakeWord")

        isRecording = true
        serviceScope.launch {
            startAudioRecording()
        }
    }

    /**
     * 停止监听
     */
    private fun stopListening() {
        isRecording = false
        stopAudioRecording()
        _isRunning.value = false
    }

    /**
     * 开始注册模式
     */
    private fun startEnrollment() {
        isEnrolling = true
        enrollmentAudioBuffer.clear()
        enrollment.startEnrollment()
        showNotification("请说唤醒词（第1次）")
    }

    /**
     * 停止注册模式
     */
    private fun stopEnrollment() {
        isEnrolling = false
        enrollmentAudioBuffer.clear()
        showNotification("注册完成")
    }

    /**
     * 启动音频录制循环
     */
    private fun startAudioRecording() {
        try {
            val bufferSize = android.media.AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )

            val actualBufferSize = maxOf(bufferSize, FRAME_SIZE * 2)

            audioRecorder = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            if (audioRecorder?.state != android.media.AudioRecord.STATE_INITIALIZED) {
                return
            }

            audioRecorder?.startRecording()

            val audioBuffer = ShortArray(FRAME_SIZE)
            val floatBuffer = FloatArray(FRAME_SIZE)

            while (isRecording) {
                val readCount = audioRecorder?.read(audioBuffer, 0, FRAME_SIZE)
                    ?: break

                if (readCount <= 0) continue

                // Short 转 Float（归一化到 [-1.0, 1.0]）
                for (i in 0 until readCount) {
                    floatBuffer[i] = audioBuffer[i].toFloat() / 32768f
                }

                // VAD 检测
                val vadProb = vad.detect(floatBuffer)

                if (isEnrolling) {
                    // 注册模式：收集语音数据
                    processEnrollmentFrame(floatBuffer, vadProb)
                } else {
                    // 检测模式：唤醒词匹配
                    val detected = wakeListener.processFrame(floatBuffer, vadProb)
                    if (detected) {
                        onWakeWordDetected?.invoke()
                        _wakeWordDetected.value = true
                        showNotification("检测到唤醒词！")
                        // 重置检测状态
                        serviceScope.launch {
                            delay(2000)
                            _wakeWordDetected.value = false
                            if (isRecording) {
                                showNotification("正在监听唤醒词...")
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // 没有录音权限
        } catch (e: Exception) {
            // 录音异常
        } finally {
            stopAudioRecording()
        }
    }

    /**
     * 处理注册帧
     */
    private fun processEnrollmentFrame(audioFrame: FloatArray, vadProb: Float) {
        val isSpeech = vadProb >= 0.5f

        if (isSpeech) {
            for (sample in audioFrame) {
                enrollmentAudioBuffer.add(sample)
            }
        } else if (enrollmentAudioBuffer.isNotEmpty()) {
            // 语音结束，处理注册样本
            if (enrollmentAudioBuffer.size >= SAMPLE_RATE / 4) {
                // 至少0.25秒的语音
                val audioData = enrollmentAudioBuffer.toFloatArray()
                val count = enrollment.addEnrollmentSample(audioData)
                onEnrollmentSampleReady?.invoke(count)

                if (!enrollment.needsMoreSamples()) {
                    // 注册完成，更新模板
                    val templates = enrollment.loadTemplates()
                    wakeListener.setTemplatesFromFlat(templates)
                    isEnrolling = false
                    showNotification("唤醒词注册完成")
                } else {
                    showNotification("请说唤醒词（第${count + 1}次）")
                }
            }
            enrollmentAudioBuffer.clear()
        }
    }

    /**
     * 停止音频录制
     */
    private fun stopAudioRecording() {
        try {
            audioRecorder?.stop()
            audioRecorder?.release()
        } catch (e: Exception) {
            // 忽略停止异常
        }
        audioRecorder = null
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音唤醒服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "语音唤醒后台监听"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示前台通知
     */
    private fun showNotification(text: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音唤醒")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
