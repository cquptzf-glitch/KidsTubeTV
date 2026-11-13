package dev.kidstubetv.data

/** Pure response sniffer: servers can return HTML/Base64 with HTTP 200. */
internal object SubscriptionResponsePolicy {
    fun validate(text: String) {
        val data = text.trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        if (data.startsWith("<!doctype", ignoreCase = true) ||
            data.startsWith("<html", ignoreCase = true) ||
            data.startsWith("<head", ignoreCase = true)) {
            throw SubscriptionDownloadException(SubscriptionProblem.HTML_RESPONSE)
        }
        val compact = data.filterNot(Char::isWhitespace)
        if (compact.length > 24 && !compact.contains(':') &&
            compact.all { it.isLetterOrDigit() || it in "+/=_-" }) {
            throw SubscriptionDownloadException(SubscriptionProblem.BASE64_RESPONSE)
        }
        try {
            SubscriptionFormat.validateYaml(data)
        } catch (error: IllegalArgumentException) {
            val unsafe = error.message.orEmpty().let {
                it.contains("allow-lan") || it.contains("external-controller")
            }
            throw SubscriptionDownloadException(
                if (unsafe) SubscriptionProblem.UNSAFE_PROFILE else SubscriptionProblem.INVALID_YAML
            )
        }
    }

    fun retryWithClashUserAgent(error: SubscriptionDownloadException): Boolean =
        error.problem in setOf(
            SubscriptionProblem.HTML_RESPONSE,
            SubscriptionProblem.BASE64_RESPONSE,
            SubscriptionProblem.INVALID_YAML
        ) || (error.problem == SubscriptionProblem.HTTP &&
            error.httpStatus in listOf(403, 406, 415))
}

/** Client IDs recognized by different Clash/Mihomo subscription panels. */
internal object SubscriptionRequestPolicy {
    // One trial per identifier; keep a stable ordering and no arbitrary headers.
    val userAgents: List<String> = listOf(
        "FlClash", "ClashMetaForAndroid", "clash.meta", "clash"
    )
}
