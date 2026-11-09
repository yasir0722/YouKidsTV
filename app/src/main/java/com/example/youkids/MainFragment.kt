package com.example.youkids

import java.util.Timer
import java.util.TimerTask

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Executors

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {
    private data class CatalogueFocus(
        val movieId: Long?,
        val videoTagName: String?,
        val settingsCard: Boolean,
    )


    private val mHandler = Handler(Looper.getMainLooper())
    private val apiExecutor = Executors.newSingleThreadExecutor()
    private val homeRefreshPolicy = HomeRefreshPolicy()
    @Volatile private var homeForeground = false
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics
    private var selectedVideoTitleView: TextView? = null
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null
    private var catalogueMovies: List<Movie> = emptyList()
    private var selectedTag: String? = null
    private var tagBackCallback: OnBackPressedCallback? = null
    private var selectedMovieId: Long? = null
    private var selectedVideoTagName: String? = null
    private var selectedSettingsCard = false
    private var pendingCatalogueMovies: List<Movie>? = null
    private var pendingCatalogueFetch = false
    private var pendingUpdateRelease: AppReleaseInfo? = null
    private var dismissedUpdateVersionCodeThisLaunch: Long? = null
    private var updateDialog: AlertDialog? = null
    var homeVideoId: Long? = null

    private val homeMaintenanceTask = Runnable { runHomeMaintenance() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        prepareBackgroundManager()

        setupUIElements()

        loadRows()

        setupEventListeners()
    }

    override fun onDestroy() {
        homeForeground = false
        mHandler.removeCallbacks(homeMaintenanceTask)
        updateDialog?.dismiss()
        updateDialog = null
        super.onDestroy()
        Log.d(TAG, "onDestroy: " + mBackgroundTimer?.toString())
        mBackgroundTimer?.cancel()
        apiExecutor.shutdownNow()
    }

    override fun onResume() {
        super.onResume()
        homeForeground = true
        pendingCatalogueMovies?.let { movies ->
            pendingCatalogueMovies = null
            pendingCatalogueFetch = false
            applyCatalogue(movies, currentCatalogueFocus())
        }
        if (pendingCatalogueFetch) {
            pendingCatalogueFetch = false
            loadRows(preserveFocus = true)
        }
        pendingUpdateRelease?.let { release ->
            pendingUpdateRelease = null
            showUpdatePrompt(release)
        }
        runHomeMaintenance()
    }

    override fun onPause() {
        homeForeground = false
        mHandler.removeCallbacks(homeMaintenanceTask)
        super.onPause()
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
        hideSearchAffordance()
    }

    private fun hideSearchAffordance() {
        getTitleViewAdapter()?.getSearchAffordanceView()?.visibility = View.GONE
    }

    private fun loadRows(preserveFocus: Boolean = false) {
        val focusToRestore = if (preserveFocus) currentCatalogueFocus() else null
        apiExecutor.execute {
            try {
                val movies = VideoLibraryRepository().fetchVideos()
                mHandler.post {
                    if (!isAdded) return@post
                    applyCatalogue(movies, focusToRestore)
                    openHomeVideo(catalogueMovies)
                }

            } catch (exception: IOException) {
                Log.e(TAG, "Unable to load the video catalogue", exception)
                mHandler.post {
                    if (isAdded) showLoadError(getString(R.string.catalogue_load_failed))
                }
            }

        }
    }

    private fun applyCatalogue(
        movies: List<Movie>,
        focusToRestore: CatalogueFocus? = null,
    ) {
        val randomizedMovies = movies.shuffled()
        catalogueMovies = randomizedMovies
        MovieList.replace(randomizedMovies)
        publishHomeScreenChannel(randomizedMovies)

        if (randomizedMovies.isEmpty()) {
            showLoadError(getString(R.string.empty_catalogue))
            return
        }

        val activeTag = selectedTag
        if (activeTag != null &&
            MovieList.videosForTag(randomizedMovies, activeTag).isNotEmpty()
        ) {
            showTagVideos(activeTag, focusToRestore)
        } else {
            selectedTag = null
            tagBackCallback?.isEnabled = false
            showHomeRows(randomizedMovies, focusToRestore)
        }
    }

    private fun publishHomeScreenChannel(movies: List<Movie>) {
        try {
            HomeScreenChannelPublisher(requireContext().applicationContext).publish(movies)
        } catch (exception: SecurityException) {
            Log.e(TAG, "YouKids is not allowed to publish a home-screen channel", exception)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "The home-screen channel data is invalid", exception)
        }
    }

    private fun currentCatalogueFocus(): CatalogueFocus =
        CatalogueFocus(
            movieId = selectedMovieId,
            videoTagName = selectedVideoTagName,
            settingsCard = selectedSettingsCard,
        )

    private fun hasCatalogueChanged(previous: List<Movie>, latest: List<Movie>): Boolean =
        previous.size != latest.size ||
            previous.associateBy(Movie::id) != latest.associateBy(Movie::id)

    private fun runHomeMaintenance() {
        if (!homeForeground || !isAdded) return

        val now = SystemClock.elapsedRealtime()
        if (homeRefreshPolicy.tryStartUpdateCheck(now)) {
            checkForUpdatesInBackground()
        }
        if (homeRefreshPolicy.tryStartCatalogueSync(now)) {
            syncCatalogueInBackground()
        }
        scheduleHomeMaintenance()
    }

    private fun scheduleHomeMaintenance() {
        mHandler.removeCallbacks(homeMaintenanceTask)
        if (!homeForeground || !isAdded) return

        val now = SystemClock.elapsedRealtime()
        val delayMs = minOf(
            homeRefreshPolicy.updateCheckDelayMs(now),
            homeRefreshPolicy.catalogueSyncDelayMs(now),
        ).coerceAtLeast(MIN_HOME_MAINTENANCE_DELAY_MS)
        mHandler.postDelayed(homeMaintenanceTask, delayMs)
    }

    private fun checkForUpdatesInBackground() {
        val appContext = requireContext().applicationContext
        try {
            apiExecutor.execute {
                if (!homeForeground) {
                    mHandler.post {
                        homeRefreshPolicy.cancelUpdateCheck()
                        scheduleHomeMaintenance()
                    }
                    return@execute
                }

                try {
                    val release = AppUpdateRepository(appContext).latestRelease()
                    mHandler.post {
                        homeRefreshPolicy.finishUpdateCheck()
                        if (!isAdded) return@post
                        if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
                            if (homeForeground) {
                                showUpdatePrompt(release)
                            } else {
                                pendingUpdateRelease = release
                            }
                        }
                        scheduleHomeMaintenance()
                    }
                } catch (exception: IOException) {
                    Log.e(TAG, "Unable to check for app updates automatically", exception)
                    mHandler.post {
                        homeRefreshPolicy.finishUpdateCheck()
                        scheduleHomeMaintenance()
                    }
                }
            }
        } catch (exception: RejectedExecutionException) {
            homeRefreshPolicy.cancelUpdateCheck()
            Log.e(TAG, "Unable to queue the automatic app update check", exception)
        }
    }

    private fun syncCatalogueInBackground() {
        val appContext = requireContext().applicationContext
        try {
            apiExecutor.execute {
                if (!homeForeground) {
                    mHandler.post {
                        homeRefreshPolicy.cancelCatalogueSync()
                        scheduleHomeMaintenance()
                    }
                    return@execute
                }

                try {
                    val result = AppUpdateRepository(appContext).refreshCatalogue()
                    val catalogueChanged =
                        result.created > 0 || result.updated > 0 || result.deleted > 0
                    if (catalogueChanged && !homeForeground) {
                        mHandler.post {
                            homeRefreshPolicy.finishCatalogueSync()
                            if (isAdded) pendingCatalogueFetch = true
                            scheduleHomeMaintenance()
                        }
                        return@execute
                    }
                    val movies = if (catalogueChanged) {
                        VideoLibraryRepository().fetchVideos()
                    } else {
                        null
                    }
                    mHandler.post {
                        homeRefreshPolicy.finishCatalogueSync()
                        if (!isAdded) return@post
                        if (movies != null && hasCatalogueChanged(catalogueMovies, movies)) {
                            if (homeForeground) {
                                applyCatalogue(movies, currentCatalogueFocus())
                            } else {
                                pendingCatalogueMovies = movies
                            }
                        }
                        scheduleHomeMaintenance()
                    }
                } catch (exception: CatalogueRefreshRateLimitedException) {
                    Log.i(
                        TAG,
                        "Automatic catalogue sync is rate-limited for ${exception.retryAfterSeconds} seconds",
                    )
                    mHandler.post {
                        homeRefreshPolicy.finishCatalogueSync()
                        scheduleHomeMaintenance()
                    }
                } catch (exception: IOException) {
                    Log.e(TAG, "Unable to sync the video catalogue automatically", exception)
                    mHandler.post {
                        homeRefreshPolicy.finishCatalogueSync()
                        scheduleHomeMaintenance()
                    }
                }
            }
        } catch (exception: RejectedExecutionException) {
            homeRefreshPolicy.cancelCatalogueSync()
            Log.e(TAG, "Unable to queue the automatic catalogue sync", exception)
        }
    }

    private fun showUpdatePrompt(release: AppReleaseInfo) {
        if (!isAdded ||
            !homeForeground ||
            !isResumed ||
            release.versionCode <= BuildConfig.VERSION_CODE ||
            dismissedUpdateVersionCodeThisLaunch == release.versionCode ||
            updateDialog?.isShowing == true
        ) {
            return
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.app_update_prompt_title)
            .setMessage(getString(R.string.app_update_prompt_message, release.versionName))
            .setPositiveButton(R.string.app_update_prompt_now) { _, _ ->
                dismissedUpdateVersionCodeThisLaunch = release.versionCode
                updateDialog = null
                startActivity(
                    Intent(requireContext(), AppUpdateActivity::class.java)
                        .putExtra(AppUpdateActivity.EXTRA_AUTO_DOWNLOAD_UPDATE, true),
                )
            }
            .setNegativeButton(R.string.app_update_prompt_later) { _, _ ->
                dismissedUpdateVersionCodeThisLaunch = release.versionCode
                updateDialog = null
            }
            .setOnCancelListener {
                dismissedUpdateVersionCodeThisLaunch = release.versionCode
                updateDialog = null
            }
            .create()
        updateDialog = dialog
        dialog.setOnDismissListener {
            if (updateDialog === dialog) updateDialog = null
        }
        dialog.show()
    }

    private fun applyUpdatedCatalogue(movies: List<Movie>) {
        applyCatalogue(movies, currentCatalogueFocus())
    }

    private fun openHomeVideo(movies: List<Movie>) {
        val videoId = homeVideoId ?: return
        homeVideoId = null
        val movie = movies.firstOrNull { it.id == videoId } ?: return
        startActivity(
            Intent(requireContext(), PlaybackActivity::class.java)
                .putExtra(DetailsActivity.MOVIE, movie),
        )
    }

    private fun showHomeRows(
        movies: List<Movie>,
        focusToRestore: CatalogueFocus? = null,
    ) {
        selectedTag = null
        tagBackCallback?.isEnabled = false
        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, ListRowPresenter())
            addClassPresenter(YouTubeGridRow::class.java, YouTubeGridRowPresenter())
        }
        val rowsAdapter = ArrayObjectAdapter(presenterSelector)
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
            rowsAdapter.add(
                YouTubeGridRow(
                    HeaderItem(1, getString(R.string.youtube_row)),
                    youtubeMovies,
                ),
            )
        }

        val otherMovies = movies.filterNot(::isYouTubeVideo)
        if (otherMovies.isNotEmpty()) {
            addMovieRow(
                rowsAdapter,
                if (youtubeMovies.isNotEmpty()) 2 else 1,
                getString(R.string.other_videos_row),
                otherMovies,
            )
        }
        addAppSettingsRow(rowsAdapter)
        adapter = rowsAdapter
        if (focusToRestore != null) {
            restoreHomeFocus(movies, focusToRestore)
        } else if (youtubeMovies.isNotEmpty()) {
            restoreGridFocus(1, 0)
        }
    }

    private fun showTagVideos(
        tag: String,
        focusToRestore: CatalogueFocus? = null,
    ) {
        val taggedMovies = MovieList.videosForTag(catalogueMovies, tag)
        if (taggedMovies.isEmpty()) return

        selectedTag = tag
        tagBackCallback?.isEnabled = true
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        addMovieRow(rowsAdapter, 0, getString(R.string.tagged_videos_row, tag), taggedMovies)
        adapter = rowsAdapter
        focusToRestore?.movieId?.let { movieId ->
            val itemPosition = taggedMovies.indexOfFirst { it.id == movieId }
                .takeIf { it >= 0 }
                ?: 0
            restoreGridFocus(0, itemPosition)
        }
    }

    private fun restoreHomeFocus(movies: List<Movie>, focus: CatalogueFocus) {
        val tags = MovieList.tagsFor(movies)
        val youtubeMovies = movies.filter(::isYouTubeVideo)
        val otherMovies = movies.filterNot(::isYouTubeVideo)
        val position = when {
            focus.movieId != null -> {
                val youtubeIndex = youtubeMovies.indexOfFirst { it.id == focus.movieId }
                if (youtubeIndex >= 0) {
                    1 to youtubeIndex
                } else {
                    val otherIndex = otherMovies.indexOfFirst { it.id == focus.movieId }
                    if (otherIndex >= 0) {
                        (if (youtubeMovies.isNotEmpty()) 2 else 1) to otherIndex
                    } else {
                        null
                    }
                }
            }
            focus.videoTagName != null -> {
                0 to tags.indexOfFirst { it.name == focus.videoTagName }.coerceAtLeast(0)
            }
            focus.settingsCard -> {
                (1 + (if (youtubeMovies.isNotEmpty()) 1 else 0) +
                    (if (otherMovies.isNotEmpty()) 1 else 0)) to 0
            }
            else -> null
        } ?: (0 to 0)

        restoreGridFocus(position.first, position.second)
    }

    private fun restoreGridFocus(rowPosition: Int, itemPosition: Int) {
        mHandler.post {
            if (!isAdded || !homeForeground) return@post
            setSelectedPosition(rowPosition, false, object : Presenter.ViewHolderTask() {
                override fun run(viewHolder: Presenter.ViewHolder) {
                    val gridView = when (viewHolder) {
                        is ListRowPresenter.ViewHolder -> viewHolder.gridView
                        else -> viewHolder.view as? HorizontalGridView
                    }
                    gridView?.setSelectedPosition(itemPosition)
                }
            })
        }
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

        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
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
                val intent = Intent(context!!, PlaybackActivity::class.java)
                intent.putExtra(DetailsActivity.MOVIE, item)
                startActivity(intent)
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
            hideSearchAffordance()
            selectedMovieId = (item as? Movie)?.id
            selectedVideoTagName = (item as? VideoTag)?.name
            selectedSettingsCard = item is AppSettingsCard
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
        private const val MIN_HOME_MAINTENANCE_DELAY_MS = 1_000L

        private val BACKGROUND_UPDATE_DELAY = 300
    }

    @Deprecated("Use Activity Result APIs when this screen is modernized.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CATALOGUE_REFRESH_REQUEST && resultCode == Activity.RESULT_OK) {
            homeRefreshPolicy.recordManualCatalogueSync(SystemClock.elapsedRealtime())
            loadRows(preserveFocus = true)
        }
    }
}