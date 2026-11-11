package dev.kidstubetv.proxy

/**
 * Boundary for the single libmihomo-android JNI backend.
 * The established TUN fd is transferred to the native core.
 */
internal interface NativeProxyCore {
    val tunAddress: String
    val tunDns: String
    val tunMtu: Int get() = 1400
    fun setup(
        nativeLibraryDirectory: String,
        configDirectory: String,
        sdk: Int,
        selected: Map<String, String>,
        done: (String?) -> Unit
    )
    /** Return false if the core rejects the TUN descriptor. */
    fun startTun(fd: Int, callbacks: NativeTunCallbacks): Boolean
    fun stopTun()
}

internal interface ProxyControlBackend {
    fun queryGroups(done: (Result<List<ProxyGroup>>) -> Unit)
    fun select(group: String, node: String, done: (Result<String>) -> Unit)
    fun testNode(node: String, done: (Result<String>) -> Unit)
    fun traffic(): String
}
