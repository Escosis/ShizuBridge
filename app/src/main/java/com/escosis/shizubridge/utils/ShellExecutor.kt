package com.escosis.shizubridge.utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.escosis.shizubridge.IShellService
import com.escosis.shizubridge.ShellUserService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ShellExecutor {

    private var shellService: IShellService? = null

    /**
     * 执行Shell命令，首次调用自动绑定服务
     */
    suspend fun exec(command: String): String = suspendCancellableCoroutine { cont ->
        if (!ShizukuManager.hasPermission()) {
            cont.resume("错误: 未获得Shizuku权限")
            return@suspendCancellableCoroutine
        }

        // 服务已绑定，直接执行
        shellService?.let { service ->
            cont.resume(
                runCatching { service.execCommand(command) }
                    .getOrElse { "执行异常: ${it.message ?: it.javaClass.simpleName}" }
            )
            return@suspendCancellableCoroutine
        }

        // 首次调用，绑定服务
        ShellUserService.bind(object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = IShellService.Stub.asInterface(service)
                shellService = binder
                cont.resume(
                    runCatching { binder.execCommand(command) }
                        .getOrElse { "执行异常: ${it.message ?: it.javaClass.simpleName}" }
                )
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shellService = null
            }
        })
    }

    /**
     * 释放服务连接
     */
    fun release() {
        runCatching { shellService?.destroy() }
        shellService = null
    }
}