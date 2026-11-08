package com.example.youkids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshPolicyTest {
    @Test
    fun updateCheckIsThrottledAndCannotOverlap() {
        val policy = HomeRefreshPolicy()

        assertTrue(policy.tryStartUpdateCheck(0L))
        assertFalse(policy.tryStartUpdateCheck(1L))
        assertEquals(
            HomeRefreshPolicy.UPDATE_CHECK_INTERVAL_MS,
            policy.updateCheckDelayMs(1L),
        )
        policy.finishUpdateCheck()
        assertFalse(
            policy.tryStartUpdateCheck(HomeRefreshPolicy.UPDATE_CHECK_INTERVAL_MS - 1L),
        )
        assertTrue(
            policy.tryStartUpdateCheck(HomeRefreshPolicy.UPDATE_CHECK_INTERVAL_MS),
        )
    }

    @Test
    fun catalogueSyncUsesSeparateFifteenMinuteThrottle() {
        val policy = HomeRefreshPolicy()

        assertTrue(policy.tryStartCatalogueSync(0L))
        assertFalse(policy.tryStartCatalogueSync(1L))
        policy.finishCatalogueSync()
        assertEquals(
            HomeRefreshPolicy.CATALOGUE_SYNC_INTERVAL_MS - 1L,
            policy.catalogueSyncDelayMs(1L),
        )
        assertTrue(
            policy.tryStartCatalogueSync(HomeRefreshPolicy.CATALOGUE_SYNC_INTERVAL_MS),
        )
    }

    @Test
    fun manualSyncResetsAutomaticSyncInterval() {
        val policy = HomeRefreshPolicy()
        policy.recordManualCatalogueSync(10_000L)

        assertFalse(policy.tryStartCatalogueSync(10_001L))
        assertEquals(
            HomeRefreshPolicy.CATALOGUE_SYNC_INTERVAL_MS - 1L,
            policy.catalogueSyncDelayMs(10_001L),
        )
    }
}
