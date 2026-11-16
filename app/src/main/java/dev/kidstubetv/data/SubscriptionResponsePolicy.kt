package dev.kidstubetv.data

/** Format-aware validation delegates to the exact same normalizer as downloads. */
internal object SubscriptionResponsePolicy {
    fun validate(text: String) {
        SubscriptionContentNormalizer.normalize(text)
    }

    fun retryWithClashUserAgent(error: SubscriptionDownloadException): Boolean =
        error.problem in setOf(
            SubscriptionProblem.HTML_RESPONSE,
            SubscriptionProblem.BASE64_RESPONSE,
            SubscriptionProblem.INVALID_YAML
        ) || (error.problem == SubscriptionProblem.HTTP &&
            error.httpStatus in listOf(403, 406, 415))
}

/** Client identifiers seen by common subscription panels; bounded attempts. */
internal object SubscriptionRequestPolicy {
    val userAgents = listOf("FlClash", "ClashMetaForAndroid", "clash.meta", "clash")
}
