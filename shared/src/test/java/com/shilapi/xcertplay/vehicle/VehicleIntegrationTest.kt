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

    @Test fun runtimeBx11ModelSelectsCollectedProfile() {
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "BX11", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        val app = RuntimeEnvironment.getApplication()
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL, VehicleIntegration.profile(app))
    }

    @Test fun collectedIdentifiersSelectPetrolProfileWithoutTrimSelection() {
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL,
            VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "IHU514G", "xecarx", 28))
    }

    @Test fun bx11ModelSelectsTheSameCollectedProfile() {
        // BX11 固件的 Build.MODEL 直接回报车型代号，其余标识不变。
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL,
            VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "BX11", "xecarx", 28))
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL,
            VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "bx11", "xecarx", 28))
        assertEquals(VehicleIntegration.LYNK_06_2023_PETROL,
            VehicleIntegration.detectedProfile("IHU514G", "IHU514G", " bx11 ", "xecarx", 28))
    }

    @Test fun bx11ModelDoesNotWidenTheRestOfTheDeviceCombination() {
        assertNull(VehicleIntegration.detectedProfile("other", "IHU514G", "BX11", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "other", "BX11", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "BX11", "other", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "BX11", "xecarx", 30))
        // 只接受准确的车型代号，不用 contains 放宽。
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "BX110", "xecarx", 28))
    }

    @Test fun partialIdentifiersAndOtherPlatformsDoNotSelectCollectedProfile() {
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "other", "IHU514G", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("other", "IHU514G", "IHU514G", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "other", "xecarx", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "IHU514G", "other", 28))
        assertNull(VehicleIntegration.detectedProfile("IHU514G", "IHU514G", "IHU514G", "xecarx", 30))
        assertNull(VehicleIntegration.detectedProfile(null, null, null, null, 28))
    }

    @Test fun unmatchedDeviceFallsBackToTheBuildTargetAdapter() {
        for ((field, value) in mapOf("PRODUCT" to "sdk_gphone", "DEVICE" to "generic",
            "MODEL" to "sdk_gphone", "HARDWARE" to "ranchu")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        val app = RuntimeEnvironment.getApplication()
        // 库的默认构建资源选择 BYD；未匹配具体车型时由它接管。
        assertNull(VehicleIntegration.detectedAdapter())
        assertEquals(VehicleAdapters.BYD, VehicleIntegration.targetAdapter(app).id)
        assertEquals(VehicleAdapters.BYD, VehicleIntegration.effectiveAdapter(app).id)
        assertTrue(VehicleIntegration.bydEnabled(app))
    }

    @Test fun diagnosticReportsTheEffectiveAdapter() {
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "IHU514G", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        val app = RuntimeEnvironment.getApplication()
        assertTrue(VehicleIntegration.diagnosticReport(app).contains("有效车型适配器：${VehicleAdapters.LYNK_06_2023_PETROL}"))
    }

    @Test fun diagnosticReportCarriesFullFirmwareIdentityAndScreen() {
        // 未适配车型（如领克 03）的报告也必须带齐适配所需的固件身份。
        for ((field, value) in mapOf(
            "FINGERPRINT" to "LynkCo/BX11/BX11:10/QQ3A.200805.001/eng.build:user/release-keys",
            "ID" to "QQ3A.200805.001",
            "TYPE" to "user",
            "TAGS" to "release-keys",
            "BOOTLOADER" to "unknown",
        )) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "INCREMENTAL", "eng.build.20240101")
        val app = RuntimeEnvironment.getApplication()
        val report = VehicleIntegration.diagnosticReport(app)
        assertTrue(report, report.contains("Firmware fingerprint: LynkCo/BX11/BX11:10/QQ3A.200805.001/eng.build:user/release-keys"))
        assertTrue(report, report.contains("Firmware build: id=QQ3A.200805.001; incremental=eng.build.20240101; type=user; tags=release-keys; bootloader=unknown"))
        assertTrue(report, report.lines().any { it.startsWith("Firmware version name: ") })
        val screen = report.lines().single { it.startsWith("Screen: ") }
        assertTrue(screen, screen.matches(Regex("Screen: \\d+x\\d+ \\d+dpi.*")))
    }

}
