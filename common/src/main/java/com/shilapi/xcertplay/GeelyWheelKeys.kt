package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.geely.lib.oneosapi.input.IInputListener
import com.geely.lib.oneosapi.input.IInputManager
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.network.GeelyHeadUnit

/**
 * Geely/Lynk steering-wheel media keys.
 *
 * The head unit maps the wheel's keys to vendor key codes and routes them only through its own input
 * stack - no standard Android KeyEvent is produced - so a media session or accessibility service
 * never sees them. Its input service (`com.geely.inputservice.InputService`) is exported and its
 * `registerListener` has no permission check, so we subscribe to the raw codes there (exactly how
 * the car's own `KeyInputController` does) and forward them to the active CarPlay session.
 *
 * With [AirPlayPersistence.loadCarBluetoothAudio] on, the car's own Bluetooth (AVRCP) control drives
 * the iPhone's music, so forwarding the media keys too would double-fire; only the microphone key is
 * registered then, for the long-press Siri shortcut.
 *
 * The microphone key's long press is answered by the car's HiCar app, which registers for the same
 * code and cannot be intercepted from here; disable HiCar over ADB if Siri should answer instead
 * (see docs/COMPATIBILITY.md).
 *
 * 未验证固件使用标准媒体键；服务或契约不匹配时记录原因并回退。
 */
internal object GeelyWheelKeys {
    private const val TAG = "DiPlay-GeelyWheel"
    private const val INPUT_PACKAGE = "com.geely.inputservice"
    private const val INPUT_CLASS = "com.geely.inputservice.InputService"

    private const val RETRY_MILLIS = 3_000L

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var bound = false
    private var manager: IInputManager? = null
    private var listener: IInputListener? = null
    @Volatile private var status = "未启动"

    fun diagnosticReport(context: Context): String {
        val info = runCatching {
            context.packageManager.getServiceInfo(ComponentName(INPUT_PACKAGE, INPUT_CLASS), 0)
        }.getOrNull()
        return "Geely input service: visible=${info != null}; enabled=${info?.enabled}; " +
            "exported=${info?.exported}; permission=${info?.permission}; status=$status"
    }

    /** Binds while a CarPlay session is active; safe to call repeatedly. */
    @Synchronized
    fun start(context: Context) {
        if (!GeelyHeadUnit.wheelInputSupported) {
            status = "固件输入契约未验证，使用标准媒体键"
            return
        }
        appContext = context.applicationContext
        if (bound) return
        bindLocked()
    }

    /** Unbinds when the CarPlay session ends. */
    @Synchronized
    fun stop() {
        listener?.let { registered ->
            val packageName = appContext?.packageName ?: return@let
            runCatching { manager?.unregisterListener(registered, packageName) }
        }
        listener = null
        manager = null
        handler.removeCallbacksAndMessages(null)
        val context = appContext
        if (bound && context != null) runCatching { context.unbindService(connection) }
        bound = false
        appContext = null
    }

    private fun bindLocked() {
        val context = appContext ?: return
        val info = runCatching {
            context.packageManager.getServiceInfo(ComponentName(INPUT_PACKAGE, INPUT_CLASS), 0)
        }.getOrNull()
        if (info == null || !info.enabled || !info.applicationInfo.enabled || !info.exported ||
            (!info.permission.isNullOrEmpty() && context.checkSelfPermission(info.permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            status = "输入服务不可用或缺少权限，使用标准媒体键"
            Log.i(TAG, status)
            return
        }
        val intent = Intent().setComponent(ComponentName(INPUT_PACKAGE, INPUT_CLASS))
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.w(TAG, "绑定输入服务失败，使用标准媒体键", it) }
            .getOrDefault(false)
        status = if (bound) "等待输入服务连接" else "绑定失败，使用标准媒体键"
        Log.i(TAG, "bind geely input service requested=$bound")
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(this@GeelyWheelKeys) {
                val context = appContext ?: return
                // 描述符是必要条件，不能替代上面的已验证固件白名单。
                if (runCatching { service?.interfaceDescriptor }.getOrNull() !=
                    "com.geely.lib.oneosapi.input.IInputManager") {
                    status = "输入服务 Binder 契约不匹配，使用标准媒体键"
                    Log.w(TAG, status)
                    stop()
                    return
                }
                val input = IInputManager.Stub.asInterface(service)
                val registered = object : IInputListener.Stub() {
                    override fun onKeyCodeEvent(keyCode: Int, event: Int, softKeyFunction: Int) {}
                    override fun onShortClick(keyCode: Int, softKeyFunction: Int) {
                        forward(keyCode)
                    }
                    override fun onHoldingPressStarted(keyCode: Int, softKeyFunction: Int) {}
                    override fun onHoldingPressStopped(keyCode: Int, softKeyFunction: Int) {}
                    override fun onLongPressTriggered(keyCode: Int, softKeyFunction: Int) {
                        Log.i(TAG, "long press key=$keyCode func=$softKeyFunction")
                        // The microphone key's long press: Siri, once the car's HiCar app is disabled.
                        if (keyCode == GeelyWheelKeyCodes.KEY_VOICE_ASSIST) {
                            Log.i(TAG, "Siri requested=${CarPlayMediaKeys.onExternalSiriKey()}")
                        }
                    }
                    override fun onDoubleClick(keyCode: Int, softKeyFunction: Int) {}
                }
                manager = input
                listener = registered
                runCatching {
                    // With CarPlay audio on the car's Bluetooth, the head unit's own AVRCP already drives
                    // the iPhone's music, so forwarding play/pause and skip over iAP2 as well would
                    // double-fire. Keep only the microphone key (long press -> Siri) then.
                    val bluetoothAudio = AirPlayPersistence.loadCarBluetoothAudio(context)
                    val codes = GeelyWheelKeyCodes.registeredCodes(bluetoothAudio)
                    if (bluetoothAudio) {
                        Log.i(TAG, "car Bluetooth audio on: media keys pass to the head unit, only the Siri key is wired")
                    }
                    input.registerListener(registered, context.packageName, codes)
                    codes
                }
                    .onSuccess {
                        status = "已注册按键：${it.joinToString()}"
                        Log.i(TAG, status)
                    }
                    .onFailure {
                        status = "注册输入监听失败，使用标准媒体键"
                        Log.w(TAG, status, it)
                        stop()
                    }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            val retry: Boolean
            synchronized(this@GeelyWheelKeys) {
                manager = null
                listener = null
                status = "输入服务断开"
                retry = appContext != null
            }
            if (retry) {
                // The car service is persistent; rebind shortly after an unexpected drop.
                handler.postDelayed({
                    synchronized(this@GeelyWheelKeys) {
                        if (appContext != null && manager == null) {
                            if (bound) runCatching { appContext?.unbindService(connection) }
                            bound = false
                            bindLocked()
                        }
                    }
                }, RETRY_MILLIS)
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            status = "输入服务返回空绑定，使用标准媒体键"
            stop()
        }

        override fun onBindingDied(name: ComponentName?) {
            status = "输入服务绑定失效，使用标准媒体键"
            stop()
        }
    }

    private fun forward(keyCode: Int) {
        val index = when (keyCode) {
            GeelyWheelKeyCodes.KEY_PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
            GeelyWheelKeyCodes.KEY_NEXT -> CarPlayMediaButton.NEXT
            GeelyWheelKeyCodes.KEY_PREVIOUS -> CarPlayMediaButton.PREVIOUS
            else -> return
        }
        CarPlayMediaKeys.onExternalMediaKey(index)
    }
}
