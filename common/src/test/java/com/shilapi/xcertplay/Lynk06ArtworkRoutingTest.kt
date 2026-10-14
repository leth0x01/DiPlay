package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.os.Looper
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class Lynk06ArtworkRoutingTest {
    private val controller = mock(CarPlayController::class.java)
    private val media = mock(Lynk06MediaCenter::class.java)
    private val cache get() = ReflectionHelpers.getField<LinkedHashMap<Int, Bitmap?>>(CarPlayMediaKeys, "artworkCache")

    @After fun tearDown() {
        ReflectionHelpers.setField(CarPlayMediaKeys, "lynk06MediaCenter", null)
        CarPlayMediaKeys.detach(controller)
    }

    @Test fun newTrackDoesNotPublishPreviousCoverWhileCurrentCoverIsPending() {
        assertTrackCoverRouting(nextReference = 2)
    }

    @Test fun reusedReferenceDoesNotPublishPreviousTracksCover() {
        assertTrackCoverRouting(nextReference = 1)
    }

    @Test fun lyricUpdateKeepsCurrentTracksCover() {
        val previous = CarPlayNowPlaying(title = "上一句歌词", artist = "歌手", artworkTransferId = 1)
        val next = previous.copy(title = "下一句歌词")
        val cover = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        ReflectionHelpers.setField(CarPlayMediaKeys, "controller", controller)
        ReflectionHelpers.setField(CarPlayMediaKeys, "nowPlaying", previous)
        ReflectionHelpers.setField(CarPlayMediaKeys, "artwork", cover)
        ReflectionHelpers.setField(CarPlayMediaKeys, "lynk06MediaCenter", media)
        cache[1] = cover
        `when`(controller.nowPlayingSnapshot()).thenReturn(next)
        try {
            refresh()
            verify(media).update(next, cover)
        } finally { cover.recycle() }
    }

    @Test fun decodedCoverSurvivesDelayedDurationAndArtistCorrections() {
        val initial = CarPlayNowPlaying(title = "歌曲", artist = "歌手", album = "专辑",
            durationMillis = 0, artworkTransferId = 130, elapsedMillis = 1_000, playing = true)
        val cover = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        ReflectionHelpers.setField(CarPlayMediaKeys, "controller", controller)
        ReflectionHelpers.setField(CarPlayMediaKeys, "nowPlaying", initial)
        ReflectionHelpers.setField(CarPlayMediaKeys, "artwork", cover)
        ReflectionHelpers.setField(CarPlayMediaKeys, "lynk06MediaCenter", media)
        cache[130] = cover
        try {
            val updates = listOf(
                initial.copy(durationMillis = 180_000, elapsedMillis = 2_300),
                initial.copy(durationMillis = 180_000, artist = "补正后的歌手", elapsedMillis = 2_800),
                initial.copy(title = "作曲：歌手", artist = "歌曲", album = "歌词",
                    durationMillis = 180_000, elapsedMillis = 2_800),
                initial.copy(durationMillis = 180_000, artist = null, album = null, elapsedMillis = 3_300),
            )
            for (update in updates) {
                `when`(controller.nowPlayingSnapshot()).thenReturn(update)
                refresh()
                verify(media).update(update, cover)
                assertSame(cover, cache[130])
                assertSame(cover, ReflectionHelpers.getField<ClusterMusicState.Snapshot>(ClusterMusicState, "state").artwork)
            }
        } finally { cover.recycle() }
    }

    private fun assertTrackCoverRouting(nextReference: Int) {
        val previous = CarPlayNowPlaying(title = "上一首", artist = "歌手", album = "专辑", artworkTransferId = 1,
            durationMillis = 180_000, elapsedMillis = 60_000)
        val next = previous.copy(title = "下一首", artworkTransferId = nextReference,
            durationMillis = 240_000, elapsedMillis = 0)
        val oldCover = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val newCover = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        ReflectionHelpers.setField(CarPlayMediaKeys, "controller", controller)
        ReflectionHelpers.setField(CarPlayMediaKeys, "nowPlaying", previous)
        ReflectionHelpers.setField(CarPlayMediaKeys, "artwork", oldCover)
        ReflectionHelpers.setField(CarPlayMediaKeys, "lynk06MediaCenter", media)
        cache[1] = oldCover
        `when`(controller.nowPlayingSnapshot()).thenReturn(next)
        try {
            refresh()
            // 原车会按新歌信息缓存图片，不能把界面暂留的旧封面传给它。
            verify(media).update(next, null)
            assertSame(oldCover, ReflectionHelpers.getField(CarPlayMediaKeys, "artwork"))
            clearInvocations(media)

            cache[nextReference] = newCover
            refresh()
            verify(media).update(next, newCover)
        } finally {
            oldCover.recycle()
            newCover.recycle()
        }
    }

    private fun refresh() {
        ReflectionHelpers.callInstanceMethod<Unit>(CarPlayMediaKeys, "onNowPlayingChanged",
            ClassParameter.from(CarPlayController::class.java, controller))
        shadowOf(Looper.getMainLooper()).idle()
    }
}
