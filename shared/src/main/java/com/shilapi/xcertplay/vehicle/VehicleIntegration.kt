package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.shared.R
import java.util.Locale

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

    /** 厂商事件和录音回退仅用于完整匹配的实车，构建目标不能启用它们。 */
    fun isDetectedLynk06(): Boolean = detectedAdapter()?.id == LYNK_06_2023_PETROL

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

    /**
     * 车机屏幕的物理参数，适配显示比例与按键布局的依据。
     *
     * 用应用上下文的 resources，而不是界面的：界面可能被 InterfaceSize 改过密度。
     */
    private fun screenDescription(context: Context): String {
        val metrics = context.resources.displayMetrics
        val refresh = runCatching {
            (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
        }.getOrNull()
        val refreshText = refresh?.takeIf { it > 0f }?.let { " %.1fHz".format(Locale.US, it) } ?: ""
        // 视频适配参考：GPU 支持的 GLES 版本，不需要创建 GL 上下文。
        val glEs = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .deviceConfigurationInfo.reqGlEsVersion
        }.getOrNull()?.takeIf { it != 0 }?.let { "; glEs=${it shr 16}.${it and 0xFFFF}" } ?: ""
        return "${metrics.widthPixels}x${metrics.heightPixels} ${metrics.densityDpi}dpi$refreshText$glEs"
    }

    fun diagnosticReport(context: Context): String = buildString {
        appendLine("Vehicle build target: ${context.getString(R.string.config_vehicle_profile)}")
        appendLine("车机自动匹配：${detectedProfile() ?: "未匹配，使用构建目标"}")
        appendLine("有效车型适配器：${effectiveAdapter(context).id}")
        appendLine("BYD integration: ${bydEnabled(context)}")
        appendLine("Device identifiers: manufacturer=${Build.MANUFACTURER}; brand=${Build.BRAND}; model=${Build.MODEL}; product=${Build.PRODUCT}; device=${Build.DEVICE}; board=${Build.BOARD}; hardware=${Build.HARDWARE}")
        val versionName = firmwareVersionName()
        appendLine("Firmware: ${Build.DISPLAY}; ABI=${Build.SUPPORTED_ABIS.joinToString()}")
        // 适配新车的依据：fingerprint 是固件身份，incremental 常是车机固件的真实版本号。
        appendLine("Firmware fingerprint: ${Build.FINGERPRINT}")
        appendLine("Firmware build: id=${Build.ID}; incremental=${Build.VERSION.INCREMENTAL}; type=${Build.TYPE}; tags=${Build.TAGS}; bootloader=${Build.BOOTLOADER}")
        appendLine("Firmware version name: ${versionName ?: "unknown"}")
        // SoC 型号只在 Android 12+ 可查；解码器兼容问题（如 MTK 调优）按它归因。
        if (Build.VERSION.SDK_INT >= 31) appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        appendLine("Screen: ${screenDescription(context)}")
        appendLine("Geely input contract verified: ${GeelyHeadUnit.wheelInputSupported}")
        appendLine("Siri source: ${if (GeelyHeadUnit.siriVoiceCommunication) "VOICE_COMMUNICATION" else "VOICE_RECOGNITION (standard fallback enabled)"}")
        appendLine("Audio focus policy: standard; display policy: standard")
    }
}
