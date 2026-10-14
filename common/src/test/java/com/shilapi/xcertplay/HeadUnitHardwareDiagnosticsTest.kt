package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HeadUnitHardwareDiagnosticsTest {

    @Test fun inputDeviceLineCarriesIdentitySourcesAndSupportedKeys() {
        val line = HeadUnitHardwareDiagnostics.formatInputDevice(
            id = 7, name = "steering_wheel", descriptor = "abc123",
            vendorId = 0x1234, productId = 0x5678,
            sources = 0x00000101, isVirtual = false,
            supportedKeys = listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_F7),
        )
        assertEquals(
            "id=7 \"steering_wheel\" desc=abc123 vendor=0x1234 product=0x5678 " +
                "sources=[keyboard] keys=[MEDIA_NEXT, F7]",
            line,
        )
    }

    @Test fun inputDeviceLineMarksVirtualAndOmitsEmptyKeys() {
        val line = HeadUnitHardwareDiagnostics.formatInputDevice(
            id = -1, name = "virtual", descriptor = null,
            vendorId = 0, productId = 0, sources = 0x00004002, isVirtual = true,
            supportedKeys = emptyList(),
        )
        assertTrue(line, line.contains("desc=?"))
        assertTrue(line, line.contains("sources=[stylus]"))
        assertTrue(line, line.contains(" virtual"))
        assertFalse(line, line.contains("keys="))
    }

    @Test fun unknownSourcesFallBackToHex() {
        val line = HeadUnitHardwareDiagnostics.formatInputDevice(
            id = 1, name = "x", descriptor = "d", vendorId = 0, productId = 0,
            sources = 0x08000000, isVirtual = false, supportedKeys = emptyList(),
        )
        assertTrue(line, line.contains("sources=[0x8000000]"))
    }

    @Test fun codecLineCarriesDirectionTypesClassAndMaxSize() {
        assertEquals(
            "decoder OMX.MTK.VIDEO.DECODER.HEVC [video/hevc] hardware (max 3840x2176)",
            HeadUnitHardwareDiagnostics.formatCodec(
                name = "OMX.MTK.VIDEO.DECODER.HEVC", types = listOf("video/hevc"),
                encoder = false, hardwareClass = "hardware", maxVideoSize = "3840x2176",
            ),
        )
        assertEquals(
            "encoder c2.android.opus.encoder [audio/opus] software",
            HeadUnitHardwareDiagnostics.formatCodec(
                name = "c2.android.opus.encoder", types = listOf("audio/opus"),
                encoder = true, hardwareClass = "software", maxVideoSize = null,
            ),
        )
    }

    @Test fun keyCandidatesCoverTheWheelKeyRangesDiPlayUses() {
        val candidates = HeadUnitHardwareDiagnostics.KEY_CANDIDATES
        // 领克方控回报 F7；其余是 CarPlay 常用键。
        assertTrue(KeyEvent.KEYCODE_F7 in candidates)
        assertTrue(KeyEvent.KEYCODE_MEDIA_NEXT in candidates)
        assertTrue(KeyEvent.KEYCODE_VOICE_ASSIST in candidates)
        assertTrue(KeyEvent.KEYCODE_DPAD_CENTER in candidates)
        assertTrue(KeyEvent.KEYCODE_CALL in candidates)
    }

    @Test fun emptySystemDataProducesAReadableLine() {
        assertEquals("no input devices visible", HeadUnitHardwareDiagnostics.inputDevicesReport(emptyList()))
        assertEquals("no codec information available", HeadUnitHardwareDiagnostics.codecReport(emptyList()))
    }
}
