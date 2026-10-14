package com.shilapi.xcertplay

import android.app.PendingIntent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** 本车 XSFMediaCenter Stub 中核对的 transaction 编号；不依赖其他车型的 SDK。 */
internal class Lynk06MediaCenterService(private val remote: IBinder) {
    init { require(remote.interfaceDescriptor == DESCRIPTOR) { "原车媒体中心 Binder 契约不匹配" } }

    fun register(client: IBinder, packageName: String): IBinder? =
        // registerMusic(1) 会以空包名建令牌；领克控制权与来源通知需要真实包名。
        call(19, { it.writeString(packageName); it.writeStrongBinder(client) }) { it.readStrongBinder() }
    fun unregister(token: IBinder): Boolean = tokenCall(4, token)
    fun requestControl(token: IBinder): Boolean = tokenCall(5, token)
    fun updatePlayback(token: IBinder, playback: IBinder): Boolean =
        call(6, { it.writeStrongBinder(token); it.writeStrongBinder(playback) }) { it.readInt() != 0 }
    fun updateSources(token: IBinder) {
        call(7, { it.writeStrongBinder(token); it.writeIntArray(intArrayOf(SOURCE_MUSIC)) }) { }
        selectMusicSource(token)
    }
    fun selectMusicSource(token: IBinder) =
        call(8, { it.writeStrongBinder(token); it.writeInt(SOURCE_MUSIC) }) { }
    fun updateProgress(token: IBinder, position: Long) =
        call(10, { it.writeStrongBinder(token); it.writeLong(position) }) { }
    fun declareWheelControl(token: IBinder) =
        // 0 为标准媒体键，2 为仪表 DIM 控制；不声明原车语音控制能力。
        call(30, { it.writeStrongBinder(token); it.writeIntArray(intArrayOf(0, 2)) }) { }

    private fun tokenCall(code: Int, token: IBinder): Boolean =
        call(code, { it.writeStrongBinder(token) }) { it.readInt() != 0 }

    private fun <T> call(code: Int, write: (Parcel) -> Unit, read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            check(remote.transact(code, data, reply, 0)) { "原车媒体中心未处理调用：$code" }
            reply.readException()
            return read(reply)
        } finally { data.recycle(); reply.recycle() }
    }

    companion object {
        const val DESCRIPTOR = "ecarx.xsf.mediacenter.IMediaCenterSvc"
        // 本车 PlaybackState.SOURCE_TYPE_ONLINE；DIM 将来源 6 编码为仪表“音乐”。
        const val SOURCE_MUSIC = 6
    }
}

/** 原车音乐客户端回调，四种操作分别转发，不能把明确播放/暂停再次变为切换。 */
internal class Lynk06MusicClient(
    private val playback: IBinder,
    private val snapshot: () -> CarPlayNowPlaying,
    private val send: (Int) -> Boolean,
    private val focusChanged: (String?) -> Unit,
    private val selectMusic: () -> Boolean = { false },
    private val sourceChanged: (Int, String?) -> Unit = { _, _ -> },
) : Binder() {
    init { attachInterface(null, DESCRIPTOR) }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
        if (code !in 1..34 || reply == null) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val command = when (code) {
            1 -> CarPlayMediaButton.PLAY
            2 -> CarPlayMediaButton.PAUSE
            3 -> CarPlayMediaButton.NEXT
            4 -> CarPlayMediaButton.PREVIOUS
            else -> null
        }
        val sent = when (code) {
            8 -> data.readInt() == Lynk06MediaCenterService.SOURCE_MUSIC && selectMusic()
            17 -> {
                // SourceTypeHolder 向所有音乐客户端广播来源与所属包，不是播放请求。
                sourceChanged(data.readInt(), data.readString())
                true
            }
            21 -> {
                val source = data.readInt()
                val uuid = data.readString()
                // 仅接入本客户端的音乐来源，歌曲列表中的其他项目不能误播当前歌曲。
                source == Lynk06MediaCenterService.SOURCE_MUSIC &&
                    (uuid.isNullOrEmpty() || uuid == lynk06MusicId(snapshot())) && selectMusic()
            }
            else -> command?.let(send)
        }
        if (code == 25) focusChanged(data.readString())
        reply.writeNoException()
        when (code) {
            1, 2, 3, 4, 8, 17, 21 -> reply.writeInt(if (sent == true) 1 else 0)
            10 -> reply.writeStrongBinder(playback)
            11 -> reply.writeIntArray(intArrayOf(Lynk06MediaCenterService.SOURCE_MUSIC))
            12 -> reply.writeInt(Lynk06MediaCenterService.SOURCE_MUSIC)
            13 -> reply.writeLong(snapshot().elapsedMillis ?: 0)
            14, 28 -> reply.writeList(emptyList<Any>())
            25, 33, 34 -> Unit
            29 -> reply.writeStrongBinder(null)
            else -> reply.writeInt(0)
        }
        return true
    }

    companion object { const val DESCRIPTOR = "ecarx.xsf.mediacenter.IMusicClient" }
}

/** 原车会同步读取这些 getter；只读取 iPhone 快照，不在回调中再次访问服务。 */
internal class Lynk06MusicPlayback(
    private val packageName: String,
    private val launchIntent: PendingIntent?,
    private val artwork: () -> Uri? = { null },
    private val snapshot: () -> CarPlayNowPlaying,
) : Binder() {
    init { attachInterface(null, DESCRIPTOR) }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
        if (code !in 1..33 || reply == null) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val info = snapshot()
        reply.writeNoException()
        when (code) {
            1, 33 -> {
                reply.writeInt(if (launchIntent == null) 0 else 1)
                launchIntent?.writeToParcel(reply, 0)
            }
            2 -> reply.writeString(info.title.orEmpty())
            3 -> reply.writeString(info.artist.orEmpty())
            4 -> reply.writeString(info.album.orEmpty())
            5, 6, 12, 14, 26, 30 -> reply.writeString("")
            7 -> reply.writeLong(info.durationMillis ?: 0)
            9 -> reply.writeInt(Lynk06MediaCenterService.SOURCE_MUSIC)
            // 实车日志：暂停 mPlaybackState=0，播放 mPlaybackState=1。
            11 -> reply.writeInt(if (info.playing) 1 else 0)
            16 -> {
                val uri = artwork()
                reply.writeInt(if (uri == null) 0 else 1)
                uri?.writeToParcel(reply, 0)
            }
            24 -> reply.writeString(lynk06MusicId(info))
            25 -> reply.writeString("LynkPlay")
            27 -> reply.writeString(packageName)
            // Uri、可选能力、循环模式等 getter 均使用该 ABI 的空值或默认值编码。
            else -> reply.writeInt(0)
        }
        return true
    }

    companion object { const val DESCRIPTOR = "ecarx.xsf.mediacenter.IMusicPlaybackInfo" }
}

internal fun lynk06MusicId(info: CarPlayNowPlaying): String =
    "${info.title.orEmpty()}|${info.artist.orEmpty()}"
