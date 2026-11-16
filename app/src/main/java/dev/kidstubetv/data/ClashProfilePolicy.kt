package dev.kidstubetv.data

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Turn an untrusted Clash/FlClash export into a safe app-only Mihomo profile.
 *
 * Desktop exports often expose mixed-port, allow-lan and an external controller.
 * KidsTubeTV owns an Android app-scoped TUN; inbound/listener ports are never
 * needed. Normalize these values rather than rejecting otherwise valid YAML.
 */
object ClashProfilePolicy {
    private fun read(content: String): Map<*, *> {
        val bytes = content.toByteArray(Charsets.UTF_8).size
        require(bytes in 12..SubscriptionFormat.MAX_PROFILE_BYTES) {
            "配置文件必须为 12 字节至 4 MiB"
        }
        val opts = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 20
            nestingDepthLimit = 40
            codePointLimit = SubscriptionFormat.MAX_PROFILE_BYTES
        }
        val root = try {
            Yaml(SafeConstructor(opts)).load<Any?>(content)
        } catch (_: Exception) {
            throw IllegalArgumentException("YAML 语法、重复字段或复杂度不符合要求")
        }
        return root as? Map<*, *>
            ?: throw IllegalArgumentException("Clash 配置根节点必须是 YAML 对象")
    }

    fun normalize(input: String): String {
        val original = input.removePrefix("\uFEFF")
        val root = read(original)
        val nodes = root["proxies"] as? List<*>
        val providers = root["proxy-providers"] as? Map<*, *>
        require(!nodes.isNullOrEmpty() || !providers.isNullOrEmpty()) {
            "配置中没有 proxies 或 proxy-providers"
        }

        val copy = linkedMapOf<String, Any?>()
        for ((key, value) in root) {
            if (key !is String) throw IllegalArgumentException("配置根字段必须是字符串")
            copy[key] = value
        }

        // Remove any desktop inbound/control APIs that would be inappropriate
        // on a TV. No LAN-facing management or local proxy listener is needed.
        copy["allow-lan"] = false
        copy["bind-address"] = "127.0.0.1"
        for (key in listOf("port", "socks-port", "mixed-port", "redir-port", "tproxy-port")) {
            copy[key] = 0
        }
        for (key in listOf(
            "external-controller", "external-controller-tls", "external-ui",
            "external-ui-url", "external-controller-cors", "secret",
            "listeners", "tun", "ss-config", "vmess"
        )) {
            copy.remove(key)
        }

        // Bare share lists exported as YAML do not route traffic automatically.
        // Keep any existing provider/groups/rules exactly as supplied.
        val groups = copy["proxy-groups"] as? List<*>
        if (groups.isNullOrEmpty() && !nodes.isNullOrEmpty()) {
            val names = nodes.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }
                .filter(String::isNotBlank)
            require(names.isNotEmpty()) { "proxies 缺少节点名称" }
            copy["proxy-groups"] = listOf(
                mapOf("name" to "KidsTubeTV 自动选择", "type" to "url-test",
                    "proxies" to names, "url" to "https://www.gstatic.com/generate_204",
                    "interval" to 300),
                mapOf("name" to "KidsTubeTV 手动选择", "type" to "select",
                    "proxies" to (listOf("KidsTubeTV 自动选择") + names))
            )
            if ((copy["rules"] as? List<*>)?.isEmpty() != false) {
                copy["rules"] = listOf("MATCH,KidsTubeTV 手动选择")
            }
        }

        val dump = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            width = 120
            lineBreak = DumperOptions.LineBreak.UNIX
        }
        val output = Yaml(dump).dump(copy)
        require(output.toByteArray(Charsets.UTF_8).size <= SubscriptionFormat.MAX_PROFILE_BYTES) {
            "安全处理后的 YAML 超过 4 MiB"
        }
        return output
    }

    fun validate(content: String) { normalize(content) }
}
