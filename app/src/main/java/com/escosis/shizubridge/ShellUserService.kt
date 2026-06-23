package com.escosis.shizubridge

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.ParcelFileDescriptor
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
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

    override fun openFileRead(path: String): ParcelFileDescriptor? {
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
    }

    override fun openFileWrite(path: String, append: Boolean): ParcelFileDescriptor? {
        return runCatching {
            val file = File(path)
            // 确保父目录存在
            file.parentFile?.mkdirs()
            val mode = if (append) {
                ParcelFileDescriptor.MODE_APPEND
            } else {
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            }
            ParcelFileDescriptor.open(file, mode)
        }.getOrNull()
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
                .version(6) // 版本号+1，强制重建服务以加载新接口
                .daemon(false)
            Shizuku.bindUserService(serviceArgs, connection)
        }
    }
}