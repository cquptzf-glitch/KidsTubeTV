package dev.kidstubetv.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFileStoreTest {
    @Test fun updatesWithoutDeletingOldProfileAndSupportsRollback() {
        val dir = Files.createTempDirectory("kids-profile-")
        try {
            val file = dir.resolve("mihomo/config.yaml").toFile()
            val store = ProfileFileStore(file)
            store.install("proxies: first".toByteArray())
            assertEquals("proxies: first", file.readText())
            store.install("proxies: second".toByteArray())
            assertEquals("proxies: second", file.readText())
            assertEquals("proxies: first",
                dir.resolve("mihomo/config-backup.yaml").toFile().readText())
            assertTrue(store.restore())
            assertEquals("proxies: first", file.readText())
            assertFalse(store.restore())
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test fun emptyPayloadDoesNotDamageExistingFile() {
        val dir = Files.createTempDirectory("kids-profile-")
        try {
            val file = dir.resolve("config.yaml").toFile()
            val store = ProfileFileStore(file)
            store.install("proxies: existing".toByteArray())
            try {
                store.install(byteArrayOf())
                throw AssertionError("Expected error")
            } catch (_: IllegalArgumentException) {
                assertEquals("proxies: existing", file.readText())
            }
        } finally { dir.toFile().deleteRecursively() }
    }
}
