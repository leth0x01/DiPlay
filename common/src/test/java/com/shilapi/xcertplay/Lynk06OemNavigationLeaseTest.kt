package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class Lynk06OemNavigationLeaseTest {
    private class Backend(var enabled: Int = 0) : Lynk06OemNavigationLease.Backend {
        var journal: Int? = null
        var failSet = false
        var failWake = false
        var wakes = 0
        var writes = 0
        override fun state() = enabled
        override fun setState(state: Int) {
            check(journal != null) // Every mutation must be journaled first.
            if (failSet) error("permission denied")
            enabled = state
            writes++
        }
        override fun saved() = journal
        override fun save(state: Int) { journal = state }
        override fun clear() { journal = null }
        override fun wake() { if (failWake) error("wake failed"); wakes++ }
    }

    @Test fun `crash after disable restores exact default or explicitly enabled state`() {
        for (original in 0..1) {
            val backend = Backend(original)
            assertTrue(Lynk06OemNavigationLease(backend).pause())
            assertEquals(2, backend.enabled)
            Lynk06OemNavigationLease(backend).restore() // New process, only journal survives.
            assertEquals(original, backend.enabled)
            assertNull(backend.journal)
            assertEquals(1, backend.wakes)
        }
    }

    @Test fun `existing disabled package belongs to someone else`() {
        for (original in 2..4) {
            val backend = Backend(original)
            val lease = Lynk06OemNavigationLease(backend)
            assertFalse(lease.pause())
            lease.restore()
            assertEquals(original, backend.enabled)
            assertEquals(0, backend.writes)
            assertNull(backend.journal)
        }
    }

    @Test fun `failed restoration keeps journal for retry and respects subsequent external state`() {
        val backend = Backend(1)
        val lease = Lynk06OemNavigationLease(backend)
        lease.pause()
        backend.failSet = true
        assertTrue(runCatching { lease.restore() }.isFailure)
        assertEquals(1, backend.journal)
        backend.failSet = false
        backend.enabled = 3 // External actor changed it after our pause.
        lease.restore()
        assertEquals(3, backend.enabled)
        assertEquals(1, backend.writes)
        assertNull(backend.journal)
    }

    @Test fun `wake failure remains recoverable after state was restored`() {
        val backend = Backend()
        val lease = Lynk06OemNavigationLease(backend)
        lease.pause()
        backend.failWake = true
        assertTrue(runCatching { lease.restore() }.isFailure)
        assertEquals(0, backend.enabled)
        assertEquals(0, backend.journal)
        backend.failWake = false
        Lynk06OemNavigationLease(backend).restore()
        assertNull(backend.journal)
        assertEquals(1, backend.wakes)
    }
}
