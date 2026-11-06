package dev.kidstubetv.proxy

import org.json.JSONObject

/**
 * Strictly derives the parent-facing selectors from Mihomo's getProxies
 * response. Other policy groups and malformed entries are not clickable.
 */
object ProxyGroupsParser {
    fun parse(raw: String): List<ProxyGroup> {
        val root = JSONObject(raw)
        val groups = root.optJSONObject("proxies")
            ?: root.optJSONObject("data")?.optJSONObject("proxies")
            ?: return emptyList()
        return groups.keys().asSequence().mapNotNull { group ->
            if (group.isBlank() || group.length > 160) return@mapNotNull null
            val obj = groups.optJSONObject(group) ?: return@mapNotNull null
            val kind = obj.optString("type")
            if (!kind.equals("Selector", ignoreCase = true) &&
                !kind.equals("select", ignoreCase = true)) return@mapNotNull null
            val all = obj.optJSONArray("all") ?: return@mapNotNull null
            val nodes = (0 until all.length()).mapNotNull { index ->
                all.optString(index).takeIf { node ->
                    node.isNotBlank() && node.length <= 160 &&
                        node.none(Char::isISOControl)
                }
            }.distinct()
            if (nodes.isEmpty()) return@mapNotNull null
            ProxyGroup(group, obj.optString("now"), nodes)
        }.sortedBy { it.name }.toList()
    }
}
