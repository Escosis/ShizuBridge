package com.escosis.shizubridge;

import android.os.ParcelFileDescriptor;

interface IShellService {
    // 执行Shell命令（保留，便于调试）
    String[] exec(String command);
    void destroy();

    // 打开文件只读，返回ParcelFileDescriptor
    ParcelFileDescriptor openFileRead(String path);
    // 打开文件写入，append为true时追加，否则覆盖
    ParcelFileDescriptor openFileWrite(String path, boolean append);
}