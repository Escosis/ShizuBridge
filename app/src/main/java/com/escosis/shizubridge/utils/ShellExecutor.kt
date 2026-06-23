package com.escosis.shizubridge.utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.escosis.shizubridge.IShellService
import com.escosis.shizubridge.ShellUserService
import com.escosis.shizubridge.data.CommandResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

object ShellExecutor {

    private const val TOTAL_TIMEOUT_MS = 3000L // 连接/操作超时
    private var shellService: IShellService? = null

    private suspend fun ensureService(): Boolean = suspendCancellableCoroutine { cont ->
        shellService?.let {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                shellService = IShellService.Stub.asInterface(service)
                if (cont.isActive) cont.resume(true)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shellService = null
            }
        }

        cont.invokeOnCancellation {
            if (cont.isActive) cont.resume(false)
        }

        runCatching {
            ShellUserService.bind(connection)
        }.getOrElse {
            if (cont.isActive) cont.resume(false)
        }
    }

    // 执行Shell命令（保留）
    suspend fun exec(command: String): CommandResult {
        if (!ShizukuManager.hasPermission()) {
            return CommandResult(-1, "", "未获得Shizuku权限")
        }
        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                if (!ensureService()) return@withTimeout CommandResult(-1, "", "服务绑定失败")
                val result = shellService!!.exec(command)
                val exitCode = result.getOrNull(0)?.toIntOrNull() ?: -1
                CommandResult(exitCode, result.getOrNull(1) ?: "", result.getOrNull(2) ?: "")
            }
        } catch (e: TimeoutCancellationException) {
            resetConnection()
            CommandResult(-2, "", "Shizuku连接超时")
        } catch (e: Exception) {
            resetConnection()
            CommandResult(-1, "", "服务异常: ${e.message}")
        }
    }

    // 打开远程文件只读，返回ParcelFileDescriptor
    suspend fun openFileRead(path: String): ParcelFileDescriptor? {
        if (!ShizukuManager.hasPermission()) return null
        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                if (!ensureService()) return@withTimeout null
                shellService!!.openFileRead(path)
            }
        } catch (e: Exception) {
            resetConnection()
            null
        }
    }

    // 打开远程文件写入，返回ParcelFileDescriptor
    suspend fun openFileWrite(path: String, append: Boolean = false): ParcelFileDescriptor? {
        if (!ShizukuManager.hasPermission()) return null
        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                if (!ensureService()) return@withTimeout null
                shellService!!.openFileWrite(path, append)
            }
        } catch (e: Exception) {
            resetConnection()
            null
        }
    }

    fun resetConnection() {
        runCatching { shellService?.destroy() }
        shellService = null
    }

    fun release() {
        resetConnection()
    }
}