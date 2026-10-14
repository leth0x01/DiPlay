package com.shilapi.xcertplay.vehicle

/**
 * 未匹配具体车型、也没有启用 BYD 时的通用回退。
 *
 * 所有厂商能力保持关闭，品牌名称不能代替实车匹配；只保留不依赖车型的通用功能。
 */
object GenericAdapter : VehicleAdapter {
    override val id: String = VehicleAdapters.GENERIC

    override val capabilities: VehicleCapabilities = VehicleCapabilities()

    override val settingsDefaults: VehicleSettingsDefaults = VehicleSettingsDefaults()

    /** 通用回退不参与设备标识自动匹配。 */
    override val autoDetected: Boolean = false

    override fun matches(device: VehicleDeviceInfo): Boolean = false
}
