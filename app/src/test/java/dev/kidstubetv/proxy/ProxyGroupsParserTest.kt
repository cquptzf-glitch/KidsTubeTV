package dev.kidstubetv.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyGroupsParserTest {
    @Test fun parsesOnlySelectorsAndDeduplicatesNodes() {
        val raw = """{"proxies":{
          "PROXY":{"type":"Selector","now":"香港 01","all":["香港 01","香港 01","自动选择"]},
          "AUTO":{"type":"URLTest","now":"a","all":["a"]},
          "DIRECT":{"type":"Direct"},
          "EMPTY":{"type":"Selector","all":[]}
        }}"""
        val list = ProxyGroupsParser.parse(raw)
        assertEquals(1, list.size)
        assertEquals("PROXY", list[0].name)
        assertEquals(listOf("香港 01", "自动选择"), list[0].nodes)
    }

    @Test fun supportsWrappedResponseAndMalformedGroups() {
        val raw = """{"data":{"proxies":{
          "SELECT":{"type":"select","now":"node-a","all":["node-a","node-b"]},
          "BROKEN":{"type":"Selector","all":null}
        }}}"""
        assertEquals("SELECT", ProxyGroupsParser.parse(raw).single().name)
        assertTrue(ProxyGroupsParser.parse("{}").isEmpty())
    }
}
