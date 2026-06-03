package com.example.androiddevagent.voice

import kotlin.math.min
import kotlin.math.sqrt

/**
 * 唤醒词监听器
 * 使用 MFCC 特征 + DTW（动态时间规整）进行唤醒词匹配
 * 参考 Operit 的 PersonalWakeListener 实现方式
 */
class PersonalWakeListener(
    private val featureExtractor: PersonalWakeFeatureExtractor,
    private val dtwThreshold: Float = 0.6f   // DTW 匹配阈值（越小越严格）
) {

    // 注册的唤醒词模板（多个模板取平均距离）
    private var templates: List<Array<FloatArray>> = emptyList()

    // 音频缓冲区
    private val audioBuffer = mutableListOf<Float>()
    private var bufferSize = 0

    // VAD 状态
    private var isListeningForWakeWord = false
    private var speechStartFrame = 0
    private var silenceCount = 0
    private var speechCount = 0

    companion object {
        private const val MAX_AUDIO_BUFFER_SIZE = 16000 * 3  // 最多缓冲3秒音频
        private const val MIN_SPEECH_FRAMES = 8              // 最少语音帧数（约0.25秒）
        private const val MAX_SPEECH_FRAMES = 80             // 最多语音帧数（约2.5秒）
        private const val SILENCE_TIMEOUT = 15               // 静音超时帧数
        private const val VAD_THRESHOLD = 0.5f               // VAD 概率阈值
    }

    /**
     * 设置唤醒词模板
     * @param templateFeatures 注册时采集的多个模板的 MFCC 特征
     */
    fun setTemplates(templateFeatures: List<Array<FloatArray>>) {
        templates = templateFeatures
    }

    /**
     * 设置唤醒词模板（从展平的 float 数组）
     */
    fun setTemplatesFromFlat(flatTemplates: List<FloatArray>) {
        templates = flatTemplates.map { featureExtractor.unflattenFeatures(it) }
    }

    /**
     * 处理一帧音频数据
     * @param audioFrame 一帧音频数据（通常为 512 样本 @ 16kHz）
     * @param vadProbability VAD 检测的语音概率
     * @return 如果检测到唤醒词返回 true，否则返回 false
     */
    fun processFrame(audioFrame: FloatArray, vadProbability: Float): Boolean {
        val isSpeech = vadProbability >= VAD_THRESHOLD

        if (isSpeech) {
            // 将语音帧添加到缓冲区
            for (sample in audioFrame) {
                if (audioBuffer.size < MAX_AUDIO_BUFFER_SIZE) {
                    audioBuffer.add(sample)
                }
            }
            speechCount++
            silenceCount = 0

            if (!isListeningForWakeWord) {
                isListeningForWakeWord = true
                speechStartFrame = 0
            }
        } else {
            if (isListeningForWakeWord) {
                silenceCount++
                // 静音超时或语音过长，进行匹配
                if (silenceCount >= SILENCE_TIMEOUT || speechCount >= MAX_SPEECH_FRAMES) {
                    val detected = checkWakeWord()
                    resetBuffer()
                    return detected
                }
            }
        }

        return false
    }

    /**
     * 检查缓冲区中的音频是否匹配唤醒词
     */
    private fun checkWakeWord(): Boolean {
        if (audioBuffer.size < featureExtractor.frameSize) {
            return false
        }

        if (templates.isEmpty()) {
            return false
        }

        // 语音太短，不匹配
        if (speechCount < MIN_SPEECH_FRAMES) {
            return false
        }

        // 提取 MFCC 特征
        val audioArray = audioBuffer.toFloatArray()
        val features = featureExtractor.extract(audioArray)

        if (features.isEmpty()) {
            return false
        }

        // 与所有模板进行 DTW 匹配
        var minDistance = Float.MAX_VALUE
        for (template in templates) {
            val distance = computeDtw(features, template)
            if (distance < minDistance) {
                minDistance = distance
            }
        }

        // 归一化距离
        val normalizedDistance = minDistance / (features.size + templates.firstOrNull()?.size?.let { it / 2 } ?: 1)

        return normalizedDistance <= dtwThreshold
    }

    /**
     * 计算动态时间规整（DTW）距离
     * DTW 允许时间轴上的弹性匹配，适合语音识别
     *
     * @param seq1 特征序列1（待检测）
     * @param seq2 特征序列2（模板）
     * @return DTW 距离
     */
    fun computeDtw(seq1: Array<FloatArray>, seq2: Array<FloatArray>): Float {
        val n = seq1.size
        val m = seq2.size

        if (n == 0 || m == 0) return Float.MAX_VALUE

        // DTW 距离矩阵
        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        // 使用 Sakoe-Chiba 带约束，限制弯曲路径
        val band = maxOf(1, (maxOf(n, m) * 0.3).toInt())

        for (i in 1..n) {
            val jStart = maxOf(1, i - band)
            val jEnd = minOf(m, i + band)
            for (j in jStart..jEnd) {
                val cost = euclideanDistance(seq1[i - 1], seq2[j - 1])
                val minPrev = minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
                dtw[i][j] = cost + minPrev
            }
        }

        return dtw[n][m]
    }

    /**
     * 计算两个特征向量之间的欧几里得距离
     */
    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        val len = min(a.size, b.size)
        var sum = 0f
        for (i in 0 until len) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum.toDouble()).toFloat()
    }

    /**
     * 重置音频缓冲区
     */
    fun resetBuffer() {
        audioBuffer.clear()
        isListeningForWakeWord = false
        speechCount = 0
        silenceCount = 0
        speechStartFrame = 0
    }

    /**
     * 是否已注册唤醒词模板
     */
    fun hasTemplate(): Boolean = templates.isNotEmpty()

    /**
     * 获取当前缓冲区的语音帧数
     */
    fun getSpeechFrameCount(): Int = speechCount
}
