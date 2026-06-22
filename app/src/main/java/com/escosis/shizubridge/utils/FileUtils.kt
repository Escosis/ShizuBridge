package com.escosis.shizubridge.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    // 临时缓存目录名
    private const val TEMP_DIR_NAME = "import_temp"

    /**
     * 从Uri获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown_file"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        return name
    }

    /**
     * 将Uri文件复制到应用内部缓存目录，返回缓存后的文件
     */
    fun copyUriToCache(context: Context, uri: Uri): File {
        val fileName = getFileName(context, uri)
        val tempDir = File(context.cacheDir, TEMP_DIR_NAME)
        tempDir.mkdirs()
        val targetFile = File(tempDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output, bufferSize = 8 * 1024 * 1024)
            }
        }
        return targetFile
    }

    /**
     * 清理临时缓存目录
     */
    fun clearTempCache(context: Context) {
        val tempDir = File(context.cacheDir, TEMP_DIR_NAME)
        if (tempDir.exists() && tempDir.isDirectory) {
            tempDir.listFiles()?.forEach { it.delete() }
        }
    }
}