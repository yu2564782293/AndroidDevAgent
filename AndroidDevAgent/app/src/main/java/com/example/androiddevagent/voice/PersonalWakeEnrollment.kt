package com.example.androiddevagent.voice

import android.content.Context
import android.content.SharedPreferences

/**
 * 唤醒词注册器
 * 用户说3次唤醒词，提取 MFCC 特征作为模板存储
 * 模板存储在 SharedPreferences 中，以 float 数组形式序列化
 */
class PersonalWakeEnrollment(
    private val context: Context,
    private val featureExtractor: PersonalWakeFeatureExtractor
) {

    companion object {
        private const val PREFS_NAME = "voice_wake_prefs"
        private const val KEY_WAKE_WORD = "wake_word_name"
        private const val KEY_TEMPLATE_COUNT = "template_count"
        private const val KEY_TEMPLATE_PREFIX = "template_"
        private const val KEY_TEMPLATE_FRAME_PREFIX = "template_frames_"
        private const val REQUIRED_ENROLLMENTS = 3  // 需要注册3次
        private const val MAX_TEMPLATES = 5          // 最多存储5个模板
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 注册过程中的临时存储
    private val enrollmentSamples = mutableListOf<FloatArray>()
    private var currentEnrollmentCount = 0

    /**
     * 开始注册流程
     * 清除之前的注册数据
     */
    fun startEnrollment() {
        enrollmentSamples.clear()
        currentEnrollmentCount = 0
    }

    /**
     * 添加一次注册样本
     * @param audioData 用户说出唤醒词的音频数据
     * @return 当前注册次数，达到 REQUIRED_ENROLLMENTS 时注册完成
     */
    fun addEnrollmentSample(audioData: FloatArray): Int {
        // 提取 MFCC 特征
        val features = featureExtractor.extract(audioData)
        if (features.isEmpty() || features.size < 3) {
            return currentEnrollmentCount  // 音频太短，忽略
        }

        enrollmentSamples.add(featureExtractor.flattenFeatures(features))
        currentEnrollmentCount++

        // 达到注册次数，保存模板
        if (currentEnrollmentCount >= REQUIRED_ENROLLMENTS) {
            saveTemplates()
        }

        return currentEnrollmentCount
    }

    /**
     * 获取当前注册进度
     */
    fun getEnrollmentProgress(): Int = currentEnrollmentCount

    /**
     * 是否需要更多注册样本
     */
    fun needsMoreSamples(): Boolean = currentEnrollmentCount < REQUIRED_ENROLLMENTS

    /**
     * 保存模板到 SharedPreferences
     */
    private fun saveTemplates() {
        prefs.edit().apply {
            putInt(KEY_TEMPLATE_COUNT, enrollmentSamples.size)
            for (i in enrollmentSamples.indices) {
                putString("${KEY_TEMPLATE_PREFIX}$i", floatArrayToString(enrollmentSamples[i]))
            }
            apply()
        }
    }

    /**
     * 保存唤醒词名称
     */
    fun saveWakeWordName(name: String) {
        prefs.edit().putString(KEY_WAKE_WORD, name).apply()
    }

    /**
     * 获取唤醒词名称
     */
    fun getWakeWordName(): String {
        return prefs.getString(KEY_WAKE_WORD, "") ?: ""
    }

    /**
     * 加载已保存的模板
     * @return 模板列表（展平的 float 数组），如果无模板则返回空列表
     */
    fun loadTemplates(): List<FloatArray> {
        val count = prefs.getInt(KEY_TEMPLATE_COUNT, 0)
        if (count == 0) return emptyList()

        val templates = mutableListOf<FloatArray>()
        for (i in 0 until count) {
            val str = prefs.getString("${KEY_TEMPLATE_PREFIX}$i", null)
            if (str != null) {
                val flat = stringToFloatArray(str)
                if (flat.isNotEmpty()) {
                    templates.add(flat)
                }
            }
        }
        return templates
    }

    /**
     * 是否已注册唤醒词
     */
    fun isEnrolled(): Boolean {
        return prefs.getInt(KEY_TEMPLATE_COUNT, 0) >= REQUIRED_ENROLLMENTS
    }

    /**
     * 清除注册数据
     */
    fun clearEnrollment() {
        prefs.edit().clear().apply()
        enrollmentSamples.clear()
        currentEnrollmentCount = 0
    }

    /**
     * 将 float 数组序列化为字符串
     * 格式：值1,值2,值3,...
     */
    private fun floatArrayToString(arr: FloatArray): String {
        return arr.joinToString(",") { it.toString() }
    }

    /**
     * 从字符串反序列化为 float 数组
     */
    private fun stringToFloatArray(str: String): FloatArray {
        if (str.isBlank()) return FloatArray(0)
        return try {
            str.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (e: Exception) {
            FloatArray(0)
        }
    }
}
