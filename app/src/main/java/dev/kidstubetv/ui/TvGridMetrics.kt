package dev.kidstubetv.ui

/** Pure screen-size calculation so TV card sizing is testable without Android. */
object TvGridMetrics {
    data class Spec(val columns: Int, val cardWidth: Int)

    fun calculate(
        viewportWidth: Int,
        horizontalPadding: Int,
        gap: Int,
        minimumCardWidth: Int,
    ): Spec {
        require(viewportWidth > 0 && horizontalPadding >= 0 && gap >= 0 && minimumCardWidth > 0)
        val usable = (viewportWidth - horizontalPadding).coerceAtLeast(1)
        val columns = when {
            (usable - 2 * gap) / 3 >= minimumCardWidth -> 3
            (usable - gap) / 2 >= minimumCardWidth -> 2
            else -> 1
        }
        val cardWidth = ((usable - (columns - 1) * gap) / columns).coerceAtLeast(1)
        return Spec(columns, cardWidth)
    }
}
