package dev.kidstubetv.proxy

import android.os.Handler
import android.os.Looper
import io.github.oviron.libmihomo.Clash
import org.json.JSONArray
import org.json.JSONObject

data class ProxyGroup(val name: String, val selected: String, val nodes: List<String>)

/** Adapt to the libmihomo-android v0.3.13 action envelope (callback is async). */
object MihomoControl {
    private val main = Handler(Looper.getMainLooper())
    fun queryGroups(done: (Result<List<ProxyGroup>>) -> Unit) {
        invoke("getProxies", "") { result ->
            done(result.mapCatching { parseGroups(it) })
        }
    }
    fun select(group: String, node: String, done: (Result<String>) -> Unit) {
        val args = JSONObject().put("group-name", group).put("proxy-name", node).toString()
        invoke("changeProxy", args, done)
    }
    fun testNode(node: String, done: (Result<String>) -> Unit) {
        val args = JSONObject().put("proxy-name", node)
            .put("test-url", "https://www.gstatic.com/generate_204")
            .put("timeout", 6000).toString()
        invoke("testDelay", args, done)
    }
    fun traffic(): String = if (Clash.isLoaded()) {
        runCatching { Clash.getTraffic() }.getOrDefault("")
    } else ""

    private fun invoke(method: String, data: String, done: (Result<String>) -> Unit) {
        if (!Clash.isLoaded() || ProxyStatus.phase != ProxyStatus.Phase.READY) {
            main.post { done(Result.failure(IllegalStateException("请先连接 VPN"))) }
            return
        }
        val payload = JSONObject().put("id", "tv").put("method", method).put("data", data).toString()
        runCatching {
            Clash.invokeAction(payload) { raw ->
                val result = runCatching {
                    val obj = JSONObject(raw ?: error("内核未响应"))
                    require(obj.optInt("code", -1) == 0) { obj.optString("data", "操作失败") }
                    obj.opt("data")?.toString().orEmpty()
                }
                main.post { done(result) }
            }
        }.onFailure { main.post { done(Result.failure(it)) } }
    }

    fun parseGroups(raw: String): List<ProxyGroup> {
        val outer = JSONObject(raw)
        val map = outer.optJSONObject("proxies") ?: return emptyList()
        return map.keys().asSequence().mapNotNull { key ->
            val obj = map.optJSONObject(key) ?: return@mapNotNull null
            val nodes = obj.optJSONArray("all") ?: return@mapNotNull null
            val type = obj.optString("type")
            if (!type.equals("Selector", ignoreCase = true) && !type.equals("select", ignoreCase = true)) {
                return@mapNotNull null
            }
            val names = (0 until nodes.length()).mapNotNull { nodes.optString(it).takeIf(String::isNotBlank) }
            ProxyGroup(key, obj.optString("now"), names)
        }.sortedBy(ProxyGroup::name).toList()
    }
}
