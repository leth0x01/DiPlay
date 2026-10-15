package com.shilapi.xcertplay

import java.util.Locale

/**
 * Formats the CarPlay touch diagnostics that the host writes to the session log.
 *
 * A CarPlay button that does nothing can be a touch that never left the head unit, and from the
 * driver's seat the two look the same. The Settings swipe, the side panel tab and the letterbox bars
 * all take touches before CarPlay gets them, so a gesture is logged with the contacts it actually
 * sent: how many, where, and whether anything was dropped or cancelled. A gesture that ends without
 * the host sending an up leaves a contact held down in CarPlay, which is logged as an anomaly
 * because it makes later touches behave strangely (for example a map button that stops responding).
 *
 * Coordinates are normalized to the CarPlay picture (0..1), so lines from different resolutions can
 * be compared.
 */
internal object TouchDiagnostics {

    /** Three decimals of one normalized coordinate: `(0.500,0.250)`. */
    fun point(x: Float, y: Float): String = "(${coordinate(x)},${coordinate(y)})"

    /** The Android pointer ids of an event in the order CarPlay receives the contacts: `[4,7]`. */
    fun ids(values: List<Int>): String = values.joinToString(separator = ",", prefix = "[", postfix = "]")

    /** One line for a whole gesture, so the log does not need every move event to be readable. */
    fun summary(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        maxPointers: Int,
        moves: Int,
        durationMillis: Long,
        sent: Int,
        dropped: Int,
        cancelled: Boolean,
    ): String = buildString {
        append("touch gesture start=")
        append(point(startX, startY))
        append(" end=")
        append(point(endX, endY))
        append(" pointers=")
        append(maxPointers)
        append(" moves=")
        append(moves)
        append(' ')
        append(durationMillis)
        append("ms sent=")
        append(sent)
        if (dropped > 0) {
            append(" dropped=")
            append(dropped)
        }
        if (cancelled) append(" cancelled")
    }

    /** A dot decimal separator in every locale, so a right-to-left log stays greppable. */
    private fun coordinate(value: Float): String = String.format(Locale.US, "%.3f", value)
}
