package dev.kidstubetv.data

/**
 * Apply a parent's multi-select decisions only to the items visible in that
 * specific picker. Explicitly deselected items are removed; unrelated manually
 * approved items remain untouched.
 */
object ApprovedPickerPolicy {
    fun <T> apply(existing: Set<T>, displayed: Collection<T>, selected: Collection<T>): Set<T> {
        val visible = displayed.toSet()
        return (existing - visible) + selected.filter { it in visible }
    }
}
