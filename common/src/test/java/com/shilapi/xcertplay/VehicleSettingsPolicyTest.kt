package com.shilapi.xcertplay

import android.os.Build
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [LynkVehicleResources::class])
class VehicleSettingsPolicyTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "BX11", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        cleanup()
    }

    @After fun cleanup() {
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
    }

    @Test fun verifiedDefaultsKeepUserRollbackChoices() {
        assertTrue(AirPlayPersistence.loadMtkDecoderTuning(app))
        assertTrue(AirPlayPersistence.loadDirectVideoOutput(app))
        assertTrue(AirPlayPersistence.loadClusterMapEnabled(app))
        assertFalse(AirPlayPersistence.loadSmoothVideo(app))
        assertFalse(AirPlayPersistence.loadHevcEnabled(app))
        AirPlayPersistence.saveMtkDecoderTuning(app, false)
        AirPlayPersistence.saveDirectVideoOutput(app, false)
        AirPlayPersistence.saveClusterMapEnabled(app, false)
        assertFalse(AirPlayPersistence.loadMtkDecoderTuning(app))
        assertFalse(AirPlayPersistence.loadDirectVideoOutput(app))
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(app))
    }

    @Test fun hiddenSettingsCannotActivateFromHistoricalValues() {
        AirPlayPersistence.saveHarmanFullscreen(app, true)
        AirPlayPersistence.saveLowLatencyDecoder(app, true)
        assertFalse(AirPlayPersistence.loadHarmanFullscreen(app))
        assertFalse(AirPlayPersistence.loadLowLatencyDecoder(app))
        // 换回其他固件后仍能读取原始偏好，不破坏导入或旧版保存的数据。
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "other")
        assertTrue(AirPlayPersistence.loadHarmanFullscreen(app))
        assertTrue(AirPlayPersistence.loadLowLatencyDecoder(app))
    }

    @Test fun targetResourcesAndPartialDeviceMatchCannotApplyFirmwarePolicy() {
        for (field in listOf("PRODUCT", "DEVICE", "MODEL", "HARDWARE")) {
            val original = Build::class.java.getField(field).get(null)
            ReflectionHelpers.setStaticField(Build::class.java, field, "other")
            assertTrue(VehicleSettingsPolicy.showHarmanFullscreen)
            assertTrue(VehicleSettingsPolicy.showQualcommLowLatency)
            assertFalse(AirPlayPersistence.loadMtkDecoderTuning(app))
            assertFalse(AirPlayPersistence.loadDirectVideoOutput(app))
            assertFalse(AirPlayPersistence.loadClusterMapEnabled(app))
            ReflectionHelpers.setStaticField(Build::class.java, field, original)
        }
    }
}
