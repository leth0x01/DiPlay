package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import org.junit.Assert.*
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
@Config(sdk = [29], shadows = [LynkVehicleResources::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LynkSetupUiTest {
    @Test fun targetGuideAndBydIsolationRemainReadableWithLargeFonts() {
        for ((width, locale, density) in listOf(
            Triple(360, "zh-rCN", "mdpi"), Triple(1280, "zh-rCN", "xhdpi"), Triple(600, "ar", "mdpi"),
        )) {
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h960dp-$density")
            val app = RuntimeEnvironment.getApplication()
            app.resources.updateConfiguration(Configuration(app.resources.configuration).apply {
                fontScale = 1.5f
            }, app.resources.displayMetrics)
            val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
            activity.setTheme(android.R.style.Theme_Material_NoActionBar)
            try {
                val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "setupCarStep",
                    ClassParameter.from(LinearLayout::class.java, root))
                val labels = descendants(root).filterIsInstance<TextView>()
                assertTrue(labels.any { it.text == activity.getString(R.string.settings_lynk06_target) })
                assertFalse(labels.any { it.text.contains("DiLink", ignoreCase = true) })
                val scale = activity.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((width * scale).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((960 * scale).toInt(), View.MeasureSpec.AT_MOST))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertTrue(labels.all { it.width > 0 && it.height > 0 })
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    val directory = File("build/reports/lynk-setup-ui").apply { mkdirs() }
                    File(directory, "$locale-${width}dp-$density-font-1.5.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { bitmap.recycle() }

                root.removeAllViews()
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "advancedVehicleDataSettings",
                    ClassParameter.from(LinearLayout::class.java, root))
                assertEquals(0, root.childCount)
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "setupFeaturesStep",
                    ClassParameter.from(LinearLayout::class.java, root))
                val features = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(features.contains(activity.getString(R.string.report_location_to_iphone)))
                assertFalse(features.any { it.contains("BYD") || it.contains("DiLink") })
            } finally {
                activity.finish()
                app.resources.updateConfiguration(Configuration(app.resources.configuration).apply {
                    fontScale = 1f
                }, app.resources.displayMetrics)
            }
        }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }
}
