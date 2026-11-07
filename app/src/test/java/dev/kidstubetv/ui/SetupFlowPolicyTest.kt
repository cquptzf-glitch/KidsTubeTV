package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFlowPolicyTest {
    @Test fun beginsWithPinAndNeverSkipsNetworkSafety() {
        val n = SetupFlowPolicy::next
        assertEquals(SetupFlowPolicy.Step.PIN, n(false, true, true, true, true))
        assertEquals(SetupFlowPolicy.Step.PROFILE, n(true, false, true, true, true))
        assertEquals(SetupFlowPolicy.Step.VPN, n(true, true, false, true, true))
    }

    @Test fun allowsApiKeyOrOAuthButRequiresAtLeastOneApprovedSource() {
        assertEquals(SetupFlowPolicy.Step.AUTH, SetupFlowPolicy.next(true, true, true, false, true))
        assertEquals(SetupFlowPolicy.Step.APPROVAL, SetupFlowPolicy.next(true, true, true, true, false))
        assertEquals(SetupFlowPolicy.Step.COMPLETE, SetupFlowPolicy.next(true, true, true, true, true))
    }
}
