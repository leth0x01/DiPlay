package com.shilapi.xcertplay.orchestration

/**
 * Bounds the wait for the iPhone to re-enumerate after a CarPlay mode request.
 *
 * [WiredSessionMemory] records that an iPhone which DiPlay itself disconnected stays in CarPlay mode
 * on the same device node and only takes a new session after it resets USB. When it does not reset,
 * nothing else drives the next step: the field record from 2026-10-16 (BX11 / IHU514G) shows the
 * mode request accepted and then no USB attach for the rest of the attempt, so the reconnect waited
 * until the driver unplugged the cable. This wait makes that case bounded.
 *
 * The wait is one-shot and cancellable: a re-enumeration that arrives first must not run it later,
 * and a newer wait replaces an older one.
 */
internal class ReenumerationWatchdog(
    private val timeoutMillis: Long,
    private val schedule: (delayMillis: Long, run: Runnable) -> Unit,
) {
    private var armed: Runnable? = null

    val isArmed: Boolean get() = armed != null

    fun arm(onExpired: () -> Unit) {
        cancel()
        var ticket: Runnable? = null
        ticket = Runnable {
            // A cancelled or replaced ticket is stale; only the current one may expire.
            if (armed == null || armed !== ticket) return@Runnable
            armed = null
            onExpired()
        }
        armed = ticket
        schedule(timeoutMillis, ticket)
    }

    fun cancel() {
        armed = null
    }
}
