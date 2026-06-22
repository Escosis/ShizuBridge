package com.escosis.shizubridge.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object ImportHelper {

    // 外部存储缓存目录：Shell用户可直接访问
    private fun getTempDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "import_temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 将Uri文件复制到外部公共缓存目录，返回缓存文件绝对路径
     */
    fun copyUriToPublicCache(context: Context, uri: Uri, fileName: String): String {
        val tempFile = File(getTempDir(context), fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 8 * 1024 * 1024)
            }
        }
        return tempFile.absolutePath
    }

    /**
     * 清理所有临时缓存文件
     */
    fun clearTempCache(context: Context) {
        getTempDir(context).listFiles()?.forEach { it.delete() }
    }
}