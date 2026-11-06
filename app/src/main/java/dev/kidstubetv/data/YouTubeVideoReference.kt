package dev.kidstubetv.data

import java.net.URI

/** Parent-facing YouTube watch/short/video URLs to canonical 11-char video ID. */
object YouTubeVideoReference {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    fun parse(input: String): String {
        val text = input.trim()
        if (videoId.matches(text)) return text
        require(text.length in 15..2048) { "请输入 11 位视频 ID 或 YouTube 链接" }
        val url = runCatching { URI(text) }.getOrElse {
            throw IllegalArgumentException("YouTube 视频链接格式错误")
        }
        require(url.scheme.equals("https", true) &&
            url.userInfo == null &&
            url.host?.lowercase() in hosts) {
            "仅支持 HTTPS YouTube 官方视频链接"
        }
        val path = url.path.trim('/')
        val candidate = when {
            url.host.equals("youtu.be", ignoreCase = true) -> path.takeIf { !it.contains('/') }
            path == "watch" -> url.query?.split('&')?.firstOrNull { it.startsWith("v=") }?.drop(2)
            path.startsWith("shorts/") -> path.removePrefix("shorts/").takeIf { !it.contains('/') }
            path.startsWith("embed/") -> path.removePrefix("embed/").takeIf { !it.contains('/') }
            path.startsWith("live/") -> path.removePrefix("live/").takeIf { !it.contains('/') }
            else -> null
        }
        require(candidate != null && videoId.matches(candidate)) {
            "未能从链接解析出有效的视频 ID"
        }
        return candidate
    }
}
