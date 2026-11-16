package dev.kidstubetv.data

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/** Describes the verified format without exposing proxy credentials. */
internal data class SubscriptionFetchResult(
    val yaml: String,
    val format: String,
    val proxyCount: Int,
    val skippedCount: Int
)

/**
 * V2Board-like panels may return Clash YAML, plain share links or a Base64
 * wrapped node list. Convert common protocols locally; never send private
 * node credentials to an external converter.
 */
internal object SubscriptionContentNormalizer {
    private val knownSchemes = setOf(
        "vless", "vmess", "ss", "trojan", "hy2", "hysteria2",
        "ssr", "tuic", "anytls"
    )

    fun normalize(body: String): SubscriptionFetchResult {
        val cleaned = body.removePrefix("\uFEFF").trim()
        if (cleaned.startsWith("<html", true) ||
            cleaned.startsWith("<!doctype", true) ||
            cleaned.startsWith("<head", true)) {
            throw SubscriptionDownloadException(SubscriptionProblem.HTML_RESPONSE)
        }
        if (cleaned.isBlank()) throw SubscriptionDownloadException(SubscriptionProblem.INVALID_YAML)

        if (Regex("(?m)^\\s*(proxies|proxy-providers)\\s*:").containsMatchIn(cleaned)) {
            val safe = safeYaml(cleaned)
            return SubscriptionFetchResult(safe, "Clash YAML", countNodes(safe), 0)
        }

        val unwrapped = decode64(cleaned)
        val text = if (unwrapped?.contains("://") == true ||
            unwrapped?.trimStart()?.startsWith("{") == true) unwrapped else cleaned

        fromSip008(text)?.let { return it }

        val proxies = mutableListOf<Map<String, Any?>>()
        var skipped = 0
        for (line in text.lineSequence()) {
            val item = line.trim()
            if (item.isBlank() || item.startsWith("#")) continue
            val type = item.substringBefore("://", "").lowercase()
            if (type !in knownSchemes) continue
            val node = runCatching { shareNode(type, item) }.getOrNull()
            if (node == null) skipped++ else proxies += node
        }
        if (proxies.isEmpty()) {
            throw SubscriptionDownloadException(
                if (skipped > 0) SubscriptionProblem.UNSUPPORTED_FORMAT
                else if (unwrapped != null) SubscriptionProblem.BASE64_RESPONSE
                else SubscriptionProblem.INVALID_YAML
            )
        }
        val unique = uniqueNames(proxies)
        return SubscriptionFetchResult(
            safeYaml(toYaml(unique)),
            if (unwrapped != null) "Base64 节点列表" else "节点分享链接",
            unique.size, skipped
        )
    }

    private fun safeYaml(text: String): String = try {
        ClashProfilePolicy.normalize(text)
    } catch (_: IllegalArgumentException) {
        throw SubscriptionDownloadException(SubscriptionProblem.INVALID_YAML)
    }

    private fun countNodes(text: String): Int {
        val root = Yaml().load<Any?>(text) as? Map<*, *> ?: return 0
        return ((root["proxies"] as? List<*>)?.size ?: 0) +
            ((root["proxy-providers"] as? Map<*, *>)?.size ?: 0)
    }

    private fun decode64(input: String): String? {
        val compact = input.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
        if (compact.length !in 8..(SubscriptionFormat.MAX_PROFILE_BYTES * 2) ||
            compact.any { !(it.isLetterOrDigit() || it == '+' || it == '/' || it == '=') }) return null
        return runCatching {
            val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
            String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun shareNode(type: String, raw: String): Map<String, Any?>? = when (type) {
        "vless" -> parseVless(raw)
        "vmess" -> parseVmess(raw)
        "ss" -> parseShadowsocks(raw)
        "trojan" -> parseTrojan(raw)
        "hy2", "hysteria2" -> parseHysteria2(raw)
        else -> null
    }

    private fun parseVless(raw: String): Map<String, Any?>? {
        val uri = URI(raw)
        if (uri.host.isNullOrBlank() || uri.port !in 1..65535) return null
        val uuid = decode(uri.rawUserInfo.orEmpty())
        if (uuid.isBlank()) return null
        val query = params(uri.rawQuery.orEmpty())
        val transport = network(query["type"])
        val security = query["security"].orEmpty().lowercase()
        if (security !in setOf("", "none", "tls", "reality")) return null
        val node = linkedMapOf<String, Any?>(
            "name" to label(uri), "type" to "vless",
            "server" to uri.host, "port" to uri.port,
            "uuid" to uuid, "network" to transport, "udp" to true,
            "tls" to (security == "tls" || security == "reality")
        )
        query["sni"]?.takeIf(String::isNotBlank)?.let { node["servername"] = it }
        query["fp"]?.takeIf(String::isNotBlank)?.let { node["client-fingerprint"] = it }
        query["flow"]?.takeIf(String::isNotBlank)?.let { node["flow"] = it }
        query["alpn"]?.split(',')?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }
            ?.let { node["alpn"] = it }
        if (security == "reality") {
            val key = query["pbk"].orEmpty()
            if (key.isBlank()) return null
            val reality = linkedMapOf<String, Any>("public-key" to key)
            query["sid"]?.let { reality["short-id"] = it }
            node["reality-opts"] = reality
        }
        transport(node, transport, query)
        return node
    }

    private fun parseVmess(raw: String): Map<String, Any?>? {
        val content = decode64(raw.substringAfter("://")) ?: return null
        val json = JSONObject(content)
        val host = json.optString("add").trim()
        val port = json.optString("port").toIntOrNull() ?: return null
        val id = json.optString("id")
        if (host.isBlank() || port !in 1..65535 || id.isBlank()) return null
        val mode = network(json.optString("net"))
        val node = linkedMapOf<String, Any?>(
            "name" to json.optString("ps").ifBlank { host },
            "type" to "vmess", "server" to host, "port" to port,
            "uuid" to id, "alterId" to (json.optString("aid").toIntOrNull() ?: 0),
            "cipher" to json.optString("scy").ifBlank { "auto" },
            "tls" to json.optString("tls").equals("tls", true),
            "network" to mode, "udp" to true
        )
        json.optString("sni").takeIf(String::isNotBlank)?.let { node["servername"] = it }
        transport(node, mode, mapOf(
            "path" to json.optString("path"),
            "host" to json.optString("host"),
            "serviceName" to json.optString("path")
        ))
        return node
    }

    private fun parseShadowsocks(raw: String): Map<String, Any?>? {
        val part = raw.substringAfter("://").substringBefore('#').substringBefore('?')
        val name = decode(raw.substringAfter('#', ""))
        val decoded = if (part.contains('@')) {
            val key = part.substringBeforeLast('@')
            val info = decode64(key) ?: decode(key)
            info + "@" + part.substringAfterLast('@')
        } else decode64(part) ?: return null
        val credentials = decoded.substringBeforeLast('@', "")
        val endpoint = decoded.substringAfterLast('@', "")
        val split = credentials.indexOf(':')
        if (split <= 0) return null
        val uri = URI("ss://" + endpoint)
        if (uri.host.isNullOrBlank() || uri.port !in 1..65535) return null
        return linkedMapOf(
            "name" to name.ifBlank { uri.host }, "type" to "ss",
            "server" to uri.host, "port" to uri.port,
            "cipher" to credentials.substring(0, split),
            "password" to credentials.substring(split + 1), "udp" to true
        )
    }

    private fun parseTrojan(raw: String): Map<String, Any?>? {
        val uri = URI(raw)
        if (uri.host.isNullOrBlank() || uri.port !in 1..65535) return null
        val password = decode(uri.rawUserInfo.orEmpty())
        if (password.isBlank()) return null
        val q = params(uri.rawQuery.orEmpty())
        val mode = network(q["type"])
        val node = linkedMapOf<String, Any?>(
            "name" to label(uri), "type" to "trojan",
            "server" to uri.host, "port" to uri.port,
            "password" to password, "udp" to true
        )
        q["sni"]?.takeIf(String::isNotBlank)?.let { node["sni"] = it }
        q["fp"]?.takeIf(String::isNotBlank)?.let { node["client-fingerprint"] = it }
        q["alpn"]?.split(',')?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }
            ?.let { node["alpn"] = it }
        if (mode != "tcp") node["network"] = mode
        transport(node, mode, q)
        return node
    }

    private fun parseHysteria2(raw: String): Map<String, Any?>? {
        val uri = URI(raw)
        if (uri.host.isNullOrBlank() || uri.port !in 1..65535) return null
        val password = decode(uri.rawUserInfo.orEmpty())
        if (password.isBlank()) return null
        val q = params(uri.rawQuery.orEmpty())
        val node = linkedMapOf<String, Any?>(
            "name" to label(uri), "type" to "hysteria2",
            "server" to uri.host, "port" to uri.port,
            "password" to password, "udp" to true
        )
        q["sni"]?.takeIf(String::isNotBlank)?.let { node["sni"] = it }
        q["obfs"]?.takeIf(String::isNotBlank)?.let { node["obfs"] = it }
        q["obfs-password"]?.takeIf(String::isNotBlank)?.let { node["obfs-password"] = it }
        return node
    }

    private fun fromSip008(input: String): SubscriptionFetchResult? {
        val json = runCatching { JSONObject(input) }.getOrNull() ?: return null
        val servers = json.optJSONArray("servers") ?: return null
        val nodes = mutableListOf<Map<String, Any?>>()
        for (i in 0 until servers.length()) {
            val item = servers.optJSONObject(i) ?: continue
            val server = item.optString("server")
            val port = item.optInt("server_port")
            if (server.isBlank() || port !in 1..65535) continue
            nodes += linkedMapOf(
                "name" to item.optString("remarks").ifBlank { server },
                "type" to "ss", "server" to server, "port" to port,
                "cipher" to item.optString("method"),
                "password" to item.optString("password"), "udp" to true
            )
        }
        if (nodes.isEmpty()) return null
        val unique = uniqueNames(nodes)
        return SubscriptionFetchResult(safeYaml(toYaml(unique)), "SIP008 JSON", unique.size, 0)
    }

    private fun transport(
        node: MutableMap<String, Any?>, mode: String, query: Map<String, String>
    ) {
        when (mode) {
            "ws" -> node["ws-opts"] = mapOf(
                "path" to query["path"].orEmpty().ifBlank { "/" },
                "headers" to mapOf("Host" to query["host"].orEmpty())
            )
            "grpc" -> node["grpc-opts"] =
                mapOf("grpc-service-name" to (query["serviceName"] ?: ""))
        }
    }

    private fun network(raw: String?): String = when (raw?.lowercase()) {
        "ws" -> "ws"
        "grpc" -> "grpc"
        else -> "tcp"
    }

    private fun params(raw: String): Map<String, String> =
        raw.split('&').filter(String::isNotBlank).associate { part ->
            val key = part.substringBefore('=')
            decode(key) to decode(part.substringAfter('=', ""))
        }

    private fun label(uri: URI) = decode(uri.rawFragment.orEmpty()).ifBlank {
        uri.host.orEmpty()
    }

    private fun decode(text: String): String =
        runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    private fun uniqueNames(nodes: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val names = mutableSetOf("KidsTubeTV 自动选择", "KidsTubeTV 手动选择")
        return nodes.mapIndexed { index, node ->
            val base = node["name"].toString().ifBlank { "节点-" + (index + 1) }
            var unique = base
            var counter = 2
            while (!names.add(unique)) unique = base + "-" + counter++
            node.toMutableMap().apply { put("name", unique) }
        }
    }

    private fun toYaml(nodes: List<Map<String, Any?>>): String {
        val names = nodes.map { it["name"].toString() }
        val profile = linkedMapOf<String, Any>(
            "mode" to "rule", "log-level" to "warning",
            "proxies" to nodes,
            "proxy-groups" to listOf(
                mapOf("name" to "KidsTubeTV 自动选择", "type" to "url-test",
                    "proxies" to names,
                    "url" to "https://www.gstatic.com/generate_204", "interval" to 300),
                mapOf("name" to "KidsTubeTV 手动选择", "type" to "select",
                    "proxies" to (listOf("KidsTubeTV 自动选择") + names))
            ),
            "rules" to listOf("MATCH,KidsTubeTV 手动选择")
        )
        val dump = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            width = 120
        }
        return Yaml(dump).dump(profile)
    }
}
