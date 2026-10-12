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
) {
    private val context = context.applicationContext
    private val thread = HandlerThread("lynk06-cluster-link").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var dim: Any? = null
    private var dimClass: Class<*>? = null
    private var menu: Any? = null
    private var menuClass: Class<*>? = null
    private var navi: Any? = null
    private var naviClass: Class<*>? = null
    private var connectClass: Class<*>? = null
    private var callback: Any? = null
    private var connected = false
    private var navigation = false
    private var sentNavigation = false
    private var state = Lynk06ClusterState()

    fun start() { worker.post { initialize() } }
    fun setNavigation(active: Boolean) { worker.post { if (!stopped) { navigation = active; refresh() } } }

    private fun initialize() {
        if (stopped) return
        try {
            val type = Class.forName("com.ecarx.xui.adaptapi.diminteraction.DimInteraction")
            dimClass = type
            dim = type.getMethod("create", Context::class.java).invoke(null, context)
                ?: error("DimInteraction.create 返回空")
            val connect = Class.forName("com.ecarx.xui.adaptapi.binder.IConnectable")
            if (connect.isInstance(dim)) {
                connectClass = connect
                val watcher = Class.forName("com.ecarx.xui.adaptapi.binder.IConnectable\$IConnectWatcher")
                val proxy = callbackProxy(watcher) { name, _ ->
                    worker.post {
                        if (!stopped) {
                            if (name == "onConnected") attach()
                            if (name == "onDisConnected") {
                                connected = false
                                publish(Lynk06ClusterState())
                            }
                        }
                    }
                }
                connect.getMethod("registerConnectWatcher", watcher).invoke(dim, proxy)
                connect.getMethod("connect").invoke(dim)
            } else attach()
        } catch (error: Throwable) { failed(error) }
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
            // 服务重连后重新发布本次导航，停止时仍负责撤销。
            if (navigation) notifyNavigation(true)
            logOnMain("领克仪表：AdaptAPI 已连接")
            worker.removeCallbacks(poll)
            refresh()
            worker.postDelayed(poll, 1000)
        } catch (error: Throwable) { failed(error) }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (stopped || !connected) return
            refresh()
            if (connected) worker.postDelayed(this, 1000)
        }
    }

    private fun refresh() {
        if (stopped) return
        try {
            // 菜单读取失败也不能阻止导航结束通知，避免本次导航状态残留。
            if (!navigation && sentNavigation) notifyNavigation(false)
            if (!connected) return
            if (navigation && !sentNavigation) notifyNavigation(true)
            val mode = menuClass!!.getMethod("getNaviMode").invoke(menu) as Int
            publish(Lynk06ClusterState(true, navigation, mode))
        } catch (error: Throwable) { failed(error) }
    }

    private fun notifyNavigation(active: Boolean) {
        // 与实车 XSFNaviService 一致：1 为导航成功进行，0 为未导航。
        naviClass!!.getMethod("notifyNavigationStatus", Int::class.javaPrimitiveType).invoke(navi, if (active) 1 else 0)
        sentNavigation = active
        logOnMain("领克仪表：导航状态=${if (active) "进行中" else "结束"}")
    }

    private fun publish(next: Lynk06ClusterState) {
        if (state == next) return
        state = next
        main.post { if (!stopped) onState(next) }
    }

    private fun failed(error: Throwable) {
        connected = false
        publish(Lynk06ClusterState())
        logOnMain("领克仪表：接口调用失败 ${error.cause?.message ?: error.message}")
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
