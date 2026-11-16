package dev.kidstubetv.ui

/** Hardware D-pad left navigation must preserve movement between V2 columns. */
internal object DenseSettingsFocusPolicy {
    fun forceRailOnLeft(insideRightPane: Boolean): Boolean = !insideRightPane
}
