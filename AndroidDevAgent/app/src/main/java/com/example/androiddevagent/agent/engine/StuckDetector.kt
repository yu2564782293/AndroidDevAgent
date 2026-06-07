package com.example.androiddevagent.agent.engine

import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.data.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

data class StuckState(
    val isStuck: Boolean,
    val reason: String,
    val strategy: StuckStrategy
)

enum class StuckStrategy {
    NONE,
    SWITCH_APPROACH,
    ASK_USER,
    ABORT
}

@Singleton
class StuckDetector @Inject constructor(
    private val secureStorage: SecureStorage
) {

    private val maxConsecutiveFailures = 3
    private val maxSameActionRepeats = 3

    fun detect(history: List<AgentEvent>, currentIteration: Int): StuckState {
        val repeatStuck = detectRepeatedActions(history)
        if (repeatStuck.isStuck) return repeatStuck

        val errorStuck = detectRepeatedErrors(history)
        if (errorStuck.isStuck) return errorStuck

        val buildStuck = detectBuildFailureLoop(history)
        if (buildStuck.isStuck) return buildStuck

        if (currentIteration >= secureStorage.getMaxIterations()) {
            return StuckState(
                isStuck = true,
                reason = "已达到最大迭代次数 (${secureStorage.getMaxIterations()})，任务可能过于复杂。",
                strategy = StuckStrategy.ASK_USER
            )
        }

        return StuckState(false, "", StuckStrategy.NONE)
    }

    private fun detectRepeatedActions(history: List<AgentEvent>): StuckState {
        val toolCalls = history.filterIsInstance<AgentEvent.ToolCallEvent>()
        if (toolCalls.size < maxSameActionRepeats) {
            return StuckState(false, "", StuckStrategy.NONE)
        }

        val last = toolCalls.takeLast(maxSameActionRepeats)
        val allSame = last.all { call ->
            call.name == last.first().name && call.args == last.first().args
        }

        if (allSame) {
            return StuckState(
                isStuck = true,
                reason = "Agent 重复执行相同操作: ${last.first().name}，参数相同。当前方法可能无效。",
                strategy = StuckStrategy.SWITCH_APPROACH
            )
        }

        val sameTool = last.all { it.name == last.first().name }
        if (sameTool) {
            val results = history.filterIsInstance<AgentEvent.ToolResultEvent>().takeLast(maxSameActionRepeats)
            val allFailed = results.all { !it.success }
            if (allFailed) {
                return StuckState(
                    isStuck = true,
                    reason = "Agent 持续调用 ${last.first().name} 但一直失败，需要尝试不同的方法。",
                    strategy = StuckStrategy.SWITCH_APPROACH
                )
            }
        }

        return StuckState(false, "", StuckStrategy.NONE)
    }

    private fun detectRepeatedErrors(history: List<AgentEvent>): StuckState {
        val toolResults = history.filterIsInstance<AgentEvent.ToolResultEvent>()
        if (toolResults.size < maxConsecutiveFailures) {
            return StuckState(false, "", StuckStrategy.NONE)
        }

        val lastResults = toolResults.takeLast(maxConsecutiveFailures)
        val allFailed = lastResults.all { !it.success }
        if (!allFailed) {
            return StuckState(false, "", StuckStrategy.NONE)
        }

        val outputs = lastResults.map { it.output.take(200) }
        val allSameError = outputs.distinct().size == 1
        if (allSameError) {
            return StuckState(
                isStuck = true,
                reason = "Agent 持续遇到相同错误且无法修复。错误: ${outputs.first().take(100)}",
                strategy = StuckStrategy.ASK_USER
            )
        }

        return StuckState(false, "", StuckStrategy.NONE)
    }

    private fun detectBuildFailureLoop(history: List<AgentEvent>): StuckState {
        val buildResults = history.filterIsInstance<AgentEvent.BuildResultEvent>()
        if (buildResults.size < maxConsecutiveFailures) {
            return StuckState(false, "", StuckStrategy.NONE)
        }

        val lastBuilds = buildResults.takeLast(maxConsecutiveFailures)
        val allFailed = lastBuilds.all { !it.success }
        if (allFailed) {
            val errorSnippets = lastBuilds.map { it.output.take(150) }
            val sameErrors = errorSnippets.distinct().size <= 2
            if (sameErrors) {
                return StuckState(
                    isStuck = true,
                    reason = "构建已连续失败 ${lastBuilds.size} 次且错误相似，当前修复方案未能解决根本问题。",
                    strategy = StuckStrategy.SWITCH_APPROACH
                )
            }
        }

        return StuckState(false, "", StuckStrategy.NONE)
    }
}
