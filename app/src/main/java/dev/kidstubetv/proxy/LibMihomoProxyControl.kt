package dev.kidstubetv.proxy

import android.os.Handler
import android.os.Looper
import io.github.oviron.libmihomo.Clash
import org.json.JSONObject

/** Adapt to the libmihomo-android v0.3.13 action envelope (callback is async). */
internal object ProxyControlFactory {
    fun create(): ProxyControlBackend = LibMihomoProxyControl()
}

internal class LibMihomoProxyControl : ProxyControlBackend {
    private val main = Handler(Looper.getMainLooper())
    override fun queryGroups(done: (Result<List<ProxyGroup>>) -> Unit) {
        invoke("getProxies", "") { result ->
            done(result.mapCatching { ProxyGroupsParser.parse(it) })
        }
    }
    override fun select(group: String, node: String, done: (Result<String>) -> Unit) {
        val args = JSONObject().put("group-name", group).put("proxy-name", node).toString()
        invoke("changeProxy", args, done)
    }
    override fun testNode(node: String, done: (Result<String>) -> Unit) {
        val args = JSONObject().put("proxy-name", node)
            .put("test-url", "https://www.gstatic.com/generate_204")
            .put("timeout", 6000).toString()
        invoke("testDelay", args, done)
    }
    override fun traffic(): String = if (Clash.isLoaded()) {
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
}
