package com.escosis.shizubridge

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellUserService : IShellService.Stub() {

    override fun exec(command: String): Array<String> {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exitCode = process.waitFor().toString()
            arrayOf(exitCode, stdout, stderr)
        }.getOrElse {
            arrayOf("-1", "", "执行异常: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        fun bind(connection: ServiceConnection) {
            val serviceArgs = Shizuku.UserServiceArgs(
                ComponentName(
                    "com.escosis.shizubridge",
                    ShellUserService::class.java.name
                )
            )
                .processNameSuffix("shell_service")
                .version(5) // 版本号+1，强制重建干净的服务进程
                .daemon(false)
            Shizuku.bindUserService(serviceArgs, connection)
        }
    }
}