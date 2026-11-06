package dev.kidstubetv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minimal persistent watching/favourites store, independent of YouTube account login. */
class WatchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("kids_history_v1", Context.MODE_PRIVATE)
    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet().orEmpty()
    fun isFavorite(id: String) = id in favorites()
    fun toggleFavorite(id: String): Boolean {
        val fav = favorites().toMutableSet()
        val selected = if (id in fav) { fav.remove(id); false } else { fav.add(id); true }
        prefs.edit().putStringSet("favorites", fav).apply()
        return selected
    }
    fun recentIds(): List<String> {
        val a = JSONArray(prefs.getString("recent", "[]"))
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    }
    fun record(id: String) {
        val updated = listOf(id) + recentIds().filter { it != id }
        val a = JSONArray()
        updated.take(50).forEach(a::put)
        prefs.edit().putString("recent", a.toString()).apply()
    }
    fun clear() { prefs.edit().remove("recent").remove("favorites").apply() }
}

/** Daily screen-time ledger; parental convenience, not a tamper-proof OS restriction. */
class WatchTime(context: Context) {
    private val prefs = context.getSharedPreferences("kids_watch_time_v1", Context.MODE_PRIVATE)
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    @Synchronized fun elapsedSeconds(): Int {
        return if (prefs.getString("date", "") == day()) prefs.getInt("seconds", 0) else 0
    }
    @Synchronized fun addSecond(): Int {
        val result = elapsedSeconds() + 1
        prefs.edit().putString("date", day()).putInt("seconds", result).apply()
        return result
    }
    fun remaining(limitMinutes: Int): Int = (limitMinutes * 60 - elapsedSeconds()).coerceAtLeast(0)
}
