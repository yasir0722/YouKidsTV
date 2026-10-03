package com.example.youkids

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide

data class AppSettingsCard(
    val title: String,
    val description: String,
)

class AppSettingsCardPresenter : Presenter() {
    private var selectedBackgroundColor = 0
    private var defaultBackgroundColor = 0
    private var defaultImage: Drawable? = null

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        selectedBackgroundColor =
            ContextCompat.getColor(parent.context, R.color.selected_background)
        defaultBackgroundColor =
            ContextCompat.getColor(parent.context, R.color.default_background)
        defaultImage = ContextCompat.getDrawable(parent.context, R.drawable.movie)

        val card = object : ImageCardView(parent.context) {
            override fun setSelected(selected: Boolean) {
                updateBackground(this, selected)
                super.setSelected(selected)
            }
        }
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        updateBackground(card, false)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val settingsCard = item as AppSettingsCard
        val card = viewHolder.view as ImageCardView
        card.titleText = settingsCard.title
        card.contentText = settingsCard.description
        Glide.with(card.context)
            .load(defaultImage)
            .centerCrop()
            .into(card.mainImageView!!)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.mainImage = null
    }

    private fun updateBackground(card: ImageCardView, selected: Boolean) {
        val color = if (selected) selectedBackgroundColor else defaultBackgroundColor
        card.setBackgroundColor(color)
        card.setInfoAreaBackgroundColor(color)
    }

    private companion object {
        const val CARD_WIDTH = 313
        const val CARD_HEIGHT = 176
    }
}
