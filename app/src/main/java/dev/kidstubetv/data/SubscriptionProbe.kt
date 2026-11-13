package dev.kidstubetv.data

/**
 * Runs exactly the same bounded HTTPS + Clash YAML validation path as a
 * normal subscription update, but never saves or activates the result.
 *
 * A subscription URL commonly contains a bearer token: never include the
 * URL, response body, raw Throwable message or full YAML in a result.
 */
data class SubscriptionProbeResult(
    val successful: Boolean,
    val message: String
)

class SubscriptionProbe(
    private val client: SubscriptionClient = SubscriptionClient()
) {
    fun check(url: String, vpnReady: Boolean): SubscriptionProbeResult {
        return try {
            val profile = client.fetch(url)
            val bytes = profile.toByteArray(Charsets.UTF_8).size
            SubscriptionProbeResult(
                successful = true,
                message = "HTTPS 下载成功；Clash/Mihomo YAML 校验通过（$bytes 字节）。" +
                    "未修改当前配置；这不代表 VPN、DNS 或视频播放一定可用。"
            )
        } catch (failure: Exception) {
            SubscriptionProbeResult(
                successful = false,
                message = SubscriptionFailurePolicy.describe(failure, vpnReady)
            )
        }
    }
}
