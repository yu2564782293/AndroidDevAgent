package com.example.androiddevagent.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object InputValidator {

    fun validateApiKey(apiKey: String): ValidationResult {
        val normalized = apiKey.trim()
        return when {
            normalized.isEmpty() -> ValidationResult.Invalid("请填写 API Key")
            normalized.length < MIN_API_KEY_LENGTH -> ValidationResult.Invalid("API Key 长度过短")
            normalized.length > MAX_API_KEY_LENGTH -> ValidationResult.Invalid("API Key 长度超出限制")
            normalized.any { it.isWhitespace() } -> ValidationResult.Invalid("API Key 不能包含空白字符")
            !API_KEY_PATTERN.matches(normalized) -> ValidationResult.Invalid("API Key 包含不支持的字符")
            else -> ValidationResult.Valid
        }
    }

    fun validateUrl(url: String): ValidationResult {
        val normalized = url.trim()
        val parsedUrl = normalized.toHttpUrlOrNull()
        return when {
            normalized.isEmpty() -> ValidationResult.Invalid("请填写 Base URL")
            parsedUrl == null -> ValidationResult.Invalid("Base URL 格式不正确")
            parsedUrl.scheme != "https" -> ValidationResult.Invalid("生产环境仅支持 HTTPS Base URL")
            parsedUrl.host.isBlank() -> ValidationResult.Invalid("Base URL 缺少主机名")
            else -> ValidationResult.Valid
        }
    }

    fun validateModelName(modelName: String): ValidationResult {
        val normalized = modelName.trim()
        return when {
            normalized.isEmpty() -> ValidationResult.Invalid("请填写模型名称")
            normalized.length > MAX_MODEL_NAME_LENGTH -> ValidationResult.Invalid("模型名称长度超出限制")
            !MODEL_NAME_PATTERN.matches(normalized) -> {
                ValidationResult.Invalid("模型名称仅支持字母、数字、点号、下划线、斜杠和连字符")
            }
            else -> ValidationResult.Valid
        }
    }

    fun sanitizeUserInput(
        input: String,
        maxLength: Int = MAX_USER_INPUT_LENGTH
    ): SanitizedInput {
        val normalized = input
            .replace("\u0000", "")
            .replace(CONTROL_CHARACTER_PATTERN, "\n")
            .lineSequence()
            .joinToString("\n") { line -> line.trimEnd() }
            .trim()

        val sanitized = normalized.take(maxLength.coerceAtLeast(1))
        return SanitizedInput(
            value = sanitized,
            wasTruncated = normalized.length > sanitized.length,
            maxLength = maxLength
        )
    }

    fun sanitizeModelName(modelName: String): String {
        return modelName.trim().take(MAX_MODEL_NAME_LENGTH)
    }

    fun sanitizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }

    fun sanitizeApiKey(apiKey: String): String {
        return apiKey.trim().take(MAX_API_KEY_LENGTH)
    }

    const val MAX_USER_INPUT_LENGTH = 24_000

    private const val MIN_API_KEY_LENGTH = 12
    private const val MAX_API_KEY_LENGTH = 512
    private const val MAX_MODEL_NAME_LENGTH = 128
    private val API_KEY_PATTERN = Regex("^[A-Za-z0-9._:/+=\\-]+$")
    private val MODEL_NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._/\\-]{0,127}$")
    private val CONTROL_CHARACTER_PATTERN = Regex("[\\u0001-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val message: String) : ValidationResult
}

data class SanitizedInput(
    val value: String,
    val wasTruncated: Boolean,
    val maxLength: Int
)

fun ValidationResult.errorOrNull(): String? {
    return (this as? ValidationResult.Invalid)?.message
}
