package com.example.youkids

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class AppUpdateActivity : FragmentActivity() {
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val repository by lazy { AppUpdateRepository(applicationContext) }
    private val playbackPreferences by lazy { PlaybackPreferences(applicationContext) }

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var checkButton: Button
    private lateinit var refreshButton: Button
    private lateinit var installButton: Button
    private lateinit var audioLanguageButton: Button
    private lateinit var playbackSpeedButton: Button
    private lateinit var videoQualityButton: Button
    private lateinit var subtitleLanguageButton: Button
    private var availableRelease: AppReleaseInfo? = null
    private var downloadedApk: File? = null
    private var awaitingInstallPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_update)

        audioLanguageButton = findViewById(R.id.audio_language_button)
        playbackSpeedButton = findViewById(R.id.playback_speed_button)
        videoQualityButton = findViewById(R.id.video_quality_button)
        subtitleLanguageButton = findViewById(R.id.subtitle_language_button)
        audioLanguageButton.setOnClickListener { chooseAudioLanguage() }
        playbackSpeedButton.setOnClickListener { choosePlaybackSpeed() }
        videoQualityButton.setOnClickListener { chooseVideoQuality() }
        subtitleLanguageButton.setOnClickListener { chooseSubtitleLanguage() }
        updatePlaybackPreferenceLabels()

        statusText = findViewById(R.id.app_update_status)
        progressBar = findViewById(R.id.app_update_progress)
        checkButton = findViewById<Button>(R.id.app_update_check_button).apply {
            setOnClickListener { checkForUpdates() }
        }
        refreshButton = findViewById<Button>(R.id.app_refresh_catalogue_button).apply {
            setOnClickListener { refreshCatalogue() }
        }
        installButton = findViewById<Button>(R.id.app_update_install_button).apply {
            visibility = View.GONE
            setOnClickListener {
                val apk = downloadedApk?.takeIf(File::isFile)
                if (apk == null) downloadAndInstall() else installDownloadedApk(apk)
            }
        }

        findViewById<TextView>(R.id.app_update_current_version).text =
            getString(
                R.string.app_update_current_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            )
        checkButton.requestFocus()
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        val apk = downloadedApk
        if (awaitingInstallPermission &&
            packageManager.canRequestPackageInstalls() &&
            apk?.isFile == true
        ) {
            awaitingInstallPermission = false
            installDownloadedApk(apk)
        }
    }

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun checkForUpdates() {
        setBusy(true)
        statusText.setText(R.string.app_update_checking)
        updateExecutor.execute {
            try {
                val release = repository.latestRelease()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setBusy(false)
                    availableRelease = release
                    when {
                        release == null -> {
                            statusText.setText(R.string.app_update_no_release)
                            installButton.visibility = View.GONE
                        }
                        release.versionCode <= BuildConfig.VERSION_CODE -> {
                            statusText.setText(R.string.app_update_up_to_date)
                            installButton.visibility = View.GONE
                        }
                        else -> {
                            statusText.text = getString(
                                R.string.app_update_available,
                                release.versionName,
                                release.versionCode,
                            )
                            installButton.setText(R.string.app_update_download_install)
                            installButton.visibility = View.VISIBLE
                            installButton.requestFocus()
                        }
                    }
                }
            } catch (exception: IOException) {
                android.util.Log.e(TAG, "Unable to check for app updates", exception)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setBusy(false)
                    statusText.text = getString(
                        R.string.app_update_error,
                        exception.message ?: getString(R.string.app_update_unknown_error),
                    )
                    installButton.visibility = View.GONE
                }
            }
        }
    }

    private fun downloadAndInstall() {
        val release = availableRelease
        if (release == null) {
            checkForUpdates()
            return
        }

        setBusy(true)
        statusText.setText(R.string.app_update_downloading)
        updateExecutor.execute {
            try {
                val apk = repository.downloadAndVerify(release)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setBusy(false)
                    downloadedApk = apk
                    installButton.setText(R.string.app_update_install_downloaded)
                    installButton.visibility = View.VISIBLE
                    installDownloadedApk(apk)
                }
            } catch (exception: IOException) {
                android.util.Log.e(TAG, "Unable to download app update", exception)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setBusy(false)
                    statusText.text = getString(
                        R.string.app_update_error,
                        exception.message ?: getString(R.string.app_update_unknown_error),
                    )
                    installButton.visibility = View.VISIBLE
                    installButton.setText(R.string.app_update_retry_download)
                }
            }
        }
    }

    private fun refreshCatalogue() {
        setBusy(true)
        statusText.setText(R.string.app_refresh_catalogue_running)
        updateExecutor.execute {
            try {
                repository.refreshCatalogue()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    statusText.setText(R.string.app_refresh_catalogue_waiting)
                    statusText.postDelayed({
                        if (isFinishing || isDestroyed) return@postDelayed
                        setResult(RESULT_OK)
                        finish()
                    }, REFRESH_SETTLE_DELAY_MS)
                }
            } catch (exception: IOException) {
                android.util.Log.e(TAG, "Unable to refresh the video catalogue", exception)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setBusy(false)
                    statusText.text = getString(
                        R.string.app_refresh_catalogue_error,
                        exception.message ?: getString(R.string.app_update_unknown_error),
                    )
                }
            }
        }
    }

    private fun chooseAudioLanguage() {
        val languages = PlaybackPreferences.audioLanguages
        val labels = languages.map(::audioLanguageLabel)
        showPreferenceChoices(
            title = R.string.playback_audio_language_title,
            labels = labels,
            selectedIndex = languages.indexOf(playbackPreferences.audioLanguage).coerceAtLeast(0),
        ) { index ->
            playbackPreferences.audioLanguage = languages[index]
            updatePlaybackPreferenceLabels()
        }
    }

    private fun choosePlaybackSpeed() {
        val speeds = PlaybackPreferences.speedOptions
        showPreferenceChoices(
            title = R.string.playback_speed_title,
            labels = speeds.map { getString(R.string.youtube_playback_speed, it) },
            selectedIndex = speeds.indexOf(playbackPreferences.playbackSpeed).coerceAtLeast(0),
        ) { index ->
            playbackPreferences.playbackSpeed = speeds[index]
            updatePlaybackPreferenceLabels()
        }
    }

    private fun chooseVideoQuality() {
        val qualities = PlaybackPreferences.videoQualities
        showPreferenceChoices(
            title = R.string.playback_quality_title,
            labels = qualities.map(::videoQualityLabel),
            selectedIndex = qualities.indexOf(playbackPreferences.videoQuality).coerceAtLeast(0),
        ) { index ->
            playbackPreferences.videoQuality = qualities[index]
            updatePlaybackPreferenceLabels()
        }
    }

    private fun chooseSubtitleLanguage() {
        val languages = PlaybackPreferences.subtitleLanguages
        showPreferenceChoices(
            title = R.string.playback_subtitle_title,
            labels = languages.map(::subtitleLanguageLabel),
            selectedIndex = languages.indexOf(playbackPreferences.subtitleLanguage).coerceAtLeast(0),
        ) { index ->
            playbackPreferences.subtitleLanguage = languages[index]
            updatePlaybackPreferenceLabels()
        }
    }

    private fun showPreferenceChoices(
        title: Int,
        labels: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, index ->
                onSelected(index)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updatePlaybackPreferenceLabels() {
        audioLanguageButton.text = getString(
            R.string.playback_audio_language,
            audioLanguageLabel(playbackPreferences.audioLanguage),
        )
        playbackSpeedButton.text = getString(
            R.string.playback_speed_setting,
            playbackPreferences.playbackSpeed,
        )
        videoQualityButton.text = getString(
            R.string.playback_quality_setting,
            videoQualityLabel(playbackPreferences.videoQuality),
        )
        subtitleLanguageButton.text = getString(
            R.string.playback_subtitle_setting,
            subtitleLanguageLabel(playbackPreferences.subtitleLanguage),
        )
    }

    private fun audioLanguageLabel(language: String): String =
        when (language) {
            PlaybackPreferences.LANGUAGE_MALAY -> getString(R.string.playback_language_malay)
            PlaybackPreferences.LANGUAGE_ENGLISH -> getString(R.string.playback_language_english)
            PlaybackPreferences.LANGUAGE_ARABIC -> getString(R.string.playback_language_arabic)
            PlaybackPreferences.LANGUAGE_INDONESIAN -> getString(R.string.playback_language_indonesian)
            else -> getString(R.string.playback_language_original)
        }

    private fun subtitleLanguageLabel(language: String): String =
        if (language == PlaybackPreferences.LANGUAGE_OFF) {
            getString(R.string.playback_language_off)
        } else {
            audioLanguageLabel(language)
        }

    private fun videoQualityLabel(quality: String): String =
        when (quality) {
            PlaybackPreferences.QUALITY_720P -> getString(R.string.playback_quality_720p)
            PlaybackPreferences.QUALITY_480P -> getString(R.string.playback_quality_480p)
            PlaybackPreferences.QUALITY_AUTO -> getString(R.string.playback_quality_auto)
            else -> getString(R.string.playback_quality_1080p)
        }

    private fun installDownloadedApk(apk: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            awaitingInstallPermission = true
            statusText.setText(R.string.app_update_install_permission)
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
            } catch (exception: ActivityNotFoundException) {
                awaitingInstallPermission = false
                statusText.setText(R.string.app_update_install_settings_unavailable)
            }
            return
        }

        awaitingInstallPermission = false
        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "$packageName.apkprovider",
                apk,
            )
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_update_installer_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        checkButton.isEnabled = !busy
        refreshButton.isEnabled = !busy
        installButton.isEnabled = !busy
    }

    companion object {
        private const val TAG = "AppUpdateActivity"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val REFRESH_SETTLE_DELAY_MS = 5_000L
    }
}
