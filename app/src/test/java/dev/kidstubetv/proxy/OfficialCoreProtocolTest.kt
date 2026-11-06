package dev.kidstubetv.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OfficialCoreProtocolTest {
    @Test fun decodesOfficialResultEnvelope() {
        assertEquals(
            """{"proxies":{}}""",
            OfficialCoreProtocol.parse("""{"id":"x","result":{"proxies":{}},"error":null}""")
        )
        assertEquals("{}", OfficialCoreProtocol.parse("""{"id":"x","result":null}"""))
    }

    @Test fun propagatesCoreErrorsAndRejectsMalformedResponses() {
        assertThrows(IllegalStateException::class.java) {
            OfficialCoreProtocol.parse(
                """{"error":{"code":"core_error","message":"offline"},"result":null}"""
            )
        }
        assertThrows(IllegalStateException::class.java) {
            OfficialCoreProtocol.parse("""{"id":"x"}""")
        }
    }

    @Test fun rejectsUnknownProxyNamesEvenWhenRpcIsSuccessful() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialCoreProtocol.validateSelection(
                """{"message":"Not found group","changed":false}"""
            )
        }
        OfficialCoreProtocol.validateSelection("""{"changed":true,"message":""}""")
    }
}
