package com.example.youkids

internal class HomeRefreshPolicy(
    private val updateCheckIntervalMs: Long = UPDATE_CHECK_INTERVAL_MS,
    private val catalogueSyncIntervalMs: Long = CATALOGUE_SYNC_INTERVAL_MS,
) {
    private var lastUpdateCheckAtMs: Long? = null
    private var lastCatalogueSyncAtMs: Long? = null

    var updateCheckInFlight: Boolean = false
        private set
    var catalogueSyncInFlight: Boolean = false
        private set

    init {
        require(updateCheckIntervalMs > 0L)
        require(catalogueSyncIntervalMs > 0L)
    }

    fun tryStartUpdateCheck(nowMs: Long): Boolean {
        if (updateCheckInFlight || !isDue(lastUpdateCheckAtMs, nowMs, updateCheckIntervalMs)) {
            return false
        }
        lastUpdateCheckAtMs = nowMs
        updateCheckInFlight = true
        return true
    }

    fun finishUpdateCheck() {
        updateCheckInFlight = false
    }

    fun cancelUpdateCheck() {
        updateCheckInFlight = false
        lastUpdateCheckAtMs = null
    }

    fun tryStartCatalogueSync(nowMs: Long): Boolean {
        if (catalogueSyncInFlight ||
            !isDue(lastCatalogueSyncAtMs, nowMs, catalogueSyncIntervalMs)
        ) {
            return false
        }
        lastCatalogueSyncAtMs = nowMs
        catalogueSyncInFlight = true
        return true
    }

    fun finishCatalogueSync() {
        catalogueSyncInFlight = false
    }

    fun cancelCatalogueSync() {
        catalogueSyncInFlight = false
        lastCatalogueSyncAtMs = null
    }

    fun recordManualCatalogueSync(nowMs: Long) {
        lastCatalogueSyncAtMs = nowMs
    }

    fun updateCheckDelayMs(nowMs: Long): Long =
        if (updateCheckInFlight) {
            updateCheckIntervalMs
        } else {
            delayUntilNextAttempt(lastUpdateCheckAtMs, nowMs, updateCheckIntervalMs)
        }

    fun catalogueSyncDelayMs(nowMs: Long): Long =
        if (catalogueSyncInFlight) {
            catalogueSyncIntervalMs
        } else {
            delayUntilNextAttempt(lastCatalogueSyncAtMs, nowMs, catalogueSyncIntervalMs)
        }

    private fun isDue(lastAttemptAtMs: Long?, nowMs: Long, intervalMs: Long): Boolean =
        lastAttemptAtMs == null || nowMs - lastAttemptAtMs >= intervalMs

    private fun delayUntilNextAttempt(
        lastAttemptAtMs: Long?,
        nowMs: Long,
        intervalMs: Long,
    ): Long =
        if (lastAttemptAtMs == null) {
            0L
        } else {
            (intervalMs - (nowMs - lastAttemptAtMs)).coerceAtLeast(0L)
        }

    companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 5 * 60_000L
        const val CATALOGUE_SYNC_INTERVAL_MS = 15 * 60_000L
    }
}
