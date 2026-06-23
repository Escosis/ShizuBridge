# ShizuBridge

跨越 Android 多用户隔离的文件传输工具。（**注意：本项目要求与改进均为本人提出，代码由 AI 编写，可能含有各种注释以及无用代码片段，不影响最终功能。**）

## 背景

Android 的多用户机制为每个用户提供了独立的数据空间。机主（User 0）只能访问 `/storage/emulated/0/`，其他用户（如 User 10）只能访问 `/storage/emulated/10/`。系统没有提供任何跨用户的共享目录——即使是机主也无法直接读写其他用户的存储空间。

这意味着，若要在不同用户之间传递文件，常规途径只有三种：

- 使用 USB OTG 或 U 盘
- 使用 SD 卡
- 通过网络上传再下载

这三种方式要么依赖额外硬件，要么依赖网络连接，既不高效也不便捷。

## 原有的手动方案及其局限

在开发 ShizuBridge 之前，我的办法是利用 `/data/local/tmp` 作为中转：

1. 机主通过 MT 管理器（借助 Shizuku）将文件写入 `/data/local/tmp`，并执行 `chmod 777`。
2. 其他用户通过 Mixplorer（同样借助 Shizuku）从 `/data/local/tmp` 读取文件。

这个方案虽然能工作，但存在诸多问题：

- 用户下的 MT 管理器无法访问 `/data/local/tmp`（MT 本身不支持跨用户 Shizuku）。
- 用户下的 Mixplorer 虽然能读取文件，但**无法删除**，授权也频繁失效，需要反复重新授权。

这些痛点促使我借助 AI 开发 ShizuBridge——一个专门为此场景设计的工具。

## ShizuBridge 是什么

ShizuBridge 是一个利用 Shizuku 在 Android 不同用户之间传输文件的工具。它通过 Shizuku 的 `UserService` 机制，以 `shell` 权限直接操作 `/data/local/tmp` 目录，并通过文件描述符（File Descriptor）传递实现跨用户的数据传输。

整个过程**无需 Root、无需网络、无需 USB 外设**。

## 工作原理

### 为何是 `/data/local/tmp`

`/data/local/tmp` 目录属于 `shell` 用户组，其访问不受 Android 用户挂载命名空间的隔离限制——它是一个所有用户共享的物理位置。

Shizuku 服务默认运行在机主（User 0）的 `shell` 上下文中。当其他用户中的应用通过 Shizuku API 请求服务时，该应用同样能获得 `shell` 权限——因为 Shizuku 的授权基于 Binder，与当前用户无关。

### 文件描述符传递

ShizuBridge 通过 Shizuku 的 `UserService` 在 `shell` 上下文中直接打开目标文件的 `ParcelFileDescriptor`，并将其通过 Binder 传递回应用进程：

- **导出**（`/data/local/tmp/ShizuBridge` → 外部存储）：应用通过 Shizuku 获取源文件的只读 FD，通过 `ParcelFileDescriptor.AutoCloseInputStream` 读取数据，再通过 SAF（Storage Access Framework）写入用户指定的位置。
- **导入**（外部存储 → `/data/local/tmp/ShizuBridge`）：应用通过 SAF 读取外部文件，通过 Shizuku 获取目标文件的写入 FD，直接进行流拷贝。

数据流在应用与内核之间直接传递，传输速度接近本地文件复制。

## 使用要求

- Android 7.0（API 24）及以上
- 机主用户和其他用户均已安装并激活 [Shizuku](https://shizuku.rikka.app/)（推荐使用黑阈激活）
- 机主用户和其他用户均已安装 ShizuBridge（注意：仅在其他用户安装 ShizuBridge 是无法正常访问的）
- 在两个用户中分别为 ShizuBridge 授予 Shizuku 权限

## 下载

请前往 [Releases](https://github.com/Escosis/ShizuBridge/releases) 页面下载最新版本。

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku) - 提供核心 API
- [RikkaApps](https://github.com/RikkaApps) - Shizuku 的开发与维护
