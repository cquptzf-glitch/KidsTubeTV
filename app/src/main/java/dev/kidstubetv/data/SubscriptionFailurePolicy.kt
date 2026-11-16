package dev.kidstubetv.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Never expose raw provider URLs, node credentials or exception messages. */
enum class SubscriptionProblem {
    HTTP, HTML_RESPONSE, BASE64_RESPONSE, INVALID_YAML,
    UNSUPPORTED_FORMAT, UNSAFE_PROFILE, TOO_LARGE, REDIRECT, UNKNOWN
}

class SubscriptionDownloadException(
    val problem: SubscriptionProblem,
    val httpStatus: Int? = null
) : IOException(problem.name + (httpStatus?.let { " HTTP " + it } ?: ""))

object SubscriptionFailurePolicy {
    fun describe(cause: Throwable, vpnReady: Boolean): String = when (cause) {
        is UnknownHostException -> "DNS 解析失败：请检查网络或先通过本地 YAML 导入启动"
        is SocketTimeoutException -> "订阅下载超时：请检查服务商或网络"
        is SSLException -> "HTTPS 证书验证失败：检查设备时间及服务商证书"
        is ConnectException -> "订阅服务器连接失败：请检查当前网络"
        is SubscriptionDownloadException -> when (cause.problem) {
            SubscriptionProblem.HTTP -> when (cause.httpStatus) {
                401 -> "HTTP 401：订阅令牌失效或未授权"
                403 -> "HTTP 403：服务商拒绝订阅请求"
                404 -> "HTTP 404：订阅链接失效"
                429 -> "HTTP 429：请求过频，请稍后重试"
                in 500..599 -> "订阅服务器异常（HTTP " + cause.httpStatus + "）"
                else -> "订阅请求失败（HTTP " + (cause.httpStatus ?: "未知") + "）"
            }
            SubscriptionProblem.HTML_RESPONSE -> "订阅返回登录网页而非配置"
            SubscriptionProblem.BASE64_RESPONSE -> "Base64 内容无法识别为支持的节点列表"
            SubscriptionProblem.INVALID_YAML -> "订阅不是有效的 Clash/Mihomo YAML 或可识别节点格式"
            SubscriptionProblem.UNSUPPORTED_FORMAT -> "已识别订阅中的节点，但协议/传输不受本次转换支持，请用 FlClash 导出 Mihomo YAML"
            SubscriptionProblem.UNSAFE_PROFILE -> "配置存在不安全的网络监听入口"
            SubscriptionProblem.TOO_LARGE -> "订阅内容超过 4 MiB"
            SubscriptionProblem.REDIRECT -> "订阅发生不安全或过多重定向"
            SubscriptionProblem.UNKNOWN -> "无法识别的订阅响应"
        }
        is ProfileValidationException -> cause.kind.message
        is IllegalArgumentException -> "配置格式或订阅地址无效，请检查格式后重试"
        else -> if (vpnReady)
            "更新失败：请检查代理与订阅服务，原配置已保留"
        else "下载失败：请检查直连网络或先导入本地 YAML"
    }
}
