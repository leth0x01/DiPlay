package com.shilapi.xcertplay

import android.content.Context

/** 低密度宽屏使用较大的操作尺寸；分屏、窄窗和低高度窗口保留紧凑布局。 */
internal object CarWindowLayout {
    private const val WIDE_MIN_WIDTH_DP = 1400
    private const val WIDE_MIN_HEIGHT_DP = 600
    const val WIDE_MENU_WIDTH_DP = 1120
    const val WIDE_DIALOG_WIDTH_DP = 960
    const val WIDE_CONTROL_HEIGHT_DP = 72
    const val WIDE_TEXT_SP = 20f
    const val WIDE_TITLE_SP = 26f

    fun isWide(widthDp: Int, heightDp: Int) =
        widthDp >= WIDE_MIN_WIDTH_DP && heightDp >= WIDE_MIN_HEIGHT_DP

    fun isWide(context: Context): Boolean = context.resources.configuration.let {
        isWide(it.screenWidthDp, it.screenHeightDp)
    }

    fun controlHeightDp(context: Context, compactHeight: Int) =
        if (isWide(context)) WIDE_CONTROL_HEIGHT_DP else compactHeight
}
