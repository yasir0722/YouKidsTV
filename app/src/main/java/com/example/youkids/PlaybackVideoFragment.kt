package com.example.youkids

import android.net.Uri
import android.media.PlaybackParams
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import android.widget.Toast
import java.io.IOException

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<MediaPlayerAdapter>
    private val playbackPreferences by lazy { PlaybackPreferences(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val movie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.intent?.getSerializableExtra(
                DetailsActivity.MOVIE,
                Movie::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
        }

        if (movie == null) {
            showPlaybackError()
            return
        }

        val title = movie.title
        val description = movie.description

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        val playerAdapter = MediaPlayerAdapter(context)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = PlaybackTransportControlGlue(getActivity(), playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = title
        mTransportControlGlue.subtitle = description
        mTransportControlGlue.addPlayerCallback(object : PlaybackGlue.PlayerCallback() {
            override fun onPreparedStateChanged(glue: PlaybackGlue) {
                if (glue.isPrepared) {
                    playerAdapter.mediaPlayer.playbackParams =
                        PlaybackParams().setSpeed(playbackPreferences.playbackSpeed)
                    selectPreferredTracks(playerAdapter.mediaPlayer)
                }
            }
        })

        if (movie.sourceType == "telegram") {
            Thread({
                try {
                    val videoUrl = VideoLibraryRepository().playbackUrl(movie.id)
                    Handler(Looper.getMainLooper()).post {
                        if (!isAdded) return@post
                        playerAdapter.setDataSource(Uri.parse(videoUrl))
                        mTransportControlGlue.playWhenPrepared()
                    }
                } catch (exception: IOException) {
                    android.util.Log.e(TAG, "Unable to get the video playback URL", exception)
                    Handler(Looper.getMainLooper()).post { showPlaybackError() }
                }
            }, "video-playback-url").start()
        } else {
            val videoUrl = movie.videoUrl
            if (videoUrl.isNullOrBlank()) {
                showPlaybackError()
                return
            }
            playerAdapter.setDataSource(Uri.parse(videoUrl))
            mTransportControlGlue.playWhenPrepared()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mTransportControlGlue.isInitialized) {
            mTransportControlGlue.pause()
        }
    }

    private fun showPlaybackError() {
        Toast.makeText(context, R.string.playback_unavailable, Toast.LENGTH_LONG).show()
        activity?.finish()
    }

    private fun selectPreferredTracks(player: MediaPlayer) {
        val tracks = player.trackInfo
        val preferredAudioLanguage = playbackPreferences.audioLanguage
        val preferredSubtitleLanguage = playbackPreferences.subtitleLanguage
        val subtitleTrackTypes = setOf(
            MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT,
            MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE,
        )

        if (preferredAudioLanguage != PlaybackPreferences.LANGUAGE_ORIGINAL) {
            val audioTrack = tracks.indices.firstOrNull { index ->
                tracks[index].trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO &&
                    tracks[index].language.matchesLanguage(preferredAudioLanguage)
            }
            if (audioTrack != null) player.selectTrack(audioTrack)
        }

        tracks.indices
            .filter { tracks[it].trackType in subtitleTrackTypes }
            .forEach(player::deselectTrack)

        if (preferredSubtitleLanguage != PlaybackPreferences.LANGUAGE_OFF) {
            val subtitleTrack = tracks.indices.firstOrNull { index ->
                tracks[index].trackType in subtitleTrackTypes &&
                    tracks[index].language.matchesLanguage(preferredSubtitleLanguage)
            }
            if (subtitleTrack != null) player.selectTrack(subtitleTrack)
        }
    }

    private fun String?.matchesLanguage(language: String): Boolean =
        !this.isNullOrBlank() &&
            (equals(language, ignoreCase = true) ||
                startsWith("$language-", ignoreCase = true) ||
                startsWith("${language}_", ignoreCase = true))

    companion object {
        private const val TAG = "PlaybackVideoFragment"
    }
}