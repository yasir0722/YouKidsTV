package com.example.youkids

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import com.bumptech.glide.Glide

class YouTubeGridRow(
    header: HeaderItem,
    val movies: List<Movie>,
) : Row(header)

data class YouTubeGrid(val movies: List<Movie>) {
    val rows: List<List<Movie>>
        get() {
            if (movies.isEmpty()) return emptyList()
            val rowCount = minOf(MAX_ROWS, movies.size)
            val moviesPerRow = (movies.size + rowCount - 1) / rowCount
            return movies.chunked(moviesPerRow)
        }

    companion object {
        private const val MAX_ROWS = 3
    }
}

class YouTubeGridRowPresenter : RowPresenter() {
    override fun createRowViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(
            HorizontalGridView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    GRID_HEIGHT.dp(context),
                )
                setNumRows(MAX_ROWS)
                setHorizontalSpacing(CARD_GAP.dp(context))
                setVerticalSpacing(ROW_GAP.dp(context))
                setItemAlignmentOffset(0)
                setItemAlignmentOffsetPercent(0f)
                clipChildren = false
                clipToPadding = false
                setPadding(EDGE_PADDING.dp(context), 0, EDGE_PADDING.dp(context), 0)
            },
        )

    override fun onBindRowViewHolder(viewHolder: ViewHolder, item: Any) {
        super.onBindRowViewHolder(viewHolder, item)
        val gridView = viewHolder.view as HorizontalGridView
        val row = item as YouTubeGridRow
        val objectAdapter = ArrayObjectAdapter(YouTubeGridCardPresenter()).apply {
            YouTubeGrid(row.movies).rows.flatten().forEach(::add)
        }
        gridView.adapter = ItemBridgeAdapter(objectAdapter)
    }

    override fun onUnbindRowViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as HorizontalGridView).adapter = null
        super.onUnbindRowViewHolder(viewHolder)
    }

    private companion object {
        const val MAX_ROWS = 3
        const val CARD_GAP = 8
        const val ROW_GAP = 8
        const val EDGE_PADDING = 24
        const val GRID_HEIGHT = 328
    }
}

private class YouTubeGridCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val card = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            background = cardBackground(context, false)
            setOnFocusChangeListener { view, focused ->
                view.background = cardBackground(context, focused)
                view.foreground = if (focused) cardFocusOverlay(context) else null
                view.scaleX = if (focused) 1.05f else 1f
                view.scaleY = if (focused) 1.05f else 1f
            }
        }
        card.addView(
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            },
            FrameLayout.LayoutParams(
                CARD_WIDTH.dp(context),
                IMAGE_HEIGHT.dp(context),
            ),
        )
        card.addView(
            TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6.dp(context), 0, 6.dp(context), 0)
                setBackgroundColor(
                    ContextCompat.getColor(context, R.color.playback_controls_background),
                )
            },
            FrameLayout.LayoutParams(
                CARD_WIDTH.dp(context),
                TITLE_HEIGHT.dp(context),
            ).apply {
                topMargin = IMAGE_HEIGHT.dp(context)
            },
        )
        card.setOnClickListener {
            val movie = card.tag as? Movie ?: return@setOnClickListener
            context.startActivity(
                Intent(context, DetailsActivity::class.java)
                    .putExtra(DetailsActivity.MOVIE, movie),
            )
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as Movie
        val card = viewHolder.view as FrameLayout
        card.tag = movie
        (card.getChildAt(1) as TextView).text = movie.title.orEmpty().ifBlank {
            card.context.getString(R.string.movie)
        }
        Glide.with(card.context)
            .load(movie.cardImageUrl)
            .placeholder(R.drawable.movie)
            .error(R.drawable.movie)
            .into(card.getChildAt(0) as ImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as FrameLayout
        (card.getChildAt(0) as ImageView).setImageDrawable(null)
        card.tag = null
    }

    private companion object {
        const val CARD_WIDTH = 170
        const val IMAGE_HEIGHT = 78
        const val TITLE_HEIGHT = 26
    }
}

private fun Int.dp(context: android.content.Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

private fun cardBackground(
    context: android.content.Context,
    focused: Boolean,
) = GradientDrawable().apply {
    cornerRadius = 6.dp(context).toFloat()
    setColor(
        ContextCompat.getColor(
            context,
            if (focused) R.color.selected_background else R.color.default_background,
        ),
    )
    if (focused) {
        setStroke(4.dp(context), Color.RED)
    }
}

private fun cardFocusOverlay(context: android.content.Context) =
    GradientDrawable().apply {
        cornerRadius = 6.dp(context).toFloat()
        setColor(Color.TRANSPARENT)
        setStroke(2.dp(context), Color.WHITE)
    }
