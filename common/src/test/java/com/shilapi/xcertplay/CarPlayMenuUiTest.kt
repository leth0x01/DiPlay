package com.shilapi.xcertplay

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarPlayMenuUiTest {
    @Test fun menuKeepsHeaderAndActionsVisibleAcrossThemesAndWindowShapes() {
        for ((width, height, locale, density, night, name) in listOf(
            Scenario(360, 960, "zh-rCN", "mdpi", true, "compact-dark"),
            Scenario(960, 360, "zh-rCN", "xhdpi", false, "1920x720-light"),
            Scenario(1920, 720, "zh-rCN", "mdpi", false, "lynk06-160dpi-light"),
            Scenario(1920, 720, "zh-rCN", "mdpi", true, "lynk06-160dpi-dark"),
            Scenario(1800, 670, "ar", "mdpi", false, "lynk06-rtl-safe-window"),
            Scenario(1280, 720, "zh-rCN", "xhdpi", true, "full-dark"),
            Scenario(960, 360, "ar", "xhdpi", false, "rtl"),
        )) {
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${height}dp-$density")
            val app = RuntimeEnvironment.getApplication()
            val previous = Configuration(app.resources.configuration)
            val flags = app.applicationInfo.flags
            app.applicationInfo.flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
            app.resources.updateConfiguration(Configuration(previous).apply { fontScale = 1.5f }, app.resources.displayMetrics)
            AirPlayPersistence.saveManualHotspotSsid(app, "Lynk06")
            AirPlayPersistence.saveManualHotspotPassphrase(app, "12345678")
            AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
            val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
            activity.setTheme(android.R.style.Theme_Material_NoActionBar)
            ReflectionHelpers.setField(activity, "airPlayIdentity", AirPlayIdentity.generate())
            try {
                invoke(activity, "loadPersistedSettings")
                ReflectionHelpers.setField(activity, "appNight", night)
                invoke(activity, "buildContentView")
                invoke(activity, "openSettingsMenu")
                val menu = ReflectionHelpers.getField<View>(activity, "settingsMenu")
                val scale = activity.resources.displayMetrics.density
                val pixelWidth = (width * scale).toInt()
                val pixelHeight = (height * scale).toInt()
                val root = menu.parent as View
                fun layout() {
                    repeat(4) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(pixelWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(pixelHeight, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, pixelWidth, pixelHeight)
                    }
                }
                layout()
                if (CarWindowLayout.isWide(width, height)) {
                    assertEquals(1120, (menu as ViewGroup).getChildAt(0).width)
                    val inputs = descendants(menu).filterIsInstance<EditText>().filter { it.isShown }
                    assertTrue("宽屏输入框应保留触控高度：${inputs.map { it.height }}", inputs.all { it.height >= 72 })
                    assertTrue(inputs.all { it.textSize / it.resources.displayMetrics.scaledDensity >= 20f })
                }
                val views = descendants(menu)
                val header = views.single { it.tag == CarPlayMenuLayout.HEADER_TAG }
                val footer = views.single { it.tag == CarPlayMenuLayout.FOOTER_TAG }
                val title = views.filterIsInstance<TextView>().single { it.text == activity.getString(R.string.carplay_settings) }
                assertEquals(DiPlayPalette.of(night).primaryText, title.currentTextColor)
                assertTrue(views.count { it.tag == CarPlayMenuLayout.CATEGORY_TAG } >= 4)
                assertFullyVisible(header); assertFullyVisible(footer)
                val scroll = views.filterIsInstance<ScrollView>().single()
                val headerTop = header.top
                val footerTop = footer.top
                capture(menu, "$name-top")
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                assertFullyVisible(header); assertFullyVisible(footer)
                assertEquals(headerTop, header.top); assertEquals(footerTop, footer.top)
                capture(menu, "$name-bottom")
                scroll.scrollTo(0, 0)
                val input = views.filterIsInstance<EditText>().single { it.text.toString() == "Lynk06" }
                assertTrue(input.background is android.graphics.drawable.RippleDrawable)
                if (width == 360) assertEquals(LinearLayout.VERTICAL, (input.parent as LinearLayout).orientation)
                input.setText("Unsaved hotspot")
                val cancel = descendants(header).filterIsInstance<Button>().single { it.contentDescription == activity.getString(R.string.discard_changes_and_exit_settings) }
                cancel.performClick()
                assertFalse(ReflectionHelpers.getField<Boolean>(activity, "menuOpen"))
                assertEquals("Lynk06", AirPlayPersistence.loadManualHotspotSsid(activity))
            } finally {
                ReflectionHelpers.getField<AtomicBoolean>(activity, "shuttingDown").set(true)
                ReflectionHelpers.getField<Handler>(activity, "mainHandler").removeCallbacksAndMessages(null)
                ReflectionHelpers.getField<ExecutorService>(activity, "teardownExecutor").shutdownNow()
                ReflectionHelpers.getField<ExecutorService>(activity, "airPlayCommandExecutor").shutdownNow()
                activity.finish()
                CarPlayBackgroundSession.clear()
                app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
                app.resources.updateConfiguration(previous, app.resources.displayMetrics)
                app.applicationInfo.flags = flags
            }
        }
    }

    private data class Scenario(val width: Int, val height: Int, val locale: String, val density: String, val night: Boolean, val name: String)
    private fun invoke(activity: CarPlayHostActivity, name: String) {
        activity.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }
    private fun assertFullyVisible(view: View) {
        val bounds = Rect()
        assertTrue("菜单固定区域必须可见", view.getGlobalVisibleRect(bounds))
        assertEquals("菜单固定区域不能裁切", view.height, bounds.height())
    }
    private fun capture(menu: View, name: String) {
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(menu.width, menu.height, Bitmap.Config.ARGB_8888)
        try {
            menu.draw(Canvas(bitmap))
            val directory = File("build/reports/carplay-menu-ui").apply { mkdirs() }
            File(directory, "$name-font-1.5.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
