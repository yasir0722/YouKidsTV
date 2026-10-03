package com.example.youkids

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter

class TagCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val title = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
        }
        val count = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            isFocusable = true
            isFocusableInTouchMode = true
            background = cardBackground(context, false)
            layoutParams = ViewGroup.LayoutParams(240.dp(context), 160.dp(context))
            addView(
                title,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(
                count,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnFocusChangeListener { view, hasFocus ->
                view.background = cardBackground(context, hasFocus)
                view.scaleX = if (hasFocus) 1.06f else 1f
                view.scaleY = if (hasFocus) 1.06f else 1f
            }
        }

        return TagCardViewHolder(card, title, count)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val tag = item as VideoTag
        val holder = viewHolder as TagCardViewHolder
        holder.title.text = tag.name
        holder.count.text = if (tag.videoCount == 0) {
            holder.card.resources.getString(R.string.no_video_tags_hint)
        } else {
            holder.card.resources.getQuantityString(
                R.plurals.tag_video_count,
                tag.videoCount,
                tag.videoCount,
            )
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit

    private fun cardBackground(context: android.content.Context, selected: Boolean) =
        GradientDrawable().apply {
            cornerRadius = 8.dp(context).toFloat()
            setColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.selected_background else R.color.default_background,
                ),
            )
        }

    private class TagCardViewHolder(
        val card: LinearLayout,
        val title: TextView,
        val count: TextView,
    ) : ViewHolder(card)

    private fun Int.dp(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
