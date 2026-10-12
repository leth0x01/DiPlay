package com.shilapi.xcertplay

import android.content.Context
import android.content.res.ColorStateList
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
    const val HEADER_TAG = "carplay_menu_header"
    const val FOOTER_TAG = "carplay_menu_footer"
    const val CARD_TAG = "carplay_menu_card"

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

    /** 保留原控件及其监听器，按已有分类标题组合为卡片。 */
    fun build(
        context: Context, panel: FrameLayout, content: LinearLayout, save: DiPlayButton,
        palette: DiPlayPalette, cancel: () -> Unit,
    ) {
        val title = content.getChildAt(0) as TextView
        content.removeView(title)
        title.setPadding(0, 0, 0, 0)
        title.minHeight = context.controlHeight()
        val header = LinearLayout(context).apply {
            tag = HEADER_TAG
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP),
                context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP))
        }
        val close = DiPlayButton(context).apply {
            text = "X"
            textSize = 22f
            minWidth = 0
            minHeight = context.controlHeight()
            setPadding(0, 0, 0, 0)
            contentDescription = context.getString(R.string.discard_changes_and_exit_settings)
            style(DiPlayButtonKind.SECONDARY, palette, overlay = false)
            setOnClickListener { cancel() }
        }
        header.addView(close, LinearLayout.LayoutParams(context.controlHeight(), context.controlHeight()))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(SECTION_GAP_DP) })

        val children = (0 until content.childCount).map { content.getChildAt(it) }
        content.removeAllViews()
        content.setPadding(context.dp(WINDOW_MARGIN_DP), 0, context.dp(WINDOW_MARGIN_DP), context.dp(SECTION_GAP_DP))
        var card: LinearLayout? = null
        for (child in children) {
            val heading = child.tag == CATEGORY_TAG
            if (card == null || heading) {
                card = LinearLayout(context).apply {
                    tag = CARD_TAG
                    orientation = LinearLayout.VERTICAL
                    setPadding(context.dp(CARD_PADDING_DP), context.dp(CARD_PADDING_DP),
                        context.dp(CARD_PADDING_DP), context.dp(CARD_PADDING_DP))
                    background = context.shape(palette.surface, palette.outline)
                }
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                    if (content.childCount > 1) topMargin = context.dp(SECTION_GAP_DP)
                })
            }
            val target = requireNotNull(card)
            target.addView(child, LinearLayout.LayoutParams(-1, -2).apply {
                if (target.childCount > 0) topMargin = context.dp(CONTROL_GAP_DP)
            })
        }
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(content) }
        val dismiss = DiPlayButton(context).apply {
            text = context.getString(R.string.cancel)
            textSize = 17f
            minHeight = context.controlHeight()
            style(DiPlayButtonKind.SECONDARY, palette, overlay = false)
            setOnClickListener { cancel() }
        }
        save.minHeight = context.controlHeight()
        val footer = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val desired = listOf(save, dismiss).maxOf { it.paint.measureText(it.text.toString()) + it.paddingLeft + it.paddingRight }
                val vertical = desired * 2 + context.dp(CONTROL_GAP_DP) > available
                orientation = if (vertical) VERTICAL else HORIZONTAL
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    val params = LayoutParams(if (vertical) -1 else 0, -2, if (vertical) 0f else 1f).apply {
                        if (index > 0) {
                            if (vertical) topMargin = context.dp(CONTROL_GAP_DP) else marginStart = context.dp(CONTROL_GAP_DP)
                        }
                    }
                    val old = child.layoutParams as LayoutParams
                    if (old.width != params.width || old.weight != params.weight || old.topMargin != params.topMargin || old.marginStart != params.marginStart) {
                        child.layoutParams = params
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            tag = FOOTER_TAG
            setPadding(context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP),
                context.dp(WINDOW_MARGIN_DP), context.dp(CONTROL_GAP_DP))
            addView(dismiss); addView(save)
        }
        val chrome = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(-1, -2))
        }
        panel.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        styleChoices(content, palette)
        if (CarWindowLayout.isWide(context)) enlargeText(chrome)

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
