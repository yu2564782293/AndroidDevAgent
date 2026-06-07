package com.example.androiddevagent.agent

import com.example.androiddevagent.settings.LLMConfig
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class LLMMessage(
    val role: String,
    val content: String
)

open class LLMClientException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

class LLMAuthenticationException(message: String, cause: Throwable? = null) :
    LLMClientException(message, cause)

class LLMRateLimitException(message: String, cause: Throwable? = null) :
    LLMClientException(message, cause)

class LLMTimeoutException(message: String, cause: Throwable? = null) :
    LLMClientException(message, cause)

class LLMNetworkException(message: String, cause: Throwable? = null) :
    LLMClientException(message, cause)

class LLMApiException(
    val statusCode: Int? = null,
    message: String,
    cause: Throwable? = null
) : LLMClientException(message, cause)

class LLMClient(
    private val okHttpClient: OkHttpClient
) {

    fun streamChatCompletion(
        config: LLMConfig,
        messages: List<LLMMessage>
    ): Flow<String> {
        return streamChatCompletion(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            messages = messages,
            modelName = config.modelName,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP
        )
    }

    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        messages: List<LLMMessage>,
        modelName: String,
        temperature: Double,
        maxTokens: Int,
        topP: Double = 0.9
    ): Flow<String> = flow {
        validateRequestInputs(baseUrl, apiKey, messages, modelName, maxTokens)

        var retryCount = 0
        while (true) {
            var emittedAnyToken = false
            try {
                val request = buildRequest(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    messages = messages,
                    modelName = modelName,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP
                )

                executeStreamingRequest(request) { token ->
                    emittedAnyToken = true
                    emit(token)
                }
                return@flow
            } catch (throwable: Throwable) {
                val clientException = throwable.toClientException()
                if (!clientException.shouldRetry() || retryCount >= MAX_RETRIES || emittedAnyToken) {
                    throw clientException
                }

                delay(backoffMillis(retryCount))
                retryCount += 1
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(config: LLMConfig) {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            streamChatCompletion(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                messages = listOf(LLMMessage(role = "user", content = "请回复 OK")),
                modelName = config.modelName,
                temperature = 0.0,
                maxTokens = config.maxTokens.coerceIn(1, TEST_MAX_TOKENS),
                topP = config.topP
            ).collect()
        }
    }

    private fun buildRequest(
        baseUrl: String,
        apiKey: String,
        messages: List<LLMMessage>,
        modelName: String,
        temperature: Double,
        maxTokens: Int,
        topP: Double
    ): Request {
        val body = JSONObject()
            .put("model", modelName.trim())
            .put("messages", messages.toJsonArray())
            .put("temperature", temperature.coerceIn(0.0, 2.0))
            .put("top_p", topP.coerceIn(0.0, 1.0))
            .put("max_tokens", maxTokens.coerceAtLeast(1))
            .put("stream", true)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(buildChatCompletionsUrl(baseUrl))
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Content-Type", "application/json")
            .addHeader("Cache-Control", "no-cache")
            .post(body)
            .build()
    }

    private suspend fun executeStreamingRequest(
        request: Request,
        emitToken: suspend (String) -> Unit
    ) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.toHttpException()
            }

            val body = response.body ?: throw LLMApiException(
                statusCode = response.code,
                message = "模型服务返回空响应"
            )

            parseSse(source = body.source(), emitToken = emitToken)
        }
    }

    private suspend fun parseSse(
        source: BufferedSource,
        emitToken: suspend (String) -> Unit
    ) {
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith(SSE_DATA_PREFIX)) continue

            val data = line.removePrefix(SSE_DATA_PREFIX).trim()
            if (data.isEmpty()) continue
            if (data == SSE_DONE_MARKER) return

            val token = parseToken(data)
            if (token.isNotEmpty()) {
                emitToken(token)
            }
        }
    }

    private fun parseToken(data: String): String {
        try {
            val json = JSONObject(data)
            val choices = json.optJSONArray("choices") ?: return ""
            val choice = choices.firstJSONObject() ?: return ""
            val delta = choice.optJSONObject("delta")
            val message = choice.optJSONObject("message")

            return delta?.optString("content", "")?.takeIf { it.isNotEmpty() }
                ?: message?.optString("content", "")?.takeIf { it.isNotEmpty() }
                ?: choice.optString("text", "").takeIf { it.isNotEmpty() }
                ?: ""
        } catch (exception: JSONException) {
            throw LLMApiException(message = "无法解析模型响应", cause = exception)
        }
    }

    private fun JSONArray.firstJSONObject(): JSONObject? {
        return if (length() > 0) opt(0) as? JSONObject else null
    }

    private fun List<LLMMessage>.toJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { message ->
                array.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }
        }
    }

    private fun validateRequestInputs(
        baseUrl: String,
        apiKey: String,
        messages: List<LLMMessage>,
        modelName: String,
        maxTokens: Int
    ) {
        when {
            apiKey.isBlank() -> throw LLMAuthenticationException("请先在设置中填写 API Key")
            baseUrl.isBlank() -> throw LLMApiException(message = "请先填写 Base URL")
            modelName.isBlank() -> throw LLMApiException(message = "请先填写模型名称")
            messages.isEmpty() -> throw LLMApiException(message = "消息列表不能为空")
            messages.any { it.role.isBlank() || it.content.isBlank() } ->
                throw LLMApiException(message = "消息内容不能为空")
            maxTokens <= 0 -> throw LLMApiException(message = "最大 Token 数必须大于 0")
        }
    }

    private fun buildChatCompletionsUrl(baseUrl: String): HttpUrl {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val endpoint = when {
            normalizedBaseUrl.endsWith("/chat/completions") -> normalizedBaseUrl
            normalizedBaseUrl.endsWith("/v1") || normalizedBaseUrl.endsWith("/v4") ->
                "$normalizedBaseUrl/chat/completions"
            else -> "$normalizedBaseUrl/v1/chat/completions"
        }

        return endpoint.toHttpUrlOrNull()
            ?: throw LLMApiException(message = "Base URL 格式不正确")
    }

    private fun Response.toHttpException(): LLMClientException {
        val responseMessage = body?.string().orEmpty().parseErrorMessage()
        return when (code) {
            401, 403 -> LLMAuthenticationException(responseMessage ?: "API Key 无效或无权限")
            429 -> LLMRateLimitException(responseMessage ?: "请求过于频繁，请稍后再试")
            in 500..599 -> LLMApiException(
                statusCode = code,
                message = responseMessage ?: "模型服务暂时不可用，请稍后重试"
            )
            else -> LLMApiException(
                statusCode = code,
                message = responseMessage ?: "模型请求失败: HTTP $code"
            )
        }
    }

    private fun String.parseErrorMessage(): String? {
        if (isBlank()) return null
        return runCatching {
            val json = JSONObject(this)
            val error = json.optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun Throwable.toClientException(): LLMClientException {
        return when (this) {
            is LLMClientException -> this
            is SocketTimeoutException,
            is InterruptedIOException -> LLMTimeoutException("连接超时，请稍后重试", this)
            is UnknownHostException,
            is ConnectException,
            is SSLException,
            is IOException -> LLMNetworkException("网络连接失败，请检查网络连接", this)
            else -> LLMApiException(message = message ?: "模型请求失败", cause = this)
        }
    }

    private fun LLMClientException.shouldRetry(): Boolean {
        return this is LLMRateLimitException ||
            this is LLMTimeoutException ||
            this is LLMNetworkException ||
            (this is LLMApiException && statusCode != null && statusCode in 500..599)
    }

    private fun backoffMillis(retryCount: Int): Long {
        return INITIAL_BACKOFF_MILLIS * (1L shl retryCount)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE_MARKER = "[DONE]"
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MILLIS = 1_000L
        const val TEST_TIMEOUT_MILLIS = 45_000L
        const val TEST_MAX_TOKENS = 8
    }
}
