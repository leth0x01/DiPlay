package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Looper
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Lynk06VoiceKeyReceiverTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val receiver = Lynk06VoiceKeyReceiver()
    private var nativeReceived = 0
    private val nativeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { nativeReceived++ }
    }
    private val controller = mock(CarPlayController::class.java)

    @Before fun setup() {
        CarPlayBackgroundSession.active = false
        WheelZoomSettings.setSiriKey(app, true)
        for ((field, value) in mapOf("PRODUCT" to "IHU514G", "DEVICE" to "IHU514G",
            "MODEL" to "IHU514G", "HARDWARE" to "xecarx")) {
            ReflectionHelpers.setStaticField(Build::class.java, field, value)
        }
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        app.registerReceiver(receiver, filter(999))
        app.registerReceiver(nativeReceiver, filter(0))
    }

    @After fun cleanup() {
        CarPlayMediaKeys.detach(controller)
        CarPlayBackgroundSession.active = false
        app.unregisterReceiver(receiver)
        app.unregisterReceiver(nativeReceiver)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun connectedShortPressRequestsSiriAndStopsNativeVoice() {
        connect()
        `when`(controller.requestSiri()).thenReturn(true)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        verify(controller).requestSiri()
        assertEquals(0, nativeReceived)
    }

    @Test fun disconnectedShortPressKeepsNativeVoice() {
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        verify(controller, never()).requestSiri()
        assertEquals(1, nativeReceived)
    }

    @Test fun disabledSiriKeepsNativeVoiceEvenWhileConnected() {
        connect()
        `when`(controller.requestSiri()).thenReturn(true)
        WheelZoomSettings.setSiriKey(app, false)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        verify(controller, never()).requestSiri()
        assertEquals(1, nativeReceived)
    }

    @Test fun siriSwitchAppliesImmediatelyWithoutReconnecting() {
        connect()
        `when`(controller.requestSiri()).thenReturn(true)
        WheelZoomSettings.setSiriKey(app, false)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        WheelZoomSettings.setSiriKey(app, true)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        WheelZoomSettings.setSiriKey(app, false)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        verify(controller, times(1)).requestSiri()
        assertEquals(2, nativeReceived)
    }

    @Test fun rejectedSiriKeepsNativeVoice() {
        connect()
        `when`(controller.requestSiri()).thenReturn(false)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        assertEquals(1, nativeReceived)
    }

    @Test fun callKeepsNativeVoice() {
        connect()
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        verify(controller, never()).requestSiri()
        assertEquals(1, nativeReceived)
    }

    @Test fun longPressLaunchesLynkPlayAndStopsHiCarBroadcast() {
        WheelZoomSettings.setSiriKey(app, false)
        send(Lynk06VoiceKeyReceiver.LONG_PRESS)
        val launch = shadowOf(app).nextStartedActivity
        assertEquals(DiPlayActivity::class.java.name, launch.component?.className)
        assertTrue(launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(0, nativeReceived)
    }

    @Test fun wrongHardwareAndUnknownActionArePassedThrough() {
        send(7)
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "other")
        send(Lynk06VoiceKeyReceiver.LONG_PRESS)
        assertEquals(2, nativeReceived)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun wrongKeyIsPassedThrough() {
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS, key = 231)
        assertEquals(1, nativeReceived)
    }

    @Test fun bootstrapWithoutAnActiveSessionKeepsNativeVoice() {
        CarPlayMediaKeys.attach(app, controller)
        `when`(controller.requestSiri()).thenReturn(true)
        send(Lynk06VoiceKeyReceiver.SHORT_PRESS)
        verify(controller, never()).requestSiri()
        assertEquals(1, nativeReceived)
    }

    private fun connect() {
        CarPlayMediaKeys.attach(app, controller)
        CarPlayBackgroundSession.active = true
    }

    private fun filter(priority: Int) = IntentFilter(Lynk06VoiceKeyReceiver.ACTION).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        this.priority = priority
    }

    private fun send(action: Int, key: Int = Lynk06VoiceKeyReceiver.VOICE_KEY) {
        app.sendOrderedBroadcast(Intent(Lynk06VoiceKeyReceiver.ACTION).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(Lynk06VoiceKeyReceiver.EXTRA_KEY, key)
            putExtra(Lynk06VoiceKeyReceiver.EXTRA_ACTION, action)
        }, null)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
