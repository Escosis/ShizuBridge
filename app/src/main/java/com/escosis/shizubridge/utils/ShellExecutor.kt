package com.escosis.shizubridge.utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.escosis.shizubridge.IShellService
import com.escosis.shizubridge.ShellUserService
import com.escosis.shizubridge.data.CommandResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ShellExecutor {

    private var shellService: IShellService? = null

    private suspend fun ensureService(): Boolean = suspendCancellableCoroutine { cont ->
        shellService?.let {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        ShellUserService.bind(object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                shellService = IShellService.Stub.asInterface(service)
                cont.resume(true)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shellService = null
            }
        })
    }

    /**
     * 执行Shell命令，返回结构化结果
     */
    suspend fun exec(command: String): CommandResult {
        if (!ShizukuManager.hasPermission()) {
            return CommandResult(-1, "", "未获得Shizuku权限")
        }
        if (!ensureService()) {
            return CommandResult(-1, "", "服务绑定失败")
        }
        return runCatching {
            val result = shellService!!.exec(command)
            val exitCode = result.getOrNull(0)?.toIntOrNull() ?: -1
            CommandResult(exitCode, result.getOrNull(1) ?: "", result.getOrNull(2) ?: "")
        }.getOrElse {
            shellService = null
            CommandResult(-1, "", "服务异常: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    fun release() {
        runCatching { shellService?.destroy() }
        shellService = null
    }
}