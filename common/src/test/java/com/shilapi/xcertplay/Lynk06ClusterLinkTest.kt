package com.shilapi.xcertplay

import android.os.Looper
import com.ecarx.xui.adaptapi.diminteraction.DimInteraction
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Lynk06ClusterLinkTest {
    private lateinit var vendor: DimInteraction
    private lateinit var link: Lynk06ClusterLink
    private val states = CopyOnWriteArrayList<Lynk06ClusterState>()

    @Before fun setUp() {
        vendor = DimInteraction()
        DimInteraction.instance = vendor
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        await { states.lastOrNull()?.available == true }
    }

    @After fun tearDown() { link.close(); await { vendor.disconnected } }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("异步仪表状态未在时限内完成", condition())
    }

    @Test fun `initialization does not clear OEM navigation and mode changes retain navigation`() {
        assertTrue(vendor.notifications.isEmpty())
        link.setNavigation(true)
        await { states.lastOrNull()?.navigating == true }
        assertFalse(states.last().showMap)
        vendor.mode = 3
        vendor.menuCallback!!.onChangeNaviMode(3)
        await { states.lastOrNull()?.showMap == true }
        vendor.mode = 2
        vendor.menuCallback!!.onChangeNaviMode(2)
        await { states.lastOrNull()?.mode == 2 }
        assertFalse(states.last().showMap)
        assertEquals(listOf(1), vendor.notifications.toList())
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 }
    }

    @Test fun `close restores only navigation owned by this connection and releases callbacks`() {
        link.setNavigation(true)
        await { vendor.notifications.lastOrNull() == 1 }
        link.close()
        await { vendor.disconnected }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
        assertNull(vendor.menuCallback)
        assertNull(vendor.watcher)
    }

    @Test fun `read failure hides map and teardown still restores navigation`() {
        link.setNavigation(true)
        await { vendor.notifications.lastOrNull() == 1 }
        vendor.failRead = true
        vendor.menuCallback!!.onChangeNaviMode(3)
        await { states.lastOrNull()?.available == false }
        assertFalse(states.last().showMap)
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 }
        link.close()
        await { vendor.disconnected }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
    }
}
