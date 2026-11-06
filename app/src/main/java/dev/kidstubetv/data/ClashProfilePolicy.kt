package dev.kidstubetv.data

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Parse untrusted subscription YAML with bounded, safe construction.
 * KidsTubeTV runs a per-app VPN; it must not expose a management API to the LAN.
 */
object ClashProfilePolicy {
    fun validate(content: String) {
        require(content.length in 12..SubscriptionFormat.MAX_PROFILE_BYTES) {
            "配置应在 12 B 至 1 MiB 之间"
        }
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 20
            codePointLimit = SubscriptionFormat.MAX_PROFILE_BYTES
            nestingDepthLimit = 40
        }
        val root = runCatching {
            Yaml(SafeConstructor(options)).load<Any?>(content)
        }.getOrElse {
            throw IllegalArgumentException("Clash YAML 语法错误、重复字段或复杂度过高", it)
        } as? Map<*, *> ?: throw IllegalArgumentException("Clash 配置必须是 YAML 对象")

        val nodes = root["proxies"] as? List<*>
        val providers = root["proxy-providers"] as? Map<*, *>
        require(!nodes.isNullOrEmpty() || !providers.isNullOrEmpty()) {
            "未找到可用的 proxies 或 proxy-providers；不支持 Base64 节点订阅"
        }
        require(root["allow-lan"]?.toString()?.equals("true", ignoreCase = true) != true) {
            "配置启用了 allow-lan，不能在儿童电视上开放局域网代理端口"
        }
        for (field in listOf("external-controller", "external-controller-tls")) {
            val raw = root[field]?.toString()?.trim().orEmpty()
            require(raw.isEmpty() ||
                raw.startsWith("127.0.0.1:") ||
                raw.startsWith("[::1]:") ||
                raw.startsWith("localhost:", ignoreCase = true)) {
                "$field 不允许对局域网或公网开放"
            }
        }
    }
}
