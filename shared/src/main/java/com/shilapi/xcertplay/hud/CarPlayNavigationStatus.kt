package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** CarPlay 的真实导航进行状态，与下一转向是否有提示、仪表是否显示地图分别管理。 */
object CarPlayNavigationStatus {
    /** 导航状态链路的可观测快照；计数跨会话累计，直到进程结束或测试重置。 */
    data class Snapshot(
        val active: Boolean,
        val routeGuidanceFrames: Int,
        val validNavigationFrames: Int,
        val activeTransitions: Int,
        val lastFrameElapsedNanos: Long,
    )

    private val state = FceNavigationState()
    private var listener: ((Boolean) -> Unit)? = null
    private var routeGuidanceFrames = 0
    private var validNavigationFrames = 0
    private var activeTransitions = 0
    private var lastFrameElapsedNanos = 0L

    @Synchronized fun setListener(next: (Boolean) -> Unit) {
        listener = next
        next(state.active)
    }

    @Synchronized fun clearListener(current: (Boolean) -> Unit) {
        if (listener === current) listener = null
    }

    @Synchronized internal fun onFrame(frame: Iap2Frame) {
        val previous = state.active
        routeGuidanceFrames++
        lastFrameElapsedNanos = System.nanoTime()
        if (state.accept(frame.messageId, frame.payload)) validNavigationFrames++
        if (previous != state.active) {
            activeTransitions++
            listener?.invoke(state.active)
        }
    }

    @Synchronized internal fun clear() {
        val previous = state.active
        state.clear()
        if (previous) {
            activeTransitions++
            listener?.invoke(false)
        }
    }

    @Synchronized internal fun snapshot(): Snapshot = Snapshot(
        state.active, routeGuidanceFrames, validNavigationFrames, activeTransitions, lastFrameElapsedNanos,
    )

    /** 仅测试使用；实车诊断需要跨会话保留累计计数。 */
    @Synchronized internal fun resetDiagnostics() {
        routeGuidanceFrames = 0
        validNavigationFrames = 0
        activeTransitions = 0
        lastFrameElapsedNanos = 0L
    }

    /** 领克仪表诊断的 FCE 导航状态段：确认帧是否到达、状态是否翻转。 */
    fun diagnosticReport(): String {
        val s = snapshot()
        val ago = if (s.lastFrameElapsedNanos == 0L) "none"
            else "${(System.nanoTime() - s.lastFrameElapsedNanos) / 1_000_000}ms"
        return buildString {
            appendLine("navigationActive=${s.active}")
            appendLine("routeGuidanceFrames=${s.routeGuidanceFrames}")
            appendLine("validNavigationFrames=${s.validNavigationFrames}")
            appendLine("activeTransitions=${s.activeTransitions}")
            append("lastNavigationFrameAgo=$ago")
        }
    }
}
