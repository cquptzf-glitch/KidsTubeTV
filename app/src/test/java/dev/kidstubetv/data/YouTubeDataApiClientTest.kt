package dev.kidstubetv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeDataApiClientTest {
    @Test fun refusesToFetchOrRefreshAuthIfVpnNotReady() {
        var credentialsRead = 0
        var requests = 0
        val client = YouTubeDataApiClient(
            credential = { credentialsRead++; YouTubeCredential.ApiKey("private-key") },
            vpnReady = { false },
            transport = YouTubeHttpTransport { _, _, _ ->
                requests++
                JSONObject()
            }
        )
        assertThrows(IllegalStateException::class.java) {
            client.get("videos", mapOf("id" to "abcdefghijk"))
        }
        assertEquals(0, credentialsRead)
        assertEquals(0, requests)
    }

    @Test fun routesApiKeyAndOAuthThroughSameInjectedTransport() {
        val types = mutableListOf<String>()
        val transport = YouTubeHttpTransport { endpoint, params, auth ->
            assertEquals("videos", endpoint)
            assertEquals("abcdefghijk", params["id"])
            types.add(when (auth) {
                is YouTubeCredential.ApiKey -> "api"
                is YouTubeCredential.OAuthToken -> "oauth"
            })
            JSONObject("""{"items":[]}""")
        }
        val keyClient = YouTubeDataApiClient(
            { YouTubeCredential.ApiKey("redacted") }, { true }, transport)
        val oauthClient = YouTubeDataApiClient(
            { YouTubeCredential.OAuthToken("redacted") }, { true }, transport)
        keyClient.get("videos", mapOf("id" to "abcdefghijk"))
        oauthClient.get("videos", mapOf("id" to "abcdefghijk"))
        assertEquals(listOf("api", "oauth"), types)
    }

    @Test fun vpnRevokedDuringCredentialRefreshPreventsApiRequest() {
        var connected = true
        var calls = 0
        val client = YouTubeDataApiClient(
            credential = {
                connected = false
                YouTubeCredential.OAuthToken("private")
            },
            vpnReady = { connected },
            transport = YouTubeHttpTransport { _, _, _ -> calls++; JSONObject() }
        )
        assertThrows(IllegalStateException::class.java) {
            client.get("videos", mapOf("id" to "abcdefghijk"))
        }
        assertEquals(0, calls)
    }

    @Test fun emptyCredentialsCannotReachHttpTransport() {
        var called = false
        val client = YouTubeDataApiClient(
            credential = { YouTubeCredential.OAuthToken("") },
            vpnReady = { true },
            transport = YouTubeHttpTransport { _, _, _ ->
                called = true
                JSONObject()
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            client.get("videos", emptyMap())
        }
        assertTrue(!called)
    }
}
