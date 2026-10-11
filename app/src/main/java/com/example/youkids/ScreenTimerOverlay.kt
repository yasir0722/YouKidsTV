package com.example.youkids

import android.graphics.Color
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class ScreenTimerOverlay(
    private val activity: FragmentActivity,
    private val onExpired: () -> Unit = {},
    private val onExtended: () -> Unit = {},
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val preferences = PlaybackPreferences(activity.applicationContext)
    private var timerView: TextView? = null
    private var remainingMillis = 0L
    private var lastTick = 0L
    private var running = false
    private var expiryDialogVisible = false
    private val shownWarnings = mutableSetOf<Int>()

    private val timerTask = object : Runnable {
        override fun run() {
            if (!running) return
            val now = android.os.SystemClock.elapsedRealtime()
            val previousRemainingMillis = remainingMillis
            remainingMillis -= now - lastTick
            lastTick = now
            showWarningIfNeeded(previousRemainingMillis)
            if (remainingMillis <= 0L) {
                remainingMillis = 0L
                running = false
                preferences.saveScreenTimerRemainingMillis(0L)
                updateLabel()
                onExpired()
                showExpiredDialog()
            } else {
                updateLabel()
                handler.postDelayed(this, TIMER_TICK_MS)
            }
        }
    }

    fun attach() {
        timerView = activity.findViewById(R.id.screen_timer) ?: createTimerView()
        remainingMillis = preferences.beginScreenTimerSession()
        updateLabel()
    }

    fun onResume() {
        remainingMillis = preferences.beginScreenTimerSession()
        if (remainingMillis <= 0L || running || expiryDialogVisible) return
        running = true
        lastTick = android.os.SystemClock.elapsedRealtime()
        handler.removeCallbacks(timerTask)
        handler.post(timerTask)
    }

    fun onPause() {
        if (!running) return
        val now = android.os.SystemClock.elapsedRealtime()
        remainingMillis -= now - lastTick
        running = false
        preferences.saveScreenTimerRemainingMillis(remainingMillis)
        handler.removeCallbacks(timerTask)
    }

    fun onDestroy() {
        handler.removeCallbacks(timerTask)
        timerView = null
    }

    fun resetToMinutes(minutes: Int) {
        remainingMillis = minutes * MILLIS_PER_MINUTE
        preferences.saveScreenTimerRemainingMillis(remainingMillis)
        shownWarnings.clear()
        updateLabel()
        if (!running) onResume()
    }

    private fun createTimerView(): TextView {
        val timer = TextView(activity).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundColor(activity.getColor(R.color.playback_controls_background))
            setPadding(14, 8, 14, 8)
        }
        activity.findViewById<ViewGroup>(android.R.id.content).addView(
            timer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = 24
                marginEnd = 28
            },
        )
        return timer
    }

    private fun updateLabel() {
        val totalSeconds = (remainingMillis / MILLIS_PER_SECOND).coerceAtLeast(0L)
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        timerView?.text = activity.getString(
            R.string.screen_timer_remaining,
            DateFormat.getTimeFormat(activity).format(System.currentTimeMillis()),
            "%d:%02d".format(minutes, seconds),
        )
    }

    private fun showWarningIfNeeded(previousRemainingMillis: Long) {
        listOf(5, 3, 1).forEach { minutes ->
            val thresholdMillis = minutes * MILLIS_PER_MINUTE
            if (previousRemainingMillis > thresholdMillis &&
                remainingMillis <= thresholdMillis &&
                shownWarnings.add(minutes)
            ) {
                ScreenTimerWarning.show(activity, minutes)
            }
        }
    }

    private fun showExpiredDialog() {
        if (activity.isFinishing || activity.isDestroyed || expiryDialogVisible) return
        expiryDialogVisible = true
        ScreenTimerExpiryDialog.show(activity) { minutes ->
            expiryDialogVisible = false
            remainingMillis = minutes * MILLIS_PER_MINUTE
            preferences.saveScreenTimerRemainingMillis(remainingMillis)
            updateLabel()
            onExtended()
            onResume()
        }
    }

    companion object {
        private const val TIMER_TICK_MS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val SECONDS_PER_MINUTE = 60L
    }
}
