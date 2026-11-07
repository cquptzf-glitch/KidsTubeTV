package dev.kidstubetv.data

/** Safe error translation: never echo tokens, URLs or untrusted server messages. */
object GoogleOauthErrorPolicy {
    fun explain(status: Int, reason: String?): String = when (reason) {
        "invalid_client", "unauthorized_client" -> "Google TV OAuth 客户端无效，请检查 Client ID 和 Secret"
        "invalid_grant" -> "Google 凭据已过期或无效，请重新扫码授权"
        "access_denied" -> "Google 授权被拒绝，请重新扫码"
        "expired_token" -> "Google 设备验证码已过期，请重新开始"
        "admin_policy_enforced", "org_internal" -> "Google 账户或组织策略禁止该 OAuth 应用"
        "invalid_scope" -> "当前 OAuth 客户端不支持所请求的 YouTube 访问权限"
        "rate_limit_exceeded" -> "Google 设备码请求过于频繁，请稍后重试"
        "invalid_request", "unsupported_grant_type" -> "Google OAuth 请求参数被拒绝，请检查客户端类型"
        "server_error", "temporarily_unavailable" -> "Google 授权服务暂不可用，请稍后重试"
        else -> "Google OAuth HTTP $status：请检查网络、客户端和授权范围"
    }
}
