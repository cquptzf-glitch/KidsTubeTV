package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileValidationErrorPolicyTest {
    @Test fun nativeErrorsAreClassifiedWithoutLeakingEndpointsOrPasswords() {
        val raw = "yaml: invalid value password=secret-123 endpoint=https://node.example.org/private"
        val result = ProfileValidationErrorPolicy.classify(raw)
        assertEquals(ProfileValidationError.YAML, result)
        assertFalse(result.message.contains("secret-123"))
        assertFalse(result.message.contains("node.example.org"))
    }

    @Test fun missingFilesAndProvidersHaveActionableCategories() {
        assertEquals(ProfileValidationError.MISSING_FILE,
            ProfileValidationErrorPolicy.classify("open /private/geoip.dat: no such file"))
        assertEquals(ProfileValidationError.PROVIDER,
            ProfileValidationErrorPolicy.classify("rule provider download failed"))
    }
}
