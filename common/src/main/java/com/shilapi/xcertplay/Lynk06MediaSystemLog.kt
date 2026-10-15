package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** 会话期间只读订阅原车媒体/DIM 标签，写入现有轮转日志。 */
internal class Lynk06MediaSystemLog(
    private val log: (String) -> Unit,
    private val launch: () -> Process = {
        ProcessBuilder(listOf("logcat", "-v", "threadtime", "-T",
            java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()),
            "-b", "main", "-b", "system") +
            TAGS.map { "$it:V" } + "*:S").redirectErrorStream(true).start()
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
            child!!.inputStream.bufferedReader().useLines { input ->
                input.forEach { raw ->
                    if (closed) return@forEach
                    val now = System.nanoTime()
                    if (now - window >= 1_000_000_000L) {
                        window = now
                        count = 0
                    }
                    // 固件异常刷屏时保留计数，避免淹没应用的操作记录。
                    // 投影状态先解析，再对原始行限流，避免其他固件日志淹没开关事件。
                    Lynk06MapDiagnostics.nativeLine(this, raw, log)
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
        private val TAGS = listOf("DimInteractionHelper", "DimImageLoader", "DimImageProcess",
            "MediaInteractionImpl", "MediaMainApiImpl", "MediaCtrlByDimCallback",
            "ECarXPowerManagerService", "ECarXScreenProjectionService")
        private val COVER = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
        @Volatile private var latest: Lynk06MediaSystemLog? = null
        fun diagnosticReport(): String = latest?.let {
            "原车媒体日志：state=${it.state} lines=${it.lines} suppressed=${it.suppressed}"
        } ?: "原车媒体日志：本进程尚未启动媒体会话采集"
    }
}
