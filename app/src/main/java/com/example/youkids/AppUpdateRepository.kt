package com.example.youkids

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

data class AppReleaseInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val fileSize: Long,
    val downloadUrl: String,
)

class AppUpdateRepository(private val context: Context) {

    fun refreshCatalogue() {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        val connection = trustedApiConnection(URL("$baseUrl/public/videos/refresh"))
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IOException("Afterlight catalogue refresh failed ($statusCode).")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun latestRelease(): AppReleaseInfo? {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        val connection = trustedConnection(URL("$baseUrl/public/app-updates/youkids"))

        try {
            val body = readSuccessfulResponse(connection)
            val data = try {
                JSONObject(body).optJSONObject("data")
            } catch (exception: JSONException) {
                throw IOException("Afterlight returned invalid update information.", exception)
            } ?: return null

            val release = try {
                AppReleaseInfo(
                    packageName = data.getString("package_name"),
                    versionCode = data.getLong("version_code"),
                    versionName = data.getString("version_name"),
                    sha256 = data.getString("sha256"),
                    fileSize = data.getLong("file_size"),
                    downloadUrl = data.getString("download_url"),
                )
            } catch (exception: JSONException) {
                throw IOException("Afterlight returned incomplete update information.", exception)
            }

            if (release.packageName != BuildConfig.APPLICATION_ID ||
                release.versionCode < 1 ||
                release.versionName.isBlank() ||
                !SHA256_PATTERN.matches(release.sha256) ||
                release.fileSize !in 1..MAX_APK_SIZE_BYTES
            ) {
                throw IOException("Afterlight returned invalid update information.")
            }

            return release
        } finally {
            connection.disconnect()
        }
    }

    fun downloadAndVerify(release: AppReleaseInfo): File {
        val connection = trustedConnection(URL(release.downloadUrl))
        val updateDirectory = File(context.cacheDir, APK_DIRECTORY)
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            connection.disconnect()
            throw IOException("Unable to prepare storage for the update.")
        }

        val apkFile = File(updateDirectory, "youkids-${release.versionCode}.apk")
        var verified = false

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("APK download failed ($responseCode).")
            }
            if (connection.contentLengthLong >= 0 &&
                connection.contentLengthLong != release.fileSize
            ) {
                throw IOException("The downloaded APK size does not match Afterlight.")
            }

            val digest = sha256Digest()
            var downloadedBytes = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(apkFile).buffered().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        downloadedBytes += count
                        if (downloadedBytes > release.fileSize ||
                            downloadedBytes > MAX_APK_SIZE_BYTES
                        ) {
                            throw IOException("The APK download exceeded its expected size.")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }

            if (downloadedBytes != release.fileSize) {
                throw IOException("The downloaded APK is incomplete.")
            }

            val actualSha256 = digest.digest().joinToString("") {
                "%02x".format(Locale.ROOT, it.toInt() and 0xff)
            }
            if (!actualSha256.equals(release.sha256, ignoreCase = true)) {
                throw IOException("The downloaded APK checksum does not match Afterlight.")
            }

            verifyPackage(apkFile, release)
            verified = true
            return apkFile
        } finally {
            connection.disconnect()
            if (!verified) apkFile.delete()
        }
    }

    private fun verifyPackage(apkFile: File, release: AppReleaseInfo) {
        val archive = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.PackageInfoFlags.of(0),
        ) ?: throw IOException("The downloaded file is not a valid Android package.")

        if (archive.packageName != BuildConfig.APPLICATION_ID ||
            archive.longVersionCode != release.versionCode ||
            archive.versionName != release.versionName
        ) {
            throw IOException("The APK package or version does not match Afterlight.")
        }

        val installedVersionCode = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            .longVersionCode
        if (release.versionCode <= installedVersionCode) {
            throw IOException("This YouKids version is already installed.")
        }
    }

    private fun trustedConnection(url: URL): HttpURLConnection {
        val apiUrl = try {
            URL(BuildConfig.API_BASE_URL)
        } catch (exception: IOException) {
            throw IOException("The configured Afterlight API URL is invalid.", exception)
        }

        if (apiUrl.protocol != "https" ||
            url.protocol != apiUrl.protocol ||
            !url.host.equals(apiUrl.host, ignoreCase = true) ||
            effectivePort(url) != effectivePort(apiUrl)
        ) {
            throw IOException("App updates must be downloaded from the configured HTTPS Afterlight server.")
        }

        val connection = url.openConnection() as? HttpURLConnection
            ?: throw IOException("Afterlight returned an unsupported download URL.")
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
        return connection
    }

    private fun trustedApiConnection(url: URL): HttpURLConnection {
        val apiUrl = URL(BuildConfig.API_BASE_URL)
        if (apiUrl.protocol != "https" ||
            url.protocol != apiUrl.protocol ||
            !url.host.equals(apiUrl.host, ignoreCase = true) ||
            effectivePort(url) != effectivePort(apiUrl)
        ) {
            throw IOException("Requests must be sent to the configured HTTPS Afterlight server.")
        }

        return (url.openConnection() as? HttpURLConnection)?.apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = REFRESH_TIMEOUT_MS
            instanceFollowRedirects = false
        } ?: throw IOException("Afterlight returned an unsupported refresh URL.")
    }

    private fun effectivePort(url: URL): Int =
        if (url.port == -1) url.defaultPort else url.port

    private fun readSuccessfulResponse(connection: HttpURLConnection): String {
        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (statusCode !in 200..299) {
            throw IOException("Afterlight update request failed ($statusCode).")
        }
        return body
    }

    private fun sha256Digest(): MessageDigest =
        try {
            MessageDigest.getInstance("SHA-256")
        } catch (exception: NoSuchAlgorithmException) {
            throw IOException("This device does not support APK checksum verification.", exception)
        }

    companion object {
        private const val APK_DIRECTORY = "apk-updates"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val REFRESH_TIMEOUT_MS = 60_000
        private const val MAX_APK_SIZE_BYTES = 100L * 1024 * 1024
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
    }
}
