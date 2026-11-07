package dev.kidstubetv.data

/**
 * A failed request is not an empty YouTube catalog. Permit partial success,
 * but never report a completely failed fetch as a successful empty result.
 * This is deliberately separate from per-video child admission.
 */
internal object HomeFeedLoadPolicy {
    fun <T> successfulGroups(attempts: List<Result<List<T>>>): List<List<T>> {
        if (attempts.isNotEmpty() && attempts.all { it.isFailure }) {
            error("所有已批准来源均请求失败，请检查 VPN、YouTube API 与账号权限")
        }
        return attempts.mapNotNull { it.getOrNull() }
    }
}
