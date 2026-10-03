package com.example.youkids

import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class VideoLibraryRepository {

    fun fetchVideos(): List<Movie> {
        val firstPage = getJson("public/videos?per_page=$PAGE_SIZE&page=1")
        val videos = parseVideos(firstPage.optJSONArray("data"))
        val lastPage = firstPage.optJSONObject("meta")?.optInt("last_page", 1) ?: 1

        for (page in 2..lastPage) {
            videos += parseVideos(getJson("public/videos?per_page=$PAGE_SIZE&page=$page").optJSONArray("data"))
        }

        return videos
    }

    fun playbackUrl(videoId: Long): String {
        val response = getJson("public/videos/$videoId/play-url")
        val path = response.optString("url")
        if (path.isBlank()) {
            throw IOException("The video library returned no playback URL.")
        }

        if (Uri.parse(path).scheme != null) {
            return path
        }

        val apiUri = Uri.parse(BuildConfig.API_BASE_URL)
        val authority = apiUri.encodedAuthority
            ?: throw IOException("The video library API URL is invalid.")
        return "${apiUri.scheme}://$authority$path"
    }

    private fun getJson(path: String): JSONObject {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw IOException("The video library API URL must use HTTP or HTTPS.")
        }

        val connection = URL("$baseUrl/$path").openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")

        try {
            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                val message = try {
                    JSONObject(body).optString("message")
                } catch (_: JSONException) {
                    ""
                }
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Video library request failed ($statusCode).")
            }

            return try {
                JSONObject(body)
            } catch (exception: JSONException) {
                throw IOException("The video library returned invalid JSON.", exception)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVideos(data: JSONArray?): MutableList<Movie> {
        if (data == null) {
            throw IOException("The video library returned an invalid catalogue.")
        }

        return try {
            MutableList(data.length()) { index ->
                val item = data.getJSONObject(index)
                val media = item.optJSONObject("media") ?: JSONObject()
                val sourceType = media.optString("type").takeIf { it.isNotBlank() }
                val provider = media.optString("provider").takeIf { it.isNotBlank() }
                val youtubeId = media.optString("youtube_id").takeIf { it.isNotBlank() }
                val thumbnail = item.optNullableString("thumbnail_url")?.let(::resolveUrl)
                    ?: youtubeId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                val poster = item.optNullableString("poster_url")?.let(::resolveUrl) ?: thumbnail
                val videoUrl = media.optNullableString("url")?.let(::resolveUrl)

                Movie(
                    id = item.optLong("id"),
                    title = item.optString("title").takeIf { it.isNotBlank() },
                    description = item.optString("description").takeIf { it.isNotBlank() },
                    backgroundImageUrl = poster,
                    cardImageUrl = thumbnail,
                    videoUrl = videoUrl,
                    studio = (provider ?: sourceType)?.uppercase(),
                    tag = item.optNullableString("tag"),
                    sourceType = sourceType,
                    provider = provider,
                    youtubeVideoId = youtubeId
                )
            }
        } catch (exception: JSONException) {
            throw IOException("The video library returned an invalid catalogue.", exception)
        }
    }

    private fun resolveUrl(value: String?): String? {
        val path = value?.takeIf { it.isNotBlank() } ?: return null
        return URL(URL(BuildConfig.API_BASE_URL), path).toString()
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    companion object {
        private const val PAGE_SIZE = 100
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
