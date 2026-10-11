package com.example.youkids

import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat

class HomeScreenChannelPublisher(private val context: Context) {

    fun publish(movies: List<Movie>) {
        val resolver = context.contentResolver
        val channelId = ensureChannel(resolver)
        val existingPrograms = resolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            arrayOf(TvContractCompat.PreviewPrograms._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }.orEmpty()

        existingPrograms.forEach { programId ->
            resolver.delete(TvContractCompat.buildPreviewProgramUri(programId), null, null)
        }

        movies.take(MAX_PROGRAMS).forEach { movie ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("$DEEP_LINK_BASE/${movie.id}")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
                .setTitle(movie.title ?: context.getString(R.string.app_name))
                .setDescription(movie.description ?: movie.tag)
                .setPosterArtUri(movie.cardImageUrl?.let(Uri::parse))
                .setIntent(intent)
                .build()
            resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
        }
    }

    private fun ensureChannel(resolver: android.content.ContentResolver): Long {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val savedId = preferences.getLong(CHANNEL_ID_KEY, 0L)
        if (savedId != 0L) {
            val existing = resolver.query(
                TvContractCompat.buildChannelUri(savedId),
                arrayOf(TvContractCompat.Channels._ID),
                null,
                null,
                null,
            )
            if (existing?.use { it.moveToFirst() } == true) return savedId
        }

        val channel = PreviewChannel.Builder()
            .setDisplayName(context.getString(R.string.home_screen_channel_name))
            .setAppLinkIntent(Intent(context, MainActivity::class.java))
            .build()
        val channelUri = resolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            channel.toContentValues(),
        ) ?: error("Android TV did not create the YouKids home-screen channel.")
        val channelId = ContentUris.parseId(channelUri)
        preferences.edit().putLong(CHANNEL_ID_KEY, channelId).apply()
        return channelId
    }

    companion object {
        private const val MAX_PROGRAMS = 20
        private const val PREFERENCES_NAME = "home_screen_channel"
        private const val CHANNEL_ID_KEY = "preview_channel_id"
        private const val DEEP_LINK_BASE = "youkids://video"
    }
}
