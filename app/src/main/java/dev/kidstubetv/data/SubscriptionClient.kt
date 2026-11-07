package dev.kidstubetv.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/** Subscription format validation is intentionally free from Android framework types. */
object SubscriptionFormat {
    private val sources = Regex("(?m)^\\s*(proxies|proxy-providers)\\s*:")
    fun normalizeUrl(input: String): String {
        val value = input.trim()
        require(value.length in 12..2048) { "订阅链接长度不正确" }
        val parsed = URI(value)
        require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) {
            "仅支持 HTTPS Clash/Mihomo YAML 订阅"
        }
        require(parsed.userInfo == null && parsed.fragment == null) { "订阅地址不能包含账号信息或 #fragment" }
        // Subscription URLs carry secrets and are opened by the TV. Reject
        // loopback/LAN-style hostnames and literal IPs before every redirect.
        // This is a host-shape guard, not a DNS rebinding protection.
        val host = parsed.host!!.lowercase(Locale.ROOT).trimEnd('.')
        require(host.contains('.') &&
            host != "localhost" &&
            !host.endsWith(".localhost") &&
            !host.endsWith(".local") &&
            !host.endsWith(".internal") &&
            !host.endsWith(".home.arpa") &&
            !host.startsWith("[") &&
            !host.matches(Regex("""[0-9]+(\.[0-9]+){3}"""))) {
            "订阅地址必须使用公开域名（不允许内网主机或 IP 地址）"
        }
        return value
    }

    fun validateYaml(value: String) {
        ClashProfilePolicy.validate(value)
    }

    const val MAX_PROFILE_BYTES = 4_194_304
}

/** HTTPS-only, bounded download; the URL may contain private subscription tokens. */
class SubscriptionClient(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    /**
     * Some providers choose YAML vs HTML/Base64 from the User-Agent.
     * Try only two common Clash client IDs, and retry ONLY on a response
     * proven to be the wrong format or a UA-related HTTP refusal.
     */
    fun fetch(input: String): String {
        val normalized = SubscriptionFormat.normalizeUrl(input)
        val agents = listOf("ClashMetaForAndroid", "clash")
        for ((index, agent) in agents.withIndex()) {
            try {
                return download(normalized, agent)
            } catch (e: SubscriptionDownloadException) {
                if (index == agents.lastIndex ||
                    !SubscriptionResponsePolicy.retryWithClashUserAgent(e)) throw e
            }
        }
        throw SubscriptionDownloadException(SubscriptionProblem.UNKNOWN)
    }

    private fun download(input: String, agent: String): String {
        var current = input
        repeat(4) { hop ->
            val connection = openConnection(URL(current))
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty(
                "Accept", "application/yaml, application/x-yaml, text/yaml, text/plain, */*"
            )
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept-Encoding", "gzip")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    if (hop >= 3) throw SubscriptionDownloadException(SubscriptionProblem.REDIRECT)
                    val location = connection.getHeaderField("Location")
                        ?: throw SubscriptionDownloadException(SubscriptionProblem.REDIRECT)
                    current = try {
                        SubscriptionFormat.normalizeUrl(
                            URL(URL(current), location).toExternalForm()
                        )
                    } catch (_: Exception) {
                        throw SubscriptionDownloadException(SubscriptionProblem.REDIRECT)
                    }
                    return@repeat
                }
                if (code !in 200..299) {
                    throw SubscriptionDownloadException(SubscriptionProblem.HTTP, code)
                }
                val output = ByteArrayOutputStream()
                val encoded = connection.inputStream
                val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true))
                    java.util.zip.GZIPInputStream(encoded) else encoded
                stream.use { source ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = source.read(buffer)
                        if (n == -1) break
                        if (output.size() + n > SubscriptionFormat.MAX_PROFILE_BYTES) {
                            throw SubscriptionDownloadException(SubscriptionProblem.TOO_LARGE)
                        }
                        output.write(buffer, 0, n)
                    }
                }
                val yaml = output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
                SubscriptionResponsePolicy.validate(yaml)
                return yaml
            } finally {
                connection.disconnect()
            }
        }
        throw SubscriptionDownloadException(SubscriptionProblem.REDIRECT)
    }
}
