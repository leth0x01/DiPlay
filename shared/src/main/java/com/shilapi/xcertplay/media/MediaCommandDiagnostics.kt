package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/** 只关联观察结果，不把状态更新当作手机对某条指令的确认。 */
internal class MediaCommandDiagnostics(
    private val log: (String) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Request(val command: Int, val started: Long, var executing: Boolean = false,
        var sent: Boolean = false, var observed: Boolean = false, var expired: Boolean = false)
    private val pending = linkedMapOf<Long, Request>()
    private companion object { val sequence = java.util.concurrent.atomic.AtomicLong() }

    @Synchronized fun begin(command: Int, source: String, playing: Boolean): Long {
        val id = sequence.incrementAndGet()
        if (pending.size >= 16) finish(pending.keys.first(), "超出跟踪上限")
        pending[id] = Request(command, nanoTime())
        val alreadyAtTarget = (command == CarPlayMediaButton.PLAY && playing) ||
            (command == CarPlayMediaButton.PAUSE && !playing)
        val action = when (command) {
            CarPlayMediaButton.PLAY -> "播放"
            CarPlayMediaButton.PAUSE -> "暂停"
            CarPlayMediaButton.PLAY_PAUSE -> "播放暂停切换"
            CarPlayMediaButton.NEXT -> "下一首"
            CarPlayMediaButton.PREVIOUS -> "上一首"
            else -> "未知"
        }
        emit(id, "收到 command=$command action=$action source=$source playing=$playing alreadyAtTarget=$alreadyAtTarget")
        return id
    }

    @Synchronized fun executing(id: Long) {
        pending[id]?.executing = true
        emit(id, "开始发送 queueMs=${elapsed(id)}")
    }

    @Synchronized fun sent(id: Long, pressed: Boolean, released: Boolean) {
        emit(id, "HID写入 pressed=$pressed released=$released elapsedMs=${elapsed(id)}")
        pending[id]?.sent = pressed && released
        when {
            !pressed || !released -> finish(id, "发送失败")
            pending[id]?.observed == true -> finish(id, "发送期间观察到目标变化（非指令确认）")
            pending[id]?.expired == true -> finish(id, "发送完成，但已超过观察窗口")
        }
    }

    @Synchronized fun rejected(id: Long, reason: String) { finish(id, "未发送 reason=$reason") }

    @Synchronized fun observe(playing: Boolean, playingChanged: Boolean, trackChanged: Boolean) {
        if (!playingChanged && !trackChanged) return
        // 连续请求可能共同对应一次变化；保留全部候选，不能假定最后一次请求才生效。
        val candidates = pending.filterValues { it.executing }.keys.toList()
        log("媒体诊断：手机状态 playing=$playing playingChanged=$playingChanged metadataChanged=$trackChanged candidates=$candidates")
        candidates.forEach { id ->
            val command = pending[id]?.command
            val observed = when (command) {
                CarPlayMediaButton.PLAY -> playingChanged && playing
                CarPlayMediaButton.PAUSE -> playingChanged && !playing
                CarPlayMediaButton.PLAY_PAUSE -> playingChanged
                CarPlayMediaButton.NEXT, CarPlayMediaButton.PREVIOUS -> trackChanged
                else -> false
            }
            if (observed) {
                pending[id]?.observed = true
                emit(id, "观察到目标变化（非指令确认） elapsedMs=${elapsed(id)}")
                if (pending[id]?.sent == true) pending.remove(id)
            }
        }
    }

    @Synchronized fun timeout(id: Long) {
        val request = pending[id] ?: return
        val stage = when { request.sent -> "sent"; request.executing -> "writing"; else -> "queued" }
        emit(id, "观察窗口结束 stage=$stage targetObserved=${request.observed} elapsedMs=${elapsed(id)}")
        request.expired = true
        if (request.sent) pending.remove(id)
    }

    @Synchronized fun close() { pending.keys.toList().forEach { finish(it, "会话关闭") } }

    private fun elapsed(id: Long): Long? = pending[id]?.let { (nanoTime() - it.started) / 1_000_000 }
    private fun emit(id: Long, detail: String) { log("媒体诊断：request=$id $detail") }
    private fun finish(id: Long, reason: String) {
        if (id !in pending) return
        emit(id, "$reason elapsedMs=${elapsed(id)}")
        pending.remove(id)
    }
}
