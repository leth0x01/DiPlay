package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.lang.reflect.Proxy

/** 本车 AdapterAPI 1.1.18.9 的仪表模式；显示器始终存在，不能作为模式判断。 */
internal data class Lynk06ClusterState(val available: Boolean = false, val navigating: Boolean = false, val mode: Int? = null) {
    val showMap: Boolean get() = available && navigating && mode == FULL
    companion object { const val FULL = 3 }
}

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
    private var connected = false
    private var connecting = false
    private var reconnectDelayMillis = RETRY_DELAY_MILLIS
    private var consecutiveFailures = 0
    private var navigation = false
    private var sentNavigation = false
    private var republishNavigation = false
    private var state = Lynk06ClusterState()

    fun start() { worker.post { initialize() } }
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
            connected = true
            connecting = false
            worker.removeCallbacks(connectionTimeout)
            reconnectDelayMillis = RETRY_DELAY_MILLIS
            consecutiveFailures = 0
            // 服务重连后重新发布本次导航，停止时仍负责撤销。
            worker.removeCallbacks(reconnect)
            republishNavigation = navigation
            logOnMain("领克仪表：AdaptAPI 已连接")
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
            if (!navigation && sentNavigation) notifyNavigation(false)
            if (!connected) return
            if (navigation && (!sentNavigation || republishNavigation)) notifyNavigation(true)
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
                scheduleReconnect()
            }
        } finally {
            worker.removeCallbacks(poll)
            if (!stopped && connected) worker.postDelayed(poll, RETRY_DELAY_MILLIS)
        }
    }

    private fun notifyNavigation(active: Boolean) {
        // 与实车 XSFNaviService 一致：1 为导航成功进行，0 为未导航。
        naviClass!!.getMethod("notifyNavigationStatus", Int::class.javaPrimitiveType).invoke(navi, if (active) 1 else 0)
        sentNavigation = active
        republishNavigation = false
        logOnMain("领克仪表：导航状态=${if (active) "进行中" else "结束"}")
    }

    private fun publish(next: Lynk06ClusterState) {
        if (state == next) return
        state = next
        main.post { if (!stopped) onState(next) }
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
            reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
            try {
                val connect = connectClass
                if (dim == null || (connect != null && !watcherRegistered)) initialize()
                else if (connect == null) attach()
                else beginConnection(reset = true)
            } catch (error: Throwable) { connectionFailed(error) }
        }
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 1000L
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val CONNECTION_TIMEOUT_MILLIS = 4000L
        const val MAX_RECONNECT_DELAY_MILLIS = 8000L
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
                if (sentNavigation) runCatching { notifyNavigation(false) }
                detachMenu()
                runCatching { connectClass?.getMethod("unregisterConnectWatcher")?.invoke(dim) }
                runCatching { connectClass?.getMethod("disconnect")?.invoke(dim) }
            } finally { thread.quitSafely() }
        }
    }

    private fun callbackProxy(type: Class<*>, action: (String, Array<out Any?>?) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Lynk06ClusterCallback"
                else -> { action(method.name, args); null }
            }
        }
}
