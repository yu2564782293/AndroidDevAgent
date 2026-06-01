package com.example.androiddevagent.agent

import com.example.androiddevagent.agent.api.ChatCompletionRequest
import com.example.androiddevagent.agent.api.ChatCompletionResponse
import com.example.androiddevagent.agent.api.OpenAIApi
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LLMProviderImpl : LLMProvider {

    private var apiKey: String = ""
    private var baseUrl: String = "https://api.openai.com/v1/"
    private var modelName: String = "gpt-3.5-turbo"
    private var parameters: ModelParameters = ModelParameters()
    private var api: OpenAIApi? = null

    fun configure(apiKey: String, baseUrl: String, modelName: String) {
        this.apiKey = apiKey
        this.baseUrl = baseUrl
        this.modelName = modelName
        this.api = buildApi()
    }

    private fun buildApi(): OpenAIApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()

        return retrofit.create(OpenAIApi::class.java)
    }

    override suspend fun generateCompletion(prompt: String): String {
        if (apiKey.isEmpty()) {
            return simulateResponse(prompt)
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = ChatCompletionRequest(
                    model = modelName,
                    messages = listOf(
                        ChatCompletionRequest.Message(
                            role = "system",
                            content = "你是一个专业的Android开发助手，擅长Kotlin、Java、Jetpack Compose和Android架构设计。请用中文回答。"
                        ),
                        ChatCompletionRequest.Message(
                            role = "user",
                            content = prompt
                        )
                    ),
                    temperature = parameters.temperature,
                    maxTokens = parameters.maxTokens,
                    topP = parameters.topP,
                    frequencyPenalty = parameters.frequencyPenalty,
                    presencePenalty = parameters.presencePenalty
                )

                val response = api?.createChatCompletion(request)
                response?.choices?.firstOrNull()?.message?.content
                    ?: simulateResponse(prompt)
            } catch (e: Exception) {
                "API调用失败: ${e.message}\n\n模拟响应:\n${simulateResponse(prompt)}"
            }
        }
    }

    override fun streamCompletion(prompt: String): Sequence<String> = sequence {
        val response = runCatching {
            kotlinx.coroutines.runBlocking {
                generateCompletion(prompt)
            }
        }.getOrDefault(simulateResponse(prompt))

        val words = response.split(" ")
        for (word in words) {
            yield("$word ")
            Thread.sleep(100)
        }
    }

    override fun getModelInfo(): ModelInfo {
        return ModelInfo(
            name = modelName,
            version = "1.0",
            provider = "OpenAI",
            capabilities = listOf(
                "代码生成",
                "代码解释",
                "调试建议",
                "架构设计"
            ),
            maxTokens = parameters.maxTokens
        )
    }

    override suspend fun isAvailable(): Boolean {
        return apiKey.isNotEmpty()
    }

    override fun setParameters(parameters: ModelParameters) {
        this.parameters = parameters
    }

    private fun simulateResponse(prompt: String): String {
        return when {
            prompt.contains("代码生成") || prompt.contains("generate code") -> {
                """
                // 根据需求生成的代码示例
                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        
                        initViews()
                        setupListeners()
                    }
                    
                    private fun initViews() {
                        // 初始化视图
                    }
                    
                    private fun setupListeners() {
                        // 设置监听器
                    }
                }
                """.trimIndent()
            }

            prompt.contains("解释") || prompt.contains("explain") -> {
                "这段代码是一个Android Activity的基本结构。它继承自AppCompatActivity，" +
                "并在onCreate方法中初始化界面和设置事件监听器。" +
                "这是Android应用开发的标准模式。"
            }

            prompt.contains("调试") || prompt.contains("debug") -> {
                "根据错误描述，建议检查以下几点：\n" +
                "1. 确保所有UI组件都已正确初始化\n" +
                "2. 检查布局文件是否存在且正确引用\n" +
                "3. 验证权限是否已正确申请\n" +
                "4. 查看Logcat中的详细错误日志"
            }

            prompt.contains("架构") || prompt.contains("architecture") -> {
                "推荐使用MVVM架构模式：\n" +
                "1. View层：使用Jetpack Compose构建UI\n" +
                "2. ViewModel层：管理UI状态和业务逻辑\n" +
                "3. Model层：数据仓库和数据源\n" +
                "4. 使用Hilt进行依赖注入\n" +
                "5. 采用Repository模式管理数据"
            }

            else -> {
                "我是Android开发助手，可以为您提供代码生成、解释、调试和架构设计等帮助。" +
                "请告诉我您需要什么具体的帮助。"
            }
        }
    }
}
