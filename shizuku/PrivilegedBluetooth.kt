package com.example.bluetoothtimer

import java.util.concurrent.TimeUnit

/** Runs as shell through Shizuku. Exposes only Bluetooth disable, never arbitrary commands. */
class PrivilegedBluetooth : IBluetoothOff.Stub() {
    override fun destroy() { kotlin.system.exitProcess(0) }
    private fun command(vararg args: String): Boolean {
        val p = ProcessBuilder(*args).redirectErrorStream(true)
            .redirectOutput(java.io.File("/dev/null")).start()
        if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return false }
        return p.exitValue() == 0
    }
    override fun disable(): String {
        return try {
            if (!command("/system/bin/cmd", "bluetooth_manager", "disable"))
                "关闭失败：系统拒绝蓝牙关闭命令"
            else if (command("/system/bin/cmd", "bluetooth_manager", "wait-for-state:STATE_OFF"))
                "蓝牙已关闭"
            else "关闭请求已发送，但未能确认状态"
        } catch (e: Exception) { "关闭失败：" + e.javaClass.simpleName }
    }
}
