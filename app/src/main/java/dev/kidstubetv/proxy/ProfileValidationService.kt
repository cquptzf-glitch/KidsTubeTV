package dev.kidstubetv.proxy

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import dev.kidstubetv.data.ProfileValidationError
import dev.kidstubetv.data.ProfileValidationErrorPolicy
import io.github.oviron.libmihomo.Clash
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Isolated process avoids changing the live libmihomo singleton/VPN state. */
class ProfileValidationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(RECEIVER)
        }
        val directory = intent?.getStringExtra(HOME_DIR)
        val main = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)

        fun report(error: ProfileValidationError?) {
            if (!finished.compareAndSet(false, true)) return
            main.post {
                runCatching {
                    receiver?.send(if (error == null) VALID else INVALID,
                        android.os.Bundle().apply {
                            putInt(ERROR_KIND, error?.ordinal ?: -1)
                        })
                }
                // This service process must never outlive its one-time config
                // verification or retain temporary Mihomo listeners.
                main.postDelayed({
                    stopSelf(startId)
                    Process.killProcess(Process.myPid())
                }, 300L)
            }
        }
        main.postDelayed({
            report(ProfileValidationError.TIMEOUT)
        }, 30_000L)

        Thread({
            try {
                val home = File(directory ?: "").canonicalFile
                val cache = cacheDir.canonicalFile
                require(home.path.startsWith(cache.path + File.separator)) {
                    "invalid validation directory"
                }
                require(File(home, "config.yaml").isFile) { "missing config.yaml" }
                Clash.load(applicationInfo.nativeLibraryDir)
                Clash.assertReady()
                val init = JSONObject()
                    .put("home-dir", home.absolutePath)
                    .put("version", Build.VERSION.SDK_INT)
                    .toString()
                val setup = JSONObject().put("selected-map", JSONObject()).toString()
                Clash.quickSetup(init, setup) { message ->
                    report(if (message.isNullOrBlank()) null
                        else ProfileValidationErrorPolicy.classify(message))
                }
            } catch (error: Throwable) {
                report(ProfileValidationErrorPolicy.classify(error.message))
            }
        }, "kids-native-config-verify").start()
        return START_NOT_STICKY
    }

    companion object {
        const val HOME_DIR = "profile_validation_home"
        const val RECEIVER = "profile_validation_receiver"
        const val ERROR_KIND = "profile_validation_kind"
        const val VALID = 1
        const val INVALID = 0
    }
}
