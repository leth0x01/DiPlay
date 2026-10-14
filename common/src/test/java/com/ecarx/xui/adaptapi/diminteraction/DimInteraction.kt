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
    @Volatile var createCalls = 0
    @Volatile var watcherRegistrationFailuresRemaining = 0
    @Volatile var failAfterStoringWatcher = false
    @Volatile var watcherRegistrationCalls = 0
    @Volatile var watcherUnregisterCalls = 0
    @Volatile var menuRegistrationCalls = 0
    @Volatile var failedWatcher: IConnectable.IConnectWatcher? = null
    @Volatile var suppressConnectCallback = false
    @Volatile var connectCallbackDelayMillis = 0L
    val connectTimes = CopyOnWriteArrayList<Long>()
    @Volatile var menuCallback: IDimMenuInteraction.IDimMenuInteractionCallback? = null
    @Volatile var watcher: IConnectable.IConnectWatcher? = null
    val notifications = CopyOnWriteArrayList<Int>()
    val navigationInfos = CopyOnWriteArrayList<INaviInteraction.INavigationInfo>()
    @Volatile var infoFailuresRemaining = 0
    val switchRequests = CopyOnWriteArrayList<Int>()
    val presentationSwitches = CopyOnWriteArrayList<String>()
    val extensionBundles = CopyOnWriteArrayList<android.os.Bundle>()
    @Volatile var switchAccepted = true
    @Volatile var failPresentationSwitch = false
    @Volatile var tbtActive = false
    @Volatile var presentationOption = 0
    @Volatile var disconnected = false
    @Volatile var naviCallback: INaviInteraction.INavigationInteractionCallback? = null
    @Volatile var interactionCallback: DimInteraction.IInteractionCallback? = null
    override fun registerConnectWatcher(watcher: IConnectable.IConnectWatcher) {
        watcherRegistrationCalls++
        if (watcherRegistrationFailuresRemaining > 0) {
            watcherRegistrationFailuresRemaining--
            if (failAfterStoringWatcher) {
                this.watcher = watcher
                failedWatcher = watcher
            }
            error("模拟连接 watcher 注册失败")
        }
        this.watcher = watcher
    }
    override fun unregisterConnectWatcher() { watcherUnregisterCalls++; watcher = null }
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
    override fun registerDimMenuInteractionCallback(callback: IDimMenuInteraction.IDimMenuInteractionCallback) {
        menuRegistrationCalls++
        menuCallback = callback
    }
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
    override fun updateNavigationInfo(info: INaviInteraction.INavigationInfo) {
        if (infoFailuresRemaining > 0) {
            infoFailuresRemaining--
            error("模拟完整卡片发送失败")
        }
        navigationInfos.add(info)
    }
    override fun notifyTurnByTurnStarted() { tbtActive = true }
    override fun notifyTurnByTurnStopped() { tbtActive = false }
    fun setPresentationToDimSwitch(type: Int, pkg: String, cls: String, on: Boolean) {
        if (failPresentationSwitch) error("模拟投影声明失败")
        presentationSwitches.add("$type,$pkg,$cls,$on")
    }
    override fun updateExtensionInfo(bundle: android.os.Bundle) { extensionBundles.add(bundle) }
    override fun registerNavigationInteractionCallback(callback: INaviInteraction.INavigationInteractionCallback) {
        naviCallback = callback
    }
    override fun unregisterNavigationInteractionCallback(callback: INaviInteraction.INavigationInteractionCallback) {
        naviCallback = null
    }
    fun registerInteractionCallback(callback: DimInteraction.IInteractionCallback) {
        interactionCallback = callback
    }
    fun unregisterInteractionCallback(callback: DimInteraction.IInteractionCallback) {
        interactionCallback = null
    }
    override fun switchNaviMode(mode: Int): Boolean {
        switchRequests.add(mode)
        // 拒绝时保持原车模式不变。
        if (switchAccepted) this.mode = mode
        return switchAccepted
    }
    fun getShowPresentationOption(): Int = presentationOption
    interface IInteractionCallback
    companion object {
        @Volatile var instance = DimInteraction()
        @JvmStatic fun create(context: Context): DimInteraction = instance.also { it.createCalls++ }
    }
}

interface INaviInteraction {
    fun updateNavigationInfo(info: INavigationInfo)
    interface INavigationInfo {
        fun getNavigationStatus(): Int
        fun getNavigationTurnId(): Int
        fun getNavigationTurnSVG(): String
        fun getNextGuidancePointName(): String
        fun getDistanceToNextGuidancePoint(): Long
        fun getDistanceToDestination(): Long
        fun getETA(): Long
        fun getDrivingDirection(): Int
        fun getLaneInfo(): Array<ILaneInfo>
        fun getDayNightMode(): Int
        fun getMuteState(): Int
        fun getHighwayExitInfo(): Any?
        fun getRoadCameraInfo(): Any?
        fun getServiceAreaInfo(): Any?
    }
    interface ILaneInfo
    fun notifyNavigationStatus(status: Int)
    fun notifyTurnByTurnStarted()
    fun notifyTurnByTurnStopped()
    fun updateExtensionInfo(bundle: android.os.Bundle)
    fun registerNavigationInteractionCallback(callback: INavigationInteractionCallback)
    fun unregisterNavigationInteractionCallback(callback: INavigationInteractionCallback)
    interface INavigationInteractionCallback
}
interface IDimMenuInteraction {
    interface IDimMenuInteractionCallback { fun onChangeNaviMode(mode: Int) }
    fun getNaviMode(): Int
    fun switchNaviMode(mode: Int): Boolean
    fun registerDimMenuInteractionCallback(callback: IDimMenuInteractionCallback)
    fun unregisterDimMenuInteractionCallback(callback: IDimMenuInteractionCallback)
}
