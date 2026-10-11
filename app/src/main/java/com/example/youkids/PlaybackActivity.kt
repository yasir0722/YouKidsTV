package com.example.youkids

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide

/** Loads [PlaybackVideoFragment]. */
class PlaybackActivity : FragmentActivity() {
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val playbackPreferences by lazy { PlaybackPreferences(applicationContext) }
    private var youtubeWebView: WebView? = null
    private var youtubeControls: View? = null
    private var youtubeProgress: ProgressBar? = null
    private var youtubeTime: TextView? = null
    private var youtubeSpeed: TextView? = null
    private var youtubeStatus: TextView? = null
    private var youtubeIsPlaying = false
    private var youtubePlayerReady = false
    private var pendingSeekSeconds = 0L
    private var lastPlayerState: Int? = null
    private var screenTimerView: TextView? = null
    private var screenTimerRemainingMillis = 0L
    private var screenTimerLastTick = 0L
    private var screenTimerRunning = false
    private var playbackPausedForScreenTimer = false
    private var currentMovie: Movie? = null
    private var controlsShownByDown = false
    private var videoListOverlay: View? = null

    private val screenTimerTask = object : Runnable {
        override fun run() {
            if (!screenTimerRunning) return
            val now = SystemClock.elapsedRealtime()
            val previousRemainingMillis = screenTimerRemainingMillis
            screenTimerRemainingMillis -= now - screenTimerLastTick
            screenTimerLastTick = now
            showScreenTimerWarningIfNeeded(previousRemainingMillis)
            if (screenTimerRemainingMillis <= 0L) {
                screenTimerRemainingMillis = 0L
                screenTimerRunning = false
                playbackPreferences.saveScreenTimerRemainingMillis(0L)
                updateScreenTimerLabel()
                pausePlaybackForScreenTimer()
                showScreenTimerDialog()
            } else {
                updateScreenTimerLabel()
                controlsHandler.postDelayed(this, SCREEN_TIMER_TICK_MS)
            }
        }
    }

    private val hideControlsTask = Runnable {
        youtubeControls?.visibility = View.GONE
        controlsShownByDown = false
    }

    private val updatePlayerStatusTask = object : Runnable {
        override fun run() {
            val webView = youtubeWebView ?: return
            webView.evaluateJavascript(PLAYER_STATUS_SCRIPT) { result ->
                updateYoutubePlaybackStatus(result)
            }
            controlsHandler.postDelayed(this, PLAYER_STATUS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val movie = movieFromIntent()
        if (movie == null) {
            Toast.makeText(this, R.string.playback_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentMovie = movie

        if (movie.provider == YOUTUBE_PROVIDER) {
            showYoutubePlayer(movie)
        } else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, PlaybackVideoFragment())
                .commitNow()
            addScreenTimerOverlay()
        } else {
            addScreenTimerOverlay()
        }
        initializeScreenTimer()
    }

    override fun onPause() {
        controlsHandler.removeCallbacks(updatePlayerStatusTask)
        controlsHandler.removeCallbacks(hideControlsTask)
        pauseScreenTimer()
        youtubeWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumeScreenTimer()
        youtubeWebView?.onResume()
        if (youtubeWebView != null) {
            controlsHandler.removeCallbacks(updatePlayerStatusTask)
            controlsHandler.post(updatePlayerStatusTask)
        }
    }

    override fun onDestroy() {
        controlsHandler.removeCallbacks(updatePlayerStatusTask)
        controlsHandler.removeCallbacks(hideControlsTask)
        controlsHandler.removeCallbacks(screenTimerTask)
        videoListOverlay = null
        youtubeWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            webChromeClient = null
            destroy()
        }
        youtubeWebView = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (videoListOverlay != null) {
            hideVideoList()
            return
        }
        if (youtubeControls?.visibility == View.VISIBLE) {
            hideYoutubeControls()
            youtubeWebView?.requestFocus()
            return
        }
        super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (videoListOverlay != null) return super.dispatchKeyEvent(event)
        if (handleYoutubeRemoteKey(event.keyCode, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun movieFromIntent(): Movie? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(DetailsActivity.MOVIE, Movie::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
        }

    private fun showYoutubePlayer(movie: Movie) {
        val videoId = movie.youtubeVideoId
            ?.takeIf { YOUTUBE_ID.matches(it) }
        if (videoId == null) {
            Toast.makeText(this, R.string.playback_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_youtube_playback)
        val webView = findViewById<WebView>(R.id.youtube_web_view)
        youtubeWebView = webView
        youtubeControls = findViewById(R.id.youtube_controls)
        screenTimerView = findViewById(R.id.screen_timer)
        youtubeProgress = findViewById(R.id.youtube_progress)
        youtubeTime = findViewById(R.id.youtube_time)
        youtubeStatus = findViewById(R.id.youtube_status)
        youtubeSpeed = findViewById(R.id.youtube_speed)
        findViewById<TextView>(R.id.youtube_title).text = movie.title.orEmpty()
        updateYoutubeSpeedLabel(playbackPreferences.playbackSpeed)

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.setOnKeyListener { _, keyCode, event ->
            handleYoutubeRemoteKey(keyCode, event)
        }
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        showYoutubeControls()
        webView.requestFocus(View.FOCUS_DOWN)
        webView.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            youtubePlayerHtml(videoId),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun addScreenTimerOverlay() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val timer = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundColor(getColor(R.color.playback_controls_background))
            setPadding(14, 8, 14, 8)
        }
        content.addView(
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
        screenTimerView = timer
    }

    private fun initializeScreenTimer() {
        screenTimerRemainingMillis = playbackPreferences.beginScreenTimerSession()
        updateScreenTimerLabel()
        resumeScreenTimer()
    }

    private fun pauseScreenTimer() {
        if (!screenTimerRunning) return
        val now = SystemClock.elapsedRealtime()
        screenTimerRemainingMillis -= now - screenTimerLastTick
        screenTimerRunning = false
        playbackPreferences.saveScreenTimerRemainingMillis(screenTimerRemainingMillis)
        controlsHandler.removeCallbacks(screenTimerTask)
    }

    private fun resumeScreenTimer() {
        if (screenTimerRemainingMillis <= 0L || screenTimerRunning) return
        screenTimerRunning = true
        screenTimerLastTick = SystemClock.elapsedRealtime()
        controlsHandler.removeCallbacks(screenTimerTask)
        controlsHandler.post(screenTimerTask)
    }

    private fun updateScreenTimerLabel() {
        val totalSeconds = (screenTimerRemainingMillis / MILLIS_PER_SECOND).coerceAtLeast(0L)
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        screenTimerView?.text = getString(
            R.string.screen_timer_remaining,
            DateFormat.getTimeFormat(this).format(System.currentTimeMillis()),
            "%d:%02d".format(minutes, seconds),
        )
    }

    private fun showScreenTimerDialog() {
        if (isFinishing || isDestroyed) return
        ScreenTimerExpiryDialog.show(this) { minutes ->
            screenTimerRemainingMillis = minutes * MILLIS_PER_MINUTE
            playbackPreferences.saveScreenTimerRemainingMillis(screenTimerRemainingMillis)
            updateScreenTimerLabel()
            resumePlaybackAfterScreenTimer()
            resumeScreenTimer()
        }
    }

    private fun pausePlaybackForScreenTimer() {
        playbackPausedForScreenTimer = if (youtubeWebView != null) {
            youtubeIsPlaying.also { if (it) pauseYoutube() }
        } else {
            (supportFragmentManager.findFragmentById(android.R.id.content) as? PlaybackVideoFragment)
                ?.pausePlayback() == true
        }
    }

    private fun showScreenTimerWarningIfNeeded(previousRemainingMillis: Long) {
        listOf(5, 3, 1).forEach { minutes ->
            val thresholdMillis = minutes * MILLIS_PER_MINUTE
            if (previousRemainingMillis > thresholdMillis &&
                screenTimerRemainingMillis <= thresholdMillis
            ) {
                ScreenTimerWarning.show(this, minutes)
            }
        }
    }

    private fun resumePlaybackAfterScreenTimer() {
        if (!playbackPausedForScreenTimer) return
        playbackPausedForScreenTimer = false
        if (youtubeWebView != null) {
            playYoutube()
        } else {
            (supportFragmentManager.findFragmentById(android.R.id.content) as? PlaybackVideoFragment)
                ?.resumePlayback()
        }
    }

    private fun handleYoutubeRemoteKey(keyCode: Int, event: KeyEvent): Boolean {
        if (youtubeWebView == null) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (event.repeatCount == 0) toggleYoutubePlayback()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    if (event.repeatCount == 0) playYoutube()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (event.repeatCount == 0) pauseYoutube()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (event.repeatCount == 0) seekYoutubeBy(-SEEK_SECONDS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (event.repeatCount == 0) seekYoutubeBy(SEEK_SECONDS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.repeatCount == 0) hideYoutubeControls()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.repeatCount == 0) {
                        if (youtubeControls?.visibility == View.VISIBLE && controlsShownByDown) {
                            showVideoList()
                        } else {
                            controlsShownByDown = true
                            showYoutubeControls()
                        }
                    }
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP && isYoutubeControlKey(keyCode)) {
            return true
        }

        return false
    }

    private fun isYoutubeControlKey(keyCode: Int): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> true
            else -> false
        }

    private fun toggleYoutubePlayback() {
        showYoutubeControls()
        youtubeWebView?.evaluateJavascript(
            """
            (function() {
              if (!window.player) return;
              var state = window.player.getPlayerState();
              if (state === 1 || state === 3) window.player.pauseVideo();
              else window.player.playVideo();
            })();
            """.trimIndent(),
            null
        )
    }

    private fun playYoutube() {
        showYoutubeControls()
        youtubeWebView?.evaluateJavascript("window.player && window.player.playVideo()", null)
    }

    private fun pauseYoutube() {
        showYoutubeControls()
        youtubeWebView?.evaluateJavascript("window.player && window.player.pauseVideo()", null)
    }

    private fun seekYoutubeBy(seconds: Long) {
        showYoutubeControls()
        if (!youtubePlayerReady) {
            pendingSeekSeconds += seconds
            return
        }

        performYoutubeSeek(seconds)
    }

    private fun performYoutubeSeek(seconds: Long) {
        youtubeWebView?.evaluateJavascript(
            """
            if (window.player) {
              var target = window.player.getCurrentTime() + ($seconds);
              window.player.seekTo(Math.max(0, Math.min(window.player.getDuration(), target)), true);
            }
            """.trimIndent(),
            null
        )
    }

    private fun updateYoutubePlaybackStatus(result: String?) {
        val values = result
            ?.removeSurrounding("\"")
            ?.split('|')
            ?: return
        if (values.size != STATUS_VALUE_COUNT) return

        val state = values[0].toIntOrNull() ?: return
        val currentTime = values[1].toDoubleOrNull() ?: 0.0
        val duration = values[2].toDoubleOrNull() ?: 0.0
        values[3].toFloatOrNull()?.let(::updateYoutubeSpeedLabel)
        val previousState = lastPlayerState
        lastPlayerState = state
        youtubeIsPlaying = state == PLAYER_STATE_PLAYING

        when (state) {
            PLAYER_STATE_PLAYING -> youtubeStatus?.setText(R.string.youtube_status_playing)
            PLAYER_STATE_PAUSED -> youtubeStatus?.setText(R.string.youtube_status_paused)
            PLAYER_STATE_BUFFERING -> youtubeStatus?.setText(R.string.youtube_status_buffering)
            PLAYER_STATE_ENDED -> youtubeStatus?.setText(R.string.youtube_status_ended)
            else -> youtubeStatus?.setText(R.string.youtube_status_loading)
        }

        if (duration > 0.0) {
            youtubePlayerReady = true
            youtubeTime?.text = "${formatPlaybackTime(currentTime)} / ${formatPlaybackTime(duration)}"
            youtubeProgress?.progress = ((currentTime / duration) * PROGRESS_MAX)
                .toInt()
                .coerceIn(0, PROGRESS_MAX)

            if (pendingSeekSeconds != 0L) {
                val pendingSeek = pendingSeekSeconds
                pendingSeekSeconds = 0
                performYoutubeSeek(pendingSeek)
            }
        }

        if (previousState != state) {
            if (state == PLAYER_STATE_ENDED) {
                showVideoSuggestions()
            } else if (youtubeIsPlaying) {
                scheduleControlsHide()
            } else {
                controlsHandler.removeCallbacks(hideControlsTask)
                youtubeControls?.visibility = View.VISIBLE
            }
        }
    }

    private fun formatPlaybackTime(seconds: Double): String {
        val totalSeconds = seconds.toLong().coerceAtLeast(0)
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val remainingSeconds = totalSeconds % SECONDS_PER_MINUTE

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
        } else {
            "%d:%02d".format(minutes, remainingSeconds)
        }
    }

    private fun showYoutubeControls() {
        youtubeControls?.visibility = View.VISIBLE
        if (youtubeIsPlaying) scheduleControlsHide()
    }

    private fun hideYoutubeControls() {
        controlsHandler.removeCallbacks(hideControlsTask)
        youtubeControls?.visibility = View.GONE
        controlsShownByDown = false
    }

    internal fun showVideoSuggestions() {
        if (videoListOverlay != null) return
        showVideoList()
    }

    private fun showVideoList() {
        val currentId = currentMovie?.id
        val otherMovies = MovieList.list.filterNot { it.id == currentId }
        if (otherMovies.isEmpty()) {
            Toast.makeText(this, R.string.playback_no_other_videos, Toast.LENGTH_SHORT).show()
            return
        }

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.playback_controls_background))
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        overlay.addView(TextView(this).apply {
            setText(R.string.playback_video_list_title)
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        otherMovies.forEach { movie ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(
                        getColor(
                            if (focused) R.color.selected_background
                            else R.color.playback_controls_background,
                        ),
                    )
                }
                setOnClickListener {
                    startActivity(
                        Intent(this@PlaybackActivity, PlaybackActivity::class.java)
                            .putExtra(DetailsActivity.MOVIE, movie),
                    )
                    finish()
                }
            }
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(180), dp(100))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this)
                .load(movie.cardImageUrl)
                .placeholder(R.drawable.movie)
                .error(R.drawable.movie)
                .into(image)
            card.addView(image)
            card.addView(TextView(this).apply {
                text = movie.title.orEmpty().ifBlank { getString(R.string.movie) }
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(dp(180), dp(44))
            })
            cards.addView(card, LinearLayout.LayoutParams(dp(192), dp(150)).apply {
                marginEnd = dp(8)
            })
        }
        overlay.addView(
            android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(cards)
            },
        )
        val root = findViewById<ViewGroup>(android.R.id.content)
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                marginStart = dp(28)
                marginEnd = dp(28)
                bottomMargin = dp(190)
            },
        )
        videoListOverlay = overlay
        cards.getChildAt(0)?.requestFocus()
    }

    private fun hideVideoList() {
        videoListOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        videoListOverlay = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun scheduleControlsHide() {
        controlsHandler.removeCallbacks(hideControlsTask)
        if (youtubeControls?.visibility == View.VISIBLE) {
            controlsHandler.postDelayed(hideControlsTask, CONTROLS_HIDE_DELAY_MS)
        }
    }

    private fun youtubePlayerHtml(videoId: String): String {
        val subtitleLanguage = playbackPreferences.subtitleLanguage
        val subtitlesEnabled = subtitleLanguage != PlaybackPreferences.LANGUAGE_OFF
        val quality = playbackPreferences.videoQuality
        val requestedSpeed = playbackPreferences.playbackSpeed
        val captionsSetup = if (subtitlesEnabled) {
            """
            player.loadModule('captions');
            player.setOption('captions', 'track', { languageCode: '$subtitleLanguage' });
            setTimeout(function() {
              var track = player.getOption('captions', 'track');
              if (!track || !track.languageCode ||
                  track.languageCode.toLowerCase().slice(0, 2) !== '$subtitleLanguage') {
                player.unloadModule('captions');
              }
            }, 1500);
            """.trimIndent()
        } else {
            "player.unloadModule('captions');"
        }

        return """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html, body, iframe { width: 100%; height: 100%; margin: 0; border: 0; background: #000; }
            </style>
          </head>
          <body>
            <iframe
              id="youtube-player"
              src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=0&amp;controls=1&amp;disablekb=0&amp;rel=0&amp;playsinline=1&amp;enablejsapi=1&amp;hl=ms&amp;cc_lang_pref=$subtitleLanguage&amp;cc_load_policy=${if (subtitlesEnabled) 1 else 0}&amp;vq=$quality&amp;origin=https%3A%2F%2Fwww.youtube-nocookie.com"
              title="YouTube video player"
              allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
              allowfullscreen
              referrerpolicy="strict-origin-when-cross-origin">
            </iframe>
            <script>
              var player;
              function onYouTubeIframeAPIReady() {
                player = new YT.Player('youtube-player', {
                  events: {
                    onReady: function(event) {
                      var player = event.target;
                      var rates = player.getAvailablePlaybackRates() || [];
                      var requestedSpeed = $requestedSpeed;
                      if (rates.length) {
                        requestedSpeed = rates.reduce(function(best, rate) {
                          return Math.abs(rate - $requestedSpeed) < Math.abs(best - $requestedSpeed)
                            ? rate : best;
                        }, rates[0]);
                      }
                      player.setPlaybackRate(requestedSpeed);
                      player.setPlaybackQuality('$quality');
                      $captionsSetup
                      player.playVideo();
                    }
                  }
                });
              }
            </script>
            <script src="https://www.youtube.com/iframe_api"></script>
          </body>
        </html>
        """.trimIndent()
    }

    private fun updateYoutubeSpeedLabel(speed: Float) {
        youtubeSpeed?.text = getString(R.string.youtube_playback_speed, speed)
    }

    companion object {
        private const val YOUTUBE_PROVIDER = "youtube"
        private const val PLAYER_STATUS_INTERVAL_MS = 500L
        private const val CONTROLS_HIDE_DELAY_MS = 5_000L
        private const val SCREEN_TIMER_TICK_MS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val SEEK_SECONDS = 10L
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3_600L
        private const val PROGRESS_MAX = 1_000
        private const val STATUS_VALUE_COUNT = 4
        private const val PLAYER_STATE_ENDED = 0
        private const val PLAYER_STATE_PLAYING = 1
        private const val PLAYER_STATE_PAUSED = 2
        private const val PLAYER_STATE_BUFFERING = 3
        private const val PLAYER_STATUS_SCRIPT =
            "(function(){if(!window.player||typeof window.player.getPlayerState!=='function')return '';return [window.player.getPlayerState(),window.player.getCurrentTime(),window.player.getDuration(),window.player.getPlaybackRate()].join('|');})()"
        private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}