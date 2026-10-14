package com.shilapi.xcertplay.vehicle

/**
 * 单个车型的适配器：描述身份、能力和设置默认值。
 *
 * 运行逻辑仍放在各自的子系统里，适配器只回答"这台车是什么、支持什么、默认用什么"。
 * 这样新增领克 01/02/03 或不同年款时，只需新增一个适配器并注册，不必改动分散的 `if (车型)` 分支。
 */
interface VehicleAdapter {
    /** 稳定的车型标识，与 [android.content.res.Resources] 中 config_vehicle_profile 的取值一致。 */
    val id: String

    /** 该车型已取得证据的能力；未验证的能力保持默认关闭。 */
    val capabilities: VehicleCapabilities

    /** 该车型的设置默认值。 */
    val settingsDefaults: VehicleSettingsDefaults

    /**
     * 是否参与基于设备标识的自动匹配。
     *
     * 纯构建目标车型（BYD、通用回退）为 false：它们不是靠车机标识识别出来的。
     */
    val autoDetected: Boolean get() = true

    /**
     * 只有完整匹配已采集的设备组合时才返回 true。
     *
     * 品牌、单个型号字段或 Android 版本本身不能放宽匹配，否则会把另一套固件的策略应用到本车。
     */
    fun matches(device: VehicleDeviceInfo): Boolean
}
