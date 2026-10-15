package com.shilapi.xcertplay

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Lynk06ArtworkServerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun servesOnlyRegisteredFilesThroughLoopbackWithExactTokenPaths() {
        val directory = temporary.newFolder("covers")
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 0xff.toByte(), 0xd9.toByte())
        val file = File(directory, "cover.jpg").apply { writeBytes(bytes) }
        Lynk06ArtworkServer(directory).use { server ->
            val url = server.publish(file)
            assertEquals("127.0.0.1", URL(url).host)
            request(url) { connection ->
                assertEquals(200, connection.responseCode)
                assertEquals("image/jpeg", connection.contentType)
                assertArrayEquals(bytes, connection.inputStream.use { it.readBytes() })
            }
            request(url, "HEAD") {
                assertEquals(200, it.responseCode)
                assertEquals(bytes.size, it.contentLength)
                assertEquals(0, it.inputStream.use { stream -> stream.readBytes().size })
            }
            request(url, "POST") { assertEquals(405, it.responseCode) }
            request(url.substringBefore("/covers/") + "/covers/wrong/cover.jpg") { assertEquals(404, it.responseCode) }
            request(url + "?other=1") { assertEquals(404, it.responseCode) }
            request(url.substringBeforeLast('/') + "/../unrelated.txt") { assertEquals(404, it.responseCode) }
            assertThrows(IllegalArgumentException::class.java) { server.publish(temporary.newFile("outside.jpg")) }
            server.forget(file)
            request(url) { assertEquals(404, it.responseCode) }
        }
    }

    @Test fun retainsAtMostThreeImagesAndStopsAtSessionEnd() {
        val directory = temporary.newFolder("covers")
        val server = Lynk06ArtworkServer(directory)
        val urls = mutableListOf<String>()
        try {
            repeat(4) { index ->
                urls += server.publish(File(directory, "$index.jpg").apply { writeBytes(byteArrayOf(index.toByte())) })
            }
            request(urls.first()) { assertEquals(404, it.responseCode) }
            request(urls.last()) { assertEquals(200, it.responseCode) }
        } finally { server.close() }
        assertThrows(IOException::class.java) { request(urls.last()) { it.responseCode } }
        assertThrows(IllegalStateException::class.java) { server.publish(File(directory, "3.jpg")) }
    }

    @Test fun incompleteConnectionCannotBlockFollowingImageRequests() {
        val directory = temporary.newFolder("covers")
        Lynk06ArtworkServer(directory).use { server ->
            val url = server.publish(File(directory, "cover.jpg").apply { writeBytes(byteArrayOf(1)) })
            Socket("127.0.0.1", URL(url).port).use { blocked ->
                blocked.getOutputStream().write("GET ".toByteArray())
                request(url) { assertEquals(200, it.responseCode) }
            }
        }
    }

    @Test fun oversizedHeadersAndImagesAreRejected() {
        val directory = temporary.newFolder("covers")
        Lynk06ArtworkServer(directory).use { server ->
            val url = server.publish(File(directory, "cover.jpg").apply { writeBytes(byteArrayOf(1)) })
            Socket("127.0.0.1", URL(url).port).use { socket ->
                socket.soTimeout = 4_000
                socket.getOutputStream().write(("GET / HTTP/1.1\r\nX: " + "a".repeat(8_192) + "\r\n\r\n").toByteArray())
                assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 431"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                server.publish(File(directory, "large.jpg").apply { writeBytes(ByteArray(2 * 1024 * 1024 + 1)) })
            }
        }
    }

    @Test fun requestLogsCorrelateDistinctCoversAfterReportRedaction() {
        val directory = temporary.newFolder("diagnostic-covers")
        val logs = java.util.concurrent.CopyOnWriteArrayList<String>()
        Lynk06ArtworkServer(directory, logs::add).use { server ->
            val first = server.publish(File(directory, "12345678-abcd-1234-abcd-123456789012.jpg")
                .apply { writeBytes(byteArrayOf(1, 2)) })
            val second = server.publish(File(directory, "87654321-abcd-1234-abcd-123456789012.jpg")
                .apply { writeBytes(byteArrayOf(3)) })
            request(first) { assertEquals(200, it.responseCode) }
            request(second, "HEAD") { assertEquals(200, it.responseCode) }
        }
        val report = logs.mapNotNull(DiagnosticRedactor::redact)
        assertTrue(report.any { it.contains("cover=12345678abcd status=200 bodyBytes=2") })
        assertTrue(report.any { it.contains("method=HEAD cover=87654321abcd status=200 bodyBytes=0") })
        assertEquals(2, report.count { it.contains("读取结束 request=") })
    }

    private fun request(url: String, method: String = "GET", check: (HttpURLConnection) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 1_000
        connection.readTimeout = 4_000
        connection.requestMethod = method
        try { check(connection) } finally { connection.disconnect() }
    }
}
