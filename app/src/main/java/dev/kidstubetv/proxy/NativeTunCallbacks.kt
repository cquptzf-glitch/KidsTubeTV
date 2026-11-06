package dev.kidstubetv.proxy

/** App-owned TUN callback contract, separate from any Go/JNI bridge API. */
internal interface NativeTunCallbacks {
    fun protectSocket(fd: Int): Boolean
    fun resolverProcess(protocol: Int, source: String, target: String, uid: Int): String
    /** Android 9 uses FlClash's native procfs fallback; Android 10+ can override. */
    fun resolveUid(protocol: Int, source: String, target: String): Int = -1
    fun resolvePackage(uid: Int): String = ""
}
