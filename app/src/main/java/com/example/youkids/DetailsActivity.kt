package com.example.youkids

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Details activity class that loads [VideoDetailsFragment] class.
 */
class DetailsActivity : FragmentActivity() {
    private lateinit var screenTimerOverlay: ScreenTimerOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        screenTimerOverlay = ScreenTimerOverlay(this).also(ScreenTimerOverlay::attach)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_fragment, VideoDetailsFragment())
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

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
    }
}