package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionProfileRevisionTest {
    @Test fun anOlderDownloadCannotOverwriteAnEditedSubscription() {
        val gate = SubscriptionProfileRevision()
        val old = gate.snapshot()
        gate.invalidate() // Parent changed the provider or imported YAML.
        var attempted = false
        assertFalse(gate.installIfCurrent(old) { attempted = true })
        assertFalse(attempted)
        assertTrue(gate.installIfCurrent(gate.snapshot()) { attempted = true })
        assertTrue(attempted)
    }

    @Test fun aSuccessfulInstallInvalidatesAllSiblingDownloads() {
        val gate = SubscriptionProfileRevision()
        val token = gate.snapshot()
        var writes = 0
        assertTrue(gate.installIfCurrent(token) { writes++ })
        assertFalse(gate.installIfCurrent(token) { writes++ })
        assertEquals(1, writes)
    }

    @Test fun failedInstallKeepsRevisionForReportingAndRetry() {
        val gate = SubscriptionProfileRevision()
        val token = gate.snapshot()
        assertThrows(IllegalStateException::class.java) {
            gate.installIfCurrent(token) { error("storage failure") }
        }
        // Caller can still store a failure message for this URL/revision.
        assertTrue(gate.isCurrent(token))
        assertTrue(gate.installIfCurrent(token) {})
        assertFalse(gate.isCurrent(token))
    }

    @Test fun anExplicitImportInvalidatesPriorDownloads() {
        val gate = SubscriptionProfileRevision()
        val pending = gate.snapshot()
        gate.invalidate()
        assertFalse(gate.isCurrent(pending))
        assertFalse(gate.installIfCurrent(pending) { error("stale") })
    }
}
