package dev.kidstubetv.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import dev.kidstubetv.proxy.ProfileValidationService
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the *same native quickSetup* as VPN startup in a disposable process.
 * It never touches the running VPN or installs a candidate until success.
 *
 * Call only on a background worker while parent settings are foregrounded.
 */
internal object ProfileValidationClient {
    fun verify(context: Context, normalizedYaml: String) {
        val app = context.applicationContext
        val directory = File(app.cacheDir, "profile-check/" + UUID.randomUUID().toString())
        try {
            require(directory.mkdirs()) { "无法准备校验目录" }
            File(directory, "config.yaml").writeText(normalizedYaml, Charsets.UTF_8)
            val result = AtomicReference<ProfileValidationError?>(ProfileValidationError.TIMEOUT)
            val done = CountDownLatch(1)
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(code: Int, data: android.os.Bundle?) {
                    result.set(
                        if (code == ProfileValidationService.VALID) null
                        else ProfileValidationError.values().getOrNull(
                            data?.getInt(ProfileValidationService.ERROR_KIND, -1) ?: -1
                        ) ?: ProfileValidationError.CORE
                    )
                    done.countDown()
                }
            }
            val action = Intent(app, ProfileValidationService::class.java)
                .putExtra(ProfileValidationService.HOME_DIR, directory.absolutePath)
                .putExtra(ProfileValidationService.RECEIVER, receiver)
            app.startService(action)
                ?: throw ProfileValidationException(ProfileValidationError.SERVICE)
            if (!done.await(35, TimeUnit.SECONDS)) {
                app.stopService(action)
                throw ProfileValidationException(ProfileValidationError.TIMEOUT)
            }
            result.get()?.let { throw ProfileValidationException(it) }
        } catch (error: ProfileValidationException) {
            throw error
        } catch (_: Exception) {
            throw ProfileValidationException(ProfileValidationError.SERVICE)
        } finally {
            runCatching { directory.deleteRecursively() }
        }
    }
}
