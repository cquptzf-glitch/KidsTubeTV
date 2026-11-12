package dev.kidstubetv.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppOnlyVpnScopeTest {
    @Test fun usesOnlyTheCallingApplicationPackage() {
        assertEquals("dev.kidstubetv", AppOnlyVpnScope.allowedPackage("dev.kidstubetv"))
        assertEquals("dev.kidstubetv.preview.b10",
            AppOnlyVpnScope.allowedPackage("dev.kidstubetv.preview.b10"))
    }

    @Test fun refusesEmptyOrInvalidPackageRatherThanConfiguringWholeDeviceVpn() {
        listOf("", " ", "kidstubetv").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                AppOnlyVpnScope.allowedPackage(value)
            }
        }
    }
}
