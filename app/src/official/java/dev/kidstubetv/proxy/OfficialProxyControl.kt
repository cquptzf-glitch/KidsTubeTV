package dev.kidstubetv.proxy

import android.os.Handler
import android.os.Looper
import com.follow.clash.core.mobile.Mobile
import com.follow.clash.core.mobile.ResultHandler
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

internal object ProxyControlFactory {
    fun create(): ProxyControlBackend = OfficialProxyControl()
}

/** Official method envelope uses `arguments` and `result/error`, not legacy data/code. */
internal class OfficialProxyControl : ProxyControlBackend {
    private val main = Handler(Looper.getMainLooper())
    private val sequence = AtomicLong()

    override fun queryGroups(done: (Result<List<ProxyGroup>>) -> Unit) {
        invoke("proxy.list", null) { value ->
            done(value.mapCatching { ProxyGroupsParser.parse(it) })
        }
    }

    override fun select(group: String, node: String, done: (Result<String>) -> Unit) {
        invoke("proxy.select",
            JSONObject().put("group-name", group).put("proxy-name", node)) { result ->
            done(result.mapCatching { OfficialCoreProtocol.validateSelection(it) })
        }
    }

    override fun testNode(node: String, done: (Result<String>) -> Unit) {
        invoke("proxy.testDelay", JSONObject()
            .put("proxy-name", node)
            .put("test-url", "https://www.gstatic.com/generate_204")
            .put("timeout", 6000)) { value ->
            done(value.mapCatching { result ->
                JSONObject(result).optInt("value").toString()
            })
        }
    }

    override fun traffic(): String = runCatching {
        if (ProxyStatus.phase == ProxyStatus.Phase.READY) Mobile.getTraffic(false) else ""
    }.getOrDefault("")

    private fun invoke(
        method: String,
        arguments: JSONObject?,
        done: (Result<String>) -> Unit
    ) {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            main.post { done(Result.failure(IllegalStateException("请先连接 VPN"))) }
            return
        }
        val request = JSONObject()
            .put("id", "kids-${sequence.incrementAndGet()}")
            .put("method", method)
        if (arguments != null) request.put("arguments", arguments)
        runCatching {
            Mobile.invokeMethod(request.toString(), ResultHandler { response ->
                val result = runCatching {
                    OfficialCoreProtocol.parse(
                        response?.toString(Charsets.UTF_8) ?: error("内核未响应")
                    )
                }
                main.post { done(result) }
            })
        }.onFailure { main.post { done(Result.failure(it)) } }
    }
}
