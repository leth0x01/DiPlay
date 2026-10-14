package com.shilapi.xcertplay

import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Handler
import android.os.Parcel
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.util.ReflectionHelpers
import com.shilapi.xcertplay.media.CarPlayNowPlaying

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [FileProviderPathTestShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Lynk06MusicArtworkTest {
    @Test fun returningToPublishedCoverBeforeQueuedUpdateKeepsItsUri() {
        val context = RuntimeEnvironment.getApplication()
        val authority = "${context.packageName}.lynk06-music-artwork"
        val provider = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)!!
        ShadowContentResolver.registerProviderInternal(authority,
            Lynk06MusicArtworkProvider().apply { attachInfo(context, provider) })
        val first = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val other = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val info = CarPlayNowPlaying(title = "歌曲")
        val link = Lynk06MediaCenter(context, info, { true }, {}, first)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val token = Binder()
        val remote = object : Binder() {
            init { attachInterface(null, Lynk06MediaCenterService.DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Lynk06MediaCenterService.DESCRIPTOR)
                data.readStrongBinder()
                if (code == 6) data.readStrongBinder()
                if (code == 10) data.readLong()
                reply!!.writeNoException()
                if (code != 10) reply.writeInt(1)
                return true
            }
        }
        fun cover(): android.net.Uri? {
            val playback = ReflectionHelpers.getField<IBinder>(link, "playback")
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(Lynk06MusicPlayback.DESCRIPTOR)
                assertTrue(playback.transact(16, data, reply, 0))
                reply.readException()
                return if (reply.readInt() == 1) android.net.Uri.CREATOR.createFromParcel(reply) else null
            } finally { data.recycle(); reply.recycle() }
        }
        try {
            ReflectionHelpers.setField(link, "api", Lynk06MediaCenterService(remote))
            ReflectionHelpers.setField(link, "token", token)
            ReflectionHelpers.getField<Lynk06MediaControl>(link, "control").focusChanged(context.packageName)
            ReflectionHelpers.callInstanceMethod<Unit>(link, "publishState")
            val initial = cover()
            assertNotNull(initial)
            val worker = ReflectionHelpers.getField<Handler>(link, "worker")
            worker.post { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            link.update(info, other)
            assertNull(cover())
            link.update(info, first)
            release.countDown()
            worker.post { finished.countDown() }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(initial, cover())
        } finally {
            release.countDown()
            link.close()
            first.recycle()
            other.recycle()
        }
    }

    @Test fun coverIsReadableBoundedAndRemovedAtSessionEnd() {
        val context = RuntimeEnvironment.getApplication()
        val authority = "${context.packageName}.lynk06-music-artwork"
        val info = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)!!
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
        ShadowContentResolver.registerProviderInternal(authority,
            Lynk06MusicArtworkProvider().apply { attachInfo(context, info) })
        val store = Lynk06MusicArtwork(context)
        val bitmap = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        try {
            assertNull(store.save(null))
            val first = store.save(bitmap)!!
            assertEquals("image/jpeg", context.contentResolver.getType(first))
            val decoded = context.contentResolver.openInputStream(first)!!.use(BitmapFactory::decodeStream)!!
            assertEquals(512, decoded.width)
            assertEquals(384, decoded.height)
            decoded.recycle()
            repeat(3) { store.save(bitmap) }
            val covers = File(context.cacheDir, "lynk06-music-covers")
            assertEquals(3, covers.listFiles()!!.size)
            assertThrows(java.io.FileNotFoundException::class.java) {
                context.contentResolver.openInputStream(first)
            }
            val unrelated = File(context.cacheDir, "unrelated.txt").apply { writeText("资料") }
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, authority, unrelated)
            }
            unrelated.delete()
            store.close()
            assertEquals(0, covers.listFiles()!!.size)
            assertFalse(bitmap.isRecycled)
        } finally {
            store.close()
            bitmap.recycle()
        }
    }
}
