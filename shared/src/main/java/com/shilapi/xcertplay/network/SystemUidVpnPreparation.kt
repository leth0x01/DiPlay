package com.shilapi.xcertplay.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.util.Log

/** Android 9 compares prepared VPN UIDs, so UID 1000 aliases the initial Legacy VPN owner. */
internal object SystemUidVpnPreparation {
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val TAG = "xcertplay-usb"

    fun repair(context: Context): Boolean {
        if (Build.VERSION.SDK_INT != 28 || Process.myUid() != 1000 ||
            context.checkSelfPermission("android.permission.CONTROL_VPN") != PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            // Only recover an unestablished VPN. Never replace an existing tunnel or always-on VPN.
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val service = ConnectivityManager::class.java.getDeclaredField("mService").apply {
                isAccessible = true
            }.get(manager)
            val api = Class.forName("android.net.IConnectivityManager")
            val userId = 0 // UID 1000 belongs to the owner user.
            val config = api.getMethod("getVpnConfig", Int::class.javaPrimitiveType).invoke(service, userId)
            val alwaysOn = api.getMethod("getAlwaysOnVpnPackage", Int::class.javaPrimitiveType)
                .invoke(service, userId)
            if (config != null || alwaysOn != null) return false
            if (context.packageManager.getApplicationInfo(SHELL_PACKAGE, 0).uid != 2000) return false
            val prepare = api.getMethod("prepareVpn", String::class.java, String::class.java,
                Int::class.javaPrimitiveType)
            val repaired = rebind(context.packageName) { target ->
                prepare.invoke(service, null, target, userId) == true
            }
            Log.i(TAG, "wired system UID VPN ownership repair completed=$repaired")
            repaired
        } catch (error: Exception) {
            Log.w(TAG, "wired system UID VPN ownership repair failed", error)
            false
        }
    }

    internal fun rebind(packageName: String, prepare: (String) -> Boolean): Boolean {
        // A different UID is necessary: prepareVpn(null, ownPackage) alone is a no-op for UID 1000.
        // Shell is only an intermediate owner; no shell authorization or tunnel is created.
        val switched = try { prepare(SHELL_PACKAGE) } finally {
            // Restore our package even if the intermediate binder call throws after switching.
            // The normal establish call still enforces this package's existing VPN consent.
            if (!prepare(packageName)) throw IllegalStateException("VPN owner restoration rejected")
        }
        return switched
    }
}
