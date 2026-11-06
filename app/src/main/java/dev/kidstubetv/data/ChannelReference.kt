package dev.kidstubetv.data

import java.net.URI

/** Only official YouTube URL hosts and explicit @handles are accepted. */
sealed class ChannelReference {
    data class Id(val value: String) : ChannelReference()
    data class Handle(val value: String) : ChannelReference()
    data class Username(val value: String) : ChannelReference()

    companion object {
        private val channelId = Regex("UC[A-Za-z0-9_-]{22}")
        private val handle = Regex("[\\p{L}\\p{N}._-]{3,30}")
        private val username = Regex("[A-Za-z0-9._-]{1,100}")

        fun parse(raw: String): ChannelReference {
            val text = raw.trim()
            require(text.length in 3..2048) { "请输入频道 ID、@用户名或频道 URL" }
            if (channelId.matches(text)) return Id(text)
            if (text.startsWith("@") && handle.matches(text.drop(1))) {
                return Handle(text)
            }
            val url = runCatching { URI(text) }.getOrElse {
                throw IllegalArgumentException("频道地址格式不正确")
            }
            val host = url.host?.lowercase() ?: ""
            require(url.scheme.equals("https", true) && url.userInfo == null &&
                host in setOf("youtube.com", "www.youtube.com", "m.youtube.com")) {
                "只能使用 HTTPS YouTube 官方频道地址"
            }
            val segments = url.path.trim('/').split('/').filter(String::isNotBlank)
            require(segments.isNotEmpty()) { "链接不是频道地址" }
            return when {
                segments[0] == "channel" && segments.size == 2 &&
                    channelId.matches(segments[1]) -> Id(segments[1])
                segments[0].startsWith("@") && segments.size == 1 &&
                    handle.matches(segments[0].drop(1)) -> Handle(segments[0])
                segments[0] == "user" && segments.size == 2 &&
                    username.matches(segments[1]) -> Username(segments[1])
                else -> throw IllegalArgumentException(
                    "请使用频道 /channel/UC...、/@用户名 或 /user/名称 的链接"
                )
            }
        }
    }
}

data class YouTubeChannelInfo(val id: String, val title: String)
