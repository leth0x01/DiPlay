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
            model.equals("IHU514G", true) && hardware.equals("xecarx", true) && sdk == 28)
            LYNK_06_2023_PETROL else null

    private fun detectedProfile(): String? =
        detectedProfile(Build.PRODUCT, Build.DEVICE, Build.MODEL, Build.HARDWARE, Build.VERSION.SDK_INT)

    fun profile(context: Context): String = detectedProfile() ?: context.getString(R.string.config_vehicle_profile)

    fun isLynk06(context: Context): Boolean = profile(context) == LYNK_06_2023_PETROL

    fun bydEnabled(context: Context): Boolean = detectedProfile() == null && context.resources.getBoolean(R.bool.config_byd_integration)

    fun diagnosticReport(context: Context): String = buildString {
        appendLine("Vehicle build target: ${context.getString(R.string.config_vehicle_profile)}")
        appendLine("车机自动匹配：${detectedProfile() ?: "未匹配，使用构建目标"}")
        appendLine("BYD integration: ${bydEnabled(context)}")
        appendLine("Device identifiers: product=${Build.PRODUCT}; device=${Build.DEVICE}; model=${Build.MODEL}; brand=${Build.BRAND}")
        appendLine("Firmware: ${Build.DISPLAY}; ABI=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Geely input contract verified: ${GeelyHeadUnit.wheelInputSupported}")
        appendLine("Siri source: ${if (GeelyHeadUnit.siriVoiceCommunication) "VOICE_COMMUNICATION" else "VOICE_RECOGNITION (standard fallback enabled)"}")
        appendLine("Audio focus policy: standard; display policy: standard")
    }
}
