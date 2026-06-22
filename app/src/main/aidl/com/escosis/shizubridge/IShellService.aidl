package com.escosis.shizubridge;

interface IShellService {
    // 执行Shell命令，返回 [退出码, 标准输出, 错误输出]
    String[] exec(String command);
    void destroy();
}