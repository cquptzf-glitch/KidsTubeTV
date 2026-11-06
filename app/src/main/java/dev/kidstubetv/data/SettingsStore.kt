package dev.kidstubetv.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kids_config_v1", Context.MODE_PRIVATE)
    private val filesDir = context.applicationContext.filesDir

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) { prefs.edit().putString("api_key", value.trim()).apply() }

    var channelsText: String
        get() = prefs.getString("channels", "") ?: ""
        set(value) { prefs.edit().putString("channels", value.trim()).apply() }

    var allowedVideosText: String
        get() = prefs.getString("allowed_videos", "") ?: ""
        set(value) { prefs.edit().putString("allowed_videos", value.trim()).apply() }

    var blockedVideosText: String
        get() = prefs.getString("blocked_videos", "") ?: ""
        set(value) { prefs.edit().putString("blocked_videos", value.trim()).apply() }

    val allowedChannels: Set<String> get() = tokens(channelsText).filter { it.matches(Regex("UC[A-Za-z0-9_-]{22}")) }.toSet()
    val approvedVideoIds: Set<String> get() = tokens(allowedVideosText).toSet()
    val blockedVideoIds: Set<String> get() = tokens(blockedVideosText).toSet()

    val profileFile: File get() = File(filesDir, "mihomo/profile.yaml")
    val hasProfile: Boolean get() = profileFile.isFile && profileFile.length() > 0
    val ready: Boolean get() = apiKey.isNotBlank() &&
        (allowedChannels.isNotEmpty() || approvedVideoIds.isNotEmpty()) && hasProfile && hasPin

    val hasPin: Boolean get() = prefs.contains("pin_hash") && prefs.contains("pin_salt")

    fun setPin(pin: String) {
        require(pin.length >= 4 && pin.all(Char::isDigit)) { "PIN must contain at least 4 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pinHash(pin, salt)
        prefs.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt64 = prefs.getString("pin_salt", null) ?: return false
        val hash64 = prefs.getString("pin_hash", null) ?: return false
        return runCatching {
            val salt = Base64.decode(salt64, Base64.DEFAULT)
            val saved = Base64.decode(hash64, Base64.DEFAULT)
            MessageDigest.isEqual(saved, pinHash(pin, salt))
        }.getOrDefault(false)
    }

    fun writeProfile(content: String) {
        require(content.length in 12..1024 * 1024) { "Profile must be 12 B to 1 MiB" }
        require("proxies:" in content || "proxy-providers:" in content) {
            "Import a complete Mihomo YAML profile with proxies or proxy-providers"
        }
        profileFile.parentFile?.mkdirs()
        val temp = File(profileFile.parentFile, "profile.tmp")
        temp.writeText(content, Charsets.UTF_8)
        require(temp.renameTo(profileFile)) { "Unable to save proxy profile" }
    }

    private fun tokens(value: String) = value.split(Regex("[,;\\s]+"))
        .map(String::trim).filter(String::isNotBlank)

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
