package dev.kidstubetv.data

/**
 * Selector group/name persistence validation. No nodes or secrets are echoed
 * into logs. Invalid old selections are ignored after subscription changes.
 */
object ProxySelectionPolicy {
    const val MAX_GROUPS = 64
    fun sanitize(values: Map<String, String>): Map<String, String> =
        values.entries.asSequence().filter { (group, node) ->
            valid(group) && valid(node)
        }.take(MAX_GROUPS).associate { it.key to it.value }

    private fun valid(value: String): Boolean =
        value.length in 1..160 && value.isNotBlank() &&
        value.none { it.isISOControl() }
}
