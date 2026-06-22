package com.escosis.shizubridge

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellUserService : IShellService.Stub() {

    override fun execCommand(command: String): String {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            buildString {
                append(stdout.trim())
                if (stderr.isNotBlank()) append("\n[stderr] $stderr")
            }
        }.getOrElse {
            "执行异常: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        /**
         * 绑定远程用户服务
         * @param connection 系统原生 ServiceConnection 回调
         */
        fun bind(connection: ServiceConnection) {
            val serviceArgs = Shizuku.UserServiceArgs(
                ComponentName(
                    "com.escosis.shizubridge",
                    ShellUserService::class.java.name
                )
            )
                .processNameSuffix("shell_service")
                .version(1)
                .daemon(false)
            Shizuku.bindUserService(serviceArgs, connection)
        }
    }
}