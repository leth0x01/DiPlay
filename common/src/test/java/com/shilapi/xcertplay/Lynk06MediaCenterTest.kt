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
    @Test fun instrumentMusicSourceMatchesOnlineMusicInTheVehicle() {
        val info = CarPlayNowPlaying(title = "CarPlay 歌曲", playing = true)
        val playback = Lynk06MusicPlayback("lynkplay", null) { info }
        val client = Lynk06MusicClient(playback, { info }, { true }, {})
        // 原车 DIM 来源列表将 ONLINE=6 编码为音乐；LOCAL=0 无对应项目。
        assertEquals(6, call(playback, 9) { it.readInt() })
        assertEquals(6, call(client, 12) { it.readInt() })
        assertArrayEquals(intArrayOf(6), call(client, 11) { it.createIntArray() })
    }

    @Test fun selectingInstrumentMusicClaimsControlBeforePlaying() {
        val app = RuntimeEnvironment.getApplication()
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val played = java.util.concurrent.CountDownLatch(1)
        val token = Binder()
        val link = Lynk06MediaCenter(app, CarPlayNowPlaying(title = "歌曲"),
            { events += "send:$it"; played.countDown(); true }, {})
        val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                assertSame(token, data.readStrongBinder())
                when (code) {
                    5 -> events += "claim"
                    6 -> {
                        val playback = data.readStrongBinder()!!
                        assertEquals(6, call(playback, 9) { it.readInt() })
                        events += "publish"
                    }
                    8 -> assertEquals(6, data.readInt())
                    9 -> {
                        assertEquals(6, data.readInt())
                        assertEquals(1, data.readInt())
                        events += "playlist"
                    }
                    10 -> data.readLong()
                    4 -> Unit
                    else -> fail("未预期的服务调用：$code")
                }
                reply!!.writeNoException()
                if (code == 4 || code == 5 || code == 6) reply.writeInt(1)
                return true
            }
        }
        try {
            ReflectionHelpers.setField(link, "api", Lynk06MediaCenterService(remote))
            ReflectionHelpers.setField(link, "token", token)
            val client = ReflectionHelpers.getField<IBinder>(link, "client")
            assertEquals(0, call(client, 8, { it.writeInt(1) }) { it.readInt() })
            assertEquals(1, call(client, 8, { it.writeInt(6) }) { it.readInt() })
            assertTrue("音乐选择未转发播放", played.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf("claim", "playlist", "publish", "send:${CarPlayMediaButton.PLAY}"), events)
        } finally { link.close() }
    }

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
                else if (code == 8) assertEquals(6, data.readInt())
                else assertTrue(code == 4 || code == 5 || code == 9)
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
            call(client, 17, { it.writeInt(2); it.writeString("原车蓝牙") }) { }
            assertEquals(0, call(client, 3) { it.readInt() })
            call(client, 25, { it.writeString(app.packageName) }) { }
            call(client, 25, { it.writeString("原车播放器") }) { }
            assertEquals(0, call(client, 3) { it.readInt() })
            call(client, 25, { it.writeString(app.packageName) }) { }
            link.close()
            assertEquals(0, call(client, 4) { it.readInt() })
            assertEquals(0, call(client, 8, { it.writeInt(6) }) { it.readInt() })
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

    @Test fun multimediaListGetterReturnsNullParcelableEncoding() {
        val playback = Binder()
        val client = Lynk06MusicClient(playback, { CarPlayNowPlaying() }, { false }, {})
        // getMultiMediaList(29) 返回 Parcelable IMediaLists（非 binder）；null 编码为 writeInt(0)。
        // 误用 writeStrongBinder 会写出 flat-binder 魔数（readInt 非 0）并留下多余字节。
        call(client, 29, { it.writeIntArray(intArrayOf(6)) }) {
            assertEquals(0, it.readInt())
            assertEquals(0, it.dataAvail())
        }
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
                    19 -> {
                        assertEquals("com.leth0x01.lynkplay.clustersystem", data.readString())
                        assertSame(client, data.readStrongBinder())
                    }
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
                if (code == 19) reply.writeStrongBinder(token)
                else if (code != 10) reply.writeInt(1)
                return true
            }
        }
        val api = Lynk06MediaCenterService(service)
        assertSame(token, api.register(client, "com.leth0x01.lynkplay.clustersystem"))
        assertTrue(api.requestControl(token))
        assertTrue(api.updatePlayback(token, playback))
        api.updateProgress(token, 8_000)
        assertTrue(api.unregister(token))
        assertEquals(listOf(19, 5, 6, 10, 4), transactions)
    }

    @Test fun mismatchedServiceIsRejectedBeforeSendingAnyTransaction() {
        val wrong = Binder().apply { attachInterface(null, "other.service") }
        assertThrows(IllegalArgumentException::class.java) { Lynk06MediaCenterService(wrong) }
    }

    @Test fun currentTrackReplacesTheOemPlaylistWithOneRealArtworkEntry() {
        val token = Binder()
        val info = CarPlayNowPlaying(title = "当前歌曲", artist = "歌手", album = "专辑", durationMillis = 123_000)
        val artwork = android.net.Uri.parse("http://127.0.0.1:1234/covers/session/current.jpg")
        var received = false
        val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(9, code)
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                assertSame(token, data.readStrongBinder())
                assertEquals(6, data.readInt())
                assertEquals(1, data.readInt()) // no invented previous/next tracks
                assertEquals(1, data.readInt())
                assertEquals(lynk06MusicId(info), data.readString())
                assertEquals(info.title, data.readString())
                assertEquals(info.artist, data.readString())
                assertEquals(info.album, data.readString())
                repeat(6) { assertEquals("", data.readString()) }
                assertEquals(123_000L, data.readLong())
                assertEquals(0, data.readInt())
                assertEquals(0, data.readInt())
                repeat(5) { assertEquals("", data.readString()) }
                assertEquals(6, data.readInt())
                assertNull(data.readParcelable<android.net.Uri>(javaClass.classLoader))
                assertEquals("", data.readString())
                assertNull(data.readParcelable<android.net.Uri>(javaClass.classLoader))
                assertEquals(artwork, data.readParcelable<android.net.Uri>(javaClass.classLoader))
                assertEquals("", data.readString())
                assertEquals("", data.readString())
                assertEquals(0, data.readInt())
                assertEquals("", data.readString())
                assertEquals(-1, data.readInt())
                assertEquals(0, data.readInt())
                assertEquals(0, data.readInt())
                assertEquals(0, data.dataAvail())
                received = true
                reply!!.writeNoException()
                return true
            }
        }
        Lynk06MediaCenterService(remote).updateCurrentTrack(token, info, artwork)
        assertTrue(received)
    }

    @Test fun onlyMediaKeysAndDimCapabilitiesAreDeclaredWithTheInstrumentMusicSource() {
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
                    7 -> assertArrayEquals(intArrayOf(6), data.createIntArray())
                    8 -> assertEquals(6, data.readInt())
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

    @Test fun sourceSelectionRejectsOtherSourcesAndUnrelatedTracks() {
        var selections = 0
        val info = CarPlayNowPlaying(title = "歌曲", artist = "歌手")
        val client = Lynk06MusicClient(Binder(), { info }, { false }, {},
            { selections++; true })
        assertEquals(1, call(client, 8, { it.writeInt(6) }) { it.readInt() })
        for (code in listOf(21)) {
            assertEquals(0, call(client, code, { it.writeInt(1); it.writeString(null) }) { it.readInt() })
            assertEquals(0, call(client, code, { it.writeInt(6); it.writeString("其他歌曲") }) { it.readInt() })
            assertEquals(1, call(client, code, { it.writeInt(6); it.writeString("歌曲|歌手") }) { it.readInt() })
            assertEquals(1, call(client, code, { it.writeInt(6); it.writeString("") }) { it.readInt() })
        }
        assertEquals(3, selections)
    }

    @Test fun sourceChangedNotificationNeverStartsPlaybackOrClaimsControl() {
        val changes = mutableListOf<Pair<Int, String?>>()
        val client = Lynk06MusicClient(Binder(), { CarPlayNowPlaying() },
            { fail("来源通知不能发送媒体键"); false }, {},
            { fail("来源通知不能取得控制权"); false },
            { source, owner -> changes += source to owner })
        assertEquals(1, call(client, 17, { it.writeInt(6); it.writeString("原车音乐") }) { it.readInt() })
        assertEquals(1, call(client, 17, { it.writeInt(2); it.writeString(null) }) { it.readInt() })
        assertEquals(listOf(6 to "原车音乐", 2 to null), changes)
    }

    @Test fun artworkGetterUsesOptionalUriParcelEncoding() {
        var uri: android.net.Uri? = android.net.Uri.parse("content://lynkplay/covers/1.jpg")
        val playback = Lynk06MusicPlayback("lynkplay", null, { uri }) { CarPlayNowPlaying() }
        assertEquals(uri, call(playback, 16) {
            assertEquals(1, it.readInt())
            android.net.Uri.CREATOR.createFromParcel(it)
        })
        uri = null
        assertEquals(0, call(playback, 16) { it.readInt() })
    }

    @Test fun artworkGetterCountsCallsAndNullsForTheInstrumentCoverDiagnosis() {
        fun counts(): Pair<Int, Int> {
            val numbers = Regex("calls=(\\d+) nulls=(\\d+)")
                .find(Lynk06MusicPlayback.diagnosticReport()) ?: error("缺少 getArtwork 计数")
            return numbers.groupValues[1].toInt() to numbers.groupValues[2].toInt()
        }
        val (callsBefore, nullsBefore) = counts()
        var uri: android.net.Uri? = android.net.Uri.parse("content://lynkplay/covers/1.jpg")
        val playback = Lynk06MusicPlayback("lynkplay", null, { uri }) { CarPlayNowPlaying() }
        call(playback, 16) { it.readInt() }
        call(playback, 16) { it.readInt() }
        uri = null
        call(playback, 16) { it.readInt() }
        val (callsAfter, nullsAfter) = counts()
        assertEquals(3, callsAfter - callsBefore)
        assertEquals(1, nullsAfter - nullsBefore)
    }

    @Test fun unchangedPlaybackDoesNotUnregisterOrLoseControl() {
        val app = RuntimeEnvironment.getApplication()
        val token = Binder()
        val codes = mutableListOf<Int>()
        val link = Lynk06MediaCenter(app, CarPlayNowPlaying(playing = true), { true }, {})
        val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                assertSame(token, data.readStrongBinder())
                codes += code
                when (code) {
                    6 -> data.readStrongBinder()
                    10 -> data.readLong()
                    4 -> Unit
                    9 -> Unit
                    else -> fail("未预期的调用：$code")
                }
                reply!!.writeNoException()
                if (code == 4 || code == 6) reply.writeInt(0)
                return true
            }
        }
        try {
            ReflectionHelpers.setField(link, "api", Lynk06MediaCenterService(remote))
            ReflectionHelpers.setField(link, "token", token)
            val control = ReflectionHelpers.getField<Lynk06MediaControl>(link, "control")
            control.focusChanged(app.packageName)
            ReflectionHelpers.callInstanceMethod<Unit>(link, "publishState")
            assertEquals(listOf(9, 6, 10), codes)
            codes.clear()
            ReflectionHelpers.callInstanceMethod<Unit>(link, "publishState")
            assertEquals(listOf(6, 10), codes)
            assertSame(token, ReflectionHelpers.getField<IBinder>(link, "token"))
            assertTrue(control.owned)
        } finally { link.close() }
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
