package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.VehicleIntegration

/**
 * 已采集固件的设置策略；构建目标不能代替完整实车匹配。
 *
 * 默认值与隐藏项由匹配到的 [com.shilapi.xcertplay.vehicle.VehicleAdapter] 声明，
 * 菜单、搜索和设置读取因此共用同一份规则。
 */
internal object VehicleSettingsPolicy {
    private val defaults get() = VehicleIntegration.settingsDefaults

    val showHarmanFullscreen: Boolean get() = defaults.showHarmanFullscreen
    // 领克 MTK 使用独立的 MTK 低延迟路径，保留该路径的用户回退开关。
    val showQualcommLowLatency: Boolean get() = defaults.showQualcommLowLatency
    val mtkDecoderTuningDefault: Boolean get() = defaults.mtkDecoderTuningDefault
    val directVideoOutputDefault: Boolean get() = defaults.directVideoOutputDefault
    val clusterMapEnabledDefault: Boolean get() = defaults.clusterMapEnabledDefault
}
