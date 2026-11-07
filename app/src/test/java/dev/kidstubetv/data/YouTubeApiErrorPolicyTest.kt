package dev.kidstubetv.data

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeApiErrorPolicyTest {
    @Test fun distinguishesQuotaAndAuthentication() {
        val body = JSONObject("""{"errors":[{"reason":"quotaExceeded"}]}""")
        assertTrue(YouTubeApiErrorPolicy.explain(403, body).contains("配额"))
        assertTrue(YouTubeApiErrorPolicy.explain(401, null).contains("授权"))
    }
    @Test fun doesNotLeakRawBody() {
        val body = JSONObject("""{"message":"token-is-private"}""")
        assertFalse(YouTubeApiErrorPolicy.explain(403, body).contains("token-is-private"))
    }
}
