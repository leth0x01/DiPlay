package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CarPlayNavigationStatusTest {
    private val events = mutableListOf<Boolean>()
    private val listener: (Boolean) -> Unit = { events.add(it) }

    @After fun cleanup() {
        CarPlayNavigationStatus.clearListener(listener)
        CarPlayNavigationStatus.clear()
    }

    private fun state(value: Int) = Iap2Frame(0x5201, byteArrayOf(0, 5, 0, 1, value.toByte()))

    @Test fun `navigation without maneuver remains active until arrival or disconnect`() {
        CarPlayNavigationStatus.setListener(listener)
        CarPlayNavigationStatus.onFrame(state(1))
        CarPlayNavigationStatus.onFrame(state(5))
        CarPlayNavigationStatus.onFrame(state(2))
        CarPlayNavigationStatus.onFrame(state(1))
        CarPlayNavigationStatus.clear()
        assertEquals(listOf(false, true, false, true, false), events)
    }

    @Test fun `cancelled route ends navigation even when the source name remains cached`() {
        CarPlayNavigationStatus.setListener(listener)
        CarPlayNavigationStatus.onFrame(Iap2Frame(0x5201, byteArrayOf(0, 8, 0, 0x13, 0x4d, 0x61, 0x70, 0)))
        CarPlayNavigationStatus.onFrame(state(1))
        CarPlayNavigationStatus.onFrame(state(0))
        // 迟到的转向详情不能重新开启已结束的导航。
        CarPlayNavigationStatus.onFrame(Iap2Frame(0x5202, byteArrayOf(0, 5, 0, 3, 14)))
        assertEquals(listOf(false, true, false), events)
        assertFalse(CarPlayNavigationStatus.snapshot().active)
    }

    @Test fun `cleared source name ends navigation without a state field`() {
        CarPlayNavigationStatus.setListener(listener)
        CarPlayNavigationStatus.onFrame(state(1))
        CarPlayNavigationStatus.onFrame(Iap2Frame(0x5201, byteArrayOf(0, 4, 0, 0x13)))
        assertEquals(listOf(false, true, false), events)
        assertFalse(CarPlayNavigationStatus.snapshot().active)
    }

    @Test fun `invalid update does not end navigation`() {
        CarPlayNavigationStatus.setListener(listener)
        CarPlayNavigationStatus.onFrame(state(1))
        CarPlayNavigationStatus.onFrame(Iap2Frame(0x5201, byteArrayOf(0, 9, 0, 1, 0)))
        assertEquals(listOf(false, true), events)
    }

    @Test fun `old owner cannot remove replacement listener`() {
        val old: (Boolean) -> Unit = {}
        CarPlayNavigationStatus.setListener(old)
        CarPlayNavigationStatus.setListener(listener)
        CarPlayNavigationStatus.clearListener(old)
        CarPlayNavigationStatus.onFrame(state(1))
        assertEquals(listOf(false, true), events)
    }

    @Test fun `diagnostic counters track frames and transitions`() {
        CarPlayNavigationStatus.resetDiagnostics()
        CarPlayNavigationStatus.setListener(listener)
        CarPlayNavigationStatus.onFrame(state(1))
        // 非 RouteGuidanceUpdate 帧和损坏的 TLV 都不计入有效导航帧。
        CarPlayNavigationStatus.onFrame(Iap2Frame(0x5202, byteArrayOf(0)))
        CarPlayNavigationStatus.onFrame(Iap2Frame(0x5201, byteArrayOf(0, 9, 0, 1, 0)))
        CarPlayNavigationStatus.onFrame(state(2))
        val snapshot = CarPlayNavigationStatus.snapshot()
        assertFalse(snapshot.active)
        assertEquals(4, snapshot.routeGuidanceFrames)
        assertEquals(2, snapshot.validNavigationFrames)
        assertEquals(2, snapshot.activeTransitions)
        assertTrue(snapshot.lastFrameElapsedNanos > 0)
        val report = CarPlayNavigationStatus.diagnosticReport()
        assertTrue(report.contains("navigationActive=false"))
        assertTrue(report.contains("routeGuidanceFrames=4"))
        assertTrue(report.contains("validNavigationFrames=2"))
        assertTrue(report.contains("activeTransitions=2"))
        assertTrue(report.contains("lastNavigationFrameAgo="))
    }
}
