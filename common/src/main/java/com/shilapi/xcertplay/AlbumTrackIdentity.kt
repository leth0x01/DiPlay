package com.shilapi.xcertplay

import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** iPhone 分批补正歌曲字段；单个字段变化或暂时清空不能使已解码封面失效。 */
internal fun albumTrackChanged(previous: CarPlayNowPlaying, next: CarPlayNowPlaying): Boolean {
    if (previous.artworkTransferId != next.artworkTransferId) return true
    fun changed(old: Any?, new: Any?) = old != null && new != null && old != new
    if (changed(previous.sourceApp, next.sourceApp)) return true
    // 标题也可能是歌词，必须同时有其他换歌证据；时长 0 表示尚未取得时长。
    if (!changed(previous.title, next.title)) return false
    if (changed(previous.artist, next.artist) || changed(previous.album, next.album) ||
        (previous.durationMillis?.let { it > 0 } == true && next.durationMillis?.let { it > 0 } == true &&
            previous.durationMillis != next.durationMillis)) return true
    return previous.elapsedMillis?.let { it > 15_000 } == true &&
        next.elapsedMillis?.let { it < 3_000 } == true
}
