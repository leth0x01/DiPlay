package com.shilapi.xcertplay

import com.shilapi.xcertplay.adb.LocalAdb
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VpnPreConsentTest {
    @Test fun rootAuthorizationOnlyChangesTheCurrentPackage() {
        val commands = mutableListOf<String>()
        val result = VpnPreConsent.grantRoot("com.leth0x01.lynkplay") { command ->
            commands += command
            VpnPreConsent.CommandResult(0, if (command == "id -u") "0\n" else "")
        }
        assertEquals(VpnPreConsent.RootAccess.COMMAND_SUCCEEDED, result)
        assertEquals(listOf("id -u", "appops set com.leth0x01.lynkplay ACTIVATE_VPN allow"), commands)
    }

    @Test fun deniedNonRootAndTimedOutShellsDoNotRunAppops() {
        for (probe in listOf(null, VpnPreConsent.CommandResult(1, "denied"),
            VpnPreConsent.CommandResult(0, "2000"))) {
            var calls = 0
            assertEquals(VpnPreConsent.RootAccess.UNAVAILABLE,
                VpnPreConsent.grantRoot("com.leth0x01.lynkplay") { calls++; probe })
            assertEquals(1, calls)
        }
    }

    @Test fun rootCommandFailureIsNotReportedAsSuccess() {
        for (failure in listOf(null, VpnPreConsent.CommandResult(1, "denied"))) {
            assertEquals(VpnPreConsent.RootAccess.COMMAND_FAILED,
                VpnPreConsent.grantRoot("com.leth0x01.lynkplay") {
                    if (it == "id -u") VpnPreConsent.CommandResult(0, "0") else failure
                })
        }
    }

    @Test fun adbIsOnlyUsedWhenRootIsUnavailable() {
        val app = RuntimeEnvironment.getApplication()
        for (root in VpnPreConsent.RootAccess.entries) {
            var adbCalls = 0
            val result = VpnPreConsent.grant(app, rootGrant = { root }, adbGrant = {
                adbCalls++
                LocalAdb.Access.READY
            })
            assertEquals(root, result.root)
            assertEquals(if (root == VpnPreConsent.RootAccess.UNAVAILABLE) 1 else 0, adbCalls)
            assertEquals(if (root == VpnPreConsent.RootAccess.UNAVAILABLE) LocalAdb.Access.READY else null,
                result.adb)
        }
    }

    @Test fun invalidPackageCannotBecomeAShellCommand() {
        assertThrows(IllegalArgumentException::class.java) {
            VpnPreConsent.grantRoot("bad; id") { fail("不应执行命令"); null }
        }
    }
}
