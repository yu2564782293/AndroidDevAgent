package com.example.androiddevagent.voice

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 基于 Silero VAD 模型的语音活动检测器
 * 参考 Operit 项目的实现方式，使用 ONNX Runtime 推理
 * 如果 ONNX 模型不可用，自动回退到基于能量的 VAD 检测
 */
class OnnxSileroVad(private val context: Context) {

    companion object {
        private const val MODEL_PATH = "models/silero_vad.onnx"
        private const val SAMPLE_RATE = 16000L
        private const val WINDOW_SIZE = 512  // Silero VAD 的输入窗口大小（30ms @ 16kHz）
        private const val THRESHOLD = 0.5f   // 语音活动检测阈值

        // 能量 VAD 的参数（回退方案）
        private const val ENERGY_THRESHOLD = 0.02f
        private const val SILENCE_PAD_MS = 300
    }

    // ONNX Runtime 相关
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var useOnnx = false

    // Silero VAD 的隐藏状态（h 和 c）
    private var hiddenStateH: FloatArray = FloatArray(128)
    private var hiddenStateC: FloatArray = FloatArray(128)

    // 能量 VAD 的状态
    private var energyBuffer = FloatArray(0)
    private var isInSpeech = false
    private var silenceFrames = 0

    /**
     * 初始化 VAD 模型
     * 尝试加载 ONNX 模型，失败则使用能量 VAD
     */
    fun init(): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = copyModelToCache()
            if (modelFile != null && modelFile.exists() && modelFile.length() > 100) {
                val sessionOptions = OrtSession.SessionOptions()
                sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
                useOnnx = ortSession != null
                resetState()
                useOnnx
            } else {
                // 模型文件不存在或太小（占位文件），使用能量 VAD
                useOnnx = false
                resetState()
                false
            }
        } catch (e: Exception) {
            // ONNX 初始化失败，回退到能量 VAD
            useOnnx = false
            resetState()
            false
        }
    }

    /**
     * 将 assets 中的模型文件复制到缓存目录
     */
    private fun copyModelToCache(): File? {
        return try {
            val outFile = File(context.cacheDir, "silero_vad.onnx")
            if (outFile.exists() && outFile.length() > 100) {
                return outFile
            }
            context.assets.open(MODEL_PATH).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 重置 VAD 状态
     */
    fun resetState() {
        hiddenStateH = FloatArray(128)
        hiddenStateC = FloatArray(128)
        isInSpeech = false
        silenceFrames = 0
        energyBuffer = FloatArray(0)
    }

    /**
     * 检测一段音频是否包含语音
     * @param audioData 16kHz 单声道 PCM 音频数据，范围 [-1.0, 1.0]
     * @return 语音概率（0.0 ~ 1.0），使用 ONNX 时为模型输出，否则为能量估计
     */
    fun detect(audioData: FloatArray): Float {
        return if (useOnnx) {
            detectWithOnnx(audioData)
        } else {
            detectWithEnergy(audioData)
        }
    }

    /**
     * 使用 ONNX 模型进行 VAD 检测
     */
    private fun detectWithOnnx(audioData: FloatArray): Float {
        val session = ortSession ?: return detectWithEnergy(audioData)
        val env = ortEnv ?: return detectWithEnergy(audioData)

        return try {
            // 准备输入张量
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(audioData), longArrayOf(1, audioData.size.toLong()))
            val srTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(SAMPLE_RATE)), longArrayOf(1))
            val hTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(hiddenStateH), longArrayOf(2, 1, 64))
            val cTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(hiddenStateC), longArrayOf(2, 1, 64))

            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )

            val results = session.run(inputs)

            // 获取输出
            val output = (results[0].value as Array<FloatArray>)[0][0]

            // 更新隐藏状态
            val newH = results[1].value as Array<Array<FloatArray>>
            val newC = results[2].value as Array<Array<FloatArray>>
            for (i in 0 until 64) {
                hiddenStateH[i] = newH[0][0][i]
                hiddenStateH[i + 64] = newH[1][0][i]
                hiddenStateC[i] = newC[0][0][i]
                hiddenStateC[i + 64] = newC[1][0][i]
            }

            output
        } catch (e: Exception) {
            // 推理失败，回退到能量检测
            detectWithEnergy(audioData)
        }
    }

    /**
     * 基于能量的 VAD 检测（回退方案）
     * 计算音频帧的 RMS 能量，并转换为概率估计
     */
    private fun detectWithEnergy(audioData: FloatArray): Float {
        if (audioData.isEmpty()) return 0f

        // 计算 RMS 能量
        var sumSquares = 0.0
        for (sample in audioData) {
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sumSquares / audioData.size).toFloat()

        // 将 RMS 能量映射到 [0, 1] 的概率值
        // 使用对数尺度映射，使阈值附近的响应更灵敏
        val db = if (rms > 0f) 20f * log10(rms.toDouble()).toFloat() else -100f
        // 将 -60dB ~ -10dB 映射到 0.0 ~ 1.0
        val probability = ((db + 60f) / 50f).coerceIn(0f, 1f)

        return probability
    }

    /**
     * 判断当前帧是否为语音
     * @param probability 语音概率
     */
    fun isSpeech(probability: Float): Boolean {
        return probability >= THRESHOLD
    }

    /**
     * 判断是否使用 ONNX 模型
     */
    fun isUsingOnnx(): Boolean = useOnnx

    /**
     * 释放资源
     */
    fun release() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            // 忽略关闭异常
        }
        ortSession = null
        ortEnv = null
        useOnnx = false
    }
}
