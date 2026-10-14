package com.shilapi.xcertplay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import kotlin.math.roundToInt

/** 三指菜单的尺寸、间距和形状统一由这里定义。 */
internal object CarPlayMenuLayout {
    const val MAX_WIDTH_DP = 720
    const val WINDOW_MARGIN_DP = 16
    const val SECTION_GAP_DP = 16
    const val CONTROL_GAP_DP = 12
    const val CARD_PADDING_DP = 20
    const val CONTROL_HEIGHT_DP = 60
    const val INPUT_MIN_WIDTH_DP = 180
    const val OUTLINE_DP = 1
    const val CATEGORY_TAG = "carplay_menu_category"
    const val CONTENT_TAG = "carplay_menu_content"
    const val HEADER_TAG = "carplay_menu_header"
    const val FOOTER_TAG = "carplay_menu_footer"
    const val PANEL_HEIGHT_PERCENT = 94
    const val PANEL_ELEVATION_DP = 24
    const val SCRIM_COLOR = 0xB3000000.toInt()

    private fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    fun width(context: Context, available: Int): Int {
        val availableDp = (available / context.resources.displayMetrics.density).toInt()
        val wide = CarWindowLayout.isWide(availableDp, context.resources.configuration.screenHeightDp)
        val maximum = if (wide) CarWindowLayout.WIDE_MENU_WIDTH_DP else MAX_WIDTH_DP
        return minOf(context.dp(maximum), (available - context.dp(WINDOW_MARGIN_DP) * 2).coerceAtLeast(1))
    }

    private fun Context.controlHeight() = dp(CarWindowLayout.controlHeightDp(this, CONTROL_HEIGHT_DP))

    private fun Context.shape(fill: Int, outline: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = AppDialogLayout.radiusDp(CarWindowLayout.controlHeightDp(this@shape, CONTROL_HEIGHT_DP).toFloat()) * resources.displayMetrics.density
        setStroke(dp(OUTLINE_DP), outline)
    }

    fun styleInput(input: EditText, palette: DiPlayPalette) = with(input) {
        setTextColor(palette.primaryText)
        setHintTextColor(palette.secondaryText)
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(palette.ripple), context.shape(palette.background, palette.outline), null)
        minHeight = context.controlHeight()
        setPaddingRelative(context.dp(CARD_PADDING_DP), context.dp(CONTROL_GAP_DP),
            context.dp(CARD_PADDING_DP), context.dp(CONTROL_GAP_DP))
    }

    /**
     * 面板表面：与设置页面一致，使用背景色 + 描边的圆角面板，圆角随触控高度变化。
     */
    fun chrome(
        context: Context, palette: DiPlayPalette,
        topOnly: Boolean = false, bottomOnly: Boolean = false,
    ): GradientDrawable {
        val radius = AppDialogLayout.radiusDp(CarWindowLayout.controlHeightDp(context, CONTROL_HEIGHT_DP).toFloat()) * context.resources.displayMetrics.density
        val top = if (bottomOnly) 0f else radius
        val bottom = if (topOnly) 0f else radius
        return GradientDrawable().apply {
            setColor(palette.background)
            cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            setStroke(context.dp(OUTLINE_DP), palette.outline)
        }
    }

    /**
     * 分区卡片表面：与设置页面的卡片一致（surface 填充 + outline 描边 + 圆角）。
     */
    fun card(context: Context, palette: DiPlayPalette): GradientDrawable =
        context.shape(palette.surface, palette.outline)

    /** 领克宽屏放大文字与触控高度；分页菜单构建完成后调用。 */
    fun adaptToScreen(panel: View, palette: DiPlayPalette) {
        styleChoices(panel, palette)
        if (CarWindowLayout.isWide(panel.context)) enlargeText(panel)
    }

    /** 上游玻璃面板布局沿用领克车机的窗口宽度、触控高度和安全边距。 */
    fun build(
        context: Context, panel: FrameLayout, content: LinearLayout, save: DiPlayButton,
        palette: DiPlayPalette, cancel: () -> Unit,
    ) {
        panel.background = chrome(context, palette)
        panel.clipToOutline = true
        panel.elevation = context.dp(PANEL_ELEVATION_DP).toFloat()
        val title = content.getChildAt(0) as TextView
        content.removeView(title)
        title.setPadding(0, 0, 0, 0)
        title.minHeight = context.controlHeight()
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        val close = DiPlayButton(context).apply {
            text = context.getString(R.string.close)
            textSize = 16f
            minHeight = context.controlHeight()
            contentDescription = context.getString(R.string.discard_changes_and_exit_settings)
            style(DiPlayButtonKind.SECONDARY, palette, overlay = true)
            setOnClickListener { cancel() }
        }
        val header = LinearLayout(context).apply {
            tag = HEADER_TAG
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = chrome(context, palette, topOnly = true)
            setPadding(context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP),
                context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP))
            val titleParams = LinearLayout.LayoutParams(0, -2, 1f)
            val closeParams = LinearLayout.LayoutParams(-2, -2)
            if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                closeParams.marginEnd = context.dp(CONTROL_GAP_DP)
                addView(close, closeParams)
                addView(title, titleParams)
            } else {
                closeParams.marginStart = context.dp(CONTROL_GAP_DP)
                addView(title, titleParams)
                addView(close, closeParams)
            }
        }
        content.setPadding(context.dp(CARD_PADDING_DP), context.dp(SECTION_GAP_DP),
            context.dp(CARD_PADDING_DP), context.dp(SECTION_GAP_DP))
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        save.minHeight = context.controlHeight()
        val footer = LinearLayout(context).apply {
            tag = FOOTER_TAG
            orientation = LinearLayout.VERTICAL
            background = chrome(context, palette, bottomOnly = true)
            setPadding(context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP),
                context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP))
            addView(save, LinearLayout.LayoutParams(-1, -2))
        }
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
        adaptToScreen(panel, palette)
    }

    private fun enlargeText(view: View) {
        if (view is TextView) {
            val currentSp = view.textSize / view.resources.displayMetrics.scaledDensity
            view.textSize = maxOf(currentSp, CarWindowLayout.WIDE_TEXT_SP)
            if (view is android.widget.Button || view is EditText) {
                view.minHeight = view.context.controlHeight()
                view.minimumHeight = view.context.controlHeight()
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) enlargeText(view.getChildAt(index))
    }

    private fun styleChoices(view: View, palette: DiPlayPalette) {
        if (view is RadioButton) {
            view.minHeight = view.context.controlHeight()
            view.buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, palette.secondaryText))
        }
        if (view is RadioGroup && view.orientation == LinearLayout.HORIZONTAL) {
            view.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                val buttons = (0 until view.childCount).mapNotNull { view.getChildAt(it) as? RadioButton }
                val required = buttons.maxOfOrNull { it.paint.measureText(it.text.toString()) + it.compoundPaddingLeft + it.compoundPaddingRight } ?: 0f
                val orientation = if (required * buttons.size > right - left) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                if (view.orientation != orientation) {
                    view.orientation = orientation
                    buttons.forEach {
                        it.layoutParams = if (orientation == LinearLayout.VERTICAL) RadioGroup.LayoutParams(-1, -2)
                            else RadioGroup.LayoutParams(0, -2, 1f)
                    }
                }
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleChoices(view.getChildAt(index), palette)
    }
}
