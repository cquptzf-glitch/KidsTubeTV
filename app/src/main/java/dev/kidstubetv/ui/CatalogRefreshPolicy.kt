package dev.kidstubetv.ui

/**
 * Lightweight, monotonic, view-independent refresh budget based on the
 * time-aware SmartTube browse-refresh approach. Actual lists stay visible
 * while asynchronous revalidation runs; child admission is never cached.
 */
internal class CatalogRefreshPolicy(
    private val ttlMillis: Long = 30 * 60 * 1000L,
    private val retryCooldownMillis: Long = 5 * 60 * 1000L,
    private val uptimeMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    init { require(ttlMillis > 0 && retryCooldownMillis > 0) }
    private var lastSuccessAt: Long? = null
    private var lastFailureAt: Long? = null

    fun shouldRefresh(): Boolean {
        val now = uptimeMillis()
        val failureAt = lastFailureAt
        if (failureAt != null && now >= failureAt && now - failureAt < retryCooldownMillis) {
            return false
        }
        val timestamp = lastSuccessAt ?: return true
        val elapsed = now - timestamp
        return elapsed < 0 || elapsed >= ttlMillis
    }

    fun onSuccessfulRefresh() {
        lastSuccessAt = uptimeMillis()
        lastFailureAt = null
    }

    fun onFailedRefresh() {
        lastFailureAt = uptimeMillis()
    }

    fun invalidate() {
        lastSuccessAt = null
        lastFailureAt = null
    }
}
