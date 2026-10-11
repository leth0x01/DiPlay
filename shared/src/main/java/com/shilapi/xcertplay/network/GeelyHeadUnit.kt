package com.shilapi.xcertplay.network

import android.os.Build

/** 厂商名称仅用于识别线索，不能证明输入契约或录音策略兼容。 */
object GeelyHeadUnit {
    data class Capabilities(val wheelInput: Boolean, val siriVoiceCommunication: Boolean)

    /** 当前两项策略只在这组 Android 11 车机标识上有验证记录。 */
    fun capabilities(product: String?, model: String?, sdk: Int): Capabilities {
        val verified = product.equals("geely_fx11", ignoreCase = true) &&
            model.equals("cs11_a4", ignoreCase = true) && sdk == 30
        return Capabilities(wheelInput = verified, siriVoiceCommunication = verified)
    }

    private val current: Capabilities
        get() = capabilities(Build.PRODUCT, Build.MODEL, Build.VERSION.SDK_INT)

    val wheelInputSupported: Boolean get() = current.wheelInput
    val siriVoiceCommunication: Boolean get() = current.siriVoiceCommunication
}
