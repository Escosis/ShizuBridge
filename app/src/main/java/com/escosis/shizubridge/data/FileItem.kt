package com.escosis.shizubridge.data

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val permission: String,
    val size: Long,
    val owner: String,
    val modifyTime: String
)