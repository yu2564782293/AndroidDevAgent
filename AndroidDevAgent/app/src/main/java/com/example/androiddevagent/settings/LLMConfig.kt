package com.example.androiddevagent.settings

data class LLMConfig(
    val providerName: String = LLMProvider.OPENAI.name,
    val apiKey: String = "",
    val baseUrl: String = LLMProvider.OPENAI.defaultBaseUrl,
    val modelName: String = LLMProvider.OPENAI.defaultModel,
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val maxTokens: Int = 2048
) {
    val provider: LLMProvider
        get() = LLMProvider.fromProviderName(providerName)

    fun withProviderDefaults(provider: LLMProvider): LLMConfig {
        return when (provider) {
            LLMProvider.CUSTOM -> copy(providerName = provider.name)
            else -> copy(
                providerName = provider.name,
                baseUrl = provider.defaultBaseUrl,
                modelName = provider.defaultModel
            )
        }
    }

    companion object {
        val DEFAULT = LLMConfig()
    }
}

enum class LLMProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val modelSuggestions: List<String>
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        modelSuggestions = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1")
    ),
    DEEPSEEQ(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        modelSuggestions = listOf("deepseek-chat", "deepseek-reasoner")
    ),
    QWEN(
        displayName = "Qwen",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        modelSuggestions = listOf("qwen-plus", "qwen-turbo", "qwen-max")
    ),
    KIMI(
        displayName = "Kimi",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k",
        modelSuggestions = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
    ),
    CHATGLM(
        displayName = "ChatGLM",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4-flash",
        modelSuggestions = listOf("glm-4-flash", "glm-4", "glm-4-plus")
    ),
    CUSTOM(
        displayName = "自定义",
        defaultBaseUrl = "",
        defaultModel = "",
        modelSuggestions = emptyList()
    );

    companion object {
        fun fromProviderName(providerName: String): LLMProvider {
            return entries.firstOrNull { provider ->
                provider.name.equals(providerName, ignoreCase = true) ||
                    provider.displayName.equals(providerName, ignoreCase = true)
            } ?: CUSTOM
        }
    }
}
