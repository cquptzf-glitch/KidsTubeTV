package dev.kidstubetv.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionResponsePolicyTest {
    @Test fun acceptsYAMLAndConvertsSupportedBase64ShareLinks() {
        SubscriptionResponsePolicy.validate(
            "proxies:\n  - name: alpha\n    type: direct\n"
        )
        val share = "vless://11111111-1111-1111-1111-111111111111@edge.example.org:443?security=reality&pbk=publickey&sid=abc&sni=example.org#demo"
        val encoded = Base64.getEncoder().encodeToString(share.toByteArray())
        val data = SubscriptionContentNormalizer.normalize(encoded)
        assertEquals(1, data.proxyCount)
        assertTrue(data.yaml.contains("reality-opts"))
        assertTrue(data.yaml.contains("KidsTubeTV 手动选择"))
    }

    @Test fun rejectsHtmlAndUnsupportedLinksWithoutLeakingData() {
        val html = assertThrows(SubscriptionDownloadException::class.java) {
            SubscriptionResponsePolicy.validate("<html><title>Sign in</title></html>")
        }
        assertEquals(SubscriptionProblem.HTML_RESPONSE, html.problem)
        val unknown = Base64.getEncoder().encodeToString(
            "tuic://PRIVATE@example.org:443#unknown".toByteArray()
        )
        val e = assertThrows(SubscriptionDownloadException::class.java) {
            SubscriptionResponsePolicy.validate(unknown)
        }
        assertEquals(SubscriptionProblem.UNSUPPORTED_FORMAT, e.problem)
        assertFalse(e.message.orEmpty().contains("PRIVATE"))
    }

    @Test fun formattedYamlLanFieldsAreNormalized() {
        val data = SubscriptionContentNormalizer.normalize(
            "allow-lan: true\nexternal-controller: 0.0.0.0:9090\n" +
                "proxies:\n  - name: direct\n    type: direct\n"
        )
        assertTrue(data.yaml.contains("allow-lan: false"))
        assertFalse(data.yaml.contains("external-controller"))
    }

    @Test fun retriesOnlyExpectedProviderRefusals() {
        for (status in listOf(403, 406, 415)) {
            assertTrue(SubscriptionResponsePolicy.retryWithClashUserAgent(
                SubscriptionDownloadException(SubscriptionProblem.HTTP, status)))
        }
        for (status in listOf(401, 429)) {
            assertFalse(SubscriptionResponsePolicy.retryWithClashUserAgent(
                SubscriptionDownloadException(SubscriptionProblem.HTTP, status)))
        }
    }
}
