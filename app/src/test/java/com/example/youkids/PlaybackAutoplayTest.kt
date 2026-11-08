package com.example.youkids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackAutoplayTest {
    @Test
    fun countdownStartsAtFiveAndCompletesOnlyOnceAtDeadline() {
        val countdown = PlaybackAutoplayCountdown()

        assertTrue(countdown.start(1_000L))
        assertFalse(countdown.start(1_500L))
        assertEquals(5, countdown.secondsRemaining(1_000L))
        assertEquals(1, countdown.secondsRemaining(5_999L))
        assertFalse(countdown.completeIfDue(5_999L))
        assertTrue(countdown.completeIfDue(6_000L))
        assertFalse(countdown.completeIfDue(6_000L))
        assertFalse(countdown.isActive)
    }

    @Test
    fun userCancellationStopsCountdownAndAllowsNoAutomaticCompletion() {
        val countdown = PlaybackAutoplayCountdown()
        countdown.start(1_000L)

        assertTrue(countdown.cancel())
        assertFalse(countdown.cancel())
        assertNull(countdown.secondsRemaining(7_000L))
        assertFalse(countdown.completeIfDue(7_000L))
    }

    @Test
    fun heldCountdownResumesWithItsRemainingTime() {
        val countdown = PlaybackAutoplayCountdown()
        countdown.start(1_000L)

        assertTrue(countdown.pause(2_500L))
        assertFalse(countdown.isRunning)
        assertEquals(4, countdown.secondsRemaining(7_500L))
        assertEquals(0.3f, countdown.progressFraction(7_500L), 0.001f)
        assertTrue(countdown.resume(7_500L))
        assertEquals(1, countdown.secondsRemaining(10_999L))
        assertEquals(0.7f, countdown.progressFraction(9_500L), 0.001f)
        assertFalse(countdown.completeIfDue(10_999L))
        assertEquals(1f, countdown.progressFraction(11_000L), 0f)
        assertTrue(countdown.completeIfDue(11_000L))
        assertEquals(0f, countdown.progressFraction(11_000L), 0f)
    }

    @Test
    fun firstPlayableSuggestionSkipsCurrentAndInvalidVideos() {
        val current = Movie(id = 1L, provider = "youtube", youtubeVideoId = "abcdefghijk")
        val invalid = Movie(id = 2L, provider = "youtube", youtubeVideoId = "invalid")
        val next = Movie(id = 3L, provider = "youtube", youtubeVideoId = "lmnopqrstuv")

        assertEquals(next, firstPlayableSuggestion(current, listOf(current, invalid, next)))
    }

    @Test
    fun noPlayableSuggestionProducesNoAutoplayCandidate() {
        val current = Movie(id = 1L, provider = "youtube", youtubeVideoId = "abcdefghijk")
        val invalid = Movie(id = 2L, provider = "youtube", youtubeVideoId = "invalid")

        assertNull(firstPlayableSuggestion(current, listOf(current, invalid)))
    }

    @Test
    fun suggestionsAreShuffledWithoutIncludingTheCurrentVideo() {
        val current = Movie(id = 1L)
        val catalogue = (1L..10L).map { Movie(id = it) }
        val expectedVideos = catalogue.drop(1)

        val suggestions = shuffledVideoSuggestions(current, catalogue, Random(1))

        assertEquals(expectedVideos.size, suggestions.size)
        assertEquals(expectedVideos.toSet(), suggestions.toSet())
        assertFalse(suggestions == expectedVideos)
    }
}
