package com.shilapi.xcertplay.network

import org.junit.Assert.*
import org.junit.Test

class SystemUidVpnPreparationTest {
    @Test fun legacySystemUidAliasRequiresADifferentUidBeforeOwnPackage() {
        val own = "com.leth0x01.lynkplay.clustersystem"
        var owner = "[Legacy VPN]"
        var uid = 1000
        val prepare: (String) -> Boolean = { target ->
            val targetUid = if (target == "com.android.shell") 2000 else 1000
            if (uid != targetUid) { owner = target; uid = targetUid }
            true
        }
        prepare(own)
        assertEquals("[Legacy VPN]", owner)
        assertTrue(SystemUidVpnPreparation.rebind(own, prepare))
        assertEquals(own, owner)
        assertEquals(1000, uid)
    }

    @Test fun intermediateFailureStillRestoresOwnPackage() {
        val calls = mutableListOf<String>()
        assertThrows(SecurityException::class.java) {
            SystemUidVpnPreparation.rebind("own.package") {
                calls += it
                if (it == "com.android.shell") throw SecurityException("binder failed")
                true
            }
        }
        assertEquals(listOf("com.android.shell", "own.package"), calls)
    }

    @Test fun rejectedSwitchOrRestorationCannotReportSuccess() {
        assertFalse(SystemUidVpnPreparation.rebind("own.package") { it != "com.android.shell" })
        assertThrows(IllegalStateException::class.java) {
            SystemUidVpnPreparation.rebind("own.package") { it == "com.android.shell" }
        }
    }
}
