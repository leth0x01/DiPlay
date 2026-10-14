package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [LynkVehicleResources::class])
class LynkSiriSettingsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val activities = mutableListOf<DiPlayActivity>()
    private lateinit var previousConfiguration: Configuration

    @Before fun setup() {
        previousConfiguration = Configuration(app.resources.configuration)
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "BX11", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        app.getSharedPreferences("diplay_wheel_map_zoom", 0).edit().clear().commit()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
    }

    @After fun cleanup() {
        for (activity in activities) {
            ReflectionHelpers.getField<Handler>(activity, "handler").removeCallbacksAndMessages(null)
            activity.finish()
        }
        app.resources.updateConfiguration(previousConfiguration, app.resources.displayMetrics)
    }

    @Test fun nativeVoiceSwitchNeedsNoLearningOrServiceAndSavesImmediately() {
        val activity = activity()
        val defaultControls = controls(activity)
        val switch = descendants(defaultControls).filterIsInstance<Switch>().single {
            it.contentDescription == activity.getString(R.string.wheel_siri_key)
        }
        assertTrue(switch.isChecked)
        switch.isChecked = false
        assertFalse(WheelZoomSettings.siriKey(app))
        val off = controls(activity)
        assertFalse(descendants(off).filterIsInstance<Switch>().single {
            it.contentDescription == activity.getString(R.string.wheel_siri_key)
        }.isChecked)
        switch.isChecked = true
        assertTrue(WheelZoomSettings.siriKey(app))
        assertFalse(WheelKeyService.wanted(app))
        val on = controls(activity)
        val labels = labels(on)
        assertTrue(activity.getString(R.string.settings_oem_siri_key_description) in labels)
        assertFalse(activity.getString(R.string.wheel_keys_open_settings) in labels)
        assertFalse(activity.getString(R.string.wheel_keys_enable_adb) in labels)
        assertFalse(labels.any { it.startsWith(activity.getString(R.string.wheel_key_role_siri) + " ·") })
        assertEquals(labels(off).size, labels.size)
    }

    @Test fun onlyVerifiedVehicleDefaultsToSiriWithoutOverridingSavedChoices() {
        assertTrue(WheelZoomSettings.siriKey(app))
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "other")
        assertFalse(WheelZoomSettings.siriKey(app))
        WheelZoomSettings.setSiriKey(app, true)
        assertTrue(WheelZoomSettings.siriKey(app))
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "xecarx")
        WheelZoomSettings.setSiriKey(app, false)
        assertFalse(WheelZoomSettings.siriKey(app))
    }

    @Test fun historicalCustomSiriKeyIsInactiveOnlyOnVerifiedVehicle() {
        val key = WheelKey(KeyEvent.KEYCODE_F6, 0, "external-wheel")
        WheelZoomSettings.assign(app, WheelZoomSettings.Role.SIRI, key)
        WheelZoomSettings.setSiriKey(app, true)
        assertFalse(WheelZoomSettings.isSiriKey(app, key))
        assertFalse(WheelZoomSettings.anyEnabled(app))
        WheelZoomSettings.setJoystick(app, true)
        assertTrue(WheelZoomSettings.anyEnabled(app))
        assertNull(WheelZoomSettings.conflict(app, WheelZoomSettings.Role.SELECT, key))
        WheelZoomSettings.setJoystick(app, false)

        // 构建目标仍是领克，但部分设备标识不足以启用原车广播路径。
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "other")
        assertTrue(WheelZoomSettings.isSiriKey(app, key))
        assertTrue(WheelZoomSettings.anyEnabled(app))
        val activity = activity()
        val labels = labels(controls(activity))
        assertTrue(activity.getString(R.string.wheel_siri_key_description) in labels)
        assertTrue(activity.getString(R.string.wheel_keys_open_settings) in labels)
        assertTrue(activity.getString(R.string.wheel_key_assign,
            activity.getString(R.string.wheel_key_role_siri), key.toString()) in labels)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeVoiceControlsRenderWithLargeFontsInCompactFullAndArabicLayouts() {
        WheelZoomSettings.setSiriKey(app, true)
        for ((widthDp, locale, density) in listOf(
            Triple(360, "zh-rCN", "mdpi"), Triple(1280, "zh-rCN", "xhdpi"),
            Triple(600, "ar-ldrtl", "xhdpi"),
        )) {
            RuntimeEnvironment.setQualifiers("$locale-w${widthDp}dp-h960dp-$density")
            app.resources.updateConfiguration(Configuration(app.resources.configuration).apply {
                fontScale = 1.5f
            }, app.resources.displayMetrics)
            val activity = activity()
            val root = controls(activity)
            root.layoutDirection = activity.resources.configuration.layoutDirection
            val width = (widthDp * activity.resources.displayMetrics.density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, width, root.measuredHeight)
            for (switch in descendants(root).filterIsInstance<Switch>()) {
                val row = switch.parent as ViewGroup
                assertTrue(switch.left >= 0 && switch.right <= row.width)
                assertTrue(switch.height > 0 && switch.bottom <= row.height)
            }
            val bitmap = Bitmap.createBitmap(width, root.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.rgb(28, 32, 42))
                root.draw(canvas)
                val directory = File("build/reports/lynk-siri-ui").apply { mkdirs() }
                File(directory, "$locale-${widthDp}dp-$density-font-1.5.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
        }
    }

    private fun activity() = Robolectric.buildActivity(DiPlayActivity::class.java).get().also {
        it.setTheme(android.R.style.Theme_Material_NoActionBar)
        activities.add(it)
    }

    private fun controls(activity: DiPlayActivity) = LinearLayout(activity).also {
        it.orientation = LinearLayout.VERTICAL
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "wheelKeysSettings",
            ClassParameter.from(LinearLayout::class.java, it))
    }

    private fun labels(view: View) = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
