package dev.kidstubetv.ui

/**
 * Lightweight, monotonic, view-independent refresh budget based on the
 * time-aware SmartTube browse-refresh approach. Actual lists stay visible
 * while asynchronous revalidation runs; child admission is never cached.
 */
internal class CatalogRefreshPolicy(
    private val ttlMillis: Long = 30 * 60 * 1000L,
    private val uptimeMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    init { require(ttlMillis > 0) }
    private var lastSuccessAt: Long? = null

    fun shouldRefresh(): Boolean {
        val timestamp = lastSuccessAt ?: return true
        val elapsed = uptimeMillis() - timestamp
        return elapsed < 0 || elapsed >= ttlMillis
    }

    fun onSuccessfulRefresh() {
        lastSuccessAt = uptimeMillis()
    }

    fun invalidate() { lastSuccessAt = null }
}
