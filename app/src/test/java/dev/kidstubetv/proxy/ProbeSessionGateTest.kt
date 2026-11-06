package dev.kidstubetv.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeSessionGateTest {
    @Test fun allowsOneProbePerSessionAndReleasesOnCompletion() {
        val gate = ProbeSessionGate()
        val first = gate.begin()!!
        assertNull(gate.begin())
        assertTrue(gate.complete(first))
        assertEquals(first, gate.begin())
    }

    @Test fun oldVpnProbeCannotFinishOrBlockNewVpnProbe() {
        val gate = ProbeSessionGate()
        val old = gate.begin()!!
        gate.invalidate()
        val current = gate.begin()!!
        assertFalse(gate.complete(old))
        assertNull(gate.begin())
        assertTrue(gate.complete(current))
        assertEquals(current, gate.begin())
    }
}
