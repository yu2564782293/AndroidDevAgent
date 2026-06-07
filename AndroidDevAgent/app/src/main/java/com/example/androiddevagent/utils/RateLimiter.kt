package com.example.androiddevagent.utils

import javax.inject.Singleton

@Singleton
class RateLimiter(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val lock = Any()
    private val apiCallTimestamps = ArrayDeque<Long>()
    private val actionTimestampsByKey = mutableMapOf<String, Long>()

    fun tryAcquire(actionKey: String = DEFAULT_ACTION_KEY): RateLimitResult {
        val now = clock()

        synchronized(lock) {
            removeExpiredTimestamps(now)

            val lastActionAt = actionTimestampsByKey[actionKey]
            if (lastActionAt != null) {
                val elapsedSinceLastAction = now - lastActionAt
                if (elapsedSinceLastAction in 0 until DEBOUNCE_WINDOW_MILLIS) {
                    return RateLimitResult.Blocked(
                        message = "操作过于频繁，请稍后再试",
                        retryAfterMillis = DEBOUNCE_WINDOW_MILLIS - elapsedSinceLastAction
                    )
                }
            }

            val callsLastMinute = apiCallTimestamps.count { now - it < ONE_MINUTE_MILLIS }
            val callsLastHour = apiCallTimestamps.size

            if (callsLastMinute >= MAX_CALLS_PER_MINUTE) {
                val oldestMinuteCall = apiCallTimestamps.firstOrNull { now - it < ONE_MINUTE_MILLIS } ?: now
                return RateLimitResult.Blocked(
                    message = "一分钟内请求次数已达上限，请稍后再试",
                    retryAfterMillis = (oldestMinuteCall + ONE_MINUTE_MILLIS - now).coerceAtLeast(0L)
                )
            }

            if (callsLastHour >= MAX_CALLS_PER_HOUR) {
                val oldestHourCall = apiCallTimestamps.firstOrNull() ?: now
                return RateLimitResult.Blocked(
                    message = "一小时内请求次数已达上限，请稍后再试",
                    retryAfterMillis = (oldestHourCall + ONE_HOUR_MILLIS - now).coerceAtLeast(0L)
                )
            }

            apiCallTimestamps.addLast(now)
            actionTimestampsByKey[actionKey] = now

            val warning = warningFor(
                callsLastMinute = callsLastMinute + 1,
                callsLastHour = callsLastHour + 1
            )
            return RateLimitResult.Allowed(warningMessage = warning)
        }
    }

    fun getUsageSnapshot(): RateLimitUsage {
        val now = clock()
        synchronized(lock) {
            removeExpiredTimestamps(now)
            return RateLimitUsage(
                callsLastMinute = apiCallTimestamps.count { now - it < ONE_MINUTE_MILLIS },
                callsLastHour = apiCallTimestamps.size,
                maxCallsPerMinute = MAX_CALLS_PER_MINUTE,
                maxCallsPerHour = MAX_CALLS_PER_HOUR
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            apiCallTimestamps.clear()
            actionTimestampsByKey.clear()
        }
    }

    private fun removeExpiredTimestamps(now: Long) {
        while (apiCallTimestamps.isNotEmpty() && now - apiCallTimestamps.first() >= ONE_HOUR_MILLIS) {
            apiCallTimestamps.removeFirst()
        }
    }

    private fun warningFor(callsLastMinute: Int, callsLastHour: Int): String? {
        return when {
            callsLastMinute >= MAX_CALLS_PER_MINUTE_WARNING -> "请求较频繁，接近每分钟限制"
            callsLastHour >= MAX_CALLS_PER_HOUR_WARNING -> "本小时请求较多，接近每小时限制"
            else -> null
        }
    }

    private companion object {
        const val DEFAULT_ACTION_KEY = "llm_request"
        const val ONE_MINUTE_MILLIS = 60_000L
        const val ONE_HOUR_MILLIS = 60 * ONE_MINUTE_MILLIS
        const val DEBOUNCE_WINDOW_MILLIS = 1_200L
        const val MAX_CALLS_PER_MINUTE = 12
        const val MAX_CALLS_PER_HOUR = 120
        const val MAX_CALLS_PER_MINUTE_WARNING = 10
        const val MAX_CALLS_PER_HOUR_WARNING = 96
    }
}

sealed interface RateLimitResult {
    data class Allowed(val warningMessage: String? = null) : RateLimitResult
    data class Blocked(
        val message: String,
        val retryAfterMillis: Long
    ) : RateLimitResult
}

data class RateLimitUsage(
    val callsLastMinute: Int,
    val callsLastHour: Int,
    val maxCallsPerMinute: Int,
    val maxCallsPerHour: Int
)
