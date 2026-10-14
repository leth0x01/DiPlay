package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

/** Journal before disabling: a crash must leave enough information to restore the exact state. */
internal class Lynk06OemNavigationLease(private val backend: Backend) {
    interface Backend {
        fun state(): Int
        fun setState(state: Int)
        fun saved(): Int?
        fun save(state: Int)
        fun clear()
        fun wake()
    }
    private var owned = false

    fun pause(): Boolean {
        if (owned) return true
        if (backend.saved() != null) restore()
        val original = backend.state()
        // Respect another app's or the user's pre-existing disable; do not claim it.
        if (original !in 0..1) return false
        backend.save(original)
        backend.setState(2)
        check(backend.state() == 2) { "OEM navigation disable was not applied" }
        owned = true
        return true
    }

    fun restore() {
        val original = backend.saved() ?: return
        try {
            val current = backend.state()
            if (current == 2) {
                backend.setState(original)
                check(backend.state() == original) { "OEM navigation restore was not applied" }
            }
            // Wake before deleting the journal, so a failure is retried on the next opening.
            if (backend.state() in 0..1) backend.wake()
            backend.clear()
        } finally {
            owned = false
        }
    }
}

/** This path is available only in the explicitly platform-signed BX11 build, on Android user 0. */
internal class Lynk06OemNavigationGuard(context: Context, private val log: (String) -> Unit) {
    private val app = context.applicationContext
    private val enabled = eligible(app)
    private val lease = Lynk06OemNavigationLease(AndroidBackend(app))
    private var loggedUnavailable = false
    private var paused = false
    private var loggedExternalDisable = false

    fun pause() = synchronized(lock) {
        if (!enabled) {
            if (!loggedUnavailable) {
                loggedUnavailable = true
                log("领克原车导航：未启用系统 UID 构建，跳过服务暂停")
            }
            return@synchronized
        }
        if (owner != null && owner !== this) error("OEM navigation lease already held")
        if (paused) return@synchronized
        if (lease.pause()) {
            owner = this
            paused = true
            log("领克原车导航：已暂停 ecarx.naviservice（原状态已记录）")
        } else if (!loggedExternalDisable) {
            loggedExternalDisable = true
            log("领克原车导航：原包已被外部禁用，本应用不取得暂停所有权、不擅自恢复")
        }
    }

    fun restore() = synchronized(lock) {
        if (!enabled || (owner != null && owner !== this)) return@synchronized
        try { lease.restore() } finally {
            paused = false
            loggedExternalDisable = false
            if (owner === this) owner = null
        }
    }

    private class AndroidBackend(context: Context) : Lynk06OemNavigationLease.Backend {
        private val app = context.applicationContext
        private val prefs = app.getSharedPreferences("lynk06-oem-navigation-lease", Context.MODE_PRIVATE)
        override fun state() = app.packageManager.getApplicationEnabledSetting(PACKAGE)
        override fun setState(state: Int) = app.packageManager.setApplicationEnabledSetting(PACKAGE, state, 0)
        override fun saved(): Int? = if (prefs.contains("original")) prefs.getInt("original", 0) else null
        override fun save(state: Int) { check(prefs.edit().putInt("original", state).commit()) { "OEM lease journal failed" } }
        override fun clear() { check(prefs.edit().remove("original").commit()) { "OEM lease journal cleanup failed" } }
        override fun wake() {
            app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(PACKAGE))
        }
    }

    companion object {
        private const val PACKAGE = "ecarx.naviservice"
        private val lock = Any()
        private var owner: Lynk06OemNavigationGuard? = null

        private fun eligible(context: Context): Boolean = Lynk06ClusterDisplay.supported() &&
            Process.myUid() == 1000 && context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA,
            ).metaData?.getBoolean("lynkplay.clusterSystem", false) == true

        /** Recover only our journal; never enable an untracked disable by the other CarPlay app. */
        fun recoverOnAppOpened(context: Context) {
            if (!eligible(context)) return
            Thread({
                synchronized(lock) {
                    if (owner == null) runCatching { Lynk06OemNavigationLease(AndroidBackend(context)).restore() }
                        .onFailure { android.util.Log.e("Lynk06OemGuard", "OEM navigation recovery failed", it) }
                }
            }, "lynk06-oem-recover").start()
        }
    }
}
