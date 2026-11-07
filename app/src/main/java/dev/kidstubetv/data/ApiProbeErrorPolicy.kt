package dev.kidstubetv.data

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Do not echo arbitrary HTTP exception bodies (they may contain credentials). */
internal object ApiProbeErrorPolicy {
    fun describe(error: Throwable): String = when (error) {
        is UnknownHostException -> "失败：Google 域名 DNS 解析失败"
        is SocketTimeoutException -> "失败：连接 Google 超时，检查 VPN 节点"
        is SSLException -> "失败：Google TLS 握手失败"
        else -> {
            val detail = error.message.orEmpty()
            if (detail.startsWith("YouTube ") ||
                detail.startsWith("YouTube API ") ||
                detail.startsWith("YouTube HTTP ")) {
                "失败：" + detail.take(140)
            } else "失败：YouTube API 请求未完成（${error.javaClass.simpleName}）"
        }
    }
}
