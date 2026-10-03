package com.example.youkids

import java.util.Timer
import java.util.TimerTask

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.util.concurrent.Executors

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.getMainLooper())
    private val apiExecutor = Executors.newSingleThreadExecutor()
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics
    private var selectedVideoTitleView: TextView? = null
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null
    private var catalogueMovies: List<Movie> = emptyList()
    private var currentSearchQuery = ""
    private var selectedTag: String? = null
    private var tagBackCallback: OnBackPressedCallback? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        prepareBackgroundManager()

        setupUIElements()

        loadRows()

        setupEventListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: " + mBackgroundTimer?.toString())
        mBackgroundTimer?.cancel()
        apiExecutor.shutdownNow()
    }

    private fun prepareBackgroundManager() {

        mBackgroundManager = BackgroundManager.getInstance(activity)
        mBackgroundManager.attach(activity!!.window)
        mDefaultBackground = ContextCompat.getDrawable(context!!, R.drawable.default_background)
        mMetrics = resources.displayMetrics
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        selectedVideoTitleView = requireActivity().findViewById(R.id.selected_video_title)
        // over title
        headersState = BrowseSupportFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // set fastLane (or headers) background color
        brandColor = ContextCompat.getColor(context!!, R.color.fastlane_background)
        // set search icon color
        searchAffordanceColor = ContextCompat.getColor(context!!, R.color.search_opaque)
    }

    private fun loadRows() {
        apiExecutor.execute {
            try {
                val movies = VideoLibraryRepository().fetchVideos()
                mHandler.post {
                    if (!isAdded) return@post
                    catalogueMovies = movies
                    MovieList.replace(movies)
                    if (movies.isEmpty()) {
                        showLoadError(getString(R.string.empty_catalogue))
                    } else {
                        showHomeRows(movies)
                    }
                }
            } catch (exception: IOException) {
                Log.e(TAG, "Unable to load the video catalogue", exception)
                mHandler.post {
                    if (isAdded) showLoadError(getString(R.string.catalogue_load_failed))
                }
            }
        }
    }

    private fun showHomeRows(movies: List<Movie>) {
        selectedTag = null
        tagBackCallback?.isEnabled = false
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val tags = MovieList.tagsFor(movies)
        val tagAdapter = ArrayObjectAdapter(TagCardPresenter())
        if (tags.isEmpty()) {
            tagAdapter.add(VideoTag(getString(R.string.no_video_tags), 0))
        } else {
            tags.forEach(tagAdapter::add)
        }
        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.videos_row)), tagAdapter))

        val youtubeMovies = movies.filter(::isYouTubeVideo)
        if (youtubeMovies.isNotEmpty()) {
            addMovieRow(rowsAdapter, 1, getString(R.string.youtube_row), youtubeMovies)
        }

        val otherMovies = movies.filterNot(::isYouTubeVideo)
        if (otherMovies.isNotEmpty()) {
            addMovieRow(rowsAdapter, 2, getString(R.string.other_videos_row), otherMovies)
        }
        addAppSettingsRow(rowsAdapter)
        adapter = rowsAdapter
    }

    private fun showTagVideos(tag: String) {
        val taggedMovies = MovieList.videosForTag(catalogueMovies, tag)
        if (taggedMovies.isEmpty()) return

        selectedTag = tag
        tagBackCallback?.isEnabled = true
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        addMovieRow(rowsAdapter, 0, getString(R.string.tagged_videos_row, tag), taggedMovies)
        adapter = rowsAdapter
    }

    private fun showSearchResults(movies: List<Movie>, query: String) {
        selectedTag = null
        tagBackCallback?.isEnabled = false
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        addMovieRow(rowsAdapter, 0, getString(R.string.search_results, query), movies)
        addAppSettingsRow(rowsAdapter, 1)
        adapter = rowsAdapter
    }

    fun returnToHome(): Boolean {
        if (selectedTag == null) return false

        selectedTag = null
        tagBackCallback?.isEnabled = false
        showHomeRows(catalogueMovies)
        return true
    }

    private fun addMovieRow(
        rowsAdapter: ArrayObjectAdapter,
        rowId: Long,
        title: String,
        movies: List<Movie>
    ) {
        val rowAdapter = ArrayObjectAdapter(CardPresenter())
        movies.forEach(rowAdapter::add)
        rowsAdapter.add(ListRow(HeaderItem(rowId, title), rowAdapter))
    }

    private fun addAppSettingsRow(rowsAdapter: ArrayObjectAdapter, rowId: Long = 3) {
        val settingsAdapter = ArrayObjectAdapter(AppSettingsCardPresenter())
        settingsAdapter.add(
            AppSettingsCard(
                getString(R.string.app_update_card_title),
                getString(R.string.app_update_card_description),
            ),
        )
        rowsAdapter.add(
            ListRow(
                HeaderItem(rowId, getString(R.string.app_settings_row)),
                settingsAdapter,
            ),
        )
    }

    private fun isYouTubeVideo(movie: Movie): Boolean =
        movie.provider.equals("youtube", ignoreCase = true) ||
            movie.sourceType.equals("youtube", ignoreCase = true)

    private fun showLoadError(message: String) {
        val fragmentManager = activity?.supportFragmentManager ?: return
        val errorFragment = ErrorFragment()
        fragmentManager.beginTransaction()
            .add(R.id.main_browse_fragment, errorFragment)
            .commitNow()
        errorFragment.setErrorContent(message)
    }

    private fun setupEventListeners() {
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                returnToHome()
            }
        }
        tagBackCallback = backCallback
        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, backCallback)

        setOnSearchClickedListener {
            showSearchDialog()
        }

        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private fun showSearchDialog() {
        val searchField = EditText(requireContext()).apply {
            hint = getString(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            setText(currentSearchQuery)
            setSelection(text.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.search_videos)
            .setView(searchField)
            .setPositiveButton(R.string.search_action) { _, _ ->
                val query = searchField.text.toString().trim()
                if (query.isBlank()) {
                    currentSearchQuery = ""
                    showHomeRows(catalogueMovies)
                } else {
                    val results = catalogueMovies.filter { movie ->
                        movie.title.orEmpty().contains(query, ignoreCase = true) ||
                            movie.description.orEmpty().contains(query, ignoreCase = true)
                    }
                    if (results.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            R.string.no_search_results,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        currentSearchQuery = query
                        showSearchResults(results, query)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {

            if (item is AppSettingsCard) {
                startActivityForResult(
                    Intent(context!!, AppUpdateActivity::class.java),
                    CATALOGUE_REFRESH_REQUEST,
                )
            } else if (item is Movie) {
                Log.d(TAG, "Item: " + item.toString())
                val intent = Intent(context!!, DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.MOVIE, item)

                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity!!,
                    (itemViewHolder.view as ImageCardView).mainImageView!!,
                    DetailsActivity.SHARED_ELEMENT_NAME
                )
                    .toBundle()
                startActivity(intent, bundle)
            } else if (item is VideoTag) {
                if (item.videoCount > 0) {
                    showTagVideos(item.name)
                } else {
                    Toast.makeText(context!!, R.string.no_video_tags_hint, Toast.LENGTH_SHORT).show()
                }
            } else if (item is String) {
                if (item.contains(getString(R.string.error_fragment))) {
                    val intent = Intent(context!!, BrowseErrorActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(context!!, item, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            if (item is Movie) {
                val selectedTitle = item.title?.takeIf(String::isNotBlank)
                selectedVideoTitleView?.apply {
                    text = selectedTitle.orEmpty()
                    visibility = if (selectedTitle == null) View.GONE else View.VISIBLE
                }
                mBackgroundUri = item.backgroundImageUrl
                startBackgroundTimer()
            } else {
                selectedVideoTitleView?.visibility = View.GONE
            }
        }
    }

    private fun updateBackground(uri: String?) {
        val width = mMetrics.widthPixels
        val height = mMetrics.heightPixels
        Glide.with(context!!)
            .load(uri)
            .centerCrop()
            .error(mDefaultBackground)
            .into(
                object : CustomTarget<Drawable>(width, height) {
                    override fun onResourceReady(
                        drawable: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        mBackgroundManager.drawable = drawable
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Unused
                    }
                })
        mBackgroundTimer?.cancel()
    }

    private fun startBackgroundTimer() {
        mBackgroundTimer?.cancel()
        mBackgroundTimer = Timer()
        mBackgroundTimer?.schedule(UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY.toLong())
    }

    private inner class UpdateBackgroundTask : TimerTask() {

        override fun run() {
            mHandler.post { updateBackground(mBackgroundUri) }
        }
    }

    companion object {
        private val TAG = "MainFragment"
        private const val CATALOGUE_REFRESH_REQUEST = 4101

        private val BACKGROUND_UPDATE_DELAY = 300
    }

    @Deprecated("Use Activity Result APIs when this screen is modernized.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CATALOGUE_REFRESH_REQUEST && resultCode == Activity.RESULT_OK) {
            loadRows()
        }
    }
}