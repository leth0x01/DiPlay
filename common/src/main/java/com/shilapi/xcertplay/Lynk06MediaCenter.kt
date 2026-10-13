package com.shilapi.xcertplay

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** 请求返回与真实控制权回调可能交错；旧请求不能覆盖新回调或关闭状态。 */
internal class Lynk06MediaControl(private val packageName: String) {
    private var revision = 0L
    private var closed = false
    @Volatile var owned = false
        private set
    @Synchronized fun beginRequest(): Long = revision
    @Synchronized fun completeRequest(expectedRevision: Long, accepted: Boolean) {
        if (!closed && revision == expectedRevision) owned = accepted
    }
    @Synchronized fun focusChanged(nextPackage: String?) {
        revision++
        owned = !closed && nextPackage == packageName
    }
    @Synchronized fun disconnected() { revision++; owned = false }
    @Synchronized fun close() { closed = true; revision++; owned = false }
}

/** 本车原车媒体中心客户端；注册与 Binder 调用不占用主线程。 */
internal class Lynk06MediaCenter(
    context: Context,
    initial: CarPlayNowPlaying,
    private val send: (Int) -> Boolean,
    private val log: (String) -> Unit,
) {
    private val context = context.applicationContext
    private val thread = HandlerThread("lynk06-media-center").apply { start() }
    private val worker = Handler(thread.looper)
    @Volatile private var stopped = false
    @Volatile private var nowPlaying = initial
    private val control = Lynk06MediaControl(this.context.packageName)
    private var bound = false
    private var api: Lynk06MediaCenterService? = null
    private var token: IBinder? = null
    private var retryDelay = RETRY_MILLIS
    private val launchIntent = PendingIntent.getActivity(this.context, 0,
        Intent(this.context, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private val playback = Lynk06MusicPlayback(this.context.packageName, launchIntent) { nowPlaying }
    private val client = Lynk06MusicClient(playback, { nowPlaying }, { index ->
        // 失去原车媒体控制权或退出会话后，迟到回调不能操作 iPhone。
        val handled = !stopped && control.owned && send(index)
        log("领克媒体中心：回调指令=$index control=${control.owned} handled=$handled")
        handled
    }, { packageName ->
        control.focusChanged(packageName)
        log("领克媒体中心：媒体控制权=${control.owned}")
    })
    private val publish = Runnable { publishState() }
    private val retry = Runnable { bind() }

    fun start() { worker.post { bind() } }

    fun update(info: CarPlayNowPlaying) {
        val previous = nowPlaying
        nowPlaying = info
        if (stopped) return
        worker.removeCallbacks(publish)
        worker.post(publish)
        if (!previous.playing && info.playing) worker.post {
            if (!stopped && !control.owned) claimControl()
        }
    }

    fun close() {
        // 先同步关闭回调门控，再在 worker 上注销，避免阻塞媒体键锁。
        if (stopped) return
        stopped = true
        control.close()
        worker.removeCallbacksAndMessages(null)
        worker.post {
            val registered = token
            val service = api
            if (registered != null && service != null) runCatching { service.unregister(registered) }
                .onSuccess { log("领克媒体中心：注销=$it") }
                .onFailure { logFailure("注销", it) }
            token = null
            api = null
            unbind()
            thread.quitSafely()
        }
    }

    private fun bind() {
        if (stopped || bound) return
        runCatching {
            val component = ComponentName(PACKAGE, "$PACKAGE.MediaCenterService")
            val service = context.packageManager.getServiceInfo(component, 0)
            check(service.enabled && service.applicationInfo.enabled && service.exported) { "原车媒体中心不可绑定" }
            bound = context.bindService(Intent(ACTION).setComponent(component), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "原车媒体中心拒绝绑定" }
            log("领克媒体中心：等待服务连接")
        }.onFailure { logFailure("绑定", it); scheduleRetry() }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            worker.post {
                if (stopped) return@post
                runCatching {
                    val next = Lynk06MediaCenterService(checkNotNull(service))
                    api = next
                    token = checkNotNull(next.register(client)) { "原车媒体中心未返回注册令牌" }
                    retryDelay = RETRY_MILLIS
                    next.declareWheelControl(token!!)
                    next.updateSources(token!!)
                    log("领克媒体中心：音乐客户端注册成功")
                    claimControl()
                }.onFailure { logFailure("注册", it); disconnect() }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { worker.post { disconnect() } }
        override fun onBindingDied(name: ComponentName?) { worker.post { disconnect() } }
        override fun onNullBinding(name: ComponentName?) { worker.post { disconnect() } }
    }

    private fun claimControl() {
        val service = api ?: return
        val registered = token ?: return
        if (stopped) return
        val revision = control.beginRequest()
        runCatching { service.requestControl(registered) }.onSuccess { accepted ->
            // 即时返回不保证另有回调；真实控制权变更优先于请求返回值。
            control.completeRequest(revision, accepted)
            log("领克媒体中心：请求媒体控制权 accepted=$accepted control=${control.owned}")
            if (accepted) publishState()
        }.onFailure { logFailure("请求媒体控制权", it); disconnect() }
    }

    private fun publishState() {
        if (stopped) return
        val service = api ?: return
        val registered = token ?: return
        runCatching {
            check(service.updatePlayback(registered, playback)) { "原车媒体中心未接受播放状态" }
            service.updateProgress(registered, nowPlaying.elapsedMillis ?: 0)
        }.onFailure { logFailure("同步播放状态", it); disconnect() }
    }

    private fun disconnect() {
        control.disconnected()
        val registered = token
        if (registered != null) runCatching { api?.unregister(registered) }
        token = null
        api = null
        unbind()
        scheduleRetry()
    }

    private fun unbind() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
    }

    private fun scheduleRetry() {
        if (stopped) return
        worker.removeCallbacks(retry)
        worker.postDelayed(retry, retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(30_000)
    }

    private fun logFailure(stage: String, failure: Throwable) {
        log("领克媒体中心：$stage 失败 ${failure.javaClass.simpleName} ${failure.message.orEmpty()}")
    }

    companion object {
        private const val PACKAGE = "ecarx.xsf.mediacenter"
        private const val ACTION = "ecarx.xsf.MEDIA_CENTER_SERVICE"
        private const val RETRY_MILLIS = 3_000L
    }
}
