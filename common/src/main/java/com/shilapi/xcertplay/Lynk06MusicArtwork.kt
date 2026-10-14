package com.shilapi.xcertplay

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** 系统 UID 不能直接发放普通 Uri 授权；只读接口自行核对原车媒体中心 UID。 */
class Lynk06MusicArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun enforceReader() {
        val app = checkNotNull(context)
        val caller = Binder.getCallingUid()
        if (caller == android.os.Process.myUid()) return
        val mediaUid = runCatching {
            app.packageManager.getApplicationInfo(Lynk06MusicArtwork.MEDIA_CENTER_PACKAGE, 0).uid
        }.getOrNull()
        if (caller != mediaUid) throw SecurityException("仅允许原车媒体中心读取仪表封面")
    }

    private fun fileForUri(uri: Uri): File {
        val app = checkNotNull(context)
        require(uri.scheme == "content" && uri.authority == authority(app)) { "封面 Uri 来源不匹配" }
        val segments = uri.pathSegments
        require(segments.size == 2 && segments[0] == "covers" && fileName.matches(segments[1])) {
            "封面路径无效"
        }
        val directory = directory(app).canonicalFile
        return File(directory, segments[1]).canonicalFile.also {
            require(it.parentFile == directory) { "封面路径超出缓存目录" }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        attempts.incrementAndGet()
        lastCallerUid = Binder.getCallingUid()
        return try {
            enforceReader()
            if (mode != "r") throw SecurityException("仪表封面只允许读取")
            ParcelFileDescriptor.open(fileForUri(uri), ParcelFileDescriptor.MODE_READ_ONLY).also {
                opened.incrementAndGet()
                lastFileBytes = it.statSize
                lastFailure = null
            }
        } catch (failure: Exception) {
            failures.incrementAndGet()
            lastFailure = failure.javaClass.simpleName
            throw failure
        }
    }

    override fun getType(uri: Uri): String {
        enforceReader()
        fileForUri(uri)
        return "image/jpeg"
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        enforceReader()
        val file = fileForUri(uri)
        val columns = (projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray()).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) file.name else file.length() }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("仪表封面只允许读取")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("仪表封面只允许读取")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("仪表封面只允许读取")

    companion object {
        private val fileName = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
        internal fun directory(context: Context): File = File(context.cacheDir, "lynk06-music-covers")
        private fun authority(context: Context): String = "${context.packageName}.lynk06-music-artwork"
        internal fun uriForFile(context: Context, file: File): Uri {
            require(file.canonicalFile.parentFile == directory(context).canonicalFile && fileName.matches(file.name)) {
                "封面路径超出缓存目录"
            }
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath("covers").appendPath(file.name).build()
        }

        private val attempts = AtomicInteger()
        private val opened = AtomicInteger()
        private val failures = AtomicInteger()
        @Volatile private var lastCallerUid: Int? = null
        @Volatile private var lastFileBytes = -1L
        @Volatile private var lastFailure: String? = null

        // 打开文件成功不代表原车已解码或已显示；计数只描述接口实际收到的调用。
        fun diagnosticReport(): String = "领克封面读取：attempts=${attempts.get()} opened=${opened.get()} " +
            "failures=${failures.get()} lastCallerUid=$lastCallerUid lastFileBytes=$lastFileBytes lastFailure=$lastFailure"
    }
}

/** 在媒体 worker 上写入，避免 Binder getter 或主线程压缩图片。 */
internal class Lynk06MusicArtwork(private val context: Context) {
    private val directory = Lynk06MusicArtworkProvider.directory(context)
    private val owned = linkedSetOf<File>()

    fun save(bitmap: Bitmap?): Uri? {
        if (bitmap == null || bitmap.isRecycled) return null
        check(directory.isDirectory || directory.mkdirs()) { "无法创建仪表封面缓存" }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > MAX_EDGE) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * MAX_EDGE / longest).coerceAtLeast(1),
            (bitmap.height * MAX_EDGE / longest).coerceAtLeast(1), true) else bitmap
        try {
            file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.JPEG, 90, it)) {
                "无法编码仪表封面"
            } }
            val uri = Lynk06MusicArtworkProvider.uriForFile(context, file)
            owned.add(file)
            // 同时清理上次异常退出留下的图片，目录中最多保留三张供延迟读取。
            directory.listFiles()?.filter { it.extension == "jpg" && it != file }
                ?.sortedByDescending { it.lastModified() }?.drop(MAX_FILES - 1)?.forEach(::remove)
            return uri
        } catch (failure: Exception) {
            file.delete()
            throw failure
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    fun close() {
        owned.toList().forEach(::remove)
    }

    private fun remove(file: File) {
        owned.remove(file)
        file.delete()
    }

    companion object {
        const val MEDIA_CENTER_PACKAGE = "ecarx.xsf.mediacenter"
        private const val MAX_FILES = 3
        private const val MAX_EDGE = 512
    }
}
