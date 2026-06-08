package com.example.androiddevagent.agent.llm

import com.example.androiddevagent.data.SecureStorage
import com.example.androiddevagent.data.TokenUsageDao
import com.example.androiddevagent.data.TokenUsageEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenTracker @Inject constructor(
    private val tokenUsageDao: TokenUsageDao,
    private val secureStorage: SecureStorage
) {
    private val modelPricing = mapOf(
        "gpt-4o" to Pair(2.5 / 1_000_000, 10.0 / 1_000_000),
        "gpt-4o-mini" to Pair(0.15 / 1_000_000, 0.6 / 1_000_000),
        "gpt-4-turbo" to Pair(10.0 / 1_000_000, 30.0 / 1_000_000),
        "deepseek-chat" to Pair(0.14 / 1_000_000, 0.28 / 1_000_000),
        "deepseek-coder" to Pair(0.14 / 1_000_000, 0.28 / 1_000_000),
        "glm-4" to Pair(0.1 / 1_000_000, 0.1 / 1_000_000),
        "glm-4-flash" to Pair(0.01 / 1_000_000, 0.01 / 1_000_000),
        "moonshot-v1-8k" to Pair(0.012 / 1_000_000, 0.012 / 1_000_000),
        "moonshot-v1-32k" to Pair(0.024 / 1_000_000, 0.024 / 1_000_000),
        "qwen-turbo" to Pair(0.02 / 1_000_000, 0.06 / 1_000_000),
        "qwen-plus" to Pair(0.04 / 1_000_000, 0.12 / 1_000_000),
        "qwen-max" to Pair(0.12 / 1_000_000, 0.36 / 1_000_000)
    )

    private val defaultPricing = Pair(0.5 / 1_000_000, 1.5 / 1_000_000)

    private var currentTaskId: String = ""
    private var currentProvider: String = ""
    private var currentModel: String = ""
    private var taskPromptTokens: Int = 0
    private var taskCompletionTokens: Int = 0

    fun startTask(taskId: String, provider: String, model: String) {
        currentTaskId = taskId
        currentProvider = provider
        currentModel = model
        taskPromptTokens = 0
        taskCompletionTokens = 0
    }

    fun recordUsage(promptTokens: Int, completionTokens: Int) {
        taskPromptTokens += promptTokens
        taskCompletionTokens += completionTokens
    }

    suspend fun finishTask() {
        if (currentTaskId.isEmpty()) return
        val totalTokens = taskPromptTokens + taskCompletionTokens
        val cost = estimateCost(currentModel, taskPromptTokens, taskCompletionTokens)
        val entity = TokenUsageEntity(
            taskId = currentTaskId,
            provider = currentProvider,
            model = currentModel,
            promptTokens = taskPromptTokens,
            completionTokens = taskCompletionTokens,
            totalTokens = totalTokens,
            estimatedCostUsd = cost,
            timestamp = System.currentTimeMillis()
        )
        try {
            tokenUsageDao.insert(entity)
        } catch (_: Exception) {
        }
        taskPromptTokens = 0
        taskCompletionTokens = 0
    }

    fun getCurrentTaskUsage(): Pair<Int, Int> = Pair(taskPromptTokens, taskCompletionTokens)

    fun isBudgetExceeded(): Boolean {
        val budget = secureStorage.getTokenBudget()
        if (budget <= 0) return false
        return taskPromptTokens + taskCompletionTokens >= budget
    }

    private fun estimateCost(model: String, promptTokens: Int, completionTokens: Int): Double {
        val pricing = modelPricing[model] ?: defaultPricing
        return promptTokens * pricing.first + completionTokens * pricing.second
    }

    suspend fun getTotalUsage(): Pair<Long, Double> {
        val tokens = tokenUsageDao.getTotalTokens() ?: 0L
        val cost = tokenUsageDao.getTotalCost() ?: 0.0
        return Pair(tokens, cost)
    }

    suspend fun getTodayUsage(): Pair<Long, Double> {
        val startOfDay = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000L)
        val tokens = tokenUsageDao.getTotalTokensSince(startOfDay) ?: 0L
        val cost = tokenUsageDao.getTotalCostSince(startOfDay) ?: 0.0
        return Pair(tokens, cost)
    }
}
