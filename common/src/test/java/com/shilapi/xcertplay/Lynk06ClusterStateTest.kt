package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class Lynk06ClusterStateTest {
    @Test fun `active navigation shows the map regardless of OEM mode`() {
        // 原车 naviMode 由 OEM 导航生态绑定，第三方无法驱动，不再作为显示门控。
        for (mode in listOf(null, 0, 1, 2, 3, 4, 99)) assertTrue(Lynk06ClusterState(true, true, mode).showMap)
        assertFalse(Lynk06ClusterState(true, false, 3).showMap)
        assertFalse(Lynk06ClusterState(false, true, 3).showMap)
    }
}
