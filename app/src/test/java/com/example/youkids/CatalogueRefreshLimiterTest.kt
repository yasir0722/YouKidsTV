package com.example.youkids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogueRefreshLimiterTest {
    @Test
    fun enforcesOneRefreshPerMinimumInterval() {
        val limiter = CatalogueRefreshLimiter()

        assertNull(limiter.tryStart(0L))
        limiter.finish()
        assertEquals(1_000L, limiter.tryStart(59_000L))
        assertNull(limiter.tryStart(60_000L))
    }

    @Test
    fun blocksConcurrentRefreshes() {
        val limiter = CatalogueRefreshLimiter()

        assertNull(limiter.tryStart(0L))
        assertEquals(60_000L, limiter.tryStart(0L))
        limiter.finish()
        assertEquals(59_000L, limiter.tryStart(1_000L))
    }

    @Test
    fun honorsServerRetryAfterWhenItExceedsLocalInterval() {
        val limiter = CatalogueRefreshLimiter()

        assertNull(limiter.tryStart(0L))
        limiter.recordRetryAfter(1_000L, 120L)
        limiter.finish()
        assertEquals(61_000L, limiter.tryStart(60_000L))
        assertNull(limiter.tryStart(121_000L))
    }
}
