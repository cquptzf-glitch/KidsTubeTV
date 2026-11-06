package dev.kidstubetv.proxy

import com.follow.clash.core.mobile.Mobile
import com.follow.clash.core.mobile.ResultHandler
import com.follow.clash.core.mobile.TunHandler
import org.json.JSONObject

internal object NativeCoreFactory {
    fun create(): NativeProxyCore = OfficialFlClashCore()
}

/** v0.9.3 official FlClash Go mobile API, not the legacy libmihomo AAR. */
internal class OfficialFlClashCore : NativeProxyCore {
    override val tunAddress = "198.18.0.1"
    override val tunDns = "198.18.0.2"

    override fun setup(
        nativeLibraryDirectory: String,
        configDirectory: String,
        sdk: Int,
        selected: Map<String, String>,
        done: (String?) -> Unit
    ) {
        val init = JSONObject().put("home-dir", configDirectory).put("version", sdk).toString()
        val setup = JSONObject().put("selected-map", JSONObject(selected)).toString()
        Mobile.quickSetup(init, setup, ResultHandler { payload ->
            done(payload?.toString(Charsets.UTF_8))
        })
    }

    override fun startTun(fd: Int, callbacks: NativeTunCallbacks): Boolean {
        val bridge = object : TunHandler {
            override fun protect(fd: Int): Boolean = callbacks.protectSocket(fd)
            override fun resolveUid(protocol: Int, source: String, target: String): Int =
                callbacks.resolveUid(protocol, source, target)
            override fun resolvePackage(uid: Int): String = callbacks.resolvePackage(uid)
        }
        return Mobile.startTun(
            fd, bridge, "system", "$tunAddress/30", tunDns,
            JSONObject().put("mtu", tunMtu).toString()
        )
    }

    override fun stopTun() {
        Mobile.stopTun()
        Mobile.setEventListener(null)
    }
}
