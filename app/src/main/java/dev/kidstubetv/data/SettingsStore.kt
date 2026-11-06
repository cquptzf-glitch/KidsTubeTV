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
    private val secrets = SecurePrefs(context.applicationContext)
    private val dir = context.applicationContext.filesDir

    var apiKey: String
        get() = secrets.get("youtube_api_key").ifEmpty {
            // One-time migration from the original unencrypted MVP.
            (prefs.getString("api_key", "") ?: "").also {
                if (it.isNotBlank()) {
                    secrets.put("youtube_api_key", it)
                    prefs.edit().remove("api_key").apply()
                }
            }
        }
        set(v) { secrets.put("youtube_api_key", v.trim()) }

    var subscriptionUrl: String
        get() = secrets.get("clash_subscription")
        set(v) { secrets.put("clash_subscription", if (v.isBlank()) "" else SubscriptionFormat.normalizeUrl(v)) }

    var channelsText: String
        get() = prefs.getString("channels", "") ?: ""
        set(v) { prefs.edit().putString("channels", v.trim()).apply() }

    var allowedVideosText: String
        get() = prefs.getString("allowed_videos", "") ?: ""
        set(v) { prefs.edit().putString("allowed_videos", v.trim()).apply() }

    var blockedVideosText: String
        get() = prefs.getString("blocked_videos", "") ?: ""
        set(v) { prefs.edit().putString("blocked_videos", v.trim()).apply() }

    var searchAllowed: Boolean
        get() = prefs.getBoolean("allow_search", false)
        set(v) { prefs.edit().putBoolean("allow_search", v).apply() }

    var dailyLimitMinutes: Int
        get() = prefs.getInt("daily_limit", 30).coerceIn(5, 180)
        set(v) { prefs.edit().putInt("daily_limit", v.coerceIn(5,180)).apply() }

    var subscriptionIntervalHours: Int
        get() = prefs.getInt("subscription_hours", 24).coerceIn(0, 168)
        set(v) { prefs.edit().putInt("subscription_hours", v.coerceIn(0,168)).apply() }

    var subscriptionUpdatedAt: Long
        get() = prefs.getLong("subscription_updated", 0L)
        set(v) { prefs.edit().putLong("subscription_updated", v).apply() }

    val autoRefreshDue: Boolean
        get() = subscriptionUrl.isNotBlank() && subscriptionIntervalHours > 0 &&
            System.currentTimeMillis() - subscriptionUpdatedAt > subscriptionIntervalHours * 3_600_000L

    val approvedChannels: Set<String> get() = tokenize(channelsText)
        .filter { it.matches(Regex("UC[A-Za-z0-9_-]{22}")) }.toSet()
    val allowedChannels: Set<String> get() = approvedChannels
    val approvedVideoIds: Set<String> get() = tokenize(allowedVideosText)
        .filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.toSet()
    val blockedVideoIds: Set<String> get() = tokenize(blockedVideosText)
        .filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.toSet()

    val profileFile: File get() = File(dir, "mihomo/profile.yaml")
    val hasProfile: Boolean get() = profileFile.isFile && profileFile.length() > 0

    val ready: Boolean get() = apiKey.isNotBlank() &&
        (allowedChannels.isNotEmpty() || approvedVideoIds.isNotEmpty()) && hasProfile && hasPin

    val hasPin: Boolean get() = prefs.contains("pin_hash") && prefs.contains("pin_salt")
    var pinLockedUntil: Long
        get() = prefs.getLong("pin_locked_until", 0)
        private set(v) { prefs.edit().putLong("pin_locked_until", v).apply() }

    fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all(Char::isDigit)) { "PIN 需要 4 至 12 位数字" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pinHash(pin, salt)
        prefs.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("pin_failures", 0).putLong("pin_locked_until", 0).apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (System.currentTimeMillis() < pinLockedUntil) return false
        val salt64 = prefs.getString("pin_salt", null) ?: return false
        val hash64 = prefs.getString("pin_hash", null) ?: return false
        val result = runCatching {
            val salt = Base64.decode(salt64, Base64.DEFAULT)
            val saved = Base64.decode(hash64, Base64.DEFAULT)
            MessageDigest.isEqual(saved, pinHash(pin, salt))
        }.getOrDefault(false)
        val previous = prefs.getInt("pin_failures", 0)
        if (result) {
            prefs.edit().putInt("pin_failures", 0).putLong("pin_locked_until", 0).apply()
        } else {
            val next = previous + 1
            val lock = if (next >= 5) System.currentTimeMillis() + 120_000L else 0L
            prefs.edit().putInt("pin_failures", if (next >= 5) 0 else next)
                .putLong("pin_locked_until", lock).apply()
        }
        return result
    }

    @Synchronized fun writeProfile(content: String) {
        SubscriptionFormat.validateYaml(content)
        val file = profileFile
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "profile-new.tmp")
        val backup = File(file.parentFile, "profile-backup.yaml")
        temp.writeText(content, Charsets.UTF_8)
        require(temp.length() in 12..SubscriptionFormat.MAX_PROFILE_BYTES.toLong()) { "配置格式或大小异常" }
        if (file.exists()) {
            backup.delete()
            require(file.renameTo(backup)) { "无法备份旧配置" }
        }
        if (!temp.renameTo(file)) {
            if (backup.exists()) backup.renameTo(file)
            error("无法写入代理配置")
        }
    }

    @Synchronized fun restoreBackup(): Boolean {
        val backup = File(profileFile.parentFile, "profile-backup.yaml")
        if (!backup.isFile) return false
        profileFile.delete()
        return backup.renameTo(profileFile)
    }

    private fun tokenize(v: String) = v.split(Regex("[,;\\s]+")).map(String::trim).filter(String::isNotBlank)

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
}
