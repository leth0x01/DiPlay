package com.shilapi.xcertplay

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Lynk06MediaCenterTest {
    @Test fun lateRequestCannotUndoARealFocusLossOrShutdown() {
        val control = Lynk06MediaControl("lynkplay")
        val request = control.beginRequest()
        control.focusChanged("原车播放器")
        control.completeRequest(request, true)
        assertFalse(control.owned)
        control.focusChanged("lynkplay")
        assertTrue(control.owned)
        val pending = control.beginRequest()
        control.close()
        control.completeRequest(pending, true)
        control.focusChanged("lynkplay")
        assertFalse(control.owned)
    }

    @Test fun immediateGrantWorksWithoutASeparateFocusCallback() {
        val control = Lynk06MediaControl("lynkplay")
        control.completeRequest(control.beginRequest(), true)
        assertTrue(control.owned)
        val old = control.beginRequest()
        control.disconnected()
        control.completeRequest(old, true)
        assertFalse(control.owned)
    }

    @Test fun registeredClientStopsForwardingAfterFocusLossAndSessionEnd() {
        val sent = mutableListOf<Int>()
        val app = RuntimeEnvironment.getApplication()
        val link = Lynk06MediaCenter(app, CarPlayNowPlaying(title = "歌曲", playing = false),
            { sent += it; true }, {})
        val token = Binder()
        val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                assertSame(token, data.readStrongBinder())
                if (code == 6) {
                    val playback = data.readStrongBinder()!!
                    // 原车服务在同步调用中反向查询客户端，不等待 worker 才能取到状态。
                    assertEquals("歌曲", call(playback, 2) { it.readString() })
                    assertEquals(0, call(playback, 11) { it.readInt() })
                } else if (code == 10) data.readLong()
                else assertTrue(code == 4 || code == 5)
                reply!!.writeNoException()
                if (code != 10) reply.writeInt(1)
                return true
            }
        }
        val client = ReflectionHelpers.getField<IBinder>(link, "client")
        try {
            ReflectionHelpers.setField(link, "api", Lynk06MediaCenterService(remote))
            ReflectionHelpers.setField(link, "token", token)
            assertEquals(0, call(client, 3) { it.readInt() })
            ReflectionHelpers.callInstanceMethod<Unit>(link, "claimControl")
            for (code in 1..4) assertEquals(1, call(client, code) { it.readInt() })
            call(client, 25, { it.writeString("原车播放器") }) { }
            assertEquals(0, call(client, 3) { it.readInt() })
            call(client, 25, { it.writeString(app.packageName) }) { }
            link.close()
            assertEquals(0, call(client, 4) { it.readInt() })
            assertEquals(listOf(CarPlayMediaButton.PLAY, CarPlayMediaButton.PAUSE,
                CarPlayMediaButton.NEXT, CarPlayMediaButton.PREVIOUS), sent)
        } finally {
            link.close()
        }
    }
    @Test fun oemCommandsPreservePlayPauseAndTrackIntent() {
        val sent = mutableListOf<Int>()
        val client = Lynk06MusicClient(
            playback = Binder(), snapshot = { CarPlayNowPlaying() },
            send = { sent += it; true }, focusChanged = {},
        )
        for (code in 1..4) assertEquals(1, call(client, code) { it.readInt() })
        assertEquals(listOf(CarPlayMediaButton.PLAY, CarPlayMediaButton.PAUSE,
            CarPlayMediaButton.NEXT, CarPlayMediaButton.PREVIOUS), sent)
    }

    @Test fun rejectedAndUnsupportedCommandsDoNotClaimHandling() {
        val sent = mutableListOf<Int>()
        val client = Lynk06MusicClient(Binder(), { CarPlayNowPlaying() },
            { sent += it; false }, {})
        assertEquals(0, call(client, 3) { it.readInt() })
        assertEquals(0, call(client, 5) { it.readInt() })
        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
    }

    @Test fun playbackGettersMatchTheVehicleParcelContract() {
        var info = CarPlayNowPlaying(title = "歌曲", artist = "歌手", album = "专辑",
            durationMillis = 120_000, elapsedMillis = 4_000, playing = true)
        val playback = Lynk06MusicPlayback("com.leth0x01.lynkplay", null) { info }
        assertEquals("歌曲", call(playback, 2) { it.readString() })
        assertEquals("歌手", call(playback, 3) { it.readString() })
        assertEquals(120_000L, call(playback, 7) { it.readLong() })
        assertEquals(1, call(playback, 11) { it.readInt() })
        assertEquals("com.leth0x01.lynkplay", call(playback, 27) { it.readString() })
        info = info.copy(playing = false)
        assertEquals(0, call(playback, 11) { it.readInt() })
        assertEquals(0, call(playback, 16) { it.readInt() })
    }

    @Test fun clientProvidesPlaybackProgressAndFocusCallback() {
        var focused: String? = null
        val playback = Binder()
        val client = Lynk06MusicClient(playback, { CarPlayNowPlaying(elapsedMillis = 9_000) },
            { false }, { focused = it })
        assertSame(playback, call(client, 10) { it.readStrongBinder() })
        assertEquals(9_000L, call(client, 13) { it.readLong() })
        call(client, 25, { it.writeString("原车播放器") }) { }
        assertEquals("原车播放器", focused)
    }

    @Test fun registrationStateAndUnregistrationUseVerifiedTransactions() {
        val token = Binder()
        val transactions = mutableListOf<Int>()
        val client = Binder()
        val playback = Binder()
        val service = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                transactions += code
                when (code) {
                    1 -> assertSame(client, data.readStrongBinder())
                    4, 5 -> assertSame(token, data.readStrongBinder())
                    6 -> {
                        assertSame(token, data.readStrongBinder())
                        assertSame(playback, data.readStrongBinder())
                    }
                    10 -> {
                        assertSame(token, data.readStrongBinder())
                        assertEquals(8_000L, data.readLong())
                    }
                    else -> fail("未预期的服务调用：$code")
                }
                reply!!.writeNoException()
                if (code == 1) reply.writeStrongBinder(token)
                else if (code != 10) reply.writeInt(1)
                return true
            }
        }
        val api = Lynk06MediaCenterService(service)
        assertSame(token, api.register(client))
        assertTrue(api.requestControl(token))
        assertTrue(api.updatePlayback(token, playback))
        api.updateProgress(token, 8_000)
        assertTrue(api.unregister(token))
        assertEquals(listOf(1, 5, 6, 10, 4), transactions)
    }

    @Test fun mismatchedServiceIsRejectedBeforeSendingAnyTransaction() {
        val wrong = Binder().apply { attachInterface(null, "other.service") }
        assertThrows(IllegalArgumentException::class.java) { Lynk06MediaCenterService(wrong) }
    }

    @Test fun onlyMediaKeysAndDimCapabilitiesAreDeclaredWithTheLocalSource() {
        val token = Binder()
        val codes = mutableListOf<Int>()
        val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                assertSame(token, data.readStrongBinder())
                codes += code
                when (code) {
                    30 -> assertArrayEquals(intArrayOf(0, 2), data.createIntArray())
                    7 -> assertArrayEquals(intArrayOf(0), data.createIntArray())
                    8 -> assertEquals(0, data.readInt())
                    else -> fail("未预期的调用：$code")
                }
                reply!!.writeNoException()
                return true
            }
        }
        val api = Lynk06MediaCenterService(remote)
        api.declareWheelControl(token)
        api.updateSources(token)
        assertEquals(listOf(30, 7, 8), codes)
    }

    private fun <T> call(binder: IBinder, code: Int, write: (Parcel) -> Unit = {}, read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(binder.interfaceDescriptor!!)
            write(data)
            assertTrue(binder.transact(code, data, reply, 0))
            reply.readException()
            return read(reply)
        } finally { data.recycle(); reply.recycle() }
    }
}
