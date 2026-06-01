package com.example.androiddevagent.agent.engine

import com.example.androiddevagent.agent.events.AgentEvent
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
class StuckDetector @Inject constructor() {

    private val maxConsecutiveFailures = 3
    private val maxSameActionRepeats = 3
    private val maxTotalIterations = 20

    fun detect(history: List<AgentEvent>, currentIteration: Int): StuckState {
        val repeatStuck = detectRepeatedActions(history)
        if (repeatStuck.isStuck) return repeatStuck

        val errorStuck = detectRepeatedErrors(history)
        if (errorStuck.isStuck) return errorStuck

        val buildStuck = detectBuildFailureLoop(history)
        if (buildStuck.isStuck) return buildStuck

        if (currentIteration >= maxTotalIterations) {
            return StuckState(
                isStuck = true,
                reason = "Reached maximum iterations ($maxTotalIterations). The task may be too complex.",
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
                reason = "Agent is repeating the same action: ${last.first().name} with same arguments. " +
                         "This suggests the approach is not working.",
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
                    reason = "Agent keeps calling ${last.first().name} but it keeps failing. " +
                             "Need to try a different approach.",
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
                reason = "Agent keeps getting the same error and cannot fix it. " +
                         "Error: ${outputs.first().take(100)}",
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
                    reason = "Build has failed ${lastBuilds.size} times in a row with similar errors. " +
                             "The fixes are not resolving the underlying issue.",
                    strategy = StuckStrategy.SWITCH_APPROACH
                )
            }
        }

        return StuckState(false, "", StuckStrategy.NONE)
    }
}
