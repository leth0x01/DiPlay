package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReenumerationWatchdogTest {
    private val scheduled = mutableListOf<Pair<Long, Runnable>>()

    private fun watchdog(timeoutMillis: Long = 1_000L) =
        ReenumerationWatchdog(timeoutMillis) { delayMillis, run -> scheduled += delayMillis to run }

    private fun runScheduled(index: Int = scheduled.lastIndex) = scheduled[index].second.run()

    @Test fun expiresOnceAtTheConfiguredDelay() {
        val watchdog = watchdog(timeoutMillis = 180_000L)
        var expired = 0
        watchdog.arm { expired++ }

        assertEquals(listOf(180_000L to scheduled.single().second), scheduled)
        runScheduled()
        assertEquals(1, expired)
        assertFalse(watchdog.isArmed)
        // A duplicate delivery of the same ticket must not expire twice.
        runScheduled()
        assertEquals(1, expired)
    }

    @Test fun cancelStopsAPendingExpiry() {
        val watchdog = watchdog()
        var expired = 0
        watchdog.arm { expired++ }
        watchdog.cancel()
        runScheduled()
        assertEquals(0, expired)
    }

    @Test fun aNewerWaitInvalidatesTheOlderTicket() {
        val watchdog = watchdog()
        var first = 0
        var second = 0
        watchdog.arm { first++ }
        watchdog.arm { second++ }

        // The stale ticket from the first arm fires late; only the current wait may expire.
        runScheduled(index = 0)
        assertEquals(0, first)
        assertEquals(0, second)

        runScheduled(index = 1)
        assertEquals(0, first)
        assertEquals(1, second)
    }
}
