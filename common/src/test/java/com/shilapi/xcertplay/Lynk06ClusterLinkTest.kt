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
import java.time.Duration
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

    @After fun tearDown() { link.close(); await { vendor.disconnected && vendor.watcher == null } }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 12_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            Thread.sleep(5)
        }
        assertTrue("异步仪表状态未在时限内完成", condition())
    }

    private fun realGuidance(meters: Int = 123) = com.shilapi.xcertplay.hud.ClusterTurnGuidance(
        5, 0, meters, "测试道路", remainingSeconds = 11280, remainingMeters = 281800,
        appleManeuverType = 14, drivingSide = 0,
    )

    @Test fun `full card retries a rejected transaction and stops publishing when real navigation ends`() {
        vendor.infoFailuresRemaining = 1
        link.setNavigation(true)
        link.setGuidance(realGuidance())
        await { vendor.navigationInfos.isNotEmpty() }
        assertEquals(105, vendor.navigationInfos.last().getNavigationTurnId())
        assertEquals(11280L, vendor.navigationInfos.last().getETA())
        link.setGuidance(realGuidance(77))
        await { vendor.navigationInfos.last().getDistanceToNextGuidancePoint() == 77L }
        link.setNavigation(false)
        await {
            vendor.notifications.lastOrNull() == 0 &&
                vendor.presentationSwitches.lastOrNull()?.endsWith(",false") == true &&
                !vendor.tbtActive
        }
        val count = vendor.navigationInfos.size
        link.setGuidance(realGuidance(50))
        // Synchronize through a menu callback rather than an arbitrary sleep.
        vendor.mode = 1
        vendor.menuCallback!!.onChangeNaviMode(1)
        await { states.lastOrNull()?.mode == 1 }
        assertEquals(count, vendor.navigationInfos.size)
        assertFalse(states.last().showMap)
    }

    @Test fun `projection gate retries without resending a throttled navigation card`() {
        vendor.powerOperation.failuresRemaining = 1
        link.setNavigation(true)
        link.setGuidance(realGuidance())
        await { vendor.powerOperation.attempts == 1 }
        val sentAt = android.os.SystemClock.elapsedRealtime()
        link.setGuidance(realGuidance())
        await { vendor.powerOperation.calls.isNotEmpty() }
        assertTrue(android.os.SystemClock.elapsedRealtime() - sentAt < 10_000L)
        assertEquals(1, vendor.navigationInfos.size)
        assertEquals(listOf(false to 2), vendor.powerOperation.calls.toList())
        // 用 worker 读菜单作为同步点，确认成功后不重复解除门控。
        vendor.mode = 1
        vendor.menuCallback!!.onChangeNaviMode(1)
        await { states.lastOrNull()?.mode == 1 }
        assertEquals(2, vendor.powerOperation.attempts)
    }

    @Test fun `projection gate waits until a real navigation card succeeds`() {
        vendor.infoFailuresRemaining = 100
        link.setNavigation(true)
        link.setGuidance(realGuidance())
        await { vendor.infoFailuresRemaining < 100 }
        assertEquals(0, vendor.powerOperation.attempts)
        vendor.infoFailuresRemaining = 0
        link.setGuidance(realGuidance())
        await { vendor.powerOperation.calls.isNotEmpty() }
        assertEquals(listOf(false to 2), vendor.powerOperation.calls.toList())
    }

    @Test fun `real navigation without complete guidance never sends a synthetic card`() {
        link.setNavigation(true)
        link.setGuidance(realGuidance().copy(remainingMeters = null))
        await { states.lastOrNull()?.showMap == true }
        assertTrue(vendor.navigationInfos.isEmpty())
        assertEquals(0, vendor.powerOperation.attempts)
    }

    @Test fun `initialization does not clear OEM navigation and navigation start requests map mode once`() {
        assertTrue(vendor.notifications.isEmpty())
        assertTrue(vendor.switchRequests.isEmpty())
        assertTrue(vendor.presentationSwitches.isEmpty())
        link.setNavigation(true)
        // 原车默认简洁模式；导航开始后先声明投影源，再请求一次地图模式，车机接受即切换。
        await { states.lastOrNull()?.showMap == true }
        assertEquals(listOf(3), vendor.switchRequests.toList())
        assertEquals(1, vendor.presentationSwitches.size)
        assertTrue(vendor.presentationSwitches.single().endsWith(",true"))
        assertTrue(vendor.tbtActive)
        vendor.mode = 2
        vendor.menuCallback!!.onChangeNaviMode(2)
        await { states.lastOrNull()?.mode == 2 }
        // 显示门控不依赖原车模式：导航期间切回简洁模式，地图窗口仍保持显示。
        assertTrue(states.last().showMap)
        // 用户手动切回简洁后，本次导航不再请求地图模式。
        assertEquals(listOf(3), vendor.switchRequests.toList())
        assertEquals(listOf(1), vendor.notifications.toList())
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 }
        // 导航结束时撤销投影声明与 TurnByTurn，把仪表还给原车导航。
        assertEquals(2, vendor.presentationSwitches.size)
        assertTrue(vendor.presentationSwitches.last().endsWith(",false"))
        assertFalse(vendor.tbtActive)
    }

    @Test fun `navigation requests map mode once even when getter already reports full mode`() {
        vendor.mode = 3
        vendor.menuCallback!!.onChangeNaviMode(3)
        await { states.lastOrNull()?.mode == 3 }
        link.setNavigation(true)
        await { states.lastOrNull()?.showMap == true }
        assertEquals(listOf(3), vendor.switchRequests.toList())

        // getter 变为 1 时只更新诊断，本次导航不重复请求。
        vendor.mode = 1
        vendor.menuCallback!!.onChangeNaviMode(1)
        await { states.lastOrNull()?.mode == 1 }
        assertTrue(states.last().showMap)
        assertEquals(listOf(3), vendor.switchRequests.toList())

        link.setNavigation(false)
        await { states.lastOrNull()?.navigating == false }
        link.setNavigation(true)
        await { states.lastOrNull()?.showMap == true }
        assertEquals(listOf(3, 3), vendor.switchRequests.toList())
    }

    @Test fun `presentation switch failure still sends navigation and is diagnosed`() {
        vendor.failPresentationSwitch = true
        link.setNavigation(true)
        await { states.lastOrNull()?.showMap == true }
        // 投影声明失败不阻止导航状态与地图模式请求。
        assertEquals(listOf(1), vendor.notifications.toList())
        assertEquals(listOf(3), vendor.switchRequests.toList())
        val report = Lynk06ClusterLink.diagnosticReport()
        assertTrue(report.contains("lastPresentationSwitchSuccess=false"))
        assertTrue(report.contains("presentationSwitchOn=false"))
    }

    @Test fun `rejected map mode request still shows the map and is diagnosed`() {
        vendor.switchAccepted = false
        link.setNavigation(true)
        await { vendor.switchRequests.isNotEmpty() && states.lastOrNull()?.navigating == true }
        // 模式请求被拒不阻止地图显示；拒绝结果保留在诊断中。
        assertTrue(states.last().showMap)
        assertEquals(2, states.last().mode)
        val report = Lynk06ClusterLink.diagnosticReport()
        assertTrue(report.contains("switchFullModeAccepted=false"))
        assertTrue(report.contains("presentationOption=0"))
    }

    @Test fun `close restores only navigation owned by this connection and releases callbacks`() {
        link.setNavigation(true)
        await { vendor.notifications.lastOrNull() == 1 }
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
        assertTrue(vendor.presentationSwitches.last().endsWith(",false"))
        assertNull(vendor.menuCallback)
        assertNull(vendor.watcher)
    }

    @Test fun `route end hides map before a blocked vendor cleanup returns`() {
        link.setNavigation(true)
        await { states.lastOrNull()?.showMap == true }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        vendor.endNotificationEntered = entered
        vendor.endNotificationRelease = release
        try {
            link.setNavigation(false)
            await { entered.count == 0L }
            await { states.lastOrNull()?.showMap == false }
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
        }
        await { vendor.notifications.lastOrNull() == 0 }
    }

    @Test fun `route end sends an inactive full card and retries it independently`() {
        link.setNavigation(true)
        link.setGuidance(realGuidance())
        await { vendor.navigationInfos.isNotEmpty() }
        vendor.infoFailuresRemaining = 1
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 && !vendor.tbtActive }
        await { vendor.endInfoCalls >= 2 && vendor.navigationInfos.last().getNavigationStatus() == 0 }
        val ended = vendor.navigationInfos.last()
        assertEquals(0, ended.getNavigationStatus())
        assertEquals(0, ended.getNavigationTurnId())
        assertEquals("", ended.getNextGuidancePointName())
        assertEquals(0L, ended.getDistanceToDestination())
        assertEquals(0L, ended.getETA())
        assertEquals(listOf(1, 0), vendor.notifications.toList())
        assertEquals(1, vendor.tbtStopCalls)
    }

    @Test fun `failed close end card survives a new link and is cleared before reuse`() {
        link.setNavigation(true)
        link.setGuidance(realGuidance())
        await { vendor.navigationInfos.isNotEmpty() }
        vendor.infoFailuresRemaining = 3
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        assertTrue(Lynk06ClusterLink.diagnosticReport().contains("navigationInfoCleanupPending=true"))
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        await { vendor.navigationInfos.last().getNavigationStatus() == 0 && states.lastOrNull()?.available == true }
        assertEquals(4, vendor.endInfoCalls)
        assertTrue(Lynk06ClusterLink.diagnosticReport().contains("navigationInfoCleanupPending=false"))
    }

    @Test fun `route end retries TBT stop after navigation status was cleared`() {
        link.setNavigation(true)
        await { vendor.tbtActive }
        vendor.tbtStopFailuresRemaining = 1
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 }
        await { !vendor.tbtActive && vendor.tbtStopCalls == 2 }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
        assertTrue(vendor.presentationSwitches.last().endsWith(",false"))
    }

    @Test fun `route end retries projection release independently of other cleanup`() {
        link.setNavigation(true)
        await { vendor.tbtActive }
        vendor.presentationReleaseFailuresRemaining = 1
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 }
        await { vendor.presentationSwitches.last().endsWith(",false") }
        assertEquals(1, vendor.tbtStopCalls)
        assertEquals(listOf(1, 0), vendor.notifications.toList())
    }

    @Test fun `reconnect retains projection cleanup after repeated release failures`() {
        link.setNavigation(true)
        await { vendor.tbtActive }
        vendor.presentationReleaseFailuresRemaining = 3
        link.setNavigation(false)
        await { vendor.connectCalls >= 2 }
        await { vendor.presentationSwitches.last().endsWith(",false") }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
    }

    @Test fun `close retries failed cleanup before disconnecting`() {
        link.setNavigation(true)
        await { vendor.tbtActive }
        vendor.notificationFailuresRemaining = 1
        vendor.tbtStopFailuresRemaining = 1
        vendor.presentationReleaseFailuresRemaining = 1
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
        assertFalse(vendor.tbtActive)
        assertTrue(vendor.presentationSwitches.last().endsWith(",false"))
    }

    @Test fun `exhausted close cleanup survives a new link and finishes before navigation restarts`() {
        link.setNavigation(true)
        await { vendor.tbtActive }
        vendor.presentationReleaseFailuresRemaining = 3
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        assertTrue(Lynk06ClusterLink.diagnosticReport().contains("projectionCleanupPending=true"))
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        link.setNavigation(true)
        await { vendor.presentationSwitches.count { it.endsWith(",false") } == 1 && vendor.notifications.lastOrNull() == 1 && vendor.tbtActive }
        assertTrue(vendor.presentationSwitches.takeLast(2)[0].endsWith(",false"))
        assertTrue(vendor.presentationSwitches.last().endsWith(",true"))
        assertTrue(vendor.tbtActive)
    }

    @Test fun `new link waits for closing retries before taking navigation ownership`() {
        link.setNavigation(true)
        await { vendor.tbtActive }
        vendor.notificationFailuresRemaining = 1
        val previous = link
        previous.close()
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        link.setNavigation(true)
        await { vendor.notifications.toList() == listOf(1, 0, 1) && vendor.tbtActive }
        assertEquals(1, vendor.naviCallbacks.size)
        assertEquals(1, vendor.interactionCallbacks.size)
        assertTrue(vendor.presentationSwitches.last().endsWith(",true"))
    }

    @Test fun `reconnect replaces callbacks without leaving previous registrations`() {
        vendor.watcher!!.onDisConnected()
        await { vendor.connectCalls == 2 && vendor.menuRegistrationCalls == 2 && vendor.naviCallbacks.size == 1 && vendor.interactionCallbacks.size == 1 }
        assertEquals(1, vendor.naviCallbacks.size)
        assertEquals(1, vendor.interactionCallbacks.size)
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        assertTrue(vendor.naviCallbacks.isEmpty())
        assertTrue(vendor.interactionCallbacks.isEmpty())
    }

    @Test fun `first read failure automatically recovers without a service callback`() {
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        states.clear()
        vendor = DimInteraction().apply { readFailuresRemaining = 1; mode = 3 }
        DimInteraction.instance = vendor
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        await { states.lastOrNull()?.available == true }
        assertEquals(2, vendor.readCalls)
        assertEquals(1, vendor.connectCalls)
    }

    @Test fun `repeated read failures reconnect and republish session navigation`() {
        link.setNavigation(true)
        await { vendor.notifications.lastOrNull() == 1 }
        vendor.failRead = true
        vendor.menuCallback!!.onChangeNaviMode(3)
        await { vendor.connectCalls >= 2 }
        vendor.failRead = false
        vendor.mode = 3
        await { states.lastOrNull()?.showMap == true }
        assertEquals(listOf(1, 1), vendor.notifications.toList())
        link.close()
        await { vendor.watcher == null }
        assertEquals(listOf(1, 1, 0), vendor.notifications.toList())
    }

    @Test fun `navigation notification failure automatically retries`() {
        vendor.notificationFailuresRemaining = 1
        link.setNavigation(true)
        await { states.lastOrNull()?.navigating == true }
        assertEquals(listOf(1), vendor.notifications.toList())
        vendor.notificationFailuresRemaining = 1
        link.setNavigation(false)
        await { vendor.notifications.lastOrNull() == 0 }
        await { states.lastOrNull()?.available == true }
    }

    @Test fun `service disconnect automatically reconnects`() {
        vendor.watcher!!.onDisConnected()
        await { vendor.connectCalls == 2 && states.lastOrNull()?.available == true }
    }

    @Test fun `diagnostic report reflects the attached link`() {
        val report = Lynk06ClusterLink.diagnosticReport()
        assertTrue(report.contains("linkCreated=true"))
        assertTrue(report.contains("adapterCreated=true"))
        assertTrue(report.contains("watcherRegistered=true"))
        assertTrue(report.contains("adapterConnected=true"))
        assertTrue(report.contains("menuAttached=true"))
        assertTrue(report.contains("naviAttached=true"))
        assertTrue(report.contains("lastError=none"))
        assertTrue(report.contains("presentationOption=0"))
        assertTrue(report.contains("naviCallbackRegistered=true"))
        assertTrue(report.contains("interactionCallbackRegistered=true"))
        assertNotNull(vendor.naviCallback)
        assertNotNull(vendor.interactionCallback)
    }

    @Test fun `diagnostic report shows navigation notification and mode`() {
        link.setNavigation(true)
        await { states.lastOrNull()?.navigating == true }
        vendor.mode = 3
        vendor.menuCallback!!.onChangeNaviMode(3)
        await { states.lastOrNull()?.showMap == true }
        val report = Lynk06ClusterLink.diagnosticReport()
        assertTrue(report.contains("navigationRequested=true"))
        assertTrue(report.contains("navigationSent=true"))
        assertTrue(report.contains("lastNotifySuccess=true"))
        assertTrue(report.contains("naviMode=3"))
        assertTrue(report.contains("showMap=true"))
    }

    @Test fun `missing connect callback retries until the service returns`() {
        vendor.suppressConnectCallback = true
        vendor.watcher!!.onDisConnected()
        await { vendor.connectCalls >= 2 }
        vendor.suppressConnectCallback = false
        await { vendor.connectCalls >= 3 && states.lastOrNull()?.available == true }
    }

    @Test fun `close cancels a pending recovery`() {
        vendor.failRead = true
        vendor.menuCallback!!.onChangeNaviMode(3)
        await { states.lastOrNull()?.available == false }
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        val readCalls = vendor.readCalls
        val connectCalls = vendor.connectCalls
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(readCalls, vendor.readCalls)
        assertEquals(connectCalls, vendor.connectCalls)
    }

    @Test fun `ABI snapshot lists runtime signatures without invoking vendor methods`() {
        val readCalls = vendor.readCalls
        val snapshot = Lynk06ClusterAbiProbe.snapshot()
        assertTrue(snapshot.any { it.contains("getNaviMode()") })
        assertTrue(snapshot.any { it.contains("notifyNavigationStatus(int)") })
        assertTrue(snapshot.any { it.contains("onDisConnected()") })
        assertEquals(readCalls, vendor.readCalls)
        assertTrue(vendor.notifications.isEmpty())
    }

    @Test fun `ABI runtime failure does not prevent connection and is attempted once`() {
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        states.clear()
        var probeCalls = 0
        val logs = CopyOnWriteArrayList<String>()
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, { logs.add(it) },
            abiSnapshot = { probeCalls++; throw SecurityException("模拟 ROM 拒绝反射") })
        link.start()
        await { states.lastOrNull()?.available == true }
        assertTrue(logs.any { it.contains("ABI：采集失败") })
        vendor.watcher!!.onDisConnected()
        await { states.lastOrNull()?.available == false }
        await { states.lastOrNull()?.available == true }
        assertEquals(1, probeCalls)
    }

    @Test fun `slow service connection is allowed to finish without being disconnected`() {
        vendor.connectCallbackDelayMillis = 2500
        vendor.watcher!!.onDisConnected()
        await { states.lastOrNull()?.available == false }
        await { states.lastOrNull()?.available == true }
        assertEquals(2, vendor.connectCalls)
    }

    @Test fun `cold start waits for a slow initial connection`() {
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        states.clear()
        vendor = DimInteraction().apply { connectCallbackDelayMillis = 2500 }
        DimInteraction.instance = vendor
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        await { states.lastOrNull()?.available == true }
        assertEquals(1, vendor.connectCalls)
    }

    @Test fun `missing callbacks reduce reconnect pressure with increasing delays`() {
        vendor.suppressConnectCallback = true
        vendor.watcher!!.onDisConnected()
        await { vendor.connectCalls >= 4 }
        val times = vendor.connectTimes.toList().take(4)
        val firstRetryGap = times[2] - times[1]
        val secondRetryGap = times[3] - times[2]
        assertTrue("连接等待应超过瞬时读取重试间隔", firstRetryGap >= 5000)
        assertTrue("持续缺失回调时应增加退避", secondRetryGap > firstRetryGap)
    }

    @Test fun `first watcher registration failure retries initialization and recovers`() {
        assertWatcherRegistrationRecovers(afterStoringWatcher = false)
    }

    @Test fun `partial watcher registration failure is cleaned up before retry`() {
        assertWatcherRegistrationRecovers(afterStoringWatcher = true)
    }

    private fun assertWatcherRegistrationRecovers(afterStoringWatcher: Boolean) {
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
        states.clear()
        vendor = DimInteraction().apply {
            watcherRegistrationFailuresRemaining = 1
            failAfterStoringWatcher = afterStoringWatcher
        }
        DimInteraction.instance = vendor
        link = Lynk06ClusterLink(RuntimeEnvironment.getApplication(), { states.add(it) }, {})
        link.start()
        await { states.lastOrNull()?.available == true }
        assertEquals(2, vendor.createCalls)
        assertEquals(2, vendor.watcherRegistrationCalls)
        assertEquals(1, vendor.connectCalls)
        assertEquals(1, vendor.watcherUnregisterCalls)
        if (afterStoringWatcher) {
            val menuRegistrations = vendor.menuRegistrationCalls
            vendor.failedWatcher!!.onConnected()
            // 后续 worker 操作完成后，确认旧回调没有再次获取和注册菜单接口。
            link.setNavigation(true)
            await { states.lastOrNull()?.navigating == true }
            assertEquals(menuRegistrations, vendor.menuRegistrationCalls)
        }
        link.close()
        await { vendor.disconnected && vendor.watcher == null }
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
        await { vendor.disconnected && vendor.watcher == null }
        assertEquals(listOf(1, 0), vendor.notifications.toList())
    }
}
