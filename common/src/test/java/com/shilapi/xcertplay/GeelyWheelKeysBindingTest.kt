package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import com.geely.lib.oneosapi.input.IInputListener
import com.geely.lib.oneosapi.input.IInputManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class GeelyWheelKeysBindingTest {
    @Before fun verifiedHeadUnit() {
        ReflectionHelpers.setStaticField(Build::class.java, "PRODUCT", "geely_fx11")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "CS11_A4")
    }
    @After fun stop() { GeelyWheelKeys.stop() }

    private class BindingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        var binds = 0
        var unbinds = 0
        var bindResult = true
        lateinit var connection: ServiceConnection
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            binds++
            this.connection = connection
            return bindResult
        }
        override fun unbindService(connection: ServiceConnection) { unbinds++ }
    }

    private fun installService() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).addOrUpdateService(ServiceInfo().apply {
            packageName = "com.geely.inputservice"
            name = "com.geely.inputservice.InputService"
            exported = true
            enabled = true
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.geely.inputservice"
                enabled = true
            }
        })
    }

    @Test fun unavailableServiceNeverBinds() {
        val context = BindingContext()
        GeelyWheelKeys.start(context)
        assertEquals(0, context.binds)
        assertTrue(GeelyWheelKeys.diagnosticReport(context).contains("输入服务不可用"))
    }

    @Test fun mismatchedBinderReleasesBindingAndReportsFallback() {
        installService()
        val context = BindingContext()
        GeelyWheelKeys.start(context)
        context.connection.onServiceConnected(null, Binder().apply { attachInterface(null, "other.contract") })
        assertEquals(1, context.unbinds)
        assertTrue(GeelyWheelKeys.diagnosticReport(context).contains("契约不匹配"))
    }

    @Test fun deniedRegistrationReleasesBindingAndLateCallbackCannotRegister() {
        installService()
        val context = BindingContext()
        var registrations = 0
        val service = object : IInputManager.Stub() {
            override fun interceptKeyCode(keyCode: Int, packageName: String?) = false
            override fun releaseKeyCode(keyCode: Int, packageName: String?) = false
            override fun getControlIndex() = 0
            override fun registerListener(listener: IInputListener?, packageName: String?, keyCodes: IntArray?) {
                registrations++
                throw SecurityException("测试注册拒绝")
            }
            override fun unregisterListener(listener: IInputListener?, packageName: String?) {}
        }
        GeelyWheelKeys.start(context)
        context.connection.onServiceConnected(null, service)
        assertEquals(1, context.unbinds)
        assertTrue(GeelyWheelKeys.diagnosticReport(context).contains("注册输入监听失败"))
        context.connection.onServiceConnected(null, service)
        assertEquals(1, registrations)
    }
}
