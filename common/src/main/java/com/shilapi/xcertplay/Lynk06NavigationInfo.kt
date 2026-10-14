package com.shilapi.xcertplay

import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import java.lang.reflect.Proxy

/** BX11 AdapterAPI distances are meters; ETA is remaining seconds, not an epoch timestamp. */
internal data class Lynk06NavigationInfo(
    val turnId: Int,
    val nextMeters: Long,
    val destinationMeters: Long,
    val remainingSeconds: Long,
    val road: String,
    /** The working BX11 APK supplies 0. Apple's driving side is not a compass direction. */
    val drivingDirection: Int = 0,
    val navigationStatus: Int = 1,
) {
    fun proxy(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
        when (method.name) {
            "equals" -> self === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(self)
            "toString" -> "Lynk06NavigationInfo"
            "getNavigationStatus" -> navigationStatus
            "getNavigationTurnId" -> turnId
            "getNavigationTurnSVG" -> if (navigationStatus == 0) null else ""
            "getNextGuidancePointName" -> road
            "getDistanceToNextGuidancePoint" -> nextMeters
            "getDistanceToDestination" -> destinationMeters
            "getETA" -> remainingSeconds
            "getDrivingDirection" -> drivingDirection
            "getDayNightMode", "getMuteState" -> 0
            "getHighwayExitInfo", "getRoadCameraInfo", "getServiceAreaInfo" -> null
            // No lane data is decoded on this path. An empty typed array represents absence.
            "getLaneInfo" -> java.lang.reflect.Array.newInstance(method.returnType.componentType, 0)
            else -> error("Unsupported BX11 navigation getter: ${method.name}")
        }
    }

    companion object {
        /** 与原车 stopNavi 一致：清空卡片内容，并明确告知仪表导航已经结束。 */
        fun stopped(): Lynk06NavigationInfo = Lynk06NavigationInfo(0, 0, 0, 0, "", navigationStatus = 0)

        fun from(guidance: ClusterTurnGuidance?): Lynk06NavigationInfo? {
            guidance ?: return null
            val distance = guidance.remainingMeters ?: return null
            val seconds = guidance.remainingSeconds ?: return null
            val turn = turnId(guidance.appleManeuverType)
            // Missing data must never become a fabricated route or a straight instruction.
            if (turn < 0 || distance < 0 || seconds < 0 || guidance.distanceMeters < 0) return null
            return Lynk06NavigationInfo(turn, guidance.distanceMeters.toLong(), distance, seconds, guidance.road)
        }

        /** Mapping verified against the independently working BX11 APK's ClusterTurnMapper. */
        internal fun turnId(apple: Int?): Int = when (apple) {
            3, 11 -> 109
            1, 20 -> 102
            2, 21 -> 103
            4, 18, 19, 26 -> 108
            5, 8, 9, 51 -> 120
            6 -> 111
            7 -> 112
            10, 12, 24, 25, 27 -> 115
            13, 22, 49, 52 -> 104
            14, 23, 50, 53 -> 105
            47 -> 106
            48 -> 107
            in 28..37 -> apple!! + 93
            in 38..46 -> 130
            else -> -1
        }
    }
}
