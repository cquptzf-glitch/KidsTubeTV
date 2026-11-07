package dev.kidstubetv.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SettingsStore(context: Context) {
    companion object {
        // Serializes writes across *different* SettingsStore instances used by
        // automatic and manual subscription updates in the same process.
        private val PROFILE_LOCK = Any()
    }
    private val appContext = context.applicationContext
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
        set(v) = synchronized(PROFILE_LOCK) {
            val incoming = v.trim()
            if (apiKey != incoming) {
                secrets.put("youtube_api_key", incoming)
                prefs.edit().putLong("youtube_api_revision",
                    prefs.getLong("youtube_api_revision", 0L) + 1L).apply()
            }
        }

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

    /** Parent controls continuous playback; default retains existing behavior. */
    var autoplayNext: Boolean
        get() = prefs.getBoolean("autoplay_next", true)
        set(v) { prefs.edit().putBoolean("autoplay_next", v).apply() }

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

    val channelNames: Map<String, String>
        get() {
            val json = runCatching {
                JSONObject(prefs.getString("channel_names", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            return json.keys().asSequence().mapNotNull { key ->
                if (!key.matches(Regex("UC[A-Za-z0-9_-]{22}"))) return@mapNotNull null
                json.optString(key).takeIf(String::isNotBlank)?.let { key to it }
            }.toMap()
        }

    fun rememberChannelName(id: String, title: String) {
        require(id.matches(Regex("UC[A-Za-z0-9_-]{22}"))) { "无效频道 ID" }
        val label = ChannelLabelPolicy.clean(title)
        if (label.isBlank()) return
        synchronized(PROFILE_LOCK) {
            val copy = JSONObject(channelNames)
            copy.put(id, label)
            prefs.edit().putString("channel_names", copy.toString()).apply()
        }
    }

    val videoNames: Map<String, String>
        get() {
            val json = runCatching {
                JSONObject(prefs.getString("video_names", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            return json.keys().asSequence().mapNotNull { key ->
                if (!key.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
                json.optString(key).takeIf(String::isNotBlank)?.let { key to it }
            }.take(250).toMap()
        }

    /** Stores a display label, never the video's content or credentials. */
    fun rememberVideoName(id: String, title: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "无效视频 ID" }
        val label = ChannelLabelPolicy.clean(title)
        if (label.isBlank()) return
        synchronized(PROFILE_LOCK) {
            val entries = videoNames.toMutableMap().apply { put(id, label) }
                .entries.toList().takeLast(250)
            prefs.edit().putString("video_names",
                JSONObject(entries.associate { it.key to it.value }).toString()).apply()
        }
    }

    fun forgetVideoNames(ids: Collection<String>) = synchronized(PROFILE_LOCK) {
        if (ids.isEmpty()) return@synchronized
        val json = JSONObject(videoNames)
        ids.forEach { json.remove(it) }
        prefs.edit().putString("video_names", json.toString()).apply()
    }

    val approvedChannels: Set<String> get() = tokenize(channelsText)
        .filter { it.matches(Regex("UC[A-Za-z0-9_-]{22}")) }.toSet()
    val allowedChannels: Set<String> get() = approvedChannels

    /** Advance the small home window so importing many channels stays useful. */
    fun nextHomeChannelWindow(limit: Int = 5): List<String> = synchronized(PROFILE_LOCK) {
        val channels = allowedChannels.toList()
        if (channels.isEmpty()) return@synchronized emptyList()
        val cursor = prefs.getInt("home_channel_cursor", 0)
        val selected = ApprovedChannelRotation.select(channels, cursor, limit)
        val next = ApprovedChannelRotation.nextCursor(channels.size, cursor, limit)
        prefs.edit().putInt("home_channel_cursor", next).apply()
        selected
    }
    val approvedVideoIds: Set<String> get() = tokenize(allowedVideosText)
        .filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.toSet()
    val blockedVideoIds: Set<String> get() = tokenize(blockedVideosText)
        .filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.toSet()

    val profileFile: File get() = File(dir, "mihomo/config.yaml")
    val hasProfile: Boolean get() = profileFile.isFile && profileFile.length() > 0
    val hasBackupProfile: Boolean get() =
        File(profileFile.parentFile, "config-backup.yaml").isFile

    /** VPN can start before YouTube OAuth is linked; content still stays locked. */
    val proxyReady: Boolean get() = hasProfile && hasPin
    val hasYouTubeAuth: Boolean get() = apiKey.isNotBlank() || GoogleTvAuth(appContext).isLinked
    /** Non-secret epochs ensure returning from settings refreshes the current catalog. */
    val credentialRevision: String get() =
        "${prefs.getLong("youtube_api_revision", 0L)}:${GoogleTvAuth(appContext).authRevision}"
    val ready: Boolean get() = proxyReady && hasYouTubeAuth &&
        (allowedChannels.isNotEmpty() || approvedVideoIds.isNotEmpty())
    fun oauthAccessToken(): String = GoogleTvAuth(appContext).authorizedToken()

    /** Persist user-selected Mihomo selector targets across app restarts. */
    val selectedProxyNodes: Map<String, String>
        get() = synchronized(PROFILE_LOCK) {
            val raw = prefs.getString("proxy_selections", "{}") ?: "{}"
            val data = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            val pairs = data.keys().asSequence().mapNotNull { key ->
                data.optString(key).takeIf(String::isNotBlank)?.let { key to it }
            }.toMap()
            ProxySelectionPolicy.sanitize(pairs)
        }

    fun rememberProxySelection(group: String, node: String) = synchronized(PROFILE_LOCK) {
        val updated = selectedProxyNodes.toMutableMap().apply { put(group, node) }
        prefs.edit().putString("proxy_selections",
            JSONObject(ProxySelectionPolicy.sanitize(updated)).toString()).apply()
    }

    fun clearProxySelections() = synchronized(PROFILE_LOCK) {
        prefs.edit().remove("proxy_selections").apply()
    }

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

    fun writeProfile(content: String) = synchronized(PROFILE_LOCK) {
        SubscriptionFormat.validateYaml(content)
        ProfileFileStore(profileFile).install(content.toByteArray(Charsets.UTF_8))
    }

    fun restoreBackup(): Boolean = synchronized(PROFILE_LOCK) {
        ProfileFileStore(profileFile).restore()
    }

    private fun tokenize(v: String) = v.split(Regex("[,;\\s]+")).map(String::trim).filter(String::isNotBlank)

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
}
