package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarPlayPlaybackStatusTest {
    private fun update(block: com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder.() -> Unit) =
        Iap2Messages.buildRaw(CarPlayPlaybackStatus.NOW_PLAYING_UPDATE, block)

    @Test
    fun reportsOnlyChangesOfThePlaybackStatus() {
        val status = CarPlayPlaybackStatus()

        assertEquals(true, status.accept(update { group(1) { u8(0, 1) } }))
        assertNull(status.accept(update { group(1) { u8(0, 1) } }))
        // Elapsed time alone, as the iPhone sends it every half second: no status, no change.
        assertNull(status.accept(update { group(1) { u32(1, 120_706L) } }))
        assertEquals(false, status.accept(update { group(1) { u8(0, 2) } }))
    }

    @Test
    fun otherMessagesAndSessionEnd() {
        val status = CarPlayPlaybackStatus()
        assertNull(status.accept(Iap2Messages.buildRaw(0x5201) { group(1) { u8(0, 1) } }))
        assertNull(status.clear())

        status.accept(update { group(1) { u8(0, 1) } })
        assertEquals(false, status.clear())
    }

    @Test
    fun retainsIncrementalNowPlayingMetadata() {
        val status = CarPlayPlaybackStatus { 1_000_000_000L }

        assertEquals(
            CarPlayNowPlaying(
                title = "Dreams",
                album = "Rumours",
                artist = "Fleetwood Mac",
                durationMillis = 257_000,
            ),
            status.acceptUpdate(update {
                group(0) {
                    string(1, "Dreams")
                    u32(4, 257_000)
                    string(6, "Rumours")
                    string(12, "Fleetwood Mac")
                }
            }),
        )
        assertEquals(
            CarPlayNowPlaying(
                title = "Dreams",
                album = "Rumours",
                artist = "Fleetwood Mac",
                sourceApp = "Music",
                durationMillis = 257_000,
                elapsedMillis = 12_500,
                playing = true,
                positionUpdatedAtNanos = 1_000_000_000L,
            ),
            status.acceptUpdate(update {
                group(1) {
                    u8(0, 1)
                    u32(1, 12_500)
                    string(7, "Music")
                }
            }),
        )
    }

    @Test
    fun emptyStringsClearFieldsAndArtworkIdIsRetained() {
        val status = CarPlayPlaybackStatus()
        status.acceptUpdate(update { group(0) { string(1, "Song") } })

        val updated = status.acceptUpdate(update {
            group(0) {
                string(1, "")
                u8(26, 0x81)
            }
        })

        assertNull(updated?.title)
        assertEquals(0x81, updated?.artworkTransferId)
        assertEquals(CarPlayNowPlaying(), status.clearAll())
    }

    @Test fun metadataUpdatesKeepThePositionAnchorAndExplicitPositionsRefreshIt() {
        var now = 1_000_000_000L
        val status = CarPlayPlaybackStatus { now }
        status.acceptUpdate(update { group(1) { u8(0, 1); u32(1, 5_000) } })
        now += 2_000_000_000L
        val metadata = status.acceptUpdate(update { group(0) { string(6, "补充专辑") } })!!
        assertEquals(7_000L, metadata.positionMillisAt(now))
        assertEquals(1_000_000_000L, metadata.positionUpdatedAtNanos)

        // 相同数值也可能是 seek 回原位置，必须以本次真实上报重新校准。
        val seek = status.acceptUpdate(update { group(1) { u32(1, 5_000) } })!!
        assertEquals(5_000L, seek.positionMillisAt(now))
        assertEquals(now, seek.positionUpdatedAtNanos)
        now += 1_000_000_000L
        assertEquals(6_000L, seek.positionMillisAt(now))
    }

    @Test fun statusOnlyPauseAndResumeKeepAContinuousPosition() {
        var now = 1_000_000_000L
        val status = CarPlayPlaybackStatus { now }
        status.acceptUpdate(update { group(1) { u8(0, 1); u32(1, 5_000) } })
        now += 2_000_000_000L
        val paused = status.acceptUpdate(update { group(1) { u8(0, 2) } })!!
        assertEquals(7_000L, paused.positionMillisAt(now + 10_000_000_000L))
        now += 10_000_000_000L
        val resumed = status.acceptUpdate(update { group(1) { u8(0, 1) } })!!
        assertEquals(8_000L, resumed.positionMillisAt(now + 1_000_000_000L))
        val seek = status.acceptUpdate(update { group(1) { u32(1, 1_000) } })!!
        assertEquals(1_000L, seek.positionMillisAt(now))
    }

    @Test fun unknownAndInvalidPositionsDoNotAcquireAnAnchor() {
        val status = CarPlayPlaybackStatus { 1_000_000_000L }
        val playing = status.acceptUpdate(update { group(1) { u8(0, 1) } })!!
        assertNull(playing.positionMillisAt(5_000_000_000L))
        assertNull(playing.positionUpdatedAtNanos)
        assertNull(status.acceptUpdate(update { group(1) { bytes(1, byteArrayOf(1)) } }))
    }

    @Test fun positionCalculationBoundsClockDurationAndOverflow() {
        val info = CarPlayNowPlaying(elapsedMillis = 9_000, playing = true,
            durationMillis = 10_000, positionUpdatedAtNanos = 2_000_000_000L)
        assertEquals(9_000L, info.positionMillisAt(1_000_000_000L))
        assertEquals(10_000L, info.positionMillisAt(20_000_000_000L))
        assertEquals(9_000L, info.copy(positionUpdatedAtNanos = null).positionMillisAt(20_000_000_000L))
        assertEquals(Long.MAX_VALUE, info.copy(elapsedMillis = Long.MAX_VALUE - 1, durationMillis = null)
            .positionMillisAt(20_000_000_000L))
    }
}
