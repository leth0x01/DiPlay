package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.shared.R

/** 构建目标与实车能力分离；目标车型不代表接口已在该固件验证。 */
object VehicleIntegration {
    const val LYNK_06_2023_PETROL = "lynk_06_2023_petrol"

    fun profile(context: Context): String = context.getString(R.string.config_vehicle_profile)

    fun isLynk06(context: Context): Boolean = profile(context) == LYNK_06_2023_PETROL

    fun bydEnabled(context: Context): Boolean = context.resources.getBoolean(R.bool.config_byd_integration)

    fun diagnosticReport(context: Context): String = buildString {
        appendLine("Vehicle target: ${profile(context)} (build target; not a vehicle test result)")
        appendLine("BYD integration: ${bydEnabled(context)}")
        appendLine("Device identifiers: product=${Build.PRODUCT}; device=${Build.DEVICE}; model=${Build.MODEL}; brand=${Build.BRAND}")
        appendLine("Firmware: ${Build.DISPLAY}; ABI=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Geely input contract verified: ${GeelyHeadUnit.wheelInputSupported}")
        appendLine("Siri source: ${if (GeelyHeadUnit.siriVoiceCommunication) "VOICE_COMMUNICATION" else "VOICE_RECOGNITION (standard fallback enabled)"}")
        appendLine("Audio focus policy: standard; display policy: standard")
    }
}
