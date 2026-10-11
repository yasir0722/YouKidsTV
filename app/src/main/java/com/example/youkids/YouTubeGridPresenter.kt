package com.example.youkids

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide

data class YouTubeGrid(val movies: List<Movie>)

class YouTubeGridPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(
            LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            },
        )

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val container = viewHolder.view as LinearLayout
        val grid = item as YouTubeGrid
        container.removeAllViews()

        val rowCount = minOf(MAX_ROWS, grid.movies.size)
        val moviesPerRow = (grid.movies.size + rowCount - 1) / rowCount
        grid.movies.chunked(moviesPerRow).forEach { movies ->
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                isFocusable = false
            }
            movies.forEach { movie ->
                row.addView(createCard(row, movie))
            }
            container.addView(
                HorizontalScrollView(container.context).apply {
                    isHorizontalScrollBarEnabled = false
                    isFocusable = false
                    isFillViewport = true
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(row)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    CARD_HEIGHT.dp(container),
                ).apply {
                    bottomMargin = ROW_GAP.dp(container)
                },
            )
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ViewGroup).removeAllViews()
    }

    private fun createCard(parent: ViewGroup, movie: Movie): View {
        val context = parent.context
        val card = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            background = cardBackground(context, false)
            setOnFocusChangeListener { view, focused ->
                view.background = cardBackground(context, focused)
                view.scaleX = if (focused) 1.05f else 1f
                view.scaleY = if (focused) 1.05f else 1f
                if (focused) {
                    view.post {
                        val scroll = view.parent?.parent as? HorizontalScrollView
                            ?: return@post
                        val target = view.left - (scroll.width - view.width) / 2
                        scroll.smoothScrollTo(target.coerceAtLeast(0), 0)
                    }
                }
            }
            setOnClickListener {
                context.startActivity(
                    Intent(context, DetailsActivity::class.java)
                        .putExtra(DetailsActivity.MOVIE, movie),
                )
            }
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        Glide.with(context)
            .load(movie.cardImageUrl)
            .placeholder(R.drawable.movie)
            .error(R.drawable.movie)
            .into(image)
        card.addView(
            image,
            FrameLayout.LayoutParams(
                CARD_WIDTH.dp(context),
                IMAGE_HEIGHT.dp(context),
            ),
        )
        card.addView(
            TextView(context).apply {
                text = movie.title.orEmpty().ifBlank { context.getString(R.string.movie) }
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6.dp(context), 0, 6.dp(context), 0)
                setBackgroundColor(ContextCompat.getColor(context, R.color.playback_controls_background))
            },
            FrameLayout.LayoutParams(
                CARD_WIDTH.dp(context),
                TITLE_HEIGHT.dp(context),
            ).apply {
                topMargin = IMAGE_HEIGHT.dp(context)
            },
        )
        return card
    }

    private fun cardBackground(context: android.content.Context, focused: Boolean) =
        GradientDrawable().apply {
            cornerRadius = 6.dp(context).toFloat()
            setColor(
                ContextCompat.getColor(
                    context,
                    if (focused) R.color.selected_background else R.color.default_background,
                ),
            )
        }

    private fun Int.dp(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()

    private fun Int.dp(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_ROWS = 3
        private const val CARD_WIDTH = 170
        private const val IMAGE_HEIGHT = 78
        private const val TITLE_HEIGHT = 26
        private const val CARD_HEIGHT = IMAGE_HEIGHT + TITLE_HEIGHT
        private const val ROW_GAP = 8
    }
}
