package com.shilapi.xcertplay.vehicle

/**
 * 比亚迪 / DiLink 目标。
 *
 * BYD 不是靠车机标识自动识别的，而是由构建目标选择（config_vehicle_profile=byd，
 * 或上游保留的 generic + config_byd_integration=true）。它的车辆数据、仪表和氛围灯
 * 由 BYD 子系统按 [VehicleCapabilities.bydIntegration] 统一开关。
 */
object BydAdapter : VehicleAdapter {
    override val id: String = VehicleAdapters.BYD

    override val capabilities: VehicleCapabilities = VehicleCapabilities(
        bydIntegration = true,
    )

    // BYD 沿用上游默认设置策略。
    override val settingsDefaults: VehicleSettingsDefaults = VehicleSettingsDefaults()

    /** 构建目标车型，不参与设备标识自动匹配。 */
    override val autoDetected: Boolean = false

    override fun matches(device: VehicleDeviceInfo): Boolean = false
}
