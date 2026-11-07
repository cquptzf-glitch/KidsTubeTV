package dev.kidstubetv.proxy

import java.util.Locale

/**
 * Never echo native parser failures: raw text can contain proxy passwords,
 * subscription URLs or file paths. Surface only a stable reason category.
 */
internal object VpnFailurePolicy {
    fun fromSetup(raw: String): String {
        val detail = raw.lowercase(Locale.ROOT)
        return when {
            "geo" in detail -> "VPN_GEO_DATA_FAILED：地理规则数据缺失或损坏"
            "dns" in detail -> "VPN_DNS_CONFIG_FAILED：订阅 DNS 配置无法加载"
            "proxy" in detail || "config" in detail || "yaml" in detail ||
                "parse" in detail || "rule" in detail ->
                "VPN_CONFIG_REJECTED：Clash YAML 配置解析失败，请检查订阅或节点"
            "permission" in detail -> "VPN_FILE_PERMISSION：代理配置文件无法读取"
            else -> "VPN_CORE_SETUP_FAILED：代理内核启动失败，请检查订阅格式"
        }
    }
    fun fromException(exception: Throwable, phase: String): String {
        val detail = exception.message.orEmpty().lowercase(Locale.ROOT)
        if ("missing mihomo profile" in detail) {
            return "VPN_PROFILE_MISSING：请先导入有效的 Clash YAML 订阅"
        }
        if (exception is SecurityException) {
            return "VPN_PERMISSION_FAILED：系统拒绝 VPN 操作，请重新授权"
        }
        return when (phase) {
            "TUN" -> "VPN_TUN_FAILED：Android 隧道建立失败，检查其他 VPN 或系统权限"
            "DNS" -> "VPN_DNS_INIT_FAILED：无法监听当前物理网络的 DNS"
            else -> "VPN_SETUP_EXCEPTION：代理内核初始化失败"
        }
    }
}
