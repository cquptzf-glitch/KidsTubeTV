package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvGridMetricsTest {
    @Test fun televisionUsesThreeLargePosters() {
        val spec = TvGridMetrics.calculate(1920, 120, 20, 240)
        assertEquals(3, spec.columns)
        assertEquals(586, spec.cardWidth)
    }

    @Test fun narrowerViewportsCollapseGracefully() {
        val spec = TvGridMetrics.calculate(720, 120, 24, 235)
        assertEquals(2, spec.columns)
        assertEquals(288, spec.cardWidth)
    }

    @Test fun tinyViewportUsesOneColumnWithoutNegativeSizing() {
        val spec = TvGridMetrics.calculate(330, 100, 20, 250)
        assertEquals(1, spec.columns)
        assertTrue(spec.cardWidth > 0)
    }
}
