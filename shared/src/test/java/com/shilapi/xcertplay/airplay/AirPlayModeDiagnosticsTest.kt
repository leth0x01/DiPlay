package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPlayModeDiagnosticsTest {
    @Test
    fun reportsScreenAudioAndNestedOwnershipStates() {
        assertEquals(
            "screen=1 mainAudio=2 appStates[0].appStateID=3 appStates[0].state=true " +
                "modes.resources[0].resourceID=1 modes.resources[0].entity=2",
            AirPlayModeDiagnostics.summary(mapOf(
                "screen" to 1L, "mainAudio" to 2,
                "appStates" to listOf(mapOf("appStateID" to 3, "state" to true)),
                "modes" to mapOf("resources" to listOf(mapOf("resourceID" to 1, "entity" to 2))),
            )),
        )
    }

    @Test
    fun excludesNamesPayloadsAndInvalidScalarTypesEvenUnderKnownKeys() {
        assertEquals("knownStates=0", AirPlayModeDiagnostics.summary(mapOf(
            "screen" to "private-phone-name", "mainAudio" to byteArrayOf(1, 2),
            "speech" to Double.NaN, "phoneCall" to 1.0,
            "turnByTurn" to Long.MAX_VALUE, "private-token" to 42,
            "resources" to listOf(mapOf("resourceID" to -2, "entity" to "secret"), "private-name"),
            "modes" to "private-payload",
        )))
    }

    @Test
    fun boundsListTraversalAndSummaryLength() {
        val entries = List(100) { index -> mapOf("resourceID" to index, "entity" to 65535,
            "state" to true, "borrowConstraint" to 65535, "unborrowConstraint" to 65535) }
        val summary = AirPlayModeDiagnostics.summary(mapOf(
            "resources" to entries, "modes" to mapOf("resources" to entries),
        ))
        assertTrue(summary.length <= 600)
        assertTrue(summary.contains("resources[0].resourceID=0"))
        assertFalse(summary.contains("resources[8]"))
        assertEquals("resources[7].resourceID=7", AirPlayModeDiagnostics.summary(mapOf(
            "resources" to List(9) { index -> if (index == 7) mapOf("resourceID" to 7) else emptyMap() },
        )))
    }
}
