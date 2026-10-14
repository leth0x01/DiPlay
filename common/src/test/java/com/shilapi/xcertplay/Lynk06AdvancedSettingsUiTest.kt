package com.shilapi.xcertplay

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
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
@Config(sdk = [28], shadows = [LynkVehicleResources::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Lynk06AdvancedSettingsUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private var activity: DiPlayActivity? = null

    @Before fun setup() {
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "BX11", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
    }

    @After fun cleanup() {
        activity?.finish()
        RuntimeEnvironment.setFontScale(1f)
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
        AppAppearanceRuntime.resetForTest()
    }

    @Test fun advancedMenuAndSearchFollowVehiclePolicy() {
        val screen = openAdvanced()
        val hidden = listOf(R.string.settings_harman_fullscreen_experimental, R.string.settings_low_latency_decoder)
        val labels = descendants(screen.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        hidden.forEach { assertFalse(labels.contains(screen.getString(it))) }
        val switches = descendants(screen.window.decorView).filterIsInstance<Switch>().toList()
        for (id in listOf(R.string.settings_mtk_decoder_tuning, R.string.settings_direct_video_output)) {
            assertTrue(switches.single { it.contentDescription == screen.getString(id) }.isChecked)
        }
        for (id in listOf(R.string.efficient_video, R.string.call_echo_cancellation,
            R.string.call_voice_filter, R.string.settings_car_bluetooth_audio)) {
            assertTrue(labels.contains(screen.getString(id)))
        }
        val index = ReflectionHelpers.callInstanceMethod<List<DiPlayActivity.SettingsSearchResult>>(screen, "buildSettingsSearchIndex")
        hidden.forEach { assertFalse(index.any { result -> result.title == screen.getString(it) }) }
        assertTrue(index.any { it.title == screen.getString(R.string.settings_mtk_decoder_tuning) })
    }

    @Test fun otherHardwareKeepsGenericAdvancedControls() {
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "other")
        val screen = openAdvanced()
        val labels = descendants(screen.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        assertTrue(labels.contains(screen.getString(R.string.settings_harman_fullscreen_experimental)))
        assertTrue(labels.contains(screen.getString(R.string.settings_low_latency_decoder)))
    }

    @Test fun capturesCompactAndFullAdvancedSettingsWithLargeFonts() {
        for ((qualifiers, name) in listOf(
            "zh-rCN-w360dp-h800dp-mdpi" to "compact",
            "zh-rCN-w1920dp-h720dp-mdpi" to "full",
            "ar-w960dp-h720dp-xhdpi" to "rtl",
        )) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(1.5f)
            val screen = openAdvanced()
            val root = screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val metrics = screen.resources.displayMetrics
            repeat(3) {
                root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
            }
            val scroll = ReflectionHelpers.getField<ScrollView>(screen, "rootScroll")
            val content = scroll.getChildAt(0)
            assertTrue(content.width > 0 && content.height > 0)
            capture(content, "$name-content")
            capture(root, "$name-top")
            scroll.scrollTo(0, content.height)
            capture(root, "$name-bottom")
            screen.finish()
            activity = null
        }
    }

    private fun openAdvanced(): DiPlayActivity {
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "settings")).setup().get()
        activity = screen
        screen.selectedMtkDecoderName = { "OMX.MTK.VIDEO.DECODER.AVC" }
        ReflectionHelpers.callInstanceMethod<Unit>(screen, "openSettingsCategory",
            ReflectionHelpers.ClassParameter(SettingsCategory::class.java, SettingsCategory.ADVANCED))
        shadowOf(Looper.getMainLooper()).idle()
        return screen
    }

    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            // 直接绘制根 ScrollView 时需补上父容器通常施加的滚动坐标变换。
            val canvas = Canvas(bitmap)
            canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            view.draw(canvas)
            val directory = File("build/reports/lynk06-advanced-settings-ui").apply { mkdirs() }
            File(directory, "$name-font-1.5.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
