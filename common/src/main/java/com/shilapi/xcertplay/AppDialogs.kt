package com.shilapi.xcertplay

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.ContextThemeWrapper
import com.shilapi.xcertplay.host.R

internal interface AppAppearanceOwner {
    val currentAppNight: Boolean
}

internal fun Context.appDialogTheme(): Int = if (
    (this as? AppAppearanceOwner)?.currentAppNight ?: resolveAppNightNow()
) {
    R.style.Theme_Xcertplay_Dialog_Dark
} else {
    R.style.Theme_Xcertplay_Dialog_Light
}

internal fun Context.appDialogContext(): Context = ContextThemeWrapper(this, appDialogTheme())

internal fun Context.appDialogBuilder(
    night: Boolean = (this as? AppAppearanceOwner)?.currentAppNight ?: resolveAppNightNow(),
): AlertDialog.Builder = object : AlertDialog.Builder(this,
    if (night) R.style.Theme_Xcertplay_Dialog_Dark else R.style.Theme_Xcertplay_Dialog_Light) {
    override fun create(): AlertDialog = super.create().also {
        // 提前安装内容再统一样式，调用方的 OnShowListener 仍可处理校验和保存。
        it.create()
        it.applyAppDialogStyle(DiPlayPalette.of(night))
    }
}

internal fun Context.appDialog(): Dialog = Dialog(this, appDialogTheme())
