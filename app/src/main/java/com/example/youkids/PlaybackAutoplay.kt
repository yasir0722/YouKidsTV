package com.example.youkids

import kotlin.random.Random

internal class PlaybackAutoplayCountdown(
    private val durationMs: Long = DEFAULT_DURATION_MS,
) {
    private var deadlineMs: Long? = null
    private var pausedRemainingMs: Long? = null

    val isActive: Boolean
        get() = deadlineMs != null || pausedRemainingMs != null

    val isRunning: Boolean
        get() = deadlineMs != null

    init {
        require(durationMs > 0L)
    }

    fun start(nowMs: Long): Boolean {
        if (isActive) return false
        deadlineMs = nowMs + durationMs
        return true
    }

    fun pause(nowMs: Long): Boolean {
        val deadline = deadlineMs ?: return false
        pausedRemainingMs = (deadline - nowMs).coerceAtLeast(0L)
        deadlineMs = null
        return true
    }

    fun resume(nowMs: Long): Boolean {
        if (deadlineMs != null) return false
        val remaining = pausedRemainingMs ?: return false
        deadlineMs = nowMs + remaining
        pausedRemainingMs = null
        return true
    }

    fun millisecondsRemaining(nowMs: Long): Long? =
        deadlineMs?.minus(nowMs)?.coerceAtLeast(0L) ?: pausedRemainingMs

    fun progressFraction(nowMs: Long): Float {
        val remaining = millisecondsRemaining(nowMs) ?: return 0f
        return ((durationMs - remaining).toFloat() / durationMs)
            .coerceIn(0f, 1f)
    }

    fun secondsRemaining(nowMs: Long): Int? {
        val remainingMs = millisecondsRemaining(nowMs) ?: return null
        return ((remainingMs.coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
            .toInt()
    }

    fun isDue(nowMs: Long): Boolean =
        deadlineMs?.let { nowMs >= it } ?: false

    fun completeIfDue(nowMs: Long): Boolean {
        if (!isDue(nowMs)) return false
        deadlineMs = null
        return true
    }

    fun cancel(): Boolean {
        if (!isActive) return false
        deadlineMs = null
        pausedRemainingMs = null
        return true
    }

    companion object {
        const val DEFAULT_DURATION_MS = 5_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

internal fun firstPlayableSuggestion(
    currentMovie: Movie?,
    movies: List<Movie>,
): Movie? =
    movies.firstOrNull { movie ->
        movie.id != currentMovie?.id && when {
            movie.provider == YOUTUBE_PROVIDER ->
                movie.youtubeVideoId?.matches(YOUTUBE_ID) == true
            movie.sourceType == TELEGRAM_SOURCE ->
                movie.id > 0L
            else ->
                !movie.videoUrl.isNullOrBlank()
        }
    }

internal fun shuffledVideoSuggestions(
    currentMovie: Movie?,
    movies: List<Movie>,
    random: Random = Random.Default,
): List<Movie> =
    movies.filterNot { it.id == currentMovie?.id }.shuffled(random)

private const val YOUTUBE_PROVIDER = "youtube"
private const val TELEGRAM_SOURCE = "telegram"
private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
