package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class ClashProfilePolicyTest {
    @Test fun acceptsFlClashDesktopExportsByDisablingAllInboundListeners() {
        val value = """
            mixed-port: 7890
            port: 7891
            allow-lan: true
            bind-address: '*'
            external-controller: 0.0.0.0:9090
            secret: do-not-log-me
            proxies:
              - name: direct
                type: direct
        """.trimIndent()
        val safe = ClashProfilePolicy.normalize(value)
        assertTrue(safe.contains("allow-lan: false"))
        assertTrue(safe.contains("mixed-port: 0"))
        assertTrue(safe.contains("port: 0"))
        assertFalse(safe.contains("external-controller"))
        assertFalse(safe.contains("do-not-log-me"))
        assertTrue(safe.contains("proxy-groups:"))
        assertTrue(safe.contains("rules:"))
    }

    @Test fun retainsRulesAndGroupsForFullMihomoProfiles() {
        val yaml = """
            proxies:
              - name: main
                type: direct
            proxy-groups:
              - name: 选择
                type: select
                proxies: [main, DIRECT]
            rules:
              - MATCH,选择
        """.trimIndent()
        val normalized = ClashProfilePolicy.normalize(yaml)
        val root = Yaml().load<Map<String, Any>>(normalized)
        val rules = root["rules"] as List<*>
        assertEquals(listOf("MATCH,选择"), rules)
        assertEquals(1, (root["proxy-groups"] as List<*>).size)
    }

    @Test fun rejectsInvalidYamlAndEmptyProfiles() {
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
