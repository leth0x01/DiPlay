package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb

/** 授权界面缺失时优先使用 root；未取得 root 才使用车主已批准的本机 ADB。 */
internal object VpnPreConsent {
    enum class RootAccess { UNAVAILABLE, COMMAND_SUCCEEDED, COMMAND_FAILED }
    data class Result(val root: RootAccess, val adb: LocalAdb.Access? = null)
    data class CommandResult(val exitCode: Int, val output: String)

    fun grant(
        context: Context,
        rootGrant: () -> RootAccess = { grantRoot(context.packageName) },
        adbGrant: () -> LocalAdb.Access = {
            LocalAdb(AdbKeys.load(context)).use {
                val access = it.connect(mayAsk = false)
                if (access == LocalAdb.Access.READY) {
                    it.shell("appops set ${context.packageName} ACTIVATE_VPN allow")
                }
                access
            }
        },
    ): Result {
        val root = rootGrant()
        return Result(root, if (root == RootAccess.UNAVAILABLE) adbGrant() else null)
    }

    fun grantRoot(packageName: String, runCommand: (String) -> CommandResult? = ::runRootCommand): RootAccess {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
        val probe = runCommand("id -u")
        if (probe?.exitCode != 0 || probe.output.trim() != "0") return RootAccess.UNAVAILABLE
        val result = runCommand("appops set $packageName ACTIVATE_VPN allow")
        // 命令成功不等于系统已授权；调用方仍必须通过 VpnService.prepare() 验证。
        return if (result?.exitCode == 0) RootAccess.COMMAND_SUCCEEDED else RootAccess.COMMAND_FAILED
    }

    private fun runRootCommand(command: String): CommandResult? {
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (_: java.io.IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        return try {
            val deadline = System.nanoTime() + ROOT_TIMEOUT_MILLIS * 1_000_000L
            // 使用 exitValue 轮询以兼容 API 25，并限制 root 管理器等待时间。
            var exitCode: Int? = null
            while (exitCode == null && System.nanoTime() < deadline) {
                exitCode = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exitCode == null) Thread.sleep(50)
            }
            if (exitCode == null) null else {
                // 只读已到达的少量输出，避免 su 子进程持有管道导致额外阻塞。
                val buffer = ByteArray(process.inputStream.available().coerceIn(0, 64))
                val count = if (buffer.isEmpty()) 0 else process.inputStream.read(buffer)
                val output = if (count > 0) String(buffer, 0, count, Charsets.UTF_8) else ""
                CommandResult(exitCode, output)
            }
        } catch (_: java.io.IOException) {
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    private const val ROOT_TIMEOUT_MILLIS = 15_000L
}
