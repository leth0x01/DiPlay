package com.shilapi.xcertplay

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class DiPlayBluetoothProfilesTest {
    @Test fun lynk06ProbesTheReceiverProfilesFromTheVehicleDump() {
        assertEquals(listOf("A2DP Sink" to 11, "HFP Client" to 16), DiPlayBluetooth.audioProfiles(true))
    }

    @Test fun standardProfileProbeKeepsItsExistingRoles() {
        assertEquals(listOf("A2DP" to BluetoothProfile.A2DP, "HFP" to BluetoothProfile.HEADSET),
            DiPlayBluetooth.audioProfiles(false))
    }
}
