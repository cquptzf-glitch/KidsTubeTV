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
            val profile = client.fetchDetailed(url)
            val bytes = profile.yaml.toByteArray(Charsets.UTF_8).size
            SubscriptionProbeResult(
                successful = true,
                message = "HTTPS 下载与本地格式转换通过（" + profile.format + "，" +
                    profile.proxyCount + " 个节点/提供者，跳过 " +
                    profile.skippedCount + " 个不支持节点，" + bytes + " 字节）。" +
                    "未保存配置；仍需 Mihomo 内核验证才能确认可用。"
            )
        } catch (failure: Exception) {
            SubscriptionProbeResult(
                successful = false,
                message = SubscriptionFailurePolicy.describe(failure, vpnReady)
            )
        }
    }
}
