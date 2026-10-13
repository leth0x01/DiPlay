package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.shared.R

/** 构建目标与实车能力分离；目标车型不代表接口已在该固件验证。 */
object VehicleIntegration {
    const val LYNK_06_2023_PETROL = "lynk_06_2023_petrol"

    /** 只匹配已采集的完整设备组合，不根据亿咖通品牌推断具体车型配置。 */
    fun detectedProfile(product: String?, device: String?, model: String?, hardware: String?, sdk: Int): String? =
        if (product.equals("IHU514G", true) && device.equals("IHU514G", true) &&
            isLynk06Model(model) && hardware.equals("xecarx", true) && sdk == 28)
            LYNK_06_2023_PETROL else null

    /** 不同固件的 Build.MODEL 回报不同：旧固件为 IHU514G，BX11 固件直接回报车型代号 BX11。 */
    fun isLynk06Model(model: String?): Boolean {
        val normalized = model?.trim() ?: return false
        return normalized.equals("IHU514G", true) || normalized.equals("BX11", true)
    }

    private fun detectedProfile(): String? =
        detectedProfile(Build.PRODUCT, Build.DEVICE, Build.MODEL, Build.HARDWARE, Build.VERSION.SDK_INT)

    /** 厂商事件和录音回退仅用于完整匹配的实车，构建目标不能启用它们。 */
    fun isDetectedLynk06(): Boolean = detectedProfile() == LYNK_06_2023_PETROL

    /**
     * BX11 是领克 06 的车型代号：BX11 固件的 Build.MODEL 直接为 BX11，
     * 固件版本号（如 SW0BX110520H8EHH）也带有它。车型能力仍以完整设备组合匹配为准。
     */
    fun isBx11Model(model: String?): Boolean = model?.trim().equals("BX11", ignoreCase = true)

    fun isBx11(): Boolean = isBx11Model(Build.MODEL) || isBx11Firmware()

    /** BX11 固件的版本号里带有车型代号（如 SW0BX110520H8EHH），作辅助诊断依据。 */
    fun isBx11Firmware(versionName: String?, display: String?): Boolean =
        listOfNotNull(versionName, display).any { it.contains("BX11", ignoreCase = true) }

    fun isBx11Firmware(): Boolean = isBx11Firmware(firmwareVersionName(), Build.DISPLAY)

    /** ro.build.version.versionname；部分车机固件只在这个属性里带车型代号。 */
    fun firmwareVersionName(): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java).invoke(null, "ro.build.version.versionname") as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun profile(context: Context): String = detectedProfile() ?: context.getString(R.string.config_vehicle_profile)

    fun isLynk06(context: Context): Boolean = profile(context) == LYNK_06_2023_PETROL

    fun bydEnabled(context: Context): Boolean = detectedProfile() == null && context.resources.getBoolean(R.bool.config_byd_integration)

    fun diagnosticReport(context: Context): String = buildString {
        appendLine("Vehicle build target: ${context.getString(R.string.config_vehicle_profile)}")
        appendLine("车机自动匹配：${detectedProfile() ?: "未匹配，使用构建目标"}")
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
