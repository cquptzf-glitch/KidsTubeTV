package dev.kidstubetv.proxy

import io.github.oviron.libmihomo.Clash
import io.github.oviron.libmihomo.InvokeInterface
import org.json.JSONObject

/**
 * Small native proxy seam: the Android TV UI and VpnService do not depend on
 * FlClash/Go/gomobile setup details. This version intentionally keeps the
 * existing pinned v0.3.13 libmihomo AAR backend.
 *
 * FlClash's own gomobile API is different; it needs a separate adapter and
 * Android 9 regression tests before any replacement.
 */
internal interface NativeProxyCore {
    fun setup(nativeLibraryDirectory: String, configDirectory: String, sdk: Int, selected: Map<String, String>, done: (String?) -> Unit)
    fun startTun(fd: Int, callbacks: NativeTunCallbacks)
    fun stopTun()
}

internal class CurrentMihomoAarCore : NativeProxyCore {
    override fun setup(nativeLibraryDirectory: String, configDirectory: String, sdk: Int, selected: Map<String, String>, done: (String?) -> Unit) {
        Clash.load(nativeLibraryDirectory)
        val init = JSONObject()
            .put("home-dir", configDirectory)
            .put("version", sdk).toString()
        val options = JSONObject().put("selected-map", JSONObject(selected)).toString()
        Clash.quickSetup(init, options, done)
    }

    override fun startTun(fd: Int, callbacks: NativeTunCallbacks) {
        // The legacy bridge cannot return the protect() Boolean to Go;
        // the owning service stops the VPN if protection fails.
        val bridge = object : io.github.oviron.libmihomo.TunInterface {
            override fun protect(fd: Int) {
                callbacks.protectSocket(fd)
            }
            override fun resolverProcess(
                protocol: Int, source: String, target: String, uid: Int
            ): String = callbacks.resolverProcess(protocol, source, target, uid)
        }
        Clash.startTUN(fd, bridge, "kidstubetv", "system", "172.19.0.2/30", "1.1.1.1", 1400)
    }

    override fun stopTun() {
        if (!Clash.isLoaded()) return
        Clash.stopTun()
        Clash.setEventListener(null as InvokeInterface?)
    }
}
