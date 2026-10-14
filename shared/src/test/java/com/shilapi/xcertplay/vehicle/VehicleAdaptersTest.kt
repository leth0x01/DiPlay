package com.shilapi.xcertplay.vehicle

import org.junit.Assert.*
import org.junit.Test

/**
 * 适配器注册表与能力声明的单元测试。
 *
 * 不依赖 Build，直接注入 [VehicleDeviceInfo]，覆盖完整匹配、部分标识和通用回退。
 */
class VehicleAdaptersTest {
    private fun lynk06(model: String = "IHU514G") =
        VehicleDeviceInfo("IHU514G", "IHU514G", model, "xecarx", 28)

    @Test fun registryKeepsUniqueIdentifiersWithGenericLast() {
        val ids = VehicleAdapters.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(VehicleAdapters.GENERIC, ids.last())
    }

    @Test fun byIdResolvesBuildTargetsCaseInsensitively() {
        assertSame(Lynk06PetrolAdapter, VehicleAdapters.byId("lynk_06_2023_petrol"))
        assertSame(Lynk06PetrolAdapter, VehicleAdapters.byId(" Lynk_06_2023_Petrol "))
        assertSame(BydAdapter, VehicleAdapters.byId("byd"))
        assertSame(GenericAdapter, VehicleAdapters.byId("generic"))
        assertNull(VehicleAdapters.byId("lynk_01_2021"))
        assertNull(VehicleAdapters.byId(""))
        assertNull(VehicleAdapters.byId(null))
    }

    @Test fun buildTargetOnlyAdaptersNeverAutoMatch() {
        assertFalse(BydAdapter.autoDetected)
        assertFalse(GenericAdapter.autoDetected)
        assertTrue(Lynk06PetrolAdapter.autoDetected)
        // 构建目标车型即使拿到领克设备快照也不应参与检测；检测结果只能是领克 06。
        assertFalse(BydAdapter.matches(lynk06()))
        assertFalse(GenericAdapter.matches(lynk06()))
        assertSame(Lynk06PetrolAdapter, VehicleAdapters.detect(lynk06()))
    }

    @Test fun bydAdapterEnablesTheBydIntegrationCapability() {
        assertTrue(BydAdapter.capabilities.bydIntegration)
        assertEquals(VehicleSettingsDefaults(), BydAdapter.settingsDefaults)
        assertEquals(VehicleCapabilities(bydIntegration = true), BydAdapter.capabilities)
    }

    @Test fun collectedCombinationSelectsTheLynk06Adapter() {
        assertSame(Lynk06PetrolAdapter, VehicleAdapters.detect(lynk06()))
        assertSame(Lynk06PetrolAdapter, VehicleAdapters.detect(lynk06(model = "BX11")))
        assertSame(Lynk06PetrolAdapter, VehicleAdapters.detect(lynk06(model = "bx11")))
    }

    @Test fun partialIdentifiersAndOtherPlatformsDoNotMatch() {
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo("other", "IHU514G", "BX11", "xecarx", 28)))
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo("IHU514G", "other", "BX11", "xecarx", 28)))
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo("IHU514G", "IHU514G", "BX110", "xecarx", 28)))
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo("IHU514G", "IHU514G", "BX11", "other", 28)))
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo("IHU514G", "IHU514G", "BX11", "xecarx", 30)))
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo()))
    }

    @Test fun brandAndFirmwareMarkerCannotSelectAModel() {
        // 品牌字段和 BX11 固件版本号都只是线索，不能代替完整设备组合。
        assertNull(VehicleAdapters.detect(VehicleDeviceInfo(
            "other", "other", "other", "other", 28,
            brand = "lynk", firmwareVersionName = "SW0BX110520H8EHH", display = "SW0BX110520H8EHH",
        )))
    }

    @Test fun lynk06AdapterDeclaresOnlyVerifiedCapabilities() {
        val capabilities = Lynk06PetrolAdapter.capabilities
        assertFalse(capabilities.bydIntegration)
        assertTrue(capabilities.oemClusterMapDisplay)
        assertTrue(capabilities.oemMediaCenter)
        assertTrue(capabilities.voiceKeyReceiver)
        assertTrue(capabilities.microphoneMicFallback)
        assertTrue(capabilities.bluetoothSinkProfiles)
    }

    @Test fun lynk06AdapterDeclaresTheCollectedSettingDefaults() {
        val defaults = Lynk06PetrolAdapter.settingsDefaults
        assertFalse(defaults.showHarmanFullscreen)
        assertFalse(defaults.showQualcommLowLatency)
        assertTrue(defaults.mtkDecoderTuningDefault)
        assertTrue(defaults.directVideoOutputDefault)
    }

    @Test fun genericFallbackEnablesNoVendorCapabilityAndNeverAutoMatches() {
        assertEquals(VehicleCapabilities(), GenericAdapter.capabilities)
        assertEquals(VehicleSettingsDefaults(), GenericAdapter.settingsDefaults)
        assertFalse(GenericAdapter.matches(lynk06()))
    }
}
