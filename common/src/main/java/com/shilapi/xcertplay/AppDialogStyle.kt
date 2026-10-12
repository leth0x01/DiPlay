package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.TextView
import android.widget.TimePicker
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 弹窗尺寸统一使用 dp，并随窗口宽度和字体大小收敛。 */
internal object AppDialogLayout {
    const val MAX_WIDTH_DP = 640
    const val WINDOW_MARGIN_DP = 16
    const val CONTENT_PADDING_DP = 24
    const val CONTROL_HEIGHT_DP = 60
    const val CONTROL_PADDING_DP = 16
    const val CONTROL_VERTICAL_PADDING_DP = 12
    const val CONTROL_GAP_DP = 12
    const val OUTLINE_DP = 1
    const val FOCUS_OUTLINE_DP = 3
    const val TEXT_SP = 18f
    const val TITLE_SP = 24f

    fun widthDp(availableDp: Int, heightDp: Int = 0): Int {
        val maximum = if (CarWindowLayout.isWide(availableDp, heightDp)) CarWindowLayout.WIDE_DIALOG_WIDTH_DP else MAX_WIDTH_DP
        return (availableDp - WINDOW_MARGIN_DP * 2).coerceAtLeast(1).coerceAtMost(maximum)
    }
    fun radiusDp(heightDp: Float) = minOf(24f, heightDp * 20f / 56f)
}

/** 保留 AlertDialog 的选择、键盘、校验与按钮回调，只替换应用拥有的视觉样式。 */
internal fun AlertDialog.applyAppDialogStyle(palette: DiPlayPalette) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()
    val configuration = context.resources.configuration
    val wide = CarWindowLayout.isWide(context)
    val controlHeight = CarWindowLayout.controlHeightDp(context, AppDialogLayout.CONTROL_HEIGHT_DP)
    val textSp = if (wide) CarWindowLayout.WIDE_TEXT_SP else AppDialogLayout.TEXT_SP
    val titleSp = if (wide) CarWindowLayout.WIDE_TITLE_SP else AppDialogLayout.TITLE_SP
    val widthDp = AppDialogLayout.widthDp(configuration.screenWidthDp, configuration.screenHeightDp)
    val radius = AppDialogLayout.radiusDp(controlHeight.toFloat()) * density
    fun shape(color: Int, outline: Int, strokeDp: Int = AppDialogLayout.OUTLINE_DP) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(dp(strokeDp), outline)
    }
    fun controlBackground(color: Int, outline: Int) = RippleDrawable(
        ColorStateList.valueOf(palette.ripple),
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(color, palette.focusRing, AppDialogLayout.FOCUS_OUTLINE_DP))
            addState(intArrayOf(), shape(color, outline))
        }, null,
    )
    val decor = window?.decorView ?: return
    decor.layoutDirection = android.text.TextUtils.getLayoutDirectionFromLocale(context.resources.configuration.locales[0])
    window?.setBackgroundDrawable(shape(palette.surface, palette.outline).apply { cornerRadius = radius * 1.35f })
    window?.setLayout(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
    decor.clipToOutline = true

    fun panel(name: String): View? {
        val id = context.resources.getIdentifier(name, "id", "android")
        return if (id == 0) null else decor.findViewById(id)
    }
    for (name in listOf("parentPanel", "topPanel", "contentPanel", "customPanel", "buttonPanel")) {
        panel(name)?.setBackgroundColor(Color.TRANSPARENT)
    }
    (panel("alertTitle") as? TextView)?.apply {
        setTextColor(palette.primaryText)
        textSize = titleSp
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    decor.findViewById<TextView>(android.R.id.message)?.apply {
        setTextColor(palette.secondaryText)
        textSize = textSp
    }

    fun styleFields(view: View, insideTimePicker: Boolean = false) {
        val picker = insideTimePicker || view is TimePicker
        if (view is EditText && !picker) {
            view.setTextColor(palette.primaryText)
            view.setHintTextColor(palette.secondaryText)
            view.textSize = textSp
            view.minHeight = dp(controlHeight)
            view.backgroundTintList = null
            view.background = controlBackground(palette.background, palette.outline)
            view.setPaddingRelative(dp(AppDialogLayout.CONTROL_PADDING_DP), dp(AppDialogLayout.CONTROL_VERTICAL_PADDING_DP),
                dp(AppDialogLayout.CONTROL_PADDING_DP), dp(AppDialogLayout.CONTROL_VERTICAL_PADDING_DP))
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = dp(AppDialogLayout.CONTROL_GAP_DP)
                if (view.parent is FrameLayout) {
                    it.marginStart = dp(AppDialogLayout.CONTENT_PADDING_DP)
                    it.marginEnd = dp(AppDialogLayout.CONTENT_PADDING_DP)
                    it.bottomMargin = dp(AppDialogLayout.CONTROL_GAP_DP)
                }
            }
        } else if (view is CheckBox) {
            view.setTextColor(palette.primaryText)
            view.buttonTintList = ColorStateList.valueOf(palette.accent)
            view.textSize = textSp
            view.minHeight = dp(controlHeight)
        }
        if (view is Button && !picker) {
            view.textSize = textSp
            view.minHeight = dp(controlHeight)
            view.minimumHeight = dp(controlHeight)
            view.layoutParams?.let { params ->
                if (params.height > 0) params.height = maxOf(params.height, dp(controlHeight))
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleFields(view.getChildAt(index), picker)
    }
    styleFields(decor)

    listView?.let { list ->
        val original = list.adapter
        val checked = list.checkedItemPositions?.clone()
        list.selector = RippleDrawable(ColorStateList.valueOf(palette.ripple), null, shape(Color.WHITE, Color.WHITE))
        list.adapter = object : ListAdapter by original {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                original.getView(position, convertView, parent).also { row ->
                    fun styleRow(view: View) {
                        if (view is TextView) {
                            view.setTextColor(palette.primaryText)
                            view.textSize = textSp
                            view.minHeight = dp(controlHeight)
                            if (view is CheckedTextView) view.checkMarkTintList = ColorStateList.valueOf(palette.accent)
                        }
                        if (view is ViewGroup) for (index in 0 until view.childCount) styleRow(view.getChildAt(index))
                    }
                    styleRow(row)
                }
        }
        checked?.let {
            for (index in 0 until it.size()) list.setItemChecked(it.keyAt(index), it.valueAt(index))
        }
    }

    val actions = listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE)
        .mapNotNull { getButton(it)?.takeIf { button -> button.visibility != View.GONE } }
    if (actions.isEmpty()) return
    actions.forEach { button ->
        val primary = button === getButton(AlertDialog.BUTTON_POSITIVE)
        button.isAllCaps = false
        button.textSize = textSp
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(palette.secondaryText, if (primary) palette.onAccent else palette.primaryText)))
        button.setSingleLine(false)
        button.ellipsize = null
        button.minWidth = 0
        button.minimumWidth = 0
        button.minHeight = dp(controlHeight)
        button.minimumHeight = dp(controlHeight)
        button.stateListAnimator = null
        button.backgroundTintList = null
        button.background = controlBackground(if (primary) palette.accent else palette.button,
            if (primary) palette.accent else palette.outline)
        button.setPadding(dp(AppDialogLayout.CONTROL_PADDING_DP), dp(AppDialogLayout.CONTROL_VERTICAL_PADDING_DP),
            dp(AppDialogLayout.CONTROL_PADDING_DP), dp(AppDialogLayout.CONTROL_VERTICAL_PADDING_DP))
    }
    val available = dp(widthDp - AppDialogLayout.CONTENT_PADDING_DP * 2)
    val largestAction = actions.maxOf { it.paint.measureText(it.text.toString()) + it.paddingLeft + it.paddingRight }
    val stacked = largestAction * actions.size + dp(AppDialogLayout.CONTROL_GAP_DP) * (actions.size - 1) > available
    val actionPanel = panel("buttonPanel") as? ViewGroup ?: return
    actions.forEach { (it.parent as? ViewGroup)?.removeView(it) }
    actionPanel.removeAllViews()
    val row = LinearLayout(context).apply {
        orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(AppDialogLayout.CONTENT_PADDING_DP), dp(AppDialogLayout.CONTROL_GAP_DP),
            dp(AppDialogLayout.CONTENT_PADDING_DP), dp(AppDialogLayout.CONTENT_PADDING_DP))
    }
    val ordered = if (stacked) actions.reversed() else actions
    val rowHeight = maxOf(dp(controlHeight),
        ceil(actions.maxOf { it.paint.fontSpacing } + dp(AppDialogLayout.CONTROL_VERTICAL_PADDING_DP) * 2).toInt())
    ordered.forEachIndexed { index, button ->
        row.addView(button, if (stacked) LinearLayout.LayoutParams(-1, -2).apply {
            if (index > 0) topMargin = dp(AppDialogLayout.CONTROL_GAP_DP)
        } else LinearLayout.LayoutParams(0, rowHeight, 1f).apply {
            if (index > 0) marginStart = dp(AppDialogLayout.CONTROL_GAP_DP)
        })
    }
    actionPanel.addView(row, ViewGroup.LayoutParams(-1, -2))
    actionPanel.layoutParams = actionPanel.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    // AlertDialogLayout 优先保留按钮面板的 minimumHeight，防止长内容挤掉操作区。
    row.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    actionPanel.minimumHeight = row.measuredHeight + actionPanel.paddingTop + actionPanel.paddingBottom
}
