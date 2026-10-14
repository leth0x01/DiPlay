package com.shilapi.xcertplay.vehicle

/**
 * 车机标识到车型适配器的注册表。
 *
 * 现有适配器：
 * - [Lynk06PetrolAdapter]：已采集的 2023 款领克 06 燃油版，按设备标识自动匹配；
 * - [BydAdapter]：BYD/DiLink 目标，由构建目标选择；
 * - [GenericAdapter]：未匹配任何车型时的通用回退。
 *
 * 新增车型（例如领克 01/02/03，或 2021 款 01、2023 款 03）时：
 * 1. 新建 `XxxAdapter : VehicleAdapter`，写明已采集的设备标识、能力和设置默认值；
 * 2. 加入 [all]；只有取得实车证据后才让 `matches` 返回 true，否则保持不匹配；
 * 3. 在 config_vehicle_profile 增加同名标识即可作为构建目标。
 */
object VehicleAdapters {
    const val GENERIC = "generic"
    const val BYD = "byd"
    const val LYNK_06_2023_PETROL = "lynk_06_2023_petrol"

    /** 具体车型在前、构建目标与通用回退在后；检测只使用声明了自动匹配的适配器。 */
    val all: List<VehicleAdapter> = listOf(
        Lynk06PetrolAdapter,
        BydAdapter,
        GenericAdapter,
    )

    /** 按标识查找适配器，用于把 config_vehicle_profile 资源值解析为车型。 */
    fun byId(id: String?): VehicleAdapter? {
        val normalized = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return all.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
    }

    /** 未匹配具体车型时返回 null，调用方再回退到构建目标或通用行为。 */
    fun detect(device: VehicleDeviceInfo): VehicleAdapter? =
        all.firstOrNull { it.autoDetected && it.matches(device) }

    fun detect(product: String?, device: String?, model: String?, hardware: String?, sdk: Int): VehicleAdapter? =
        detect(VehicleDeviceInfo(product, device, model, hardware, sdk))
}
