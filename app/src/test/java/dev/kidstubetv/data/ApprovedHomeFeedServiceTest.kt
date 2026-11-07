package dev.kidstubetv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedHomeFeedServiceTest {
    private val a = "UC" + "a".repeat(22)
    private val b = "UC" + "b".repeat(22)
    private val va = "a".repeat(11)
    private val vb = "b".repeat(11)
    private val ve = "e".repeat(11)

    private fun video(id: String) =
        KidVideo(id, "approved", a, "channel", "https://example.invalid/cover")

    @Test fun oneChannelBatchPreservesGroupingAndRevalidatesCandidates() {
        val called = mutableListOf<Pair<String, Map<String, String>>>()
        val source = YouTubeCatalogSource { endpoint, params ->
            called.add(endpoint to params)
            when (endpoint) {
                "channels" -> JSONObject(
                    """{"items":[
                       {"id":"$a","contentDetails":{"relatedPlaylists":{"uploads":"uploads-a"}}},
                       {"id":"$b","contentDetails":{"relatedPlaylists":{"uploads":"uploads-b"}}}
                    ]}"""
                )
                "playlistItems" -> if (params["playlistId"] == "uploads-a")
                    JSONObject("""{"items":[{"contentDetails":{"videoId":"$va"}}]}""")
                else JSONObject("""{"items":[{"contentDetails":{"videoId":"$vb"}}]}""")
                else -> error("Unexpected $endpoint")
            }
        }
        var approvedIds = emptyList<String>()
        val service = ApprovedHomeFeedService(source) { ids ->
            approvedIds = ids
            // Only exact and first channel video pass real child admission.
            ids.filter { it == ve || it == va }.map(::video)
        }
        val videos = service.load(listOf(ve), listOf(a, b))
        assertEquals(listOf(ve, va), videos.map { it.id })
        assertEquals(listOf(ve, va, vb), approvedIds)
        assertEquals(1, called.count { it.first == "channels" })
        assertEquals(2, called.count { it.first == "playlistItems" })
    }

    @Test fun supportsPartialFailureButNeverClaimsTotalOutageIsEmpty() {
        val source = YouTubeCatalogSource { endpoint, params ->
            when (endpoint) {
                "channels" -> JSONObject(
                    """{"items":[
                       {"id":"$a","contentDetails":{"relatedPlaylists":{"uploads":"bad"}}},
                       {"id":"$b","contentDetails":{"relatedPlaylists":{"uploads":"good"}}}
                    ]}"""
                )
                "playlistItems" -> if (params["playlistId"] == "bad") error("offline")
                    else JSONObject("""{"items":[{"contentDetails":{"videoId":"$vb"}}]}""")
                else -> error("unexpected")
            }
        }
        val service = ApprovedHomeFeedService(source) { ids -> ids.map(::video) }
        assertEquals(listOf(vb), service.load(emptyList(), listOf(a,b)).map { it.id })

        val failed = ApprovedHomeFeedService(YouTubeCatalogSource { _, _ ->
            error("network down")
        }) { emptyList() }
        assertThrows(IllegalStateException::class.java) {
            failed.load(emptyList(), listOf(a,b))
        }
    }

    @Test fun neverAdmitsUnverifiedVideosAndBoundsVerificationBatches() {
        val ids = (0 until 113).map { "%011d".format(it) }
        val seen = mutableListOf<List<String>>()
        val service = ApprovedHomeFeedService(
            YouTubeCatalogSource { _, _ -> error("not reached") }
        ) { batch ->
            seen.add(batch)
            batch.filter { it == ids[0] }.map(::video)
        }
        val output = service.load(ids, emptyList())
        assertEquals(listOf(ids[0]), output.map { it.id })
        assertEquals(1, seen.size) // Exact approvals are capped at 40
        assertEquals(40, seen.single().size)
        assertTrue(seen.all { it.size <= 50 })
    }

    @Test fun verifyFailurePropagatesInsteadOfReturningUnreviewedCandidates() {
        val service = ApprovedHomeFeedService(YouTubeCatalogSource { _, _ ->
            JSONObject("{}")
        }) { error("videos.list failed") }
        assertThrows(IllegalStateException::class.java) {
            service.load(listOf(va), emptyList())
        }
    }
}
