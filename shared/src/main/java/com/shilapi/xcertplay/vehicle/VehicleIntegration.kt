package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.shared.R

/**
 * 车型适配门面：把运行中的车机解析为 [VehicleAdapter]，并对外暴露能力与设置默认值。
 *
 * 具体的身份、能力与默认值由 [VehicleAdapters] 里的适配器声明；这里只做解析和兜底。
 * 构建目标与实车能力分离，目标车型不代表接口已在该固件验证。
 */
object VehicleIntegration {
    const val LYNK_06_2023_PETROL = VehicleAdapters.LYNK_06_2023_PETROL

    /** 当前运行环境的车机标识快照。 */
    fun deviceInfo(): VehicleDeviceInfo = VehicleDeviceInfo(
        product = Build.PRODUCT,
        device = Build.DEVICE,
        model = Build.MODEL,
        hardware = Build.HARDWARE,
        sdk = Build.VERSION.SDK_INT,
        brand = Build.BRAND,
        firmwareVersionName = firmwareVersionName(),
        display = Build.DISPLAY,
    )

    /** 只匹配已采集的完整设备组合，不根据亿咖通品牌推断具体车型配置。 */
    fun detectedProfile(product: String?, device: String?, model: String?, hardware: String?, sdk: Int): String? =
        VehicleAdapters.detect(product, device, model, hardware, sdk)?.id

    /** 运行时匹配到的适配器；未匹配时为 null。 */
    fun detectedAdapter(): VehicleAdapter? = VehicleAdapters.detect(deviceInfo())

    private fun detectedProfile(): String? = detectedAdapter()?.id

    /** 已匹配车型的能力；未匹配时全部关闭，未知固件保持通用行为。 */
    val detectedCapabilities: VehicleCapabilities
        get() = detectedAdapter()?.capabilities ?: VehicleCapabilities()

    /** 已匹配车型的设置默认值；未匹配时使用通用默认值。 */
    val settingsDefaults: VehicleSettingsDefaults
        get() = detectedAdapter()?.settingsDefaults ?: VehicleSettingsDefaults()

    /** 不同固件的 Build.MODEL 回报不同：旧固件为 IHU514G，BX11 固件直接回报车型代号 BX11。 */
    fun isLynk06Model(model: String?): Boolean = LynkModelIdentifiers.isLynk06Model(model)

    /** 厂商事件和录音回退仅用于完整匹配的实车，构建目标不能启用它们。 */
    fun isDetectedLynk06(): Boolean = detectedAdapter()?.id == LYNK_06_2023_PETROL

    /**
     * BX11 是领克 06 的车型代号：BX11 固件的 Build.MODEL 直接为 BX11，
     * 固件版本号（如 SW0BX110520H8EHH）也带有它。车型能力仍以完整设备组合匹配为准。
     */
    fun isBx11Model(model: String?): Boolean = LynkModelIdentifiers.isBx11Model(model)

    fun isBx11(): Boolean = isBx11Model(Build.MODEL) || isBx11Firmware()

    /** BX11 固件的版本号里带有车型代号（如 SW0BX110520H8EHH），作辅助诊断依据。 */
    fun isBx11Firmware(versionName: String?, display: String?): Boolean =
        LynkModelIdentifiers.isBx11Firmware(versionName, display)

    fun isBx11Firmware(): Boolean = isBx11Firmware(firmwareVersionName(), Build.DISPLAY)

    /** ro.build.version.versionname；部分车机固件只在这个属性里带车型代号。 */
    fun firmwareVersionName(): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java).invoke(null, "ro.build.version.versionname") as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun profile(context: Context): String = detectedAdapter()?.id ?: targetAdapter(context).id

    fun isLynk06(context: Context): Boolean = profile(context) == LYNK_06_2023_PETROL

    /**
     * 构建目标解析出的适配器。
     *
     * config_vehicle_profile 直接给出车型时用该适配器；仍为 generic 时，
     * 保留上游 config_byd_integration=true 选择 BYD 的兼容行为。
     */
    fun targetAdapter(context: Context): VehicleAdapter {
        val resolved = VehicleAdapters.byId(context.getString(R.string.config_vehicle_profile))
        if (resolved != null && resolved.id != VehicleAdapters.GENERIC) return resolved
        return if (context.resources.getBoolean(R.bool.config_byd_integration)) BydAdapter
        else resolved ?: GenericAdapter
    }

    /** 运行时匹配到的能力优先；未匹配时使用构建目标的能力。 */
    fun effectiveAdapter(context: Context): VehicleAdapter = detectedAdapter() ?: targetAdapter(context)

    fun bydEnabled(context: Context): Boolean = effectiveAdapter(context).capabilities.bydIntegration

    fun diagnosticReport(context: Context): String = buildString {
        appendLine("Vehicle build target: ${context.getString(R.string.config_vehicle_profile)}")
        appendLine("车机自动匹配：${detectedProfile() ?: "未匹配，使用构建目标"}")
        appendLine("有效车型适配器：${effectiveAdapter(context).id}")
        appendLine("BYD integration: ${bydEnabled(context)}")
        appendLine("Device identifiers: product=${Build.PRODUCT}; device=${Build.DEVICE}; model=${Build.MODEL}; brand=${Build.BRAND}")
        val versionName = firmwareVersionName()
        appendLine("Firmware: ${Build.DISPLAY}; ABI=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("BX11: model=${isBx11Model(Build.MODEL)} firmware=${isBx11Firmware(versionName, Build.DISPLAY)} (versionname=${versionName ?: "unknown"})")
        appendLine("Geely input contract verified: ${GeelyHeadUnit.wheelInputSupported}")
        appendLine("Siri source: ${if (GeelyHeadUnit.siriVoiceCommunication) "VOICE_COMMUNICATION" else "VOICE_RECOGNITION (standard fallback enabled)"}")
        appendLine("Audio focus policy: standard; display policy: standard")
    }
}
