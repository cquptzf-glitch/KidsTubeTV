package dev.kidstubetv.proxy

/** App-owned TUN callback contract, separate from any Go/JNI bridge API. */
internal interface NativeTunCallbacks {
    fun protectSocket(fd: Int): Boolean
    fun resolverProcess(protocol: Int, source: String, target: String, uid: Int): String
}
