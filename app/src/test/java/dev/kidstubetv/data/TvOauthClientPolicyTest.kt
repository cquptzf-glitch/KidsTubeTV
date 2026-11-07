package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TvOauthClientPolicyTest {
    private val id = "123456789012-sample.apps.googleusercontent.com"

    @Test fun blankSecretMeansKeepSavedSecretAndSession() {
        val result = TvOauthClientPolicy.prepare(id, "saved-secret", " $id ", "")
        assertEquals("saved-secret", result.secret)
        assertFalse(result.changed)
    }

    @Test fun clientIdOrSecretChangeRequiresNewGoogleAuthorization() {
        assertTrue(TvOauthClientPolicy.prepare(id, "old", id, "new").changed)
        val next = "333333333333-sample.apps.googleusercontent.com"
        assertTrue(TvOauthClientPolicy.prepare(id, "old", next, "").changed)
    }

    @Test fun invalidInputCannotOverwriteExistingClient() {
        assertThrows(IllegalArgumentException::class.java) {
            TvOauthClientPolicy.prepare(id, "old", "invalid", "new")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TvOauthClientPolicy.prepare(id, "", id, "")
        }
    }
}
