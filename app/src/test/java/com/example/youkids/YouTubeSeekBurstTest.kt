package com.example.youkids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSeekBurstTest {
    @Test
    fun seekStepAcceleratesAfterTheFifthPress() {
        val burst = YouTubeSeekBurst()

        repeat(5) { burst.recordPress(1, it * 100L) }
        assertEquals(50L, burst.consumeOffset())

        repeat(6) { burst.recordPress(1, it * 100L) }
        assertEquals(80L, burst.consumeOffset())
    }

    @Test
    fun seekStepAcceleratesAgainAfterTheTenthPress() {
        val burst = YouTubeSeekBurst()

        repeat(10) { burst.recordPress(1, it * 100L) }
        assertEquals(200L, burst.consumeOffset())

        repeat(11) { burst.recordPress(1, it * 100L) }
        assertEquals(245L, burst.consumeOffset())
    }

    @Test
    fun leftAndRightPressesShareTheSameAccelerationBurst() {
        val burst = YouTubeSeekBurst()

        repeat(5) { burst.recordPress(1, it * 100L) }
        burst.recordPress(-1, 500L)

        assertEquals(20L, burst.consumeOffset())
    }

    @Test
    fun debounceWaitsForSixHundredMillisecondsAndConsumeResetsTheBurst() {
        val burst = YouTubeSeekBurst()
        burst.recordPress(1, 1_000L)

        assertFalse(burst.isReadyToCommit(1_599L))
        assertTrue(burst.isReadyToCommit(1_600L))
        assertEquals(10L, burst.consumeOffset())
        assertFalse(burst.hasPendingPresses)
        assertEquals(0L, burst.millisecondsUntilCommit(1_600L))

        burst.recordPress(1, 1_600L)
        assertEquals(10L, burst.consumeOffset())
    }

    @Test
    fun targetPreviewIsClampedToVideoBoundsWhenDurationIsKnown() {
        val burst = YouTubeSeekBurst()
        burst.recordPress(-1, 0L)
        assertEquals(0.0, burst.previewTarget(5.0, 60.0), 0.0)

        burst.reset()
        burst.recordPress(1, 0L)
        assertEquals(60.0, burst.previewTarget(55.0, 60.0), 0.0)
    }
}
