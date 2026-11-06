package dev.kidstubetv.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

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
        require(parsed.host != "localhost" && parsed.host != "127.0.0.1") { "请使用公开 HTTPS 订阅地址" }
        return value
    }

    fun validateYaml(value: String) {
        require(value.length in 12..MAX_PROFILE_BYTES) { "配置应在 12 B 至 1 MiB 之间" }
        require(sources.containsMatchIn(value) && !value.trimStart().startsWith("<")) {
            "该链接未返回 Clash/Mihomo YAML。Base64/V2Ray 节点列表暂不支持"
        }
    }

    const val MAX_PROFILE_BYTES = 1_048_576
}

/** HTTPS-only, bounded download; do not log URLs containing subscription tokens. */
class SubscriptionClient {
    fun fetch(input: String): String {
        var url = SubscriptionFormat.normalizeUrl(input)
        repeat(4) { hop ->
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/yaml, text/yaml, text/plain, */*")
            connection.setRequestProperty("User-Agent", "ClashMeta/1.0 KidsTubeTV")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    require(hop < 3) { "订阅重定向次数过多" }
                    val location = connection.getHeaderField("Location") ?: error("订阅重定向缺少地址")
                    url = SubscriptionFormat.normalizeUrl(URL(URL(url), location).toExternalForm())
                } else {
                    require(code in 200..299) { "订阅服务器返回 HTTP $code" }
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { source ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = source.read(buf)
                            if (n == -1) break
                            require(output.size() + n <= SubscriptionFormat.MAX_PROFILE_BYTES) {
                                "订阅配置过大（超过 1 MiB）"
                            }
                            output.write(buf, 0, n)
                        }
                    }
                    val text = output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
                    SubscriptionFormat.validateYaml(text)
                    return text
                }
            } finally {
                connection.disconnect()
            }
        }
        error("订阅下载失败")
    }
}
