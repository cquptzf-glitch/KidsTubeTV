package dev.kidstubetv.data

/** A probe failure is advisory; an established VPN is the fail-closed request boundary. */
object GoogleRequestPolicy {
    fun requireAvailable(vpnReady: Boolean) {
        check(vpnReady) { "VPN 尚未建立，拒绝直接访问 Google / YouTube" }
    }
}
