package dev.kidstubetv.data

import org.junit.Assert.assertThrows
import org.junit.Test

class ClashProfilePolicyTest {
    @Test fun allowsNormalLocalOnlyProfiles() {
        ClashProfilePolicy.validate("""
            mixed-port: 7890
            allow-lan: false
            external-controller: 127.0.0.1:9090
            proxies:
              - name: direct
                type: direct
        """.trimIndent())
        ClashProfilePolicy.validate("""
            proxy-providers:
              main:
                type: http
                url: https://example.org/config
        """.trimIndent())
    }

    @Test fun rejectsRemoteManagementAndLanExposure() {
        listOf(
            "allow-lan: true\nproxies: [{name: direct, type: direct}]",
            "external-controller: 0.0.0.0:9090\nproxies: [{name: direct, type: direct}]",
            "external-controller-tls: 192.168.1.1:9091\nproxies: [{name: direct, type: direct}]"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                ClashProfilePolicy.validate(value)
            }
        }
    }

    @Test fun rejectsMalformedAndEmptyProfiles() {
        listOf(
            "proxies: []\nproxy-providers: {}",
            "proxies: [unterminated",
            "proxies: [1]\nproxies: [2]",
            "<html><body>not YAML</body></html>"
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                ClashProfilePolicy.validate(it)
            }
        }
    }
}
