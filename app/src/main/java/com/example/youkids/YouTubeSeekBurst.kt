package com.example.youkids

internal class YouTubeSeekBurst {
    private var pressCount = 0
    private var pendingOffsetSeconds = 0L
    private var lastPressAtMs: Long? = null

    val hasPendingPresses: Boolean
        get() = pressCount > 0

    fun recordPress(direction: Int, nowMs: Long): Long {
        require(direction == -1 || direction == 1)

        val previousPressAtMs = lastPressAtMs
        if (hasPendingPresses &&
            previousPressAtMs != null &&
            nowMs - previousPressAtMs >= DEBOUNCE_MS
        ) {
            pressCount = 0
        }

        pressCount = (pressCount + 1).coerceAtMost(MAX_PRESS_COUNT)
        val stepSeconds = when (pressCount) {
            in 1..5 -> 10L
            in 6..10 -> 30L
            else -> 45L
        }
        pendingOffsetSeconds += direction * stepSeconds
        lastPressAtMs = nowMs
        return pendingOffsetSeconds
    }

    fun millisecondsUntilCommit(nowMs: Long): Long {
        val lastPress = lastPressAtMs ?: return 0L
        return (DEBOUNCE_MS - (nowMs - lastPress)).coerceAtLeast(0L)
    }

    fun isReadyToCommit(nowMs: Long): Boolean =
        hasPendingPresses && millisecondsUntilCommit(nowMs) == 0L

    fun previewTarget(currentPositionSeconds: Double, durationSeconds: Double): Double {
        val currentPosition = currentPositionSeconds.takeIf { it.isFinite() } ?: 0.0
        val target = (currentPosition + pendingOffsetSeconds).coerceAtLeast(0.0)
        val duration = durationSeconds.takeIf { it.isFinite() && it > 0.0 }
        return duration?.let { target.coerceAtMost(it) } ?: target
    }

    fun consumeOffset(): Long? {
        if (!hasPendingPresses) return null
        return pendingOffsetSeconds.also { reset() }
    }

    fun reset() {
        pressCount = 0
        pendingOffsetSeconds = 0L
        lastPressAtMs = null
    }

    companion object {
        const val DEBOUNCE_MS = 600L
        const val MAX_PRESS_COUNT = 11
    }
}
