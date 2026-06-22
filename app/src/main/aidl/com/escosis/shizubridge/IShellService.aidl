// IShellService.aidl
package com.escosis.shizubridge;

interface IShellService {
    /**
     * 执行Shell命令，返回执行结果
     * @param command 完整命令
     * @return 结果字符串（标准输出+错误输出拼接）
     */
    String execCommand(String command);

    /**
     * 销毁服务
     */
    void destroy();
}