package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** 会话期间只读订阅原车媒体/DIM 标签，写入现有轮转日志。 */
internal class Lynk06MediaSystemLog(
    private val log: (String) -> Unit,
    // 原车 DIM/媒体日志的 tag 形如 MDC-[pid]-DimInteractionHelper，logcat 的 tag 过滤按整段
    // tag 匹配会漏掉它们（DimInteractionHelper:V 匹配不到 MDC-[…]-DimInteractionHelper），
    // 因此读全量 main/system 缓冲，再按 tag 名筛选。
    private val launch: () -> Process = {
        ProcessBuilder(listOf("logcat", "-v", "threadtime", "-T",
            java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()),
            "-b", "main", "-b", "system")).redirectErrorStream(true).start()
    },
) : AutoCloseable {
    @Volatile private var closed = false
    @Volatile private var state = "未启动"
    @Volatile private var lines = 0L
    @Volatile private var suppressed = 0L
    private var process: Process? = null

    fun start(context: Context) {
        latest = this
        Lynk06MapDiagnostics.capture(this, "starting", log)
        if (context.checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            state = "缺少 READ_LOGS，无法读取原车进程日志"
            Lynk06MapDiagnostics.capture(this, "permission-denied", log)
            emit(state)
            return
        }
        Thread({ collect() }, "lynk06-media-system-log").apply { isDaemon = true; start() }
    }

    private fun collect() {
        var child: Process? = null
        try {
            synchronized(this) {
                if (closed) return
                child = launch()
                process = child
            }
            state = "采集中"
            Lynk06MapDiagnostics.capture(this, "running", log)
            emit("开始订阅；原车行内时间为事件时间，外层时间为接收时间")
            var window = System.nanoTime()
            var count = 0
            var continuation = false
            child!!.inputStream.bufferedReader().useLines { input ->
                input.forEach { raw ->
                    if (closed) return@forEach
                    // threadtime 的短 tag 会补空格（Glide → "Glide   "），比较前必须 trim。
                    val tag = TAG.find(raw)?.groupValues?.get(1)?.trim()
                    // 无行头的续行是上一行的堆栈；只有上一行属于采集 tag 时才保留，否则整段展开会淹没报告。
                    val selected = if (tag == null) continuation
                        else TAGS.any { tag == it || tag.endsWith("-$it") }
                    if (tag != null) continuation = selected
                    if (!selected) return@forEach
                    val now = System.nanoTime()
                    if (now - window >= 1_000_000_000L) {
                        window = now
                        count = 0
                    }
                    // 固件异常刷屏时保留计数，避免淹没应用的操作记录。
                    // 投影状态先解析，再对原始行限流，避免其他固件日志淹没开关事件。
                    if (tag != null) Lynk06MapDiagnostics.nativeLine(this, raw, log)
                    if (++count > 60) {
                        suppressed++
                        Lynk06MapDiagnostics.capture(this, "rate-limited", log)
                        return@forEach
                    }
                    // 在全局 UUID 脱敏前提取短封面编号，以便关联 HTTP 与原车加载记录。
                    val covers = COVER.findAll(raw).map { Lynk06ArtworkServer.coverId(it.value) }.distinct().toList()
                    DiagnosticRedactor.redact(raw)?.let {
                        lines++
                        emit("covers=$covers $it")
                    }
                }
            }
            val exit = child!!.waitFor()
            state = if (closed) "已停止" else "采集进程退出 exit=$exit"
        } catch (failure: Exception) {
            state = if (closed) "已停止" else "采集失败 ${failure.javaClass.simpleName}"
        } finally {
            child?.destroy()
            synchronized(this) { process = null }
            Lynk06MapDiagnostics.capture(this, if (closed) "stopped" else "exited", log)
            emit("$state lines=$lines suppressed=$suppressed")
        }
    }

    private fun emit(message: String) { runCatching { log("原车媒体日志：$message") } }

    override fun close() {
        synchronized(this) {
            closed = true
            state = "已停止"
            process?.destroy()
        }
        Lynk06MapDiagnostics.capture(this, "stopped", log)
    }

    companion object {
        // 封面证据链：MusicPlaybackInfoHelper（原车看到的 artwork/Previous/Next）、
        // ThreeAlbumService（仪表三封面）、Glide/GlideUtils（加载成功/失败）不可缺。
        private val TAGS = listOf("DimInteractionHelper", "DimImageLoader", "DimImageProcess",
            "MusicPlaybackInfoHelper", "ThreeAlbumService", "Glide", "GlideUtils",
            "AbstractTask", "TPE", "MediaInteractionImpl", "MediaMainApiImpl", "MediaCtrlByDimCallback",
            "ECarXPowerManagerService", "ECarXScreenProjectionService")
        private val COVER = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
        // threadtime 行首：MM-DD HH:MM:SS.mmm PID TID PRIO TAG: message
        private val TAG = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEFSA]\\s+(.+?):")
        @Volatile private var latest: Lynk06MediaSystemLog? = null
        fun diagnosticReport(): String = latest?.let {
            "原车媒体日志：state=${it.state} lines=${it.lines} suppressed=${it.suppressed}"
        } ?: "原车媒体日志：本进程尚未启动媒体会话采集"
    }
}
