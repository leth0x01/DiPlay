package com.shilapi.xcertplay

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Lynk06MediaProgressTest {
    @Test fun ticksOnlySendProgressAndClientQueriesUseTheSameTimeBase() {
        val fixture = Fixture(playing())
        try {
            fixture.publish()
            assertEquals(listOf(9, 6, 10), fixture.codes)
            assertTrue(fixture.positions.single() >= 6_000L)
            fixture.codes.clear()
            fixture.advance()
            assertEquals(listOf(10), fixture.codes)
            val queried = fixture.queryProgress()
            assertTrue(queried >= fixture.positions.last())
            assertTrue(queried < fixture.positions.last() + 1_000L)
        } finally { fixture.close() }
    }

    @Test fun pauseAndUnknownPositionsDoNotKeepATickerAlive() {
        val fixture = Fixture(playing())
        try {
            fixture.publish()
            fixture.link.update(CarPlayNowPlaying(title = "歌曲", playing = false, elapsedMillis = 8_000))
            fixture.idle()
            assertEquals(8_000L, fixture.positions.last())
            assertEquals(8_000L, fixture.queryProgress())
            fixture.codes.clear()
            fixture.advance()
            assertTrue(fixture.codes.isEmpty())
            fixture.link.update(CarPlayNowPlaying(title = "歌曲", playing = true))
            fixture.idle()
            fixture.codes.clear()
            fixture.advance()
            assertTrue(fixture.codes.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun focusLossSourceChangeDisconnectAndCloseCancelFurtherWrites() {
        val fixture = Fixture(playing())
        try {
            fixture.publish()
            fixture.focus("原车播放器")
            fixture.assertNoTicks()
            fixture.focus(fixture.packageName)
            fixture.codes.clear()
            fixture.advance()
            assertEquals(listOf(10), fixture.codes)

            fixture.sourceChanged()
            fixture.assertNoTicks()
            fixture.focus(fixture.packageName)
            fixture.worker.post { ReflectionHelpers.callInstanceMethod<Unit>(fixture.link, "disconnect") }
            fixture.idle()
            fixture.assertNoTicks()
            fixture.close()
            fixture.codes.clear()
            // 模拟退出时已经取出的迟到任务；Looper 退出后不再推进它的时钟。
            ReflectionHelpers.getField<Runnable>(fixture.link, "progressTick").run()
            assertTrue(fixture.codes.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun losingFocusDuringAProgressTransactionDoesNotScheduleAnotherWrite() {
        val fixture = Fixture(playing())
        try {
            fixture.onProgress = { fixture.focus("原车播放器") }
            fixture.publish()
            fixture.assertNoTicks()
        } finally { fixture.close() }
    }

    private fun playing() = CarPlayNowPlaying(title = "歌曲", elapsedMillis = 1_000,
        durationMillis = 120_000, playing = true, positionUpdatedAtNanos = System.nanoTime() - 5_000_000_000L)

    private class Fixture(info: CarPlayNowPlaying) {
        private val context = RuntimeEnvironment.getApplication()
        val packageName: String = context.packageName
        val link = Lynk06MediaCenter(context, info, { fail("刷新进度不应操作播放"); false }, {})
        val worker: Handler = ReflectionHelpers.getField(link, "worker")
        val codes = mutableListOf<Int>()
        val positions = mutableListOf<Long>()
        var onProgress: () -> Unit = {}
        private var closed = false
        private val token = Binder()
        private val client: IBinder = ReflectionHelpers.getField(link, "client")
        private val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                assertSame(token, data.readStrongBinder())
                codes += code
                when (code) {
                    6 -> data.readStrongBinder()
                    10 -> { positions += data.readLong(); onProgress() }
                    4, 9 -> Unit
                    else -> fail("未预期的原车调用：$code")
                }
                reply!!.writeNoException()
                if (code == 4 || code == 6) reply.writeInt(1)
                return true
            }
        }
        init {
            ReflectionHelpers.setField(link, "api", Lynk06MediaCenterService(remote))
            ReflectionHelpers.setField(link, "token", token)
            ReflectionHelpers.getField<Lynk06MediaControl>(link, "control").focusChanged(packageName)
        }
        fun idle() { shadowOf(worker.looper).idle() }
        fun advance() { shadowOf(worker.looper).idleFor(Duration.ofSeconds(1)) }
        fun publish() { worker.post { ReflectionHelpers.callInstanceMethod<Unit>(link, "publishState") }; idle() }
        fun focus(owner: String) { transact(25, { it.writeString(owner) }) { }; idle() }
        fun sourceChanged() { transact(17, { it.writeInt(2); it.writeString("原车蓝牙") }) { it.readInt() }; idle() }
        fun queryProgress(): Long = transact(13, {}) { it.readLong() }
        fun assertNoTicks() { codes.clear(); advance(); assertTrue(codes.isEmpty()) }
        fun close() {
            if (closed) return
            closed = true
            link.close()
            idle()
        }
        private fun <T> transact(code: Int, write: (Parcel) -> Unit, read: (Parcel) -> T): T {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(Lynk06MusicClient.DESCRIPTOR)
                write(data)
                assertTrue(client.transact(code, data, reply, 0))
                reply.readException()
                return read(reply)
            } finally { data.recycle(); reply.recycle() }
        }
    }
}
