package dev.kidstubetv.proxy

import org.junit.Assert.*
import org.junit.Test

class VpnSessionGateTest {
    @Test fun staleSetupCannotStartNewSession() {
        val gate = VpnSessionGate()
        val first = gate.start()!!
        assertTrue(gate.current(first))
        assertNull(gate.start())
        assertTrue(gate.stop())
        assertFalse(gate.current(first))
        val second = gate.start()!!
        assertNotEquals(first, second)
        assertFalse(gate.current(first))
        assertTrue(gate.current(second))
    }
    @Test fun repeatedStopIsSafe() {
        val gate = VpnSessionGate()
        assertFalse(gate.stop())
        val session = gate.start()!!
        assertTrue(gate.stop())
        assertFalse(gate.stop())
        assertFalse(gate.current(session))
    }
}
