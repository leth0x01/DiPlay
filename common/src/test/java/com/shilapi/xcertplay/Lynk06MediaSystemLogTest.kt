package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class Lynk06MediaSystemLogTest {
    private fun context(permission: Int) = mock(Context::class.java).apply {
        `when`(checkSelfPermission(Manifest.permission.READ_LOGS)).thenReturn(permission)
    }

    @Test fun deniedPermissionIsReportedWithoutStartingAProcess() {
        val lines = mutableListOf<String>()
        Lynk06MediaSystemLog(lines::add) { error("不应启动进程") }.use {
            it.start(context(PackageManager.PERMISSION_DENIED))
            assertTrue(Lynk06MediaSystemLog.diagnosticReport().contains("缺少 READ_LOGS"))
        }
        assertTrue(lines.any { it.contains("缺少 READ_LOGS") })
        assertEquals("stopped", Lynk06MapDiagnostics.snapshot().nativeCapture)
    }

    @Test fun keepsCoverCorrelationWhileRedactingNativeLogsAndReportsExit() {
        val text = "10-15 12:21:13.000 100 200 I DimInteractionHelper: cover=/cache/12345678-abcd-1234-abcd-123456789012.jpg\n" +
            "10-15 12:21:14.000 100 200 I DimInteractionHelper: password=private\n"
        val lines = CopyOnWriteArrayList<String>()
        val finished = CountDownLatch(1)
        val process = FakeProcess(ByteArrayInputStream(text.toByteArray()))
        Lynk06MediaSystemLog({ lines.add(it); if (it.contains("采集进程退出")) finished.countDown() }, { process }).use {
            it.start(context(PackageManager.PERMISSION_GRANTED))
            assertTrue(finished.await(3, TimeUnit.SECONDS))
        }
        assertTrue(lines.any { it.contains("covers=[12345678abcd]") && it.contains("[identifier].jpg") })
        assertFalse(lines.any { it.contains("password=private") })
        assertTrue(process.destroyed.await(1, TimeUnit.SECONDS))
    }

    @Test fun closingSessionDestroysBlockedLogReader() {
        val input = PipedInputStream()
        val output = PipedOutputStream(input)
        val started = CountDownLatch(1)
        val process = FakeProcess(input)
        val capture = Lynk06MediaSystemLog({}, { started.countDown(); process })
        try {
            capture.start(context(PackageManager.PERMISSION_GRANTED))
            assertTrue(started.await(3, TimeUnit.SECONDS))
            capture.close()
            assertTrue(process.destroyed.await(1, TimeUnit.SECONDS))
        } finally { capture.close(); output.close(); input.close() }
    }

    private class FakeProcess(private val input: java.io.InputStream) : Process() {
        val destroyed = CountDownLatch(1)
        override fun getInputStream() = input
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun destroy() { input.close(); destroyed.countDown() }
    }
}
