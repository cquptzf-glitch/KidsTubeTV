package dev.kidstubetv.proxy

/**
 * Fixed app-only VPN policy. Android routes every installed app into a VPN
 * when no allowed applications are added. Never make the list configurable:
 * KidsTubeTV only needs its own package, including the preview package ID.
 */
internal object AppOnlyVpnScope {
    fun allowedPackage(ownPackage: String): String {
        require(ownPackage.isNotBlank() && ownPackage.trim() == ownPackage && ownPackage.contains('.')) {
            "必须使用有效的 KidsTubeTV 包名配置应用专属 VPN"
        }
        return ownPackage
    }
}
