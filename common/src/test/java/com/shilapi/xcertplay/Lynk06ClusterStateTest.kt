package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class Lynk06ClusterStateTest {
    @Test fun `only active navigation in full mode shows the map`() {
        assertTrue(Lynk06ClusterState(true, true, 3).showMap)
        for (mode in listOf(null, 0, 1, 2, 4, 99)) assertFalse(Lynk06ClusterState(true, true, mode).showMap)
        assertFalse(Lynk06ClusterState(true, false, 3).showMap)
        assertFalse(Lynk06ClusterState(false, true, 3).showMap)
    }
}
