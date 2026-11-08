package com.example.youkids

import java.io.IOException

internal class CatalogueRefreshRateLimitedException(
    val retryAfterSeconds: Long,
) : IOException("Afterlight catalogue refresh is rate-limited.")

internal class CatalogueRefreshLimiter(
    private val minimumIntervalMs: Long = MINIMUM_INTERVAL_MS,
) {
    private var lastAttemptAtMs: Long? = null
    private var blockedUntilMs: Long? = null
    private var inFlight = false

    init {
        require(minimumIntervalMs > 0L)
    }

    @Synchronized
    fun tryStart(nowMs: Long): Long? {
        val intervalWait = lastAttemptAtMs
            ?.let { (it + minimumIntervalMs - nowMs).coerceAtLeast(0L) }
            ?: 0L
        val serverWait = blockedUntilMs
            ?.let { (it - nowMs).coerceAtLeast(0L) }
            ?: 0L
        val waitMs = maxOf(intervalWait, serverWait)
        if (inFlight) return maxOf(waitMs, MINIMUM_IN_FLIGHT_WAIT_MS)
        if (waitMs > 0L) return waitMs

        lastAttemptAtMs = nowMs
        inFlight = true
        return null
    }

    @Synchronized
    fun recordRetryAfter(nowMs: Long, retryAfterSeconds: Long) {
        val retryAt = nowMs + retryAfterSeconds.coerceAtLeast(1L) * MILLIS_PER_SECOND
        blockedUntilMs = maxOf(blockedUntilMs ?: 0L, retryAt)
    }

    @Synchronized
    fun finish() {
        inFlight = false
    }

    companion object {
        const val MINIMUM_INTERVAL_MS = 60_000L
        private const val MINIMUM_IN_FLIGHT_WAIT_MS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L

        fun retryAfterSeconds(waitMs: Long): Long =
            ((waitMs.coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
                .coerceAtLeast(1L)
    }
}
