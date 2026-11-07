package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeFeedLoadPolicyTest {
    @Test fun emptyValidCatalogIsNotAnError() {
        assertEquals(emptyList<List<String>>(),
            HomeFeedLoadPolicy.successfulGroups(emptyList()))
        assertEquals(listOf(emptyList<String>()),
            HomeFeedLoadPolicy.successfulGroups(listOf(Result.success(emptyList()))))
    }

    @Test fun partialSuccessStillShowsApprovedSources() {
        val groups = HomeFeedLoadPolicy.successfulGroups(listOf(
            Result.failure(IllegalStateException("timeout")),
            Result.success(listOf("one", "two")),
            Result.failure(IllegalStateException("quota"))
        ))
        assertEquals(listOf(listOf("one", "two")), groups)
    }

    @Test fun allFailedMustNotBeCachedAsSuccessfulEmptyFeed() {
        assertThrows(IllegalStateException::class.java) {
            HomeFeedLoadPolicy.successfulGroups(listOf(
                Result.failure<List<String>>(IllegalStateException("VPN")),
                Result.failure<List<String>>(IllegalStateException("quota"))
            ))
        }
    }
}
