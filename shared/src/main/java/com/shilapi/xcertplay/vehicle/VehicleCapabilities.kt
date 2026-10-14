package com.shilapi.xcertplay.vehicle

/**
 * 车型能力开关。
 *
 * 每个字段默认关闭，未知固件因此保持通用行为；只有取得实车证据的适配器才打开对应项。
 * 新增能力时保持默认关闭，并让界面和运行入口读取同一个字段，避免菜单与运行时策略分叉。
 */
data class VehicleCapabilities(
    /** 构建目标是否启用 BYD/DiLink 专属接口。 */
    val bydIntegration: Boolean = false,
    /** 原车仪表副屏（DashboardMapDisplay）与厂商仪表链路。 */
    val oemClusterMapDisplay: Boolean = false,
    /** 原车媒体中心与方控媒体键。 */
    val oemMediaCenter: Boolean = false,
    /** 原车语音键有序广播。 */
    val voiceKeyReceiver: Boolean = false,
    /** Siri 录音在标准源失败后追加 MIC 末级回退。 */
    val microphoneMicFallback: Boolean = false,
    /** 车机作为蓝牙音频接收端，使用 A2DP Sink / HFP Client Profile。 */
    val bluetoothSinkProfiles: Boolean = false,
)
