package com.example.youkids

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

object ScreenTimerExpiryDialog {
    fun show(activity: FragmentActivity, onTimeSelected: (Int) -> Unit) {
        showQuestion(activity, 0, onTimeSelected)
    }

    private fun showQuestion(
        activity: FragmentActivity,
        wrongAttempts: Int,
        onTimeSelected: (Int) -> Unit,
    ) {
        val first = (11..19).random()
        val second = (11..19).random()
        val answer = first + second
        val choices = listOf(answer, answer - 1, answer + 1).shuffled()

        lateinit var dialog: AlertDialog
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 24), 0, dp(activity, 24), dp(activity, 16))
            addView(messageView(activity, R.string.screen_timer_finished_message))
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.screen_timer_math_question, first, second)
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(activity, 12), 0, dp(activity, 12))
                },
            )
        }
        choices.forEach { choice ->
            content.addView(
                choiceButton(activity, choice.toString()) {
                    dialog.dismiss()
                    if (choice == answer) {
                        showTimeChoices(activity, onTimeSelected)
                    } else if (wrongAttempts == 0) {
                        showQuestion(activity, 1, onTimeSelected)
                    } else {
                        activity.finishAffinity()
                    }
                },
            )
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.screen_timer_finished_title)
            .setView(content)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            content.getChildAt(2)?.requestFocus()
        }
        dialog.show()
    }

    private fun showTimeChoices(
        activity: FragmentActivity,
        onTimeSelected: (Int) -> Unit,
    ) {
        val choices = intArrayOf(5, 10, 15)
        lateinit var dialog: AlertDialog
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 24), 0, dp(activity, 24), dp(activity, 16))
            addView(messageView(activity, R.string.screen_timer_add_time_message))
        }
        choices.forEach { minutes ->
            content.addView(
                choiceButton(activity, activity.getString(R.string.screen_timer_minutes, minutes)) {
                    dialog.dismiss()
                    onTimeSelected(minutes)
                },
            )
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.screen_timer_add_time_title)
            .setView(content)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            content.getChildAt(1)?.requestFocus()
        }
        dialog.show()
    }

    private fun messageView(activity: FragmentActivity, messageId: Int): TextView =
        TextView(activity).apply {
            text = activity.getString(messageId)
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(activity, 8), 0, dp(activity, 8))
        }

    private fun choiceButton(
        activity: FragmentActivity,
        label: String,
        onClick: () -> Unit,
    ): Button =
        Button(activity).apply {
            text = label
            textSize = 18f
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            minHeight = dp(activity, 56)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(activity, 4)
            }
        }

    private fun dp(activity: FragmentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
