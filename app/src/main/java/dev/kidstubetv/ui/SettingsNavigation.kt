package dev.kidstubetv.ui

/**
 * One route table shared by television and touch layouts.
 * Keep NETWORK at index 1: older entry points use SettingsActivity.EXTRA_SECTION=1.
 */
internal enum class SettingsDestination(val title: String, val navigationTitle: String) {
    OVERVIEW("设置概览", "概览"),
    NETWORK("应用网络", "应用网络"),
    YOUTUBE("YouTube 账号", "YouTube 账号"),
    CONTENT("儿童内容", "儿童内容"),
    PARENT("家长与安全", "家长与安全");

    companion object {
        fun fromIndex(index: Int): SettingsDestination =
            entries.getOrNull(index) ?: OVERVIEW
    }
}

internal object SettingsNavigationPolicy {
    fun back(current: SettingsDestination): SettingsDestination? =
        if (current == SettingsDestination.OVERVIEW) null else SettingsDestination.OVERVIEW
}
