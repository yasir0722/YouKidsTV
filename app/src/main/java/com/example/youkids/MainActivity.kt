package com.example.youkids

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Loads [MainFragment].
 */
class MainActivity : FragmentActivity() {
    private lateinit var screenTimerOverlay: ScreenTimerOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        screenTimerOverlay = ScreenTimerOverlay(this).also(ScreenTimerOverlay::attach)
        if (savedInstanceState == null) {
            val fragment = MainFragment().apply {
                homeVideoId = intent.homeVideoId()
            }
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .commitNow()
        }
    }

    override fun onResume() {
        super.onResume()
        screenTimerOverlay.onResume()
    }

    override fun onPause() {
        screenTimerOverlay.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        screenTimerOverlay.onDestroy()
        super.onDestroy()
    }

    private fun Intent.homeVideoId(): Long? =
        data
            ?.takeIf { it.scheme == "youkids" && it.host == "video" }
            ?.pathSegments
            ?.singleOrNull()
            ?.toLongOrNull()
}