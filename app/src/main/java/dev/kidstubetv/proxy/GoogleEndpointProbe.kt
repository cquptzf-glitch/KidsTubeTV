package dev.kidstubetv.proxy

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** No credentials are sent. HTTP 4xx means TLS reached the host, not that login works. */
internal object GoogleEndpointProbe {
    fun check(done: (String) -> Unit) {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            done("VPN 未连接，已取消检查。不会回退为直连。")
            return
        }
        Thread({
            val results = listOf(
                "Google OAuth" to "https://oauth2.googleapis.com/device/code",
                "YouTube API" to "https://www.googleapis.com/youtube/v3/videos"
            ).map { (label, url) -> "$label：" + probe(url) }
            Handler(Looper.getMainLooper()).post {
                done(if (ProxyStatus.phase == ProxyStatus.Phase.READY)
                    results.joinToString("\n") else "VPN 检测过程中已断开，结果无效")
            }
        }, "kids-google-endpoint-probe").start()
    }

    private fun probe(url: String): String = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        try {
            val http = conn.responseCode
            "HTTPS 可达 (HTTP $http；不代表 API 授权通过)"
        } finally { conn.disconnect() }
    } catch (_: UnknownHostException) {
        "DNS 解析失败"
    } catch (_: SSLException) {
        "TLS 握手失败"
    } catch (_: SocketTimeoutException) {
        "连接超时"
    } catch (_: Exception) {
        "HTTPS 连接失败"
    }
}
