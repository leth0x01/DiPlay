package com.shilapi.xcertplay

import com.ecarx.xui.adaptapi.diminteraction.INaviInteraction
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import org.junit.Assert.*
import org.junit.Test

class Lynk06NavigationInfoTest {
    private val real = ClusterTurnGuidance(5, 0, 123, "测试道路", arrivalEpochSeconds = 1_800_000_000,
        remainingSeconds = 11280, remainingMeters = 281800, appleManeuverType = 14, drivingSide = 0)

    @Test fun `actual route uses long meter fields and duration instead of arrival epoch`() {
        val info = Lynk06NavigationInfo.from(real)!!.proxy(INaviInteraction.INavigationInfo::class.java)
            as INaviInteraction.INavigationInfo
        assertEquals(1, info.getNavigationStatus())
        assertEquals(105, info.getNavigationTurnId())
        assertEquals(123L, info.getDistanceToNextGuidancePoint())
        assertEquals(281800L, info.getDistanceToDestination())
        assertEquals(11280L, info.getETA())
        assertEquals("测试道路", info.getNextGuidancePointName())
        assertTrue(info.getLaneInfo().isEmpty())
        assertEquals(0, info.getMuteState())
        assertEquals(0, info.getDayNightMode())
        assertNull(info.getHighwayExitInfo())
        assertNull(info.getRoadCameraInfo())
        assertNull(info.getServiceAreaInfo())
    }

    @Test fun `incomplete or unknown route is never fabricated`() {
        assertNull(Lynk06NavigationInfo.from(null))
        assertNull(Lynk06NavigationInfo.from(real.copy(remainingSeconds = null)))
        assertNull(Lynk06NavigationInfo.from(real.copy(remainingMeters = -1)))
        assertNull(Lynk06NavigationInfo.from(real.copy(appleManeuverType = 0)))
        assertNull(Lynk06NavigationInfo.from(real.copy(drivingSide = null)))
    }

    @Test fun `turn vocabulary preserves straight turns and roundabout exits`() {
        assertEquals(109, Lynk06NavigationInfo.turnId(3))
        assertEquals(102, Lynk06NavigationInfo.turnId(1))
        assertEquals(103, Lynk06NavigationInfo.turnId(2))
        assertEquals(121, Lynk06NavigationInfo.turnId(28))
        assertEquals(130, Lynk06NavigationInfo.turnId(46))
        assertEquals(-1, Lynk06NavigationInfo.turnId(null))
    }

    @Test fun `Apple driving side is not reported as vehicle heading`() {
        val info = Lynk06NavigationInfo.from(real.copy(drivingSide = 1))!!
            .proxy(INaviInteraction.INavigationInfo::class.java) as INaviInteraction.INavigationInfo
        assertEquals(0, info.getDrivingDirection())
    }
}
