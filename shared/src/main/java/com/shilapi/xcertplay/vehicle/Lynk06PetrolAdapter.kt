package com.shilapi.xcertplay.vehicle

/**
 * 已采集的 2023 款领克 06 燃油版：IHU514G / xecarx / Android 9（亿咖通 E02）。
 *
 * 相关能力只在 product、device、model、hardware、API 28 完整匹配时启用；
 * 构建目标本身不能代替实车匹配，部分标识也不能放宽。
 */
object Lynk06PetrolAdapter : VehicleAdapter {
    override val id: String = VehicleAdapters.LYNK_06_2023_PETROL

    override val capabilities: VehicleCapabilities = VehicleCapabilities(
        // 领克 06 不使用 BYD/DiLink 专属接口。
        bydIntegration = false,
        // 实车已接入 DashboardMapDisplay 与 AdaptAPI 仪表链路。
        oemClusterMapDisplay = true,
        // 实车使用本车 XSFMediaCenter 与方控媒体键。
        oemMediaCenter = true,
        // 实车 XSFInputService 语音键有序广播。
        voiceKeyReceiver = true,
        // 实车 Android 9 只增加标准 MIC 末级回退。
        microphoneMicFallback = true,
        // 车机是音频接收端，使用 A2DP Sink / HFP Client。
        bluetoothSinkProfiles = true,
    )

    override val settingsDefaults: VehicleSettingsDefaults = VehicleSettingsDefaults(
        // 亿咖通 MTK 固件，隐藏哈曼全屏与高通低延迟开关。
        showHarmanFullscreen = false,
        showQualcommLowLatency = false,
        mtkDecoderTuningDefault = true,
        directVideoOutputDefault = true,
        clusterMapEnabledDefault = true,
    )

    override fun matches(device: VehicleDeviceInfo): Boolean =
        device.product.equals("IHU514G", true) && device.device.equals("IHU514G", true) &&
            LynkModelIdentifiers.isLynk06Model(device.model) &&
            device.hardware.equals("xecarx", true) && device.sdk == 28
}
