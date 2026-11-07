package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionFormatTest {
    @Test fun acceptsHttpsYamlSubscription() {
        assertEquals("https://example.com/sub?token=opaque",
            SubscriptionFormat.normalizeUrl("  https://example.com/sub?token=opaque "))
    }

    @Test fun rejectsHttpAndUserInfo() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFormat.normalizeUrl("http://example.com/subscription")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFormat.normalizeUrl("https://alice:pass@example.com/config")
        }
    }

    @Test fun rejectsPrivateAndLiteralSubscriptionHosts() {
        listOf(
            "https://localhost/subscription",
            "https://api.local/config",
            "https://proxy.internal/config",
            "https://router.home.arpa/config",
            "https://127.0.0.1/config",
            "https://192.168.0.1/config",
            "https://[::1]/config",
            "https://myrouter/config"
        ).forEach { address ->
            assertThrows("Rejected: $address", IllegalArgumentException::class.java) {
                SubscriptionFormat.normalizeUrl(address)
            }
        }
    }

    @Test fun rejectsHtmlAndBase64Subscriptions() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFormat.validateYaml("<html><body>error</body></html>")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFormat.validateYaml("dm1lc3M6Ly9leGFtcGxlLgo=")
        }
    }

    @Test fun acceptsClashYamlWithProxiesOrProviders() {
        SubscriptionFormat.validateYaml(
            "proxies:\n  - name: test\n    type: direct\n"
        )
        SubscriptionFormat.validateYaml(
            "proxy-providers:\n  main:\n    type: http\n    url: https://example.com\n"
        )
    }
}
