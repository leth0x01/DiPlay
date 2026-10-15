package com.shilapi.xcertplay.airplay

/** 参考 749001858/DiPlay 的 AirPlayDiagnostics，只记录有界的已知资源状态。 */
internal object AirPlayModeDiagnostics {
    private val flatKeys = listOf("screen", "mainAudio", "speech", "phoneCall", "turnByTurn")
    private val stateKeys = listOf("appStateID", "resourceID", "entity", "state", "speechMode",
        "transferType", "transferPriority", "takeConstraint", "borrowConstraint", "unborrowConstraint")

    fun summary(params: Map<String, Any?>): String {
        val fields = ArrayList<String>()
        fun record(map: Map<*, *>, prefix: String, keys: List<String>) {
            for (key in keys) {
                val value = scalar(map[key]) ?: continue
                fields.add("$prefix$key=$value")
            }
        }
        fun modes(map: Map<*, *>, prefix: String) {
            record(map, prefix, flatKeys)
            for (container in listOf("appStates", "resources")) {
                val entries = map[container] as? List<*> ?: continue
                entries.take(8).forEachIndexed { index, entry ->
                    if (entry is Map<*, *>) record(entry, "$prefix$container[$index].", stateKeys)
                }
            }
        }
        modes(params, "")
        (params["modes"] as? Map<*, *>)?.let { modes(it, "modes.") }
        return if (fields.isEmpty()) "knownStates=0" else fields.joinToString(" ").take(600)
    }

    private fun scalar(value: Any?): String? = when (value) {
        is Boolean -> value.toString()
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            .takeIf { it in -1L..65535L }?.toString()
        else -> null
    }
}
