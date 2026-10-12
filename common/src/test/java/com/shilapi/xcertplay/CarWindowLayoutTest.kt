package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class CarWindowLayoutTest {
    @Test fun widePolicyUsesUsableWindowAndKeepsShortWindowsCompact() {
        assertTrue(CarWindowLayout.isWide(1920, 720))
        assertTrue(CarWindowLayout.isWide(1800, 670))
        assertFalse(CarWindowLayout.isWide(960, 360))
        assertFalse(CarWindowLayout.isWide(1280, 720))
        assertFalse(CarWindowLayout.isWide(1800, 400))
        assertEquals(960, AppDialogLayout.widthDp(1800, 670))
        assertEquals(328, AppDialogLayout.widthDp(360, 960))
        assertEquals(640, AppDialogLayout.widthDp(1800, 400))
    }
}
