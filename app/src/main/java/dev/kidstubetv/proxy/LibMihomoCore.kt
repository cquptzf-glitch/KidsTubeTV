package dev.kidstubetv.proxy

import io.github.oviron.libmihomo.Clash
import io.github.oviron.libmihomo.InvokeInterface
import io.github.oviron.libmihomo.TunInterface
import org.json.JSONObject

internal object NativeCoreFactory {
    fun create(): NativeProxyCore = LibMihomoCore()
}

internal class LibMihomoCore : NativeProxyCore {
    override val tunAddress = "172.19.0.2"
    override val tunDns = "1.1.1.1"

    override fun setup(
        nativeLibraryDirectory: String,
        configDirectory: String,
        sdk: Int,
        selected: Map<String, String>,
        done: (String?) -> Unit
    ) {
        Clash.load(nativeLibraryDirectory)
        val init = JSONObject().put("home-dir", configDirectory).put("version", sdk).toString()
        val options = JSONObject().put("selected-map", JSONObject(selected)).toString()
        Clash.quickSetup(init, options, done)
    }

    override fun startTun(fd: Int, callbacks: NativeTunCallbacks): Boolean {
        val bridge = object : TunInterface {
            override fun protect(fd: Int) {
                // This JNI bridge has a void protect callback; VpnService handles failure.
                callbacks.protectSocket(fd)
            }
            override fun resolverProcess(
                protocol: Int, source: String, target: String, uid: Int
            ): String = callbacks.resolverProcess(protocol, source, target, uid)
        }
        Clash.startTUN(fd, bridge, "kidstubetv", "system", "$tunAddress/30", tunDns, tunMtu)
        // Old API has no success result. Never claim it proves TUN is functional.
        return true
    }

    override fun stopTun() {
        if (!Clash.isLoaded()) return
        Clash.stopTun()
        Clash.setEventListener(null as InvokeInterface?)
    }
}
