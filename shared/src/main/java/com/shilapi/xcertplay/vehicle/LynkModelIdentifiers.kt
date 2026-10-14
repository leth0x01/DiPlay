package com.shilapi.xcertplay.vehicle

/**
 * 领克车型代号判断。
 *
 * 只接受完整代号，不用 `contains` 放宽，避免 `BX110` 之类被误判为 `BX11`。
 * 型号别名按已采集固件的实际回报维护，新增车型时在这里补充。
 */
internal object LynkModelIdentifiers {
    /** 领克 06：旧固件回报 IHU514G，BX11 固件直接回报车型代号 BX11。 */
    fun isLynk06Model(model: String?): Boolean {
        val normalized = model?.trim() ?: return false
        return normalized.equals("IHU514G", true) || normalized.equals("BX11", true)
    }

    fun isBx11Model(model: String?): Boolean = model?.trim().equals("BX11", ignoreCase = true)

    /** BX11 固件的版本号里带有车型代号（如 SW0BX110520H8EHH），作辅助诊断依据。 */
    fun isBx11Firmware(versionName: String?, display: String?): Boolean =
        listOfNotNull(versionName, display).any { it.contains("BX11", ignoreCase = true) }
}
