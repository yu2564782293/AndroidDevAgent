package com.example.androiddevagent.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MFCC 特征提取器（纯 Kotlin 实现）
 * 用于从音频数据中提取梅尔频率倒谱系数，供唤醒词匹配使用
 * 实现流程：预加重 → 分帧 → 加窗 → FFT → 梅尔滤波器组 → 对数能量 → DCT
 */
class PersonalWakeFeatureExtractor(
    val sampleRate: Int = 16000,
    val frameSize: Int = 512,        // 帧大小（样本数）
    val hopSize: Int = 256,           // 帧移（样本数）
    val numMfcc: Int = 13,            // MFCC 系数个数
    val numMelFilters: Int = 26,      // 梅尔滤波器个数
    val numFftBins: Int = 512,        // FFT 点数
    val lowFreq: Float = 80f,         // 最低频率（Hz）
    val highFreq: Float = 8000f,      // 最高频率（Hz）
    val preEmphasis: Float = 0.97f    // 预加重系数
) {

    // 梅尔滤波器组（预计算）
    private val melFilterBank: Array<FloatArray> by lazy {
        createMelFilterBank()
    }

    // 汉明窗（预计算）
    private val hammingWindow: FloatArray by lazy {
        createHammingWindow()
    }

    // DCT 矩阵（预计算）
    private val dctMatrix: Array<FloatArray> by lazy {
        createDctMatrix()
    }

    /**
     * 从音频数据中提取 MFCC 特征
     * @param audioData 16kHz 单声道 PCM 音频数据，范围 [-1.0, 1.0]
     * @return MFCC 特征矩阵，每帧一行，每行 numMfcc 个系数
     */
    fun extract(audioData: FloatArray): Array<FloatArray> {
        if (audioData.size < frameSize) {
            return arrayOf(FloatArray(numMfcc))
        }

        // 1. 预加重
        val emphasized = preEmphasis(audioData)

        // 2. 分帧
        val frames = framing(emphasized)

        // 3. 对每帧提取 MFCC
        return frames.map { frame ->
            extractFrameMfcc(frame)
        }.toTypedArray()
    }

    /**
     * 从单帧音频中提取 MFCC
     */
    private fun extractFrameMfcc(frame: FloatArray): FloatArray {
        // 加窗
        val windowed = applyWindow(frame)

        // FFT
        val spectrum = fftMagnitude(windowed)

        // 梅尔滤波器组
        val melEnergies = applyMelFilterBank(spectrum)

        // 取对数
        val logMelEnergies = logMelEnergies(melEnergies)

        // DCT 得到 MFCC
        return applyDct(logMelEnergies)
    }

    /**
     * 预加重滤波器
     * y[n] = x[n] - α * x[n-1]
     * 增强高频成分，改善信噪比
     */
    private fun preEmphasis(data: FloatArray): FloatArray {
        val result = FloatArray(data.size)
        result[0] = data[0]
        for (i in 1 until data.size) {
            result[i] = data[i] - preEmphasis * data[i - 1]
        }
        return result
    }

    /**
     * 分帧
     * 将连续音频分割为重叠的帧
     */
    private fun framing(data: FloatArray): Array<FloatArray> {
        val numFrames = max(1, (data.size - frameSize) / hopSize + 1)
        val frames = Array(numFrames) { FloatArray(frameSize) }

        for (i in 0 until numFrames) {
            val start = i * hopSize
            for (j in 0 until frameSize) {
                val idx = start + j
                frames[i][j] = if (idx < data.size) data[idx] else 0f
            }
        }
        return frames
    }

    /**
     * 创建汉明窗
     */
    private fun createHammingWindow(): FloatArray {
        val window = FloatArray(frameSize)
        for (i in 0 until frameSize) {
            window[i] = (0.54 - 0.46 * cos(2.0 * PI * i / (frameSize - 1))).toFloat()
        }
        return window
    }

    /**
     * 应用汉明窗
     */
    private fun applyWindow(frame: FloatArray): FloatArray {
        val result = FloatArray(frame.size)
        for (i in frame.indices) {
            result[i] = frame[i] * hammingWindow[i]
        }
        return result
    }

    /**
     * 计算 FFT 幅度谱
     * 使用 Cooley-Tukey 基2 FFT 算法
     */
    private fun fftMagnitude(data: FloatArray): FloatArray {
        val n = numFftBins
        val real = FloatArray(n)
        val imag = FloatArray(n)

        // 填充数据（零填充到 FFT 长度）
        for (i in data.indices.coerceAtMost(n)) {
            real[i] = data[i]
        }

        // 位反转排列
        bitReversePermute(real, imag, n)

        // 蝶形运算
        var length = 2
        while (length <= n) {
            val halfLength = length / 2
            val angle = -2.0 * PI / length
            val wReal = cos(angle)
            val wImag = sin(angle)

            for (i in 0 until n step length) {
                var curReal = 1.0
                var curImag = 0.0
                for (j in 0 until halfLength) {
                    val evenIdx = i + j
                    val oddIdx = i + j + halfLength

                    val tReal = curReal * real[oddIdx] - curImag * imag[oddIdx]
                    val tImag = curReal * imag[oddIdx] + curImag * real[oddIdx]

                    real[oddIdx] = real[evenIdx] - tReal.toFloat()
                    imag[oddIdx] = imag[evenIdx] - tImag.toFloat()
                    real[evenIdx] = real[evenIdx] + tReal.toFloat()
                    imag[evenIdx] = imag[evenIdx] + tImag.toFloat()

                    val newCurReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = newCurReal
                }
            }
            length *= 2
        }

        // 计算幅度谱（只取前半部分）
        val magnitude = FloatArray(n / 2 + 1)
        for (i in magnitude.indices) {
            magnitude[i] = sqrt(real[i] * real[i] + imag[i] * imag[i].toDouble()).toFloat()
        }
        return magnitude
    }

    /**
     * 位反转排列
     */
    private fun bitReversePermute(real: FloatArray, imag: FloatArray, n: Int) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempReal = real[i]
                val tempImag = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempReal
                imag[j] = tempImag
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }
    }

    /**
     * 正弦函数（避免导入 kotlin.math.sin）
     */
    private fun sin(x: Double): Double {
        return kotlin.math.sin(x)
    }

    /**
     * 频率转梅尔刻度
     */
    private fun hzToMel(hz: Float): Float {
        return 2595f * log10(1f + hz / 700f)
    }

    /**
     * 梅尔刻度转频率
     */
    private fun melToHz(mel: Float): Float {
        return 700f * (10f.pow(mel / 2595f) - 1f)
    }

    /**
     * 创建梅尔滤波器组
     * 生成三角形状的带通滤波器
     */
    private fun createMelFilterBank(): Array<FloatArray> {
        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)

        // 在梅尔刻度上均匀分布中心点
        val melPoints = FloatArray(numMelFilters + 2)
        for (i in melPoints.indices) {
            melPoints[i] = lowMel + (highMel - lowMel) * i / (numMelFilters + 1)
        }

        // 转换回频率，再转换为 FFT bin 索引
        val binPoints = IntArray(melPoints.size)
        val fftFreqs = FloatArray(numFftBins / 2 + 1)
        for (i in fftFreqs.indices) {
            fftFreqs[i] = sampleRate * i.toFloat() / numFftBins
        }

        for (i in melPoints.indices) {
            val hz = melToHz(melPoints[i])
            binPoints[i] = ((numFftBins + 1) * hz / sampleRate).toInt().coerceIn(0, numFftBins / 2)
        }

        // 创建三角滤波器
        val filterBank = Array(numMelFilters) { FloatArray(numFftBins / 2 + 1) }
        for (m in 0 until numMelFilters) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            for (k in left..center) {
                if (center > left) {
                    filterBank[m][k] = (k - left).toFloat() / (center - left)
                }
            }
            for (k in center..right) {
                if (right > center) {
                    filterBank[m][k] = (right - k).toFloat() / (right - center)
                }
            }
        }
        return filterBank
    }

    /**
     * 应用梅尔滤波器组
     */
    private fun applyMelFilterBank(spectrum: FloatArray): FloatArray {
        val energies = FloatArray(numMelFilters)
        for (m in 0 until numMelFilters) {
            var sum = 0f
            for (k in spectrum.indices.coerceAtMost(melFilterBank[m].size)) {
                sum += spectrum[k] * melFilterBank[m][k]
            }
            energies[m] = sum
        }
        return energies
    }

    /**
     * 对梅尔能量取对数
     */
    private fun logMelEnergies(energies: FloatArray): FloatArray {
        val logEnergies = FloatArray(energies.size)
        for (i in energies.indices) {
            logEnergies[i] = if (energies[i] > 1e-10f) ln(energies[i].toDouble()).toFloat() else -230f
        }
        return logEnergies
    }

    /**
     * 创建 DCT-II 矩阵
     */
    private fun createDctMatrix(): Array<FloatArray> {
        val matrix = Array(numMfcc) { FloatArray(numMelFilters) }
        for (i in 0 until numMfcc) {
            for (j in 0 until numMelFilters) {
                matrix[i][j] = cos(PI * i * (2 * j + 1) / (2 * numMelFilters)).toFloat()
            }
        }
        return matrix
    }

    /**
     * 应用 DCT 变换得到 MFCC 系数
     */
    private fun applyDct(logEnergies: FloatArray): FloatArray {
        val mfcc = FloatArray(numMfcc)
        for (i in 0 until numMfcc) {
            var sum = 0f
            for (j in logEnergies.indices) {
                sum += dctMatrix[i][j] * logEnergies[j]
            }
            mfcc[i] = sum
        }
        return mfcc
    }

    /**
     * 将 MFCC 特征序列展平为一维数组，用于存储
     */
    fun flattenFeatures(features: Array<FloatArray>): FloatArray {
        val totalSize = features.size * numMfcc
        val flat = FloatArray(totalSize)
        for (i in features.indices) {
            for (j in 0 until numMfcc) {
                flat[i * numMfcc + j] = features[i][j]
            }
        }
        return flat
    }

    /**
     * 将一维数组还原为 MFCC 特征矩阵
     */
    fun unflattenFeatures(flat: FloatArray): Array<FloatArray> {
        val numFrames = flat.size / numMfcc
        val features = Array(numFrames) { FloatArray(numMfcc) }
        for (i in 0 until numFrames) {
            for (j in 0 until numMfcc) {
                features[i][j] = flat[i * numMfcc + j]
            }
        }
        return features
    }

    /**
     * 计算特征矩阵的帧数
     */
    fun estimateNumFrames(audioLength: Int): Int {
        return max(1, (audioLength - frameSize) / hopSize + 1)
    }
}
