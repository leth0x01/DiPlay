package com.shilapi.xcertplay.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeelyHeadUnitTest {
    @Test fun knownAndroid11CombinationRetainsEachVerifiedCapability() {
        val capabilities = GeelyHeadUnit.capabilities("geely_fx11", "CS11_A4", 30)
        assertTrue(capabilities.wheelInput)
        assertTrue(capabilities.siriVoiceCommunication)
    }

    @Test fun brandNamesAndAndroid9DoNotProveVendorContracts() {
        for ((product, model, sdk) in listOf(
            Triple("lynk", "06", 28), Triple("geely", "CS11_A4", 30),
            Triple("geely_fx11", "CS11_A4", 28), Triple("geely_fx11", "unknown", 30),
            Triple("qti", "qualcomm", 28), Triple(null, null, 28),
        )) {
            val capabilities = GeelyHeadUnit.capabilities(product, model, sdk)
            assertFalse("$product/$model/$sdk", capabilities.wheelInput)
            assertFalse("$product/$model/$sdk", capabilities.siriVoiceCommunication)
        }
    }
}
