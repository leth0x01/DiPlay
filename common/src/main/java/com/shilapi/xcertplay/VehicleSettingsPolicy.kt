package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.VehicleIntegration

/** 已采集固件的设置策略；构建目标不能代替完整实车匹配。 */
internal object VehicleSettingsPolicy {
    private val lynk06: Boolean get() = VehicleIntegration.isDetectedLynk06()

    val showHarmanFullscreen: Boolean get() = !lynk06
    // 领克 MTK 使用独立的 MTK 低延迟路径，保留该路径的用户回退开关。
    val showQualcommLowLatency: Boolean get() = !lynk06
    val mtkDecoderTuningDefault: Boolean get() = lynk06
    val directVideoOutputDefault: Boolean get() = lynk06
}
