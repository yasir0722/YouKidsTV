package com.example.youkids

import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

/** Loads [PlaybackVideoFragment]. */
class PlaybackActivity : FragmentActivity() {
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val playbackPreferences by lazy { PlaybackPreferences(applicationContext) }
    private var youtubeWebView: WebView? = null
    private var youtubeControls: View? = null
    private var youtubeProgress: ProgressBar? = null
    private var youtubeTime: TextView? = null
    private var youtubeSpeed: TextView? = null
    private var youtubeSettingsButton: View? = null
    private var youtubeStatus: TextView? = null
    private var youtubeIsPlaying = false
    private var youtubePlayerReady = false
    private var youtubeNativeMenuMode = false
    private var pendingSeekSeconds = 0L
    private var lastPlayerState: Int? = null

    private val hideControlsTask = Runnable {
        youtubeControls?.visibility = View.GONE
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

        if (movie.provider == YOUTUBE_PROVIDER) {
            showYoutubePlayer(movie)
        } else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, PlaybackVideoFragment())
                .commit()
        }
    }

    override fun onPause() {
        controlsHandler.removeCallbacks(updatePlayerStatusTask)
        controlsHandler.removeCallbacks(hideControlsTask)
        youtubeWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        youtubeWebView?.onResume()
        if (youtubeWebView != null) {
            controlsHandler.removeCallbacks(updatePlayerStatusTask)
            controlsHandler.post(updatePlayerStatusTask)
        }
    }

    override fun onDestroy() {
        controlsHandler.removeCallbacks(updatePlayerStatusTask)
        controlsHandler.removeCallbacks(hideControlsTask)
        youtubeWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            webChromeClient = null
            destroy()
        }
        youtubeWebView = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (youtubeNativeMenuMode) {
            if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK) {
                youtubeNativeMenuMode = false
                youtubeWebView?.requestFocus()
                showYoutubeControls()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (youtubeSettingsButton?.hasFocus() == true) return super.dispatchKeyEvent(event)
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
        youtubeProgress = findViewById(R.id.youtube_progress)
        youtubeTime = findViewById(R.id.youtube_time)
        youtubeStatus = findViewById(R.id.youtube_status)
        youtubeSpeed = findViewById(R.id.youtube_speed)
        findViewById<TextView>(R.id.youtube_title).text = movie.title.orEmpty()
        updateYoutubeSpeedLabel(playbackPreferences.playbackSpeed)
        youtubeSettingsButton = findViewById<View>(R.id.youtube_settings_button).apply {
            setOnClickListener {
                youtubeNativeMenuMode = true
                hideYoutubeControls()
                youtubeWebView?.requestFocus()
            }
        }

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
                    if (event.repeatCount == 0) {
                        showYoutubeControls()
                        youtubeSettingsButton?.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.repeatCount == 0) hideYoutubeControls()
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
            if (youtubeIsPlaying) {
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
    }

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