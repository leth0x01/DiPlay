package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NavigationGuidanceOutputTest {
    private val events = mutableListOf<ClusterTurnGuidance?>()
    private val listener: (ClusterTurnGuidance?) -> Unit = { events.add(it) }
    private fun tlv(id: Int, vararg bytes: Int): ByteArray =
        byteArrayOf(0, (bytes.size + 4).toByte(), 0, id.toByte()) + bytes.map(Int::toByte).toByteArray()
    private fun send(id: Int, payload: ByteArray) = BydNavigationOutputs.onFrame(Iap2Frame(id, payload))

    @After fun cleanup() {
        BydNavigationOutputs.clearNavigationGuidanceListener(listener)
        BydNavigationOutputs.endNow()
    }

    @Test fun `full route preserves phone fields and repeated values still reach OEM listener`() {
        BydNavigationOutputs.endNow()
        BydNavigationOutputs.setNavigationGuidanceListener(listener)
        send(0x5202, tlv(1, 0, 7) + tlv(3, 14) + tlv(8, 1))
        val route = tlv(1, 1) + tlv(0x0a, 0, 0, 0, 123) + tlv(0x0d, 0, 7) +
            tlv(6, 0, 0, 0, 0, 0, 0, 0, 60) + tlv(7, 0, 0, 3, 232)
        send(0x5201, route)
        val first = events.last()!!
        assertEquals(14, first.appleManeuverType)
        assertEquals(1, first.drivingSide)
        assertEquals(1000L, first.remainingMeters)
        assertEquals(60L, first.remainingSeconds)
        val count = events.size
        send(0x5201, route)
        assertEquals(count + 1, events.size)
        assertEquals(first, events.last())
        send(0x5201, tlv(1, 0))
        assertNull(events.last())
        // A new navigation status alone must not revive the previous route's card.
        send(0x5201, tlv(1, 1))
        assertNull(events.last())
    }
}
