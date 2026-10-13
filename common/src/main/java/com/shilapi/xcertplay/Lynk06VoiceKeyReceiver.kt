package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.vehicle.VehicleIntegration

/** XSFInputService 的语音键有序广播；参数来自本车 APK 的 ECarXRVoiceAssistAction。 */
class Lynk06VoiceKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!VehicleIntegration.isDetectedLynk06() || !isOrderedBroadcast ||
            intent.action != ACTION || intent.getIntExtra(EXTRA_KEY, -1) != VOICE_KEY) return
        val action = intent.getIntExtra(EXTRA_ACTION, -1)
        val handled = when (action) {
            SHORT_PRESS -> CarPlayBackgroundSession.active && !inCall(context) &&
                CarPlayMediaKeys.onExternalSiriKey()
            LONG_PRESS -> runCatching {
                context.startActivity(Intent(context, DiPlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                true
            }.getOrElse {
                Log.w(TAG, "语音键长按启动 LynkPlay 失败", it)
                false
            }
            else -> false
        }
        status = "动作=$action；处理=$handled"
        Log.i(TAG, status)
        // 只有成功处理才阻止后续接收者；Siri 不可用时仍保留原车语音。
        if (handled) abortBroadcast()
    }

    companion object {
        internal const val ACTION = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"
        internal const val EXTRA_KEY = "ecarx.extra.ECARX_KEY_EVENT_TYPE"
        internal const val EXTRA_ACTION = "ecarx.extra.ECARX_KEY_ACTION_TYPE"
        internal const val VOICE_KEY = 200231
        internal const val SHORT_PRESS = 0
        internal const val LONG_PRESS = 1
        private const val TAG = "Lynk06VoiceKey"
        @Volatile private var status = "尚未收到语音键事件"
        internal fun diagnosticReport(): String = "领克语音键：$status"
    }
}
