package com.example.youkids

import android.content.Context

class PlaybackPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var audioLanguage: String
        get() = preferences.getString(KEY_AUDIO_LANGUAGE, LANGUAGE_MALAY) ?: LANGUAGE_MALAY
        set(value) {
            preferences.edit().putString(KEY_AUDIO_LANGUAGE, value).apply()
        }

    var playbackSpeed: Float
        get() = preferences.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
        set(value) {
            preferences.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
        }

    var videoQuality: String
        get() = preferences.getString(KEY_VIDEO_QUALITY, QUALITY_1080P) ?: QUALITY_1080P
        set(value) {
            preferences.edit().putString(KEY_VIDEO_QUALITY, value).apply()
        }

    var subtitleLanguage: String
        get() = preferences.getString(KEY_SUBTITLE_LANGUAGE, LANGUAGE_MALAY) ?: LANGUAGE_MALAY
        set(value) {
            preferences.edit().putString(KEY_SUBTITLE_LANGUAGE, value).apply()
        }

    var screenTimerMinutes: Int
        get() = preferences.getInt(KEY_SCREEN_TIMER_MINUTES, DEFAULT_SCREEN_TIMER_MINUTES)
        set(value) {
            preferences.edit()
                .putInt(KEY_SCREEN_TIMER_MINUTES, value)
                .putLong(KEY_SCREEN_TIMER_REMAINING_MILLIS, value * MILLIS_PER_MINUTE)
                .apply()
        }

    fun beginScreenTimerSession(): Long {
        val savedRemainingMillis = preferences.getLong(KEY_SCREEN_TIMER_REMAINING_MILLIS, 0L)
        if (savedRemainingMillis > 0L) return savedRemainingMillis
        val durationMillis = screenTimerMinutes * MILLIS_PER_MINUTE
        saveScreenTimerRemainingMillis(durationMillis)
        return durationMillis
    }

    fun saveScreenTimerRemainingMillis(remainingMillis: Long) {
        preferences.edit()
            .putLong(KEY_SCREEN_TIMER_REMAINING_MILLIS, remainingMillis.coerceAtLeast(0L))
            .apply()
    }

    companion object {
        const val DEFAULT_PLAYBACK_SPEED = 0.75f
        const val DEFAULT_SCREEN_TIMER_MINUTES = 30
        const val LANGUAGE_MALAY = "ms"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_ARABIC = "ar"
        const val LANGUAGE_INDONESIAN = "id"
        const val LANGUAGE_ORIGINAL = "original"
        const val LANGUAGE_OFF = "off"
        const val QUALITY_1080P = "hd1080"
        const val QUALITY_720P = "hd720"
        const val QUALITY_480P = "large"
        const val QUALITY_AUTO = "auto"

        val speedOptions = listOf(0.55f, 0.65f, 0.75f, 0.85f, 1.0f)
        val audioLanguages = listOf(
            LANGUAGE_MALAY,
            LANGUAGE_ENGLISH,
            LANGUAGE_ARABIC,
            LANGUAGE_INDONESIAN,
            LANGUAGE_ORIGINAL,
        )
        val videoQualities = listOf(QUALITY_1080P, QUALITY_720P, QUALITY_480P, QUALITY_AUTO)
        val subtitleLanguages = listOf(LANGUAGE_MALAY, LANGUAGE_OFF)
        val screenTimerOptionsMinutes = listOf(5, 10, 15, 30, 45, 60)

        private const val PREFERENCES_NAME = "youkids_playback"
        private const val KEY_AUDIO_LANGUAGE = "audio_language"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val KEY_SCREEN_TIMER_MINUTES = "screen_timer_minutes"
        private const val KEY_SCREEN_TIMER_REMAINING_MILLIS = "screen_timer_remaining_millis"
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
