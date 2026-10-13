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
