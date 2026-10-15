package com.shilapi.xcertplay

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 绕过原车 Glide 的 RePlugin content 流路径，仅在本机提供本会话登记的 JPEG。 */
internal class Lynk06ArtworkServer(
    private val directory: File,
    private val log: (String) -> Unit = {},
) : AutoCloseable {
    private val token = UUID.randomUUID().toString()
    private val files = LinkedHashMap<String, File>()
    private val timeouts = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "lynk06-artwork-timeout").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private val listener = ServerSocket().apply {
        try {
            soTimeout = 250
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 4)
        } catch (failure: Exception) { close(); throw failure }
    }
    @Volatile private var closed = false
    @Volatile private var connection: Socket? = null
    private val thread = Thread(::serve, "lynk06-artwork-http").apply { isDaemon = true; start() }

    fun publish(file: File): String = synchronized(files) {
        check(!closed) { "封面服务已关闭" }
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.isFile &&
            file.extension == "jpg" && file.length() in 1..MAX_IMAGE_BYTES) { "封面文件无效" }
        val path = "/covers/$token/${file.name}"
        files[path] = file
        while (files.size > 3) files.remove(files.keys.first())
        diagnostic("登记 cover=${coverId(file.name)} bytes=${file.length()}")
        "http://127.0.0.1:${listener.localPort}$path"
    }

    fun forget(file: File) = synchronized(files) {
        files.entries.removeAll { it.value == file }
    }

    private fun serve() {
        try {
            while (!closed) {
                val socket = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
                connection = socket
                socket.use {
                    if (closed) return@use
                    it.soTimeout = 1_000
                    // 同时限制慢速头部和不读取响应的连接，写入也不能无限等待。
                    val timeout = timeouts.schedule({ runCatching { socket.close() } }, 3, TimeUnit.SECONDS)
                    val request = RequestTrace(requests.incrementAndGet())
                    diagnostic("读取开始 request=${request.id}")
                    try { answer(it, request) } catch (failure: Exception) {
                        failures.incrementAndGet()
                        request.error = failure.javaClass.simpleName
                    }
                    finally {
                        timeout.cancel(false)
                        diagnostic("读取结束 request=${request.id} method=${request.method} cover=${request.cover} " +
                            "status=${request.status} bodyBytes=${request.bytes} error=${request.error} " +
                            "elapsedMs=${(System.nanoTime() - request.started) / 1_000_000}")
                    }
                }
                connection = null
            }
        } finally {
            listener.close()
            timeouts.shutdownNow()
            connection = null
        }
    }

    private class RequestTrace(val id: Int) {
        val started = System.nanoTime()
        var method = "unknown"
        var cover = "unregistered"
        var status = 0
        var bytes = 0
        var error = "none"
    }

    private fun diagnostic(message: String) { runCatching { log("领克封面HTTP：$message") } }

    private fun answer(socket: Socket, request: RequestTrace) {
        fun reply(status: Int, bytes: ByteArray = ByteArray(0), head: Boolean = false) {
            respond(socket, status, bytes, head)
            request.status = status
            request.bytes = if (head) 0 else bytes.size
        }
        val input = socket.getInputStream()
        val header = ByteArrayOutputStream()
        var end = 0
        while (header.size() < 8_192) {
            val value = input.read()
            if (value < 0) { request.error = "header-eof"; return }
            header.write(value)
            end = (end shl 8) or value
            if (end == 0x0d0a0d0a) break
        }
        if (end != 0x0d0a0d0a) { reply(431); return }
        val line = header.toString("US-ASCII").substringBefore("\r\n").split(' ')
        if (line.size != 3 || line[2] !in listOf("HTTP/1.0", "HTTP/1.1")) {
            reply(400); return
        }
        request.method = if (line[0] in listOf("GET", "HEAD", "POST")) line[0] else "other"
        if (line[0] != "GET" && line[0] != "HEAD") { reply(405); return }
        // 只匹配已登记的完整路径，不解析客户端文件名、绝对地址或路径跳转。
        val requestedName = line[1].removePrefix("/covers/$token/")
        if (line[1].startsWith("/covers/$token/") &&
            Regex("[0-9a-fA-F-]{36}\\.jpg").matches(requestedName)) request.cover = coverId(requestedName)
        val file = synchronized(files) { files[line[1]].takeUnless { closed } }
        if (file == null) { reply(404); return }
        request.cover = coverId(file.name)
        val bytes = try {
            file.inputStream().use { stream ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (result.size() + count > MAX_IMAGE_BYTES) throw IllegalArgumentException("封面过大")
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
        } catch (failure: Exception) { request.error = failure.javaClass.simpleName; failures.incrementAndGet(); reply(404); return }
        reply(200, bytes, head = line[0] == "HEAD")
        served.incrementAndGet()
    }

    private fun respond(socket: Socket, status: Int, bytes: ByteArray = ByteArray(0), head: Boolean = false) {
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n" +
                "Content-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            if (!head) write(bytes)
            flush()
        }
    }

    override fun close() {
        closed = true
        synchronized(files) { files.clear() }
        runCatching { connection?.close() }
        // accept 有短超时，由服务线程关闭监听器，避免跨线程 close/accept 阻塞。
        thread.join(1_500)
    }

    companion object {
        // 短编号能跨 URI、HTTP 与原车日志关联，且不会被报告的 UUID 脱敏合并。
        internal fun coverId(name: String): String = name.substringBefore('.').replace("-", "").take(12)
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024L
        private val requests = AtomicInteger()
        private val served = AtomicInteger()
        private val failures = AtomicInteger()
        fun diagnosticReport(): String = "领克封面 HTTP：requests=${requests.get()} served=${served.get()} failures=${failures.get()}"
    }
}
