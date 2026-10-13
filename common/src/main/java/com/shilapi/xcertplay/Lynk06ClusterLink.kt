package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.lang.reflect.Proxy

/** 本车 AdapterAPI 1.1.18.9 的仪表模式；显示器始终存在，不能作为模式判断。 */
internal data class Lynk06ClusterState(val available: Boolean = false, val navigating: Boolean = false, val mode: Int? = null) {
    // 显示跟随真实 CarPlay 导航状态与链路可用性。原车 naviMode 由 OEM 导航生态绑定（领克 06 只认
    // 原厂导航包），第三方无法驱动，只作诊断依据，不再作为显示门控。
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
    private var republishNavigation = false
    private var mapModeRequested = false
    private var presentationSwitchOn = false
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
        activeLink = this
        worker.post { initialize() }
    }
    fun setNavigation(active: Boolean) { worker.post { if (!stopped) { navigation = active; refresh() } } }

    private fun initialize() {
        if (stopped) return
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
            detachMenu()
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
        if (stopped) return
        try {
            // 菜单读取失败也不能阻止导航结束通知，避免本次导航状态残留。
            if (!navigation && sentNavigation) {
                notifyNavigation(false)
                notifyTurnByTurn(false)
                releasePresentationSwitch()
            }
            if (!connected) return
            if (navigation && (!sentNavigation || republishNavigation)) {
                claimPresentationSwitch()
                notifyNavigation(true)
                notifyTurnByTurn(true)
                // 每次导航允许请求一次地图模式；用户手动切回简洁后本次导航不再请求。
                mapModeRequested = false
            }
            var mode = menuClass!!.getMethod("getNaviMode").invoke(menu) as Int
            if (navigation && mode != Lynk06ClusterState.FULL && !mapModeRequested) {
                mapModeRequested = true
                if (switchNaviMode(Lynk06ClusterState.FULL)) {
                    mode = menuClass!!.getMethod("getNaviMode").invoke(menu) as Int
                    updateDimDisplayMode(Lynk06ClusterState.FULL)
                }
            }
            consecutiveFailures = 0
            publish(Lynk06ClusterState(true, navigation, mode))
        } catch (error: Throwable) {
            publish(Lynk06ClusterState())
            logFailure(error)
            // 单次事务异常不代表 Service 断开；保留连接并自动再试。
            consecutiveFailures++
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                connected = false
                scheduleReconnect()
            }
        } finally {
            worker.removeCallbacks(poll)
            if (!stopped && connected) worker.postDelayed(poll, RETRY_DELAY_MILLIS)
        }
    }

    /** 与实车 XSFNaviService 一致：1 为导航成功进行，0 为未导航。 */
    private fun notifyNavigation(active: Boolean) {
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
        if (!presentationSwitchOn) setPresentationSwitch(true)
    }

    private fun releasePresentationSwitch() {
        if (presentationSwitchOn) setPresentationSwitch(false)
    }

    private fun setPresentationSwitch(on: Boolean) {
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
        } catch (error: Throwable) {
            lastPresentationSwitchSuccess = false
            logOnMain("领克仪表：投影声明失败 ${error.cause?.message ?: error.message}")
        }
    }

    /** 原厂导航同时发送 TurnByTurn 开始/结束；失败不影响导航状态链路。 */
    private fun notifyTurnByTurn(started: Boolean) {
        runCatching {
            naviClass!!.getMethod(if (started) "notifyTurnByTurnStarted" else "notifyTurnByTurnStopped").invoke(navi)
        }.onFailure { logOnMain("领克仪表：TurnByTurn 通知失败 ${it.cause?.message ?: it.message}") }
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
            main.post { if (!stopped) onState(next) }
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
            navigationRequested = navigation,
            navigationSent = sentNavigation,
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
            detachMenu()
            runCatching { connect.getMethod("disconnect").invoke(dim) }
        }
        connect.getMethod("connect").invoke(dim)
        worker.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
    }

    private val connectionTimeout = Runnable {
        if (!stopped && connecting && !connected) {
            connecting = false
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
                appendLine("naviMode=${d.naviMode} showMap=${d.showMap} switchFullModeAccepted=${d.lastSwitchAccepted}")
                appendLine("presentationOption=${d.presentationOption} presentationSwitchOn=${d.presentationSwitchOn} lastPresentationSwitchSuccess=${d.lastPresentationSwitchSuccess}")
                appendLine("naviCallbackRegistered=${d.naviCallbackRegistered} interactionCallbackRegistered=${d.interactionCallbackRegistered}")
                appendLine("reconnectAttempts=${d.reconnectAttempts} consecutiveFailures=${d.consecutiveFailures}")
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

    @Synchronized fun close() {
        if (stopped) return
        stopped = true
        worker.removeCallbacksAndMessages(null)
        main.removeCallbacksAndMessages(null)
        worker.post {
            try {
                if (sentNavigation) {
                    runCatching { notifyNavigation(false) }
                    notifyTurnByTurn(false)
                }
                releasePresentationSwitch()
                detachMenu()
                runCatching { naviCallback?.let { naviClass?.getMethod("unregisterNavigationInteractionCallback", it.javaClass.interfaces[0])?.invoke(navi, it) } }
                runCatching { interactionCallback?.let { dimClass?.getMethod("unregisterInteractionCallback", it.javaClass.interfaces[0])?.invoke(dim, it) } }
                runCatching { connectClass?.getMethod("unregisterConnectWatcher")?.invoke(dim) }
                runCatching { connectClass?.getMethod("disconnect")?.invoke(dim) }
            } finally {
                captureDiagnostics()
                thread.quitSafely()
            }
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
