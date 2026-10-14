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
internal class Lynk06ArtworkServer(private val directory: File) : AutoCloseable {
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
                    try { answer(it) } catch (_: Exception) { failures.incrementAndGet() }
                    finally { timeout.cancel(false) }
                }
                connection = null
            }
        } finally {
            listener.close()
            timeouts.shutdownNow()
            connection = null
        }
    }

    private fun answer(socket: Socket) {
        requests.incrementAndGet()
        val input = socket.getInputStream()
        val header = ByteArrayOutputStream()
        var end = 0
        while (header.size() < 8_192) {
            val value = input.read()
            if (value < 0) return
            header.write(value)
            end = (end shl 8) or value
            if (end == 0x0d0a0d0a) break
        }
        if (end != 0x0d0a0d0a) { respond(socket, 431); return }
        val line = header.toString("US-ASCII").substringBefore("\r\n").split(' ')
        if (line.size != 3 || line[2] !in listOf("HTTP/1.0", "HTTP/1.1")) {
            respond(socket, 400); return
        }
        if (line[0] != "GET" && line[0] != "HEAD") { respond(socket, 405); return }
        // 只匹配已登记的完整路径，不解析客户端文件名、绝对地址或路径跳转。
        val file = synchronized(files) { files[line[1]].takeUnless { closed } }
        if (file == null) { respond(socket, 404); return }
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
        } catch (_: Exception) { failures.incrementAndGet(); respond(socket, 404); return }
        respond(socket, 200, bytes, head = line[0] == "HEAD")
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
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024L
        private val requests = AtomicInteger()
        private val served = AtomicInteger()
        private val failures = AtomicInteger()
        fun diagnosticReport(): String = "领克封面 HTTP：requests=${requests.get()} served=${served.get()} failures=${failures.get()}"
    }
}
