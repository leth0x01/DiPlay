package com.shilapi.xcertplay.vehicle

/**
 * 车型相关的设置默认值。
 *
 * 默认值只用于尚未保存该项的用户；已保存的开启/关闭选择继续生效。
 * 隐藏项的状态在运行读取时返回关闭，但不删除原始偏好，避免破坏导入或旧版数据。
 */
data class VehicleSettingsDefaults(
    val showHarmanFullscreen: Boolean = true,
    val showQualcommLowLatency: Boolean = true,
    val mtkDecoderTuningDefault: Boolean = false,
    val directVideoOutputDefault: Boolean = false,
    val clusterMapEnabledDefault: Boolean = false,
)
