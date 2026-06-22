package com.escosis.shizubridge.data

/**
 * Shell命令执行结果封装
 * @param exitCode 退出码，0表示成功
 * @param stdout 标准输出内容
 * @param stderr 错误输出内容
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}