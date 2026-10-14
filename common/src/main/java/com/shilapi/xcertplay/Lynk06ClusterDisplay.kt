package com.shilapi.xcertplay

import android.view.Display
import com.shilapi.xcertplay.vehicle.VehicleIntegration

/** 根据实车采集限定仪表输出，构建目标本身不能作为显示设备授权依据。 */
internal object Lynk06ClusterDisplay {
    const val NAME = "DashboardMapDisplay"
    const val WIDTH = 1920
    const val HEIGHT = 720
    const val DEFAULT_SCALE_PERCENT = 100
    const val DEFAULT_MARKER_Y_PERCENT = 50

    fun supported(): Boolean = VehicleIntegration.detectedCapabilities.oemClusterMapDisplay

    fun accepts(display: Display): Boolean {
        if (!supported() || !display.isValid || display.displayId == Display.DEFAULT_DISPLAY ||
            (display.flags and Display.FLAG_PRESENTATION) == 0 || display.name != NAME) return false
        val size = ClusterMapPresentation.sizeOf(display)
        return size.x == WIDTH && size.y == HEIGHT
    }
}
