package dev.kidstubetv.data

/**
 * Prevent application-layer Google/YouTube calls when the app-only VPN is down
 * or its last HTTPS probe reported unreachable.
 *
 * This is a request gate, not proof against a DNS/IPv6 or socket-level leak;
 * only OS VPN routing and an effective TUN can supply network isolation.
 */
object GoogleRequestPolicy {
    fun requireAvailable(vpnReady: Boolean, knownUnreachable: Boolean) {
        check(vpnReady && !knownUnreachable) {
            "VPN 未就绪或 Google 网络不可用，拒绝直接访问"
        }
    }
}
