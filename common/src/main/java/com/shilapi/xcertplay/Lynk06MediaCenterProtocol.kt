package com.shilapi.xcertplay

import android.app.PendingIntent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.util.concurrent.atomic.AtomicInteger

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
    /** 本车 IPKMode=2 从播放列表取封面，单独更新 playback 不会替换原车旧列表。 */
    fun updateCurrentTrack(token: IBinder, info: CarPlayNowPlaying, artwork: Uri?) =
        call(9, {
            it.writeStrongBinder(token)
            it.writeInt(SOURCE_MUSIC)
            it.writeInt(1) // typed list size：只提供实际已知的当前歌曲。
            it.writeInt(1) // non-null IMedia
            writeLynk06CurrentTrack(it, info, artwork)
        }) { }
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

/** XSFMediaCenter 本车 IMedia.writeToParcel 的字段顺序，不是 Binder playback 的格式。 */
internal fun writeLynk06CurrentTrack(parcel: Parcel, info: CarPlayNowPlaying, artwork: Uri?) {
    parcel.writeString(lynk06MusicId(info))
    parcel.writeString(info.title.orEmpty())
    parcel.writeString(info.artist.orEmpty())
    parcel.writeString(info.album.orEmpty())
    repeat(6) { parcel.writeString("") } // author, composer, description, subtitle, rating, year
    parcel.writeLong(info.durationMillis ?: 0)
    parcel.writeInt(0) // positionInQueue
    parcel.writeInt(0) // albumIndex
    repeat(5) { parcel.writeString("") } // category, subCategory, mediaId, mediaCp, targetType
    parcel.writeInt(Lynk06MediaCenterService.SOURCE_MUSIC)
    parcel.writeParcelable(null, 0) // mediaPath
    parcel.writeString("") // lyricContent
    parcel.writeParcelable(null, 0) // lyric
    parcel.writeParcelable(artwork, 0)
    parcel.writeString("") // radioFrequency
    parcel.writeString("") // radioStationName
    parcel.writeInt(0) // vip
    parcel.writeString("") // playingMediaListId
    parcel.writeInt(-1) // playingMediaListType
    parcel.writeInt(0) // supportCollect
    parcel.writeInt(0) // collected
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
            13 -> reply.writeLong(snapshot().positionMillisAt(System.nanoTime()) ?: 0)
            14, 28 -> reply.writeList(emptyList<Any>())
            25, 33, 34 -> Unit
            // getMultiMediaList 返回 Parcelable IMediaLists（非 binder）；null 编码为 writeInt(0)，
            // 误用 writeStrongBinder 会让原车 readInt 读到 flat-binder 魔数后 createFromParcel 报错。
            29 -> reply.writeInt(0)
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
    private val log: (String) -> Unit = {},
    private val snapshot: () -> CarPlayNowPlaying,
) : Binder() {
    init { attachInterface(null, DESCRIPTOR) }
    @Volatile private var lastArtworkNull: Boolean? = null

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
                val calls = getArtworkCalls.incrementAndGet()
                if (uri == null) getArtworkNulls.incrementAndGet()
                // 原车导航布局取封面走这条 getter；记录空/非空翻转与累计次数，
                // 区分“从未调用”和“调用但一直非空”。日志不能拖累或打断这次读取。
                if ((uri == null) != lastArtworkNull) {
                    lastArtworkNull = uri == null
                    runCatching {
                        log("领克封面 getArtwork null=${uri == null} calls=$calls nulls=${getArtworkNulls.get()}")
                    }
                }
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

    companion object {
        const val DESCRIPTOR = "ecarx.xsf.mediacenter.IMusicPlaybackInfo"
        private val getArtworkCalls = AtomicInteger()
        private val getArtworkNulls = AtomicInteger()
        fun diagnosticReport(): String =
            "领克封面 getArtwork calls=${getArtworkCalls.get()} nulls=${getArtworkNulls.get()}"
    }
}

internal fun lynk06MusicId(info: CarPlayNowPlaying): String =
    "${info.title.orEmpty()}|${info.artist.orEmpty()}"
