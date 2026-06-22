package com.escosis.shizubridge.utils

import com.escosis.shizubridge.data.FileItem

object FileParser {

    /**
     * 解析 ls -la 命令输出为文件列表
     * 兼容Android系统ISO日期格式，支持带空格的文件名
     */
    fun parseLsOutput(dirPath: String, lsOutput: String): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val lines = lsOutput.lines().filter { it.isNotBlank() }

        for (line in lines) {
            // 跳过总用量统计行
            if (line.startsWith("total ")) continue

            // 按空白字符分割所有字段
            val parts = line.split("\\s+".toRegex())
            // 最少需要8个字段：权限、链接数、所有者、用户组、大小、日期、时间、文件名
            if (parts.size < 8) continue

            val permission = parts[0]
            val isDir = permission.startsWith('d')
            val owner = parts[2]
            val size = parts[4].toLongOrNull() ?: 0L
            // 拼接日期+时间
            val modifyTime = "${parts[5]} ${parts[6]}"
            // 文件名：从第7个索引开始拼接所有剩余内容，兼容带空格的文件名
            val fileName = parts.drop(7).joinToString(" ")

            // 过滤当前目录和上级目录
            if (fileName == "." || fileName == "..") continue

            list.add(
                FileItem(
                    name = fileName,
                    path = "$dirPath/$fileName",
                    isDirectory = isDir,
                    permission = permission,
                    size = size,
                    owner = owner,
                    modifyTime = modifyTime
                )
            )
        }

        // 排序规则：文件夹置顶，文件按名称字母升序
        list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        return list
    }
}