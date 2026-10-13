package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.provider.Settings
import android.bluetooth.BluetoothProfile
import com.shilapi.xcertplay.vehicle.VehicleIntegration

internal object DiPlayBluetooth {
    /** Android 9 车机是音频接收端；这些 Profile ID 来自本车蓝牙服务清单。 */
    fun audioProfiles(lynk06: Boolean = VehicleIntegration.isDetectedLynk06()): List<Pair<String, Int>> =
        if (lynk06) listOf("A2DP Sink" to 11, "HFP Client" to 16)
        else listOf("A2DP" to BluetoothProfile.A2DP, "HFP" to BluetoothProfile.HEADSET)

    fun localAddress(context: Context): String? {
        val adapter = runCatching { (context.getSystemService(BluetoothManager::class.java)?.adapter ?: BluetoothAdapter.getDefaultAdapter())?.address }.getOrNull()
        val setting = runCatching { Settings.Secure.getString(context.contentResolver, "bluetooth_address") }.getOrNull()
        return listOfNotNull(adapter, setting).firstOrNull {
            Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(it) &&
                !it.startsWith("02:00:00:00:00:") && it != "00:00:00:00:00:00"
        }
    }
}
