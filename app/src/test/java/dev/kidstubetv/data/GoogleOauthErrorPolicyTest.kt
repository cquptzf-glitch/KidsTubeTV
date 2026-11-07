package dev.kidstubetv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOauthErrorPolicyTest {
    @Test fun explainsInvalidTvClient() {
        assertTrue(GoogleOauthErrorPolicy.explain(401, "invalid_client").contains("TV"))
    }
    @Test fun neverEchoesUnexpectedErrorData() {
        assertFalse(GoogleOauthErrorPolicy.explain(400, "token=abc_secret").contains("abc_secret"))
    }
}
