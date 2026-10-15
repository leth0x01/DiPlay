package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchDiagnosticsTest {
    @Test fun `points use three decimals and a dot`() {
        assertEquals("(0.500,0.250)", TouchDiagnostics.point(0.5f, 0.25f))
        assertEquals("(0.000,1.000)", TouchDiagnostics.point(0f, 1f))
    }

    @Test fun `pointer ids keep the order CarPlay receives them`() {
        assertEquals("[4,7]", TouchDiagnostics.ids(listOf(4, 7)))
        assertEquals("[]", TouchDiagnostics.ids(emptyList()))
    }

    @Test fun `a plain tap summary omits drops and cancellation`() {
        assertEquals(
            "touch gesture start=(0.500,0.400) end=(0.500,0.400) pointers=1 moves=0 120ms sent=2",
            TouchDiagnostics.summary(
                startX = 0.5f, startY = 0.4f, endX = 0.5f, endY = 0.4f,
                maxPointers = 1, moves = 0, durationMillis = 120L,
                sent = 2, dropped = 0, cancelled = false,
            ),
        )
    }

    @Test fun `a cancelled gesture with dropped reports says so`() {
        val line = TouchDiagnostics.summary(
            startX = 0.1f, startY = 0.2f, endX = 0.8f, endY = 0.9f,
            maxPointers = 2, moves = 40, durationMillis = 900L,
            sent = 30, dropped = 12, cancelled = true,
        )
        assertEquals(
            "touch gesture start=(0.100,0.200) end=(0.800,0.900) pointers=2 moves=40 900ms " +
                "sent=30 dropped=12 cancelled",
            line,
        )
    }
}
