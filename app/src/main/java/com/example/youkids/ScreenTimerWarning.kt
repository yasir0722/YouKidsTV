package com.example.youkids

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

object ScreenTimerWarning {
    private const val WARNING_SIZE_DP = 150
    private const val WARNING_TEXT_SIZE_SP = 64f
    private const val WARNING_DISPLAY_MILLIS = 5_000L

    fun show(activity: FragmentActivity, minutes: Int) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val warning = TextView(activity).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = WARNING_TEXT_SIZE_SP
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(activity.getColor(R.color.search_opaque))
                setStroke(4, Color.WHITE)
            }
            text = minutes.toString()
            elevation = 20f
        }
        root.addView(
            warning,
            FrameLayout.LayoutParams(
                dpToPx(activity, WARNING_SIZE_DP),
                dpToPx(activity, WARNING_SIZE_DP),
                Gravity.CENTER,
            ),
        )
        Handler(Looper.getMainLooper()).postDelayed(
            { root.removeView(warning) },
            WARNING_DISPLAY_MILLIS,
        )
    }

    private fun dpToPx(activity: FragmentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
