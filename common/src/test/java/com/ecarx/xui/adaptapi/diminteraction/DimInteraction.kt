package com.ecarx.xui.adaptapi.diminteraction

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.ecarx.xui.adaptapi.binder.IConnectable
import java.util.concurrent.CopyOnWriteArrayList

/** 只用于验证反射调用、回调线程和资源释放；不作为固件实现打包。 */
class DimInteraction : IConnectable, IDimMenuInteraction, INaviInteraction {
    @Volatile var mode = 2
    @Volatile var failRead = false
    @Volatile var readFailuresRemaining = 0
    @Volatile var notificationFailuresRemaining = 0
    @Volatile var readCalls = 0
    @Volatile var connectCalls = 0
    @Volatile var suppressConnectCallback = false
    @Volatile var connectCallbackDelayMillis = 0L
    val connectTimes = CopyOnWriteArrayList<Long>()
    @Volatile var menuCallback: IDimMenuInteraction.IDimMenuInteractionCallback? = null
    @Volatile var watcher: IConnectable.IConnectWatcher? = null
    val notifications = CopyOnWriteArrayList<Int>()
    @Volatile var disconnected = false
    override fun registerConnectWatcher(watcher: IConnectable.IConnectWatcher) { this.watcher = watcher }
    override fun unregisterConnectWatcher() { watcher = null }
    override fun connect() {
        connectCalls++
        disconnected = false
        connectTimes.add(SystemClock.uptimeMillis())
        if (!suppressConnectCallback) {
            val attempt = connectCalls
            if (connectCallbackDelayMillis == 0L) watcher?.onConnected()
            else Handler(Looper.getMainLooper()).postDelayed({
                if (!disconnected && connectCalls == attempt) watcher?.onConnected()
            }, connectCallbackDelayMillis)
        }
    }
    override fun disconnect() { disconnected = true; watcher?.onDisConnected() }
    fun getDimMenuInteraction(): IDimMenuInteraction = this
    fun getNaviInteraction(): INaviInteraction = this
    override fun getNaviMode(): Int {
        readCalls++
        if (readFailuresRemaining > 0) {
            readFailuresRemaining--
            error("模拟一次读取失败")
        }
        check(!failRead) { "模拟固件读取失败" }
        return mode
    }
    override fun registerDimMenuInteractionCallback(callback: IDimMenuInteraction.IDimMenuInteractionCallback) { menuCallback = callback }
    override fun unregisterDimMenuInteractionCallback(callback: IDimMenuInteraction.IDimMenuInteractionCallback) {
        check(menuCallback === callback)
        menuCallback = null
    }
    override fun notifyNavigationStatus(status: Int) {
        if (notificationFailuresRemaining > 0) {
            notificationFailuresRemaining--
            error("模拟一次通知失败")
        }
        notifications.add(status)
    }
    companion object {
        @Volatile var instance = DimInteraction()
        @JvmStatic fun create(context: Context): DimInteraction = instance
    }
}

interface INaviInteraction { fun notifyNavigationStatus(status: Int) }
interface IDimMenuInteraction {
    interface IDimMenuInteractionCallback { fun onChangeNaviMode(mode: Int) }
    fun getNaviMode(): Int
    fun registerDimMenuInteractionCallback(callback: IDimMenuInteractionCallback)
    fun unregisterDimMenuInteractionCallback(callback: IDimMenuInteractionCallback)
}
