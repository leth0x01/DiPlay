package com.shilapi.xcertplay

import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

class Lynk06MapDiagnosticsTest {
    private val lines = mutableListOf<String>()
    private val owner = Any()
    private val prefix = "10-13 14:08:10.491  1237 26368 D ECarXPowerManagerService: <ECarXScreenProjectionService>"

    @Before fun setUp() { Lynk06MapDiagnostics.resetForTest() }
    @After fun tearDown() { Lynk06MapDiagnostics.resetForTest() }
    private fun native(body: String) = Lynk06MapDiagnostics.nativeLine(owner, prefix + body, lines::add)

    @Test fun mapsOemOpenAndCloseIndependentlyOfNavigationAndAndroidWindow() {
        Lynk06MapDiagnostics.beginLink(owner, lines::add)
        Lynk06MapDiagnostics.navigation(owner, true, lines::add)
        Lynk06MapDiagnostics.link(owner, true, true, 1, 0, lines::add)
        Lynk06MapDiagnostics.presentation(owner, true, true, 0, 1f, 1, true, true, "create", lines::add)
        Lynk06MapDiagnostics.capture(owner, "starting", lines::add)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open")
        assertTrue(Lynk06MapDiagnostics.snapshot().appMapReady)
        assertEquals(true, Lynk06MapDiagnostics.snapshot().lastMcuProjectionRequest)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY close")
        val snapshot = Lynk06MapDiagnostics.snapshot()
        assertTrue(snapshot.appMapReady)
        assertEquals(true, snapshot.navigationRequested)
        assertEquals(false, snapshot.lastMcuProjectionRequest)
        assertTrue(Lynk06MapDiagnostics.report().contains("physicalVisibility=unknown"))
    }

    @Test fun rejectedStartIsNotAnMcuOpenRequest() {
        Lynk06MapDiagnostics.capture(owner, "starting", lines::add)
        native("isEnableScreenProjection isFactoryReset=false isNaviNotStarted=true")
        native("isEnableScreenProjection ihuState=17 lcdState=0")
        native("startScreenProjection start=true mIsECarXSingalConnect=true not enable Screen Projection.")
        val snapshot = Lynk06MapDiagnostics.snapshot()
        assertEquals(true, snapshot.nativeRejected)
        assertEquals(true, snapshot.naviNotStarted)
        assertEquals(false, snapshot.factoryReset)
        assertEquals(17, snapshot.ihuState)
        assertEquals(0, snapshot.lcdState)
        assertNull(snapshot.lastMcuProjectionRequest)
    }

    @Test fun ignoresForeignTagsMalformedValuesAndOldCaptureOwners() {
        Lynk06MapDiagnostics.capture(owner, "starting", lines::add)
        Lynk06MapDiagnostics.nativeLine(owner, prefix.replace("ECarXPowerManagerService", "OtherApp") +
            "sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open", lines::add)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY opened")
        native("startScreenProjection start=maybe mIsECarXSingalConnect=true")
        assertNull(Lynk06MapDiagnostics.snapshot().lastNativeEvent)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open")
        Lynk06MapDiagnostics.capture(Any(), "starting", lines::add)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY close")
        assertNull(Lynk06MapDiagnostics.snapshot().lastMcuProjectionRequest)
    }

    @Test fun oldLinkAndPresentationCannotOverwriteTheirReplacements() {
        Lynk06MapDiagnostics.beginLink(owner, lines::add)
        val next = Any()
        Lynk06MapDiagnostics.beginLink(next, lines::add)
        Lynk06MapDiagnostics.navigation(next, true, lines::add)
        Lynk06MapDiagnostics.endLink(owner, lines::add)
        assertEquals(true, Lynk06MapDiagnostics.snapshot().navigationRequested)
        Lynk06MapDiagnostics.presentation(next, true, true, 0, 1f, 1, true, true, "create", lines::add)
        Lynk06MapDiagnostics.presentation(owner, false, false, 4, 0f, 1, false, false, "stop", lines::add)
        assertTrue(Lynk06MapDiagnostics.snapshot().appMapReady)
        Lynk06MapDiagnostics.presentation(next, false, true, 0, 1f, 1, true, true, "stop", lines::add)
        assertFalse(Lynk06MapDiagnostics.snapshot().appMapReady)
    }

    @Test fun correlationSurvivesRedactionAndRepeatedPowerLogsDoNotFloodTransitions() {
        Lynk06MapDiagnostics.capture(owner, "starting", lines::add)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open")
        val revision = Lynk06MapDiagnostics.snapshot().revision
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open")
        assertEquals(revision, Lynk06MapDiagnostics.snapshot().revision)
        assertTrue(DiagnosticRedactor.redact("封面事件 ${Lynk06MapDiagnostics.context()}")!!.contains("mapRevision=$revision"))
        assertTrue(lines.all { DiagnosticRedactor.redact(it) == it })
        assertTrue(lines.last().contains("mcuRequest=true"))
    }

    @Test fun repeatedMcuOpenRefreshesEventTimeWithoutInventingAVisibilityTransition() {
        Lynk06MapDiagnostics.capture(owner, "starting", lines::add)
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open")
        val revision = Lynk06MapDiagnostics.snapshot().revision
        Lynk06MapDiagnostics.nativeLine(owner, prefix.replace("14:08:10.491", "14:09:20.123") +
            "sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY open", lines::add)
        assertEquals(revision, Lynk06MapDiagnostics.snapshot().revision)
        assertTrue(Lynk06MapDiagnostics.report().contains("lastMcuOpenAt=10-13 14:09:20.123"))
        assertTrue(Lynk06MapDiagnostics.report().contains("mcuCloseEverSeen=false"))
        native("sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY close")
        assertTrue(Lynk06MapDiagnostics.report().contains("lastMcuCloseAt=10-13 14:08:10.491"))
        assertTrue(Lynk06MapDiagnostics.report().contains("mcuCloseEverSeen=true"))
        Lynk06MapDiagnostics.capture(Any(), "starting", lines::add)
        assertTrue(Lynk06MapDiagnostics.report().contains("lastMcuOpenAt=null lastMcuCloseAt=null"))
    }
}
