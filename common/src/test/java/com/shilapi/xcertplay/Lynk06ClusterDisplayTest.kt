package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.view.Display
import android.view.Surface
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.media.AndroidMediaSink
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowDisplayManager
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-w1920dp-h720dp-mdpi")
class Lynk06ClusterDisplayTest {
    private lateinit var activity: CarPlayHostActivity
    private val displayIds = mutableListOf<Int>()
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(DisplayManager::class.java)

    @Before fun setUp() {
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "IHU514G", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
    }

    @After fun tearDown() {
        invoke("dismissClusterPresentation")
        displayIds.forEach { ShadowDisplayManager.removeDisplay(it) }
        ReflectionHelpers.getField<Handler>(activity, "mainHandler").removeCallbacksAndMessages(null)
        for (field in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            ReflectionHelpers.getField<ExecutorService>(activity, field).shutdownNow()
        }
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        activity.finish()
    }

    @Test fun collectedDashboardDisplayIsSelectedAndReported() {
        val id = display()
        assertEquals(id, ClusterMapPresentation.findDisplay(app)?.displayId)
        assertTrue(ClusterMapPresentation.diagnosticReport(app).contains("selectedCluster=$id:DashboardMapDisplay"))
    }

    @Test fun buildTargetAloneDoesNotAuthorizeAnotherHeadUnit() {
        display()
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "other")
        assertNull(ClusterMapPresentation.findDisplay(app))
    }

    @Test fun wrongNameGeometryAndPresentationFlagAreRejected() {
        display(name = "Passenger display")
        display(spec = "w1280dp-h720dp-mdpi")
        val noPresentation = display()
        shadowOf(manager.getDisplay(noPresentation)).setFlags(0)
        assertNull(ClusterMapPresentation.findDisplay(app))
    }

    @Test fun mainDisplayCannotBecomeDashboardOutputByName() {
        val main = manager.getDisplay(Display.DEFAULT_DISPLAY)
        shadowOf(main).apply { setName("DashboardMapDisplay"); setFlags(Display.FLAG_PRESENTATION) }
        assertFalse(Lynk06ClusterDisplay.accepts(main))
        assertNull(ClusterMapPresentation.findDisplay(app))
    }

    @Test fun removedDashboardIsRejectedAndFallsBackToVirtualMapOnly() {
        val id = display()
        val removed = manager.getDisplay(id)
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        ShadowDisplayManager.removeDisplay(id)
        displayIds.remove(id)
        assertFalse(Lynk06ClusterDisplay.accepts(removed))
        assertNull(ClusterMapPresentation.findDisplay(app))
        val config = config()!!
        assertEquals(1280, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "clusterStreamOnDisplay"))
    }

    @Test fun collectedHeadUnitDoesNotFallBackToBydProjection() {
        display(name = DiLink51ClusterLayout.BASE)
        display(name = DiLink4ClusterDisplay.NAME)
        assertNull(ClusterMapPresentation.findDisplay(app))
    }

    @Test fun clusterRemainsOptInAndUsesNativeCentredMapAtThirtyFps() {
        display()
        assertNull(config())
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        val config = config()!!
        assertEquals(1920, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertEquals(30, config.fps)
        assertEquals(CarPlayClusterDisplay.MAP_URL, config.initialUrl)
        assertEquals(0, config.primaryInputDevice)
        assertEquals(config.safeArea!!.left, config.safeArea!!.right)
        assertEquals(config.safeArea!!.top, config.safeArea!!.bottom)
        assertEquals(100, AirPlayPersistence.loadClusterMapScalePercent(activity))
        assertEquals(50, AirPlayPersistence.loadClusterMarkerYPercent(activity))
        assertEquals(1920.0 / 720, MapMirrors.streamAspect, 0.00001)
    }

    @Test fun savedContentScaleAndMarkerPositionRemainEffective() {
        display()
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        AirPlayPersistence.saveClusterMapScalePercent(activity, 67)
        AirPlayPersistence.saveClusterMarkerXPercent(activity, 60)
        AirPlayPersistence.saveClusterMarkerYPercent(activity, 65)
        AirPlayPersistence.saveClusterContent(activity, CarPlayClusterDisplay.Content.TURN_CARD)
        assertEquals(CarPlayClusterDisplay.config(1920, 720, 67,
            content = CarPlayClusterDisplay.Content.TURN_CARD, markerXPercent = 60, markerYPercent = 65,
            baseSafeArea = CarPlayClusterDisplay.VIRTUAL_SAFE_AREA_PERCENT), config())
    }

    @Test fun dashboardSurfaceUsesIndependentStreamAndTurningOffReleasesPresentation() {
        display()
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        val sink = mock(AndroidMediaSink::class.java)
        ReflectionHelpers.setField(activity, "sink", sink)
        invoke("ensureClusterPresentation")
        val presentation = ReflectionHelpers.getField<ClusterMapPresentation?>(activity, "clusterPresentation")
        assertNotNull("领克仪表应创建独立 Presentation", presentation)
        assertTrue(presentation!!.isShowing)
        val surface = mock(Surface::class.java)
        activity.javaClass.getDeclaredMethod("onClusterSurface", Surface::class.java)
            .apply { isAccessible = true }.invoke(activity, surface)
        verify(sink).setSurface(111, surface)
        verify(sink, never()).setSurface(110, surface)
        AirPlayPersistence.saveClusterMapEnabled(activity, false)
        invoke("ensureClusterPresentation")
        verify(sink).clearSurface(111, surface)
        assertNull(ReflectionHelpers.getField<Surface?>(activity, "clusterSurface"))
        assertNull(ReflectionHelpers.getField<ClusterMapPresentation?>(activity, "clusterPresentation"))
        assertFalse(presentation.isShowing)
    }

    private fun display(name: String = "DashboardMapDisplay", spec: String = "w1920dp-h720dp-mdpi"): Int {
        // TYPE_HDMI 为 2，与实车采集的显示类型一致。
        val id = ShadowDisplayManager.addDisplay(spec, 2)
        displayIds += id
        shadowOf(manager.getDisplay(id)).apply { setName(name); setFlags(Display.FLAG_PRESENTATION) }
        return id
    }

    private fun config() = invoke("clusterDisplayConfig") as AirPlayDisplayConfig?
    private fun invoke(name: String): Any? = activity.javaClass.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(activity)
}
