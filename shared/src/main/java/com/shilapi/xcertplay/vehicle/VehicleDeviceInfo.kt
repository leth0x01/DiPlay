package com.shilapi.xcertplay.vehicle

/**
 * 车机标识快照。
 *
 * 来源既可以是 [android.os.Build]，也可以是诊断、测试或未来的远程配置。
 * 车型匹配只读取这份快照，不直接依赖全局 Build，便于测试和逐项采集。
 */
data class VehicleDeviceInfo(
    val product: String? = null,
    val device: String? = null,
    val model: String? = null,
    val hardware: String? = null,
    val sdk: Int = 0,
    val brand: String? = null,
    /** ro.build.version.versionname；部分车机固件只在这里带车型代号。 */
    val firmwareVersionName: String? = null,
    val display: String? = null,
)
