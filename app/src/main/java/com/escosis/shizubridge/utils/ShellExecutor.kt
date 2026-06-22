package com.escosis.shizubridge.utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.escosis.shizubridge.IShellService
import com.escosis.shizubridge.ShellUserService
import com.escosis.shizubridge.data.CommandResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ShellExecutor {

    // 全程超时：绑定服务+执行命令总计3秒
    private const val TOTAL_TIMEOUT_MS = 1000L
    private var shellService: IShellService? = null

    /**
     * 绑定服务，带取消处理，不会挂死
     */
    private suspend fun ensureService(): Boolean = suspendCancellableCoroutine { cont ->
        // 已有连接直接返回
        shellService?.let {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                shellService = IShellService.Stub.asInterface(service)
                if (cont.isActive) {
                    cont.resume(true)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shellService = null
            }
        }

        // 协程取消时直接返回失败，避免永久挂起
        cont.invokeOnCancellation {
            if (cont.isActive) {
                cont.resume(false)
            }
        }

        // 发起绑定，失败立即返回
        runCatching {
            ShellUserService.bind(connection)
        }.getOrElse {
            if (cont.isActive) {
                cont.resume(false)
            }
        }
    }

    /**
     * 执行Shell命令，全程3秒超时，超时必返回
     * 退出码：0成功 / -1通用错误 / -2连接超时
     */
    suspend fun exec(command: String): CommandResult {
        if (!ShizukuManager.hasPermission()) {
            return CommandResult(-1, "", "未获得Shizuku权限")
        }

        return try {
            // 绑定服务+执行命令 全程纳入超时
            withTimeout(TOTAL_TIMEOUT_MS) {
                if (!ensureService()) {
                    return@withTimeout CommandResult(-1, "", "服务绑定失败")
                }
                val result = shellService!!.exec(command)
                val exitCode = result.getOrNull(0)?.toIntOrNull() ?: -1
                CommandResult(exitCode, result.getOrNull(1) ?: "", result.getOrNull(2) ?: "")
            }
        } catch (e: TimeoutCancellationException) {
            // 超时：强制重置所有连接
            resetConnection()
            CommandResult(-2, "", "Shizuku连接超时，请重新授权")
        } catch (e: Exception) {
            // 所有异常都重置连接
            resetConnection()
            CommandResult(-1, "", "服务异常: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 强制重置连接，下次执行重新绑定
     */
    fun resetConnection() {
        runCatching { shellService?.destroy() }
        shellService = null
    }

    fun release() {
        resetConnection()
    }
}