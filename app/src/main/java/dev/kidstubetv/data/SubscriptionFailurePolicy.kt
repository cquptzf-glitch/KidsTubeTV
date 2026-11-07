package dev.kidstubetv.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Subscription links often embed private access tokens. Never show throwable
 * messages, response bodies or full URLs in status text / diagnostics.
 */
enum class SubscriptionProblem {
    HTTP, HTML_RESPONSE, BASE64_RESPONSE, INVALID_YAML, UNSAFE_PROFILE,
    TOO_LARGE, REDIRECT, UNKNOWN
}

class SubscriptionDownloadException(
    val problem: SubscriptionProblem,
    val httpStatus: Int? = null
) : IOException(problem.name + (httpStatus?.let { " HTTP $it" } ?: ""))

object SubscriptionFailurePolicy {
    fun describe(cause: Throwable, vpnReady: Boolean): String = when (cause) {
        is UnknownHostException ->
            "DNS 解析失败。检查手机/电视的网络和订阅域名；首次导入时可能需要更换网络"
        is SocketTimeoutException ->
            "订阅下载超时。请检查网络、服务商地址或当前代理节点"
        is SSLException ->
            "HTTPS/TLS 验证失败。检查系统时间、证书和订阅服务状态"
        is ConnectException ->
            "无法连接订阅服务器。请检查网络、订阅服务状态和当前节点"
        is SubscriptionDownloadException -> when (cause.problem) {
            SubscriptionProblem.HTTP -> when (cause.httpStatus) {
                401 -> "HTTP 401：订阅链接可能已过期或缺少授权"
                403 -> "HTTP 403：服务商拒绝请求，请检查订阅令牌或 Clash 客户端格式"
                404 -> "HTTP 404：订阅地址已失效，请重新复制 Clash/Mihomo 专用链接"
                429 -> "HTTP 429：请求太频繁，过一段时间再更新"
                in 500..599 -> "订阅服务端异常（HTTP ${cause.httpStatus}），请稍后重试"
                else -> "订阅服务返回 HTTP ${cause.httpStatus ?: "错误"}"
            }
            SubscriptionProblem.HTML_RESPONSE ->
                "服务器返回登录页/网页而非 Clash YAML，请检查订阅地址或访问授权"
            SubscriptionProblem.BASE64_RESPONSE ->
                "返回的是 Base64/通用节点格式，请使用服务商的 Clash/Mihomo YAML 订阅"
            SubscriptionProblem.INVALID_YAML ->
                "返回内容不是有效的 Clash/Mihomo YAML，请检查链接格式或服务商配置"
            SubscriptionProblem.UNSAFE_PROFILE ->
                "配置尝试开放局域网管理接口，出于安全原因拒绝导入"
            SubscriptionProblem.TOO_LARGE -> "配置文件超过 1 MiB，无法安全导入"
            SubscriptionProblem.REDIRECT -> "订阅跳转地址无效或跳转次数过多"
            SubscriptionProblem.UNKNOWN -> "订阅响应异常，请检查服务商的 Clash/Mihomo 订阅"
        }
        is IllegalArgumentException ->
            "订阅链接无效：仅支持公开域名的 HTTPS Clash/Mihomo 地址"
        else -> if (vpnReady)
            "订阅下载失败：请尝试切换代理节点或检查服务商，不会覆盖已有配置"
        else
            "订阅下载失败：当前未连接应用 VPN，请检查直连网络或先导入本地 YAML"
    }
}
