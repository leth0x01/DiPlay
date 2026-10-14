package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.lang.reflect.Proxy

/** 本车 AdapterAPI 1.1.18.9 的仪表模式；显示器始终存在，不能作为模式判断。 */
internal data class Lynk06ClusterState(val available: Boolean = false, val navigating: Boolean = false, val mode: Int? = null) {
    // naviMode getter 在物理仪表显示和消失时均可为 1，仅作为诊断依据。
    val showMap: Boolean get() = available && navigating
    companion object { const val FULL = 3 }
}

/** 仪表链路各环节的诊断快照；字段在 worker 线程更新，整体通过 @Volatile 发布。 */
internal data class Lynk06ClusterDiagnostics(
    val adapterCreated: Boolean = false,
    val watcherRegistered: Boolean = false,
    val connected: Boolean = false,
    val menuAttached: Boolean = false,
    val naviAttached: Boolean = false,
    val navigationRequested: Boolean = false,
    val navigationSent: Boolean = false,
    val navigationCleanupPending: Boolean = false,
    val turnByTurnCleanupPending: Boolean = false,
    val projectionCleanupPending: Boolean = false,
    val lastNotifySuccess: Boolean? = null,
    val lastSwitchAccepted: Boolean? = null,
    val presentationSwitchOn: Boolean = false,
    val lastPresentationSwitchSuccess: Boolean? = null,
    val naviCallbackRegistered: Boolean = false,
    val interactionCallbackRegistered: Boolean = false,
    val presentationOption: Int? = null,
    val naviMode: Int? = null,
    val showMap: Boolean = false,
    val reconnectAttempts: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null,
    val closed: Boolean = false,
)

/** 厂商 Binder 调用独立于主线程和 iAP2；关闭后撤销本次发送的导航状态。 */
internal class Lynk06ClusterLink(
    context: Context,
    private val onState: (Lynk06ClusterState) -> Unit,
    private val log: (String) -> Unit,
    private val abiSnapshot: () -> List<String> = Lynk06ClusterAbiProbe::snapshot,
) {
    private val context = context.applicationContext
    private val thread = HandlerThread("lynk06-cluster-link").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    @Volatile private var closeFinished = false
    @Volatile private var navigationRequested = false
    private var predecessor: Lynk06ClusterLink? = null
    private var abiLogged = false
    private var dim: Any? = null
    private var dimClass: Class<*>? = null
    private var menu: Any? = null
    private var menuClass: Class<*>? = null
    private var navi: Any? = null
    private var naviClass: Class<*>? = null
    private var connectClass: Class<*>? = null
    private var watcherRegistered = false
    private var watcherGeneration = 0L
    private var callback: Any? = null
    private var naviCallback: Any? = null
    private var interactionCallback: Any? = null
    private var connected = false
    private var connecting = false
    private var reconnectDelayMillis = RETRY_DELAY_MILLIS
    private var consecutiveFailures = 0
    private var navigation = false
    private var sentNavigation = false
    private var navigationCleanupPending = false
    private var turnByTurnCleanupPending = false
    private var projectionCleanupPending = false
    private var recoveringCleanup = false
    private var cleanupLoaded = false
    private var savedCleanup = -1
    private val cleanupJournal = this.context.getSharedPreferences("lynk06-cluster-cleanup", Context.MODE_PRIVATE)
    private var republishNavigation = false
    private var mapModeRequested = false
    private var presentationSwitchOn = false
    private var guidance: Lynk06NavigationInfo? = null
    private var lastGuidance: Lynk06NavigationInfo? = null
    private var lastGuidanceMillis = 0L
    @Volatile private var guidanceSentCount = 0
    @Volatile private var projectionGateReleased = false
    private val oemGuard = Lynk06OemNavigationGuard(this.context, ::logOnMain)
    private var state = Lynk06ClusterState()
    @Volatile private var lastNotifySuccess: Boolean? = null
    @Volatile private var lastSwitchAccepted: Boolean? = null
    @Volatile private var lastPresentationSwitchSuccess: Boolean? = null
    @Volatile private var naviCallbackRegistered = false
    @Volatile private var interactionCallbackRegistered = false
    @Volatile private var presentationOption: Int? = null
    @Volatile private var lastError: String? = null
    @Volatile private var reconnectAttempts = 0
    @Volatile private var diagnostics = Lynk06ClusterDiagnostics()

    fun start() {
        predecessor = activeLink?.takeIf { it !== this && it.stopped && !it.closeFinished }
        activeLink = this
        worker.post { initialize() }
    }
    fun setNavigation(active: Boolean) {
        navigationRequested = active
        // 画面关闭不等待厂商 Binder；旧的导航进行回调也不能重新打开已结束的地图。
        if (!active) main.post { if (!stopped && !navigationRequested) onState(Lynk06ClusterState()) }
        worker.post { if (!stopped) { navigation = active; refresh() } }
    }
    fun setGuidance(next: com.shilapi.xcertplay.hud.ClusterTurnGuidance?) {
        worker.post { if (!stopped) { guidance = Lynk06NavigationInfo.from(next); refresh() } }
    }

    private fun initialize() {
        if (stopped) return
        if (predecessor?.closeFinished == false) {
            // 旧链路结束前不建立新接管，避免旧链路的关闭重试撤销新导航。
            worker.postDelayed({ initialize() }, CLOSE_HANDOFF_DELAY_MILLIS)
            return
        }
        predecessor = null
        loadCleanupState()
        if (!abiLogged) {
            // 诊断只尝试一次，失败不能阻止仪表业务初始化或重连。
            abiLogged = true
            runCatching { abiSnapshot().forEach(::logOnMain) }.onFailure {
                logOnMain("领克仪表 ABI：采集失败 ${it.javaClass.simpleName}")
            }
        }
        try {
            val type = Class.forName("com.ecarx.xui.adaptapi.diminteraction.DimInteraction")
            dimClass = type
            dim = type.getMethod("create", Context::class.java).invoke(null, context)
                ?: error("DimInteraction.create 返回空")
            val connect = Class.forName("com.ecarx.xui.adaptapi.binder.IConnectable")
            if (connect.isInstance(dim)) {
                connectClass = connect
                val watcher = Class.forName("com.ecarx.xui.adaptapi.binder.IConnectable\$IConnectWatcher")
                val generation = ++watcherGeneration
                val proxy = callbackProxy(watcher) { name, _ ->
                    worker.post {
                        if (!stopped && watcherRegistered && watcherGeneration == generation) {
                            if (name == "onConnected") attach()
                            // 主动重连产生的断线回调不缩短当前连接的等待窗口。
                            if (name == "onDisConnected" && !connecting) {
                                connected = false
                                runCatching { oemGuard.restore() }.onFailure(::logFailure)
                                worker.removeCallbacks(poll)
                                publish(Lynk06ClusterState())
                                scheduleReconnect()
                            }
                        }
                    }
                }
                connect.getMethod("registerConnectWatcher", watcher).invoke(dim, proxy)
                watcherRegistered = true
                beginConnection(reset = false)
            } else attach()
        } catch (error: Throwable) {
            if (!watcherRegistered) {
                // 注册可能已产生部分副作用；撤销旧回调并让重试完整初始化。
                watcherGeneration++
                runCatching { connectClass?.getMethod("unregisterConnectWatcher")?.invoke(dim) }
                runCatching { connectClass?.getMethod("disconnect")?.invoke(dim) }
                dim = null
                dimClass = null
                connectClass = null
            }
            connectionFailed(error)
        }
    }

    private fun attach() {
        try {
            // 断线重连后重新取得接口与回调，不保留旧 Binder 对象。
            detachCallbacks()
            menuClass = Class.forName("com.ecarx.xui.adaptapi.diminteraction.IDimMenuInteraction")
            naviClass = Class.forName("com.ecarx.xui.adaptapi.diminteraction.INaviInteraction")
            menu = dimClass!!.getMethod("getDimMenuInteraction").invoke(dim) ?: error("仪表菜单接口为空")
            navi = dimClass!!.getMethod("getNaviInteraction").invoke(dim) ?: error("仪表导航接口为空")
            val type = Class.forName("com.ecarx.xui.adaptapi.diminteraction.IDimMenuInteraction\$IDimMenuInteractionCallback")
            callback = callbackProxy(type) { name, _ ->
                if (name == "onChangeNaviMode") worker.post { if (!stopped) refresh() }
            }
            menuClass!!.getMethod("registerDimMenuInteractionCallback", type).invoke(menu, callback)
            // 原厂导航同时注册导航交互回调和顶层交互回调，DIM 据此认定导航源。
            registerNaviInteractionCallback()
            registerInteractionCallback()
            connected = true
            connecting = false
            worker.removeCallbacks(connectionTimeout)
            reconnectDelayMillis = RETRY_DELAY_MILLIS
            consecutiveFailures = 0
            // 记录原车投屏选项；只读调用，拒绝时不影响仪表链路。
            presentationOption = runCatching {
                dimClass!!.getMethod("getShowPresentationOption").invoke(dim) as Int
            }.getOrNull()
            // 服务重连后需要重新声明投影源。
            presentationSwitchOn = false
            // 服务重连后重新发布本次导航，停止时仍负责撤销。
            worker.removeCallbacks(reconnect)
            republishNavigation = navigation
            lastGuidance = null
            lastGuidanceMillis = 0L
            projectionGateReleased = false
            logOnMain("领克仪表：AdaptAPI 已连接")
            captureDiagnostics()
            refresh()
        } catch (error: Throwable) { connectionFailed(error) }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (stopped || !connected) return
            refresh()
        }
    }

    private fun refresh() {
        if (stopped || dim == null) return
        try {
            // 菜单读取失败也不能阻止导航结束通知，避免本次导航状态残留。
            if (!navigation) {
                try {
                    cleanupNavigation()
                } finally {
                    oemGuard.restore()
                    lastGuidance = null
                    lastGuidanceMillis = 0L
                    guidance = null
                    projectionGateReleased = false
                }
            }
            if (!connected) return
            // 先完成上次链路留下的清理，再声明新的导航源。
            if (recoveringCleanup) {
                cleanupNavigation()
                recoveringCleanup = false
            }
            if (navigation && guidance != null) oemGuard.pause()
            if (navigation && (!sentNavigation || republishNavigation)) {
                claimPresentationSwitch()
                notifyNavigation(true)
                notifyTurnByTurn(true)
                // 每次导航允许请求一次地图模式；用户手动切回简洁后本次导航不再请求。
                mapModeRequested = false
            }
            if (navigation) pushNavigationInfo()
            if (navigation && !mapModeRequested) {
                mapModeRequested = true
                if (switchNaviMode(Lynk06ClusterState.FULL)) {
                    updateDimDisplayMode(Lynk06ClusterState.FULL)
                }
            }
            // getter 只记录诊断，不参与地图模式请求或显示判断。
            val mode = menuClass!!.getMethod("getNaviMode").invoke(menu) as Int
            consecutiveFailures = 0
            publish(Lynk06ClusterState(true, navigation, mode))
        } catch (error: Throwable) {
            publish(Lynk06ClusterState())
            logFailure(error)
            // 单次事务异常不代表 Service 断开；保留连接并自动再试。
            consecutiveFailures++
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                connected = false
                runCatching { oemGuard.restore() }.onFailure(::logFailure)
                scheduleReconnect()
            }
        } finally {
            worker.removeCallbacks(poll)
            if (!stopped && connected) worker.postDelayed(poll, RETRY_DELAY_MILLIS)
        }
    }

    /** Only real iPhone guidance is sent. Changes are throttled; unchanged cards renew every 10 s. */
    private fun pushNavigationInfo() {
        val info = guidance ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastGuidance != null && now - lastGuidanceMillis < 500L) return
        if (info == lastGuidance && now - lastGuidanceMillis < 10_000L) return
        val type = Class.forName(
            "com.ecarx.xui.adaptapi.diminteraction.INaviInteraction\$INavigationInfo", false, naviClass!!.classLoader,
        )
        naviClass!!.getMethod("updateNavigationInfo", type).invoke(navi, info.proxy(type))
        lastGuidance = info
        lastGuidanceMillis = now
        guidanceSentCount++
        // The working BX11 controller releases this gate only after publishing an active TBT card.
        if (!projectionGateReleased) {
            runCatching {
                val field = dim!!.javaClass.getDeclaredField("mPowerOperation").apply { isAccessible = true }
                val power = field.get(if (java.lang.reflect.Modifier.isStatic(field.modifiers)) null else dim)
                    ?: error("BX11 power service unavailable")
                power.javaClass.getMethod("disableScreenProjection", Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType).invoke(power, false, 2)
                projectionGateReleased = true
            }.onFailure { logOnMain("领克仪表：解除投屏门控失败 ${it.cause?.message ?: it.message}") }
        }
    }

    /** 与实车 XSFNaviService 一致：1 为导航成功进行，0 为未导航。 */
    private fun notifyNavigation(active: Boolean) {
        if (active) {
            navigationCleanupPending = true
            saveCleanupState()
        }
        try {
            naviClass!!.getMethod("notifyNavigationStatus", Int::class.javaPrimitiveType).invoke(navi, if (active) 1 else 0)
            lastNotifySuccess = true
        } catch (error: Throwable) {
            lastNotifySuccess = false
            throw error
        }
        sentNavigation = active
        republishNavigation = false
        logOnMain("领克仪表：导航状态=${if (active) "进行中" else "结束"}")
    }

    /** 请求仪表切到地图模式；车机可拒绝，失败只记录，不影响导航状态链路。 */
    private fun switchNaviMode(mode: Int): Boolean {
        return try {
            val accepted = menuClass!!.getMethod("switchNaviMode", Int::class.javaPrimitiveType)
                .invoke(menu, mode) as Boolean
            lastSwitchAccepted = accepted
            logOnMain("领克仪表：请求地图模式 ${if (accepted) "已接受" else "被拒绝"}")
            accepted
        } catch (error: Throwable) {
            lastSwitchAccepted = null
            logOnMain("领克仪表：请求地图模式失败 ${error.cause?.message ?: error.message}")
            false
        }
    }

    /** 原车导航用 setPresentationToDimSwitch 把仪表投影绑定到自己包名；导航期间用本应用包名做同样声明。 */
    private fun claimPresentationSwitch() {
        if (!presentationSwitchOn) {
            projectionCleanupPending = true
            saveCleanupState()
            setPresentationSwitch(true)
        }
    }

    private fun setPresentationSwitch(on: Boolean): Boolean {
        try {
            dimClass!!.getMethod("setPresentationToDimSwitch", Int::class.javaPrimitiveType,
                String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(dim, 1, context.packageName, context.packageName, on)
            presentationSwitchOn = on
            lastPresentationSwitchSuccess = true
            logOnMain("领克仪表：投影声明${if (on) "开启" else "关闭"}成功")
            // 声明后重读投屏选项，观察原车状态是否随之变化。
            presentationOption = runCatching {
                dimClass!!.getMethod("getShowPresentationOption").invoke(dim) as Int
            }.getOrElse { presentationOption }
            return true
        } catch (error: Throwable) {
            lastPresentationSwitchSuccess = false
            logOnMain("领克仪表：投影声明失败 ${error.cause?.message ?: error.message}")
            return false
        }
    }

    /** 原厂导航同时发送 TurnByTurn 开始/结束；失败不影响导航状态链路。 */
    private fun notifyTurnByTurn(started: Boolean): Boolean {
        if (started) {
            turnByTurnCleanupPending = true
            saveCleanupState()
        }
        return runCatching {
            naviClass!!.getMethod(if (started) "notifyTurnByTurnStarted" else "notifyTurnByTurnStopped").invoke(navi)
        }.onFailure { logOnMain("领克仪表：TurnByTurn 通知失败 ${it.cause?.message ?: it.message}") }.isSuccess
    }

    /** 各项独立完成；某项失败不能跳过其他清理，也不能丢失下次连接的补偿责任。 */
    private fun cleanupNavigation() {
        var failure: Throwable? = null
        fun attempt(action: () -> Unit) {
            runCatching(action).onFailure { if (failure == null) failure = it }
        }
        if (navigationCleanupPending) attempt {
            notifyNavigation(false)
            navigationCleanupPending = false
        }
        if (turnByTurnCleanupPending) attempt {
            check(notifyTurnByTurn(false)) { "仪表 TBT 停止失败" }
            turnByTurnCleanupPending = false
        }
        if (projectionCleanupPending) attempt {
            // 重连会重置本地投影状态，但旧声明仍须显式撤销。
            check(setPresentationSwitch(false)) { "仪表投影释放失败" }
            projectionCleanupPending = false
        }
        saveCleanupState()
        failure?.let { throw it }
    }

    private fun loadCleanupState() {
        if (cleanupLoaded) return
        val pending = cleanupJournal.getInt("pending", 0)
        navigationCleanupPending = pending and CLEANUP_NAVIGATION != 0
        turnByTurnCleanupPending = pending and CLEANUP_TBT != 0
        projectionCleanupPending = pending and CLEANUP_PROJECTION != 0
        recoveringCleanup = pending != 0
        savedCleanup = pending
        cleanupLoaded = true
    }

    /** 先写记录再调用厂商接口；崩溃或关闭重试耗尽后，下次连接继续清理。 */
    private fun saveCleanupState() {
        val pending = (if (navigationCleanupPending) CLEANUP_NAVIGATION else 0) or
            (if (turnByTurnCleanupPending) CLEANUP_TBT else 0) or
            (if (projectionCleanupPending) CLEANUP_PROJECTION else 0)
        if (savedCleanup == pending) return
        check(cleanupJournal.edit().putInt("pending", pending).commit()) { "仪表清理记录保存失败" }
        savedCleanup = pending
    }

    /** 原厂导航注册导航交互回调；回调只记录，不执行任何厂商动作。 */
    private fun registerNaviInteractionCallback() {
        val interaction = navi ?: return
        try {
            val type = Class.forName("com.ecarx.xui.adaptapi.diminteraction.INaviInteraction\$INavigationInteractionCallback")
            naviCallback = callbackProxy(type) { name, _ -> logOnMain("领克仪表：导航交互回调 $name") }
            naviClass!!.getMethod("registerNavigationInteractionCallback", type).invoke(interaction, naviCallback)
            naviCallbackRegistered = true
            logOnMain("领克仪表：已注册导航交互回调")
        } catch (error: Throwable) {
            naviCallbackRegistered = false
            logOnMain("领克仪表：注册导航交互回调失败 ${error.cause?.message ?: error.message}")
        }
    }

    /** 原厂导航注册顶层交互回调；回调只记录，不执行任何厂商动作。 */
    private fun registerInteractionCallback() {
        val interaction = dim ?: return
        try {
            val type = Class.forName("com.ecarx.xui.adaptapi.diminteraction.DimInteraction\$IInteractionCallback")
            interactionCallback = callbackProxy(type) { name, _ -> logOnMain("领克仪表：仪表交互回调 $name") }
            dimClass!!.getMethod("registerInteractionCallback", type).invoke(interaction, interactionCallback)
            interactionCallbackRegistered = true
            logOnMain("领克仪表：已注册仪表交互回调")
        } catch (error: Throwable) {
            interactionCallbackRegistered = false
            logOnMain("领克仪表：注册仪表交互回调失败 ${error.cause?.message ?: error.message}")
        }
    }

    /** 原厂导航通过 updateExtensionInfo 声明期望的仪表显示模式；失败不影响导航状态链路。 */
    private fun updateDimDisplayMode(mode: Int) {
        runCatching {
            val bundle = android.os.Bundle()
            bundle.putInt("EXT_KEY_DIM_DISPLAY_MODE", mode)
            naviClass!!.getMethod("updateExtensionInfo", android.os.Bundle::class.java).invoke(navi, bundle)
        }.onFailure { logOnMain("领克仪表：updateExtensionInfo 失败 ${it.cause?.message ?: it.message}") }
    }

    private fun publish(next: Lynk06ClusterState) {
        if (state != next) {
            state = next
            main.post { if (!stopped) onState(next.copy(navigating = next.navigating && navigationRequested)) }
        }
        captureDiagnostics()
    }

    /** 快照在状态变化点采集；诊断报告可能读取到已关闭链路的最后一次状态。 */
    private fun captureDiagnostics() {
        diagnostics = Lynk06ClusterDiagnostics(
            adapterCreated = dim != null,
            watcherRegistered = watcherRegistered,
            connected = connected,
            menuAttached = menu != null,
            naviAttached = navi != null,
            navigationRequested = navigationRequested,
            navigationSent = sentNavigation,
            navigationCleanupPending = navigationCleanupPending,
            turnByTurnCleanupPending = turnByTurnCleanupPending,
            projectionCleanupPending = projectionCleanupPending,
            lastNotifySuccess = lastNotifySuccess,
            lastSwitchAccepted = lastSwitchAccepted,
            presentationSwitchOn = presentationSwitchOn,
            lastPresentationSwitchSuccess = lastPresentationSwitchSuccess,
            naviCallbackRegistered = naviCallbackRegistered,
            interactionCallbackRegistered = interactionCallbackRegistered,
            presentationOption = presentationOption,
            naviMode = state.mode,
            showMap = state.showMap,
            reconnectAttempts = reconnectAttempts,
            consecutiveFailures = consecutiveFailures,
            lastError = lastError,
            closed = stopped,
        )
    }

    private fun connectionFailed(error: Throwable) {
        connected = false
        connecting = false
        runCatching { oemGuard.restore() }.onFailure(::logFailure)
        worker.removeCallbacks(connectionTimeout)
        worker.removeCallbacks(poll)
        publish(Lynk06ClusterState())
        logFailure(error)
        scheduleReconnect()
    }

    private fun logFailure(error: Throwable) {
        lastError = error.cause?.message ?: error.message ?: error.javaClass.simpleName
        logOnMain("领克仪表：接口调用失败 ${error.cause?.message ?: error.message}")
    }

    private fun scheduleReconnect() {
        if (stopped || connected || connecting) return
        worker.removeCallbacks(poll)
        worker.removeCallbacks(reconnect)
        worker.postDelayed(reconnect, reconnectDelayMillis)
    }

    private fun beginConnection(reset: Boolean) {
        check(watcherRegistered) { "仪表连接 watcher 尚未注册" }
        connecting = true
        worker.removeCallbacks(reconnect)
        worker.removeCallbacks(connectionTimeout)
        val connect = connectClass!!
        if (reset) {
            // 只重连仪表厂商接口；导航通知所有权保留到结束或关闭。
            detachCallbacks()
            runCatching { connect.getMethod("disconnect").invoke(dim) }
        }
        connect.getMethod("connect").invoke(dim)
        worker.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
    }

    private val connectionTimeout = Runnable {
        if (!stopped && connecting && !connected) {
            connecting = false
            runCatching { oemGuard.restore() }.onFailure(::logFailure)
            logOnMain("领克仪表：连接等待超时，稍后重试")
            scheduleReconnect()
        }
    }

    private val reconnect = Runnable {
        if (!stopped && !connected && !connecting) {
            reconnectAttempts++
            reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
            try {
                val connect = connectClass
                if (dim == null || (connect != null && !watcherRegistered)) initialize()
                else if (connect == null) attach()
                else beginConnection(reset = true)
            } catch (error: Throwable) { connectionFailed(error) }
            captureDiagnostics()
        }
    }

    internal companion object {
        private const val CLEANUP_NAVIGATION = 1
        private const val CLEANUP_TBT = 2
        private const val CLEANUP_PROJECTION = 4
        private const val MAX_CLOSE_CLEANUP_ATTEMPTS = 3
        private const val CLOSE_HANDOFF_DELAY_MILLIS = 100L
        private const val RETRY_DELAY_MILLIS = 1000L
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val CONNECTION_TIMEOUT_MILLIS = 4000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 8000L

        /** 保留最近一次链路实例，关闭后仍能读取最后状态。 */
        @Volatile private var activeLink: Lynk06ClusterLink? = null

        /** 领克仪表诊断的 AdaptAPI 链路段；链路未创建时说明可能原因。 */
        fun diagnosticReport(): String {
            val d = activeLink?.diagnostics
                ?: return "linkCreated=false（车型未匹配或仪表地图未开启）"
            return buildString {
                appendLine("linkCreated=true closed=${d.closed}")
                appendLine("adapterCreated=${d.adapterCreated} watcherRegistered=${d.watcherRegistered}")
                appendLine("adapterConnected=${d.connected} menuAttached=${d.menuAttached} naviAttached=${d.naviAttached}")
                appendLine("navigationRequested=${d.navigationRequested} navigationSent=${d.navigationSent} lastNotifySuccess=${d.lastNotifySuccess}")
                appendLine("navigationCleanupPending=${d.navigationCleanupPending} turnByTurnCleanupPending=${d.turnByTurnCleanupPending} projectionCleanupPending=${d.projectionCleanupPending}")
                appendLine("naviMode=${d.naviMode} showMap=${d.showMap} switchFullModeAccepted=${d.lastSwitchAccepted}")
                appendLine("presentationOption=${d.presentationOption} presentationSwitchOn=${d.presentationSwitchOn} lastPresentationSwitchSuccess=${d.lastPresentationSwitchSuccess}")
                appendLine("naviCallbackRegistered=${d.naviCallbackRegistered} interactionCallbackRegistered=${d.interactionCallbackRegistered}")
                appendLine("reconnectAttempts=${d.reconnectAttempts} consecutiveFailures=${d.consecutiveFailures}")
                appendLine("fullNavigationInfoSent=${activeLink?.guidanceSentCount ?: 0} projectionGateReleased=${activeLink?.projectionGateReleased}")
                append("lastError=${d.lastError ?: "none"}")
            }
        }
    }

    private fun logOnMain(message: String) { main.post { if (!stopped) log(message) } }

    private fun detachMenu() {
        callback?.let { previous ->
            runCatching { menuClass?.getMethod("unregisterDimMenuInteractionCallback", previous.javaClass.interfaces[0])?.invoke(menu, previous) }
        }
        callback = null
    }

    private fun detachCallbacks() {
        detachMenu()
        runCatching { naviCallback?.let { naviClass?.getMethod("unregisterNavigationInteractionCallback", it.javaClass.interfaces[0])?.invoke(navi, it) } }
            .onFailure(::logFailure)
        runCatching { interactionCallback?.let { dimClass?.getMethod("unregisterInteractionCallback", it.javaClass.interfaces[0])?.invoke(dim, it) } }
            .onFailure(::logFailure)
        naviCallback = null
        interactionCallback = null
        naviCallbackRegistered = false
        interactionCallbackRegistered = false
    }

    @Synchronized fun close() {
        if (stopped) return
        stopped = true
        worker.removeCallbacksAndMessages(null)
        main.removeCallbacksAndMessages(null)
        worker.post { finishClose(1) }
    }

    private fun finishClose(attempt: Int) {
        if (predecessor?.closeFinished == false) {
            worker.postDelayed({ finishClose(attempt) }, CLOSE_HANDOFF_DELAY_MILLIS)
            return
        }
        val cleanup = runCatching { loadCleanupState(); cleanupNavigation() }
        val restore = runCatching { oemGuard.restore() }
        if ((cleanup.isFailure || restore.isFailure) && attempt < MAX_CLOSE_CLEANUP_ATTEMPTS) {
            captureDiagnostics()
            worker.postDelayed({ finishClose(attempt + 1) }, RETRY_DELAY_MILLIS)
            return
        }
        cleanup.onFailure {
            logFailure(it)
            android.util.Log.e("Lynk06ClusterLink", "仪表清理失败，保留记录供下次连接重试", it)
        }
        restore.onFailure {
            android.util.Log.e("Lynk06ClusterLink", "原车导航恢复失败，保留记录供下次打开重试", it)
        }
        try {
            detachCallbacks()
            runCatching { connectClass?.getMethod("unregisterConnectWatcher")?.invoke(dim) }
            runCatching { connectClass?.getMethod("disconnect")?.invoke(dim) }
        } finally {
            captureDiagnostics()
            closeFinished = true
            thread.quitSafely()
        }
    }

    private fun callbackProxy(type: Class<*>, action: (String, Array<out Any?>?) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Lynk06ClusterCallback"
                else -> {
                    action(method.name, args)
                    // 为基本类型返回安全默认值，避免 DIM 侧拆箱失败。
                    when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        java.lang.Float.TYPE -> 0f
                        java.lang.Double.TYPE -> 0.0
                        java.lang.Byte.TYPE -> 0.toByte()
                        java.lang.Short.TYPE -> 0.toShort()
                        java.lang.Character.TYPE -> 0.toChar()
                        else -> null
                    }
                }
            }
        }
}
