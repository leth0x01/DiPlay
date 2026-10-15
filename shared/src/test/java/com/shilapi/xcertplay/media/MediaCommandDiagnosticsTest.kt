package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import org.junit.Assert.*
import org.junit.Test

class MediaCommandDiagnosticsTest {
    private val lines = mutableListOf<String>()
    private var now = 0L
    private val trace = MediaCommandDiagnostics(lines::add) { now }

    @Test fun separatesQueueDelayWriteFailureAndStateObservation() {
        val request = trace.begin(CarPlayMediaButton.PLAY, "lynk06-oem", false)
        now = 120_000_000
        trace.executing(request)
        trace.sent(request, true, false)
        trace.observe(true, true, false)
        assertTrue(lines.any { it.contains("queueMs=120") })
        assertTrue(lines.any { it.contains("pressed=true released=false") })
        assertTrue(lines.any { it.contains("发送失败") })
        assertFalse(lines.any { it.contains("request=$request 观察到目标") })
    }

    @Test fun reportsAllCandidatesRatherThanClaimingWhichRepeatedPressSucceeded() {
        val first = trace.begin(CarPlayMediaButton.PLAY, "oem", false)
        val second = trace.begin(CarPlayMediaButton.PLAY, "oem", false)
        listOf(first, second).forEach { trace.executing(it); trace.sent(it, true, true) }
        trace.observe(true, true, false)
        assertTrue(lines.any { it.contains("candidates=[$first, $second]") })
        listOf(first, second).forEach { id ->
            assertTrue(lines.any { it.contains("request=$id 观察到目标变化（非指令确认）") })
        }
    }

    @Test fun recordsStateArrivingBeforeWriteCompletion() {
        val id = trace.begin(CarPlayMediaButton.PAUSE, "oem", true)
        trace.executing(id)
        trace.observe(false, true, false)
        trace.sent(id, true, true)
        trace.timeout(id)
        assertTrue(lines.any { it.contains("发送期间观察到目标变化") })
        assertFalse(lines.any { it.contains("观察窗口结束") })
    }

    @Test fun queuedTimeoutKeepsActualLateExecutionTime() {
        val id = trace.begin(CarPlayMediaButton.PLAY, "oem", false)
        now = 5_000_000_000
        trace.timeout(id)
        now = 6_000_000_000
        trace.executing(id)
        trace.sent(id, true, true)
        assertTrue(lines.any { it.contains("stage=queued") })
        assertTrue(lines.any { it.contains("queueMs=6000") })
        assertTrue(lines.any { it.contains("发送完成，但已超过观察窗口") })
    }

    @Test fun repeatedStateDoesNotCountAsAcknowledgement() {
        val id = trace.begin(CarPlayMediaButton.PLAY, "oem", true)
        trace.executing(id)
        trace.sent(id, true, true)
        trace.observe(true, false, false)
        trace.timeout(id)
        assertTrue(lines.any { it.contains("alreadyAtTarget=true") })
        assertTrue(lines.any { it.contains("stage=sent targetObserved=false") })
    }

    @Test fun pendingRequestsAreBoundedAndClosedWithReasons() {
        repeat(17) { trace.begin(CarPlayMediaButton.NEXT, "oem", false) }
        trace.close()
        assertEquals(1, lines.count { it.contains("超出跟踪上限") })
        assertEquals(16, lines.count { it.contains("会话关闭") })
    }
}
