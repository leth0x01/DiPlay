package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** 独立 provider 只暴露仪表封面目录，读取权逐文件授予原车媒体中心。 */
class Lynk06MusicArtworkProvider : FileProvider()

/** 在媒体 worker 上写入，避免 Binder getter 或主线程压缩图片。 */
internal class Lynk06MusicArtwork(private val context: Context) {
    private val directory = File(context.cacheDir, "lynk06-music-covers")
    private val owned = linkedMapOf<File, Uri>()

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
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.lynk06-music-artwork", file)
            context.grantUriPermission(MEDIA_CENTER_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            owned[file] = uri
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
        owned.keys.toList().forEach(::remove)
    }

    private fun remove(file: File) {
        val uri = owned.remove(file) ?: FileProvider.getUriForFile(context,
            "${context.packageName}.lynk06-music-artwork", file)
        context.revokeUriPermission(MEDIA_CENTER_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        file.delete()
    }

    companion object {
        const val MEDIA_CENTER_PACKAGE = "ecarx.xsf.mediacenter"
        private const val MAX_FILES = 3
        private const val MAX_EDGE = 512
    }
}
