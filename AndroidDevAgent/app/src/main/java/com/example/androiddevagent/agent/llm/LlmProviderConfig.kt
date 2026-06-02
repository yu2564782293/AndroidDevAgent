package com.example.androiddevagent.agent.llm

data class LlmProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val apiKey: String = "",
    val modelName: String = ""
) {
    fun getEffectiveBaseUrl(): String = baseUrl.ifBlank { DEFAULT_BASE_URL }
    fun getEffectiveModel(): String = modelName.ifBlank { defaultModel }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        val BUILT_IN_PROVIDERS = listOf(
            LlmProviderConfig(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1/",
                defaultModel = "gpt-4o-mini"
            ),
            LlmProviderConfig(
                id = "deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1/",
                defaultModel = "deepseek-chat"
            ),
            LlmProviderConfig(
                id = "zhipu",
                name = "智谱 AI",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
                defaultModel = "glm-4-flash"
            ),
            LlmProviderConfig(
                id = "moonshot",
                name = "月之暗面",
                baseUrl = "https://api.moonshot.cn/v1/",
                defaultModel = "moonshot-v1-8k"
            ),
            LlmProviderConfig(
                id = "qwen",
                name = "通义千问",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
                defaultModel = "qwen-turbo"
            ),
            LlmProviderConfig(
                id = "custom",
                name = "自定义",
                baseUrl = "",
                defaultModel = ""
            )
        )
    }
}
