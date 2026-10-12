package com.ecarx.xui.adaptapi.diminteraction

import android.content.Context
import com.ecarx.xui.adaptapi.binder.IConnectable
import java.util.concurrent.CopyOnWriteArrayList

/** 只用于验证反射调用、回调线程和资源释放；不作为固件实现打包。 */
class DimInteraction : IConnectable, IDimMenuInteraction, INaviInteraction {
    @Volatile var mode = 2
    @Volatile var failRead = false
    @Volatile var menuCallback: IDimMenuInteraction.IDimMenuInteractionCallback? = null
    @Volatile var watcher: IConnectable.IConnectWatcher? = null
    val notifications = CopyOnWriteArrayList<Int>()
    @Volatile var disconnected = false
    override fun registerConnectWatcher(watcher: IConnectable.IConnectWatcher) { this.watcher = watcher }
    override fun unregisterConnectWatcher() { watcher = null }
    override fun connect() { watcher?.onConnected() }
    override fun disconnect() { disconnected = true }
    fun getDimMenuInteraction(): IDimMenuInteraction = this
    fun getNaviInteraction(): INaviInteraction = this
    override fun getNaviMode(): Int {
        check(!failRead) { "模拟固件读取失败" }
        return mode
    }
    override fun registerDimMenuInteractionCallback(callback: IDimMenuInteraction.IDimMenuInteractionCallback) { menuCallback = callback }
    override fun unregisterDimMenuInteractionCallback(callback: IDimMenuInteraction.IDimMenuInteractionCallback) {
        check(menuCallback === callback)
        menuCallback = null
    }
    override fun notifyNavigationStatus(status: Int) { notifications.add(status) }
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
