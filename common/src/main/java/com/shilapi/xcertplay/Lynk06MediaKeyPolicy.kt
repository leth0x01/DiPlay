package com.shilapi.xcertplay

import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/** 本车输入服务将方控转换为标准媒体事件，窗口按下时发送一次并消费释放。 */
internal object Lynk06MediaKeyPolicy {
    fun press(keyCode: Int, sessionActive: Boolean, inCall: Boolean): Int? {
        if (!sessionActive || inCall) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> CarPlayMediaButton.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> CarPlayMediaButton.PREVIOUS
            KeyEvent.KEYCODE_MEDIA_PLAY -> CarPlayMediaButton.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> CarPlayMediaButton.PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
            else -> null
        }
    }
}
