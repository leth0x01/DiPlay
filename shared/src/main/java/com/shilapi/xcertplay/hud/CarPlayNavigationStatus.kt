package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** CarPlay 的真实导航进行状态，与下一转向是否有提示、仪表是否显示地图分别管理。 */
object CarPlayNavigationStatus {
    private val state = FceNavigationState()
    private var listener: ((Boolean) -> Unit)? = null

    @Synchronized fun setListener(next: (Boolean) -> Unit) {
        listener = next
        next(state.active)
    }

    @Synchronized fun clearListener(current: (Boolean) -> Unit) {
        if (listener === current) listener = null
    }

    @Synchronized internal fun onFrame(frame: Iap2Frame) {
        val previous = state.active
        state.accept(frame.messageId, frame.payload)
        if (previous != state.active) listener?.invoke(state.active)
    }

    @Synchronized internal fun clear() {
        val previous = state.active
        state.clear()
        if (previous) listener?.invoke(false)
    }
}
