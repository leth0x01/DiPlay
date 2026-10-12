package com.shilapi.xcertplay.vehicle

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Build
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VehicleIntegrationTest {
    @Test fun runtimeIdentifiersOverrideBuildDefaultAndAppearInDiagnostics() {
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "IHU514G", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        val app = RuntimeEnvironment.getApplication()
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL, VehicleIntegration.profile(app))
        assertFalse(VehicleIntegration.bydEnabled(app))
        assertTrue(VehicleIntegration.diagnosticReport(app).contains("车机自动匹配：lynk_06_2023_petrol"))
    }

    @Test fun collectedIdentifiersSelectPetrolProfileWithoutTrimSelection() {
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL,
            VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "IHU514G", "xecarx", 28))
    }

    @Test fun partialIdentifiersAndOtherPlatformsDoNotSelectCollectedProfile() {
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "other", "IHU514G", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("other", "IHU514G", "IHU514G", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "other", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "IHU514G", "other", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "IHU514G", "xecarx", 30))
        assertNull(VehicleIntegration.detectedProfile(null, null, null, null, 28))
    }
}
