# 剪贴盒 clips

Mac + Android 双端本地剪贴板历史：自动/半自动记录复制过的文本，快速呼出搜索，一键复制回。纯本地 JSON 存储，无任何网络权限。

> **状态**：提案中（it-001 待确认，未开工）。规格文档见 [specs/](specs/)。

## 结构与技术栈

双端**分开实现、各自原生**（ADR-001），共享的不是代码而是规格与数据格式——`clips.json` schema 双端一致，一端导出另一端可导入（ADR-009）：

- [`mac/`](mac/) — SwiftUI + AppKit 原生菜单栏应用（macOS 13+）：后台监听剪贴板、⌘⇧V 全局热键、可选自动粘贴、开机自启
- [`android/`](android/) — Kotlin + Compose 安卓应用（与 wardrobe 同栈）：打开即捕获、快捷设置磁贴、分享存入、复制返回

双端功能按平台取舍，不追求对齐（见 [specs/00-overview.md](specs/00-overview.md)）。

## 构建

> 脚手架搭建后本节回填实测命令。

- Android：`cd android && ./gradlew assembleDebug`（单测 `testDebugUnitTest`）
- Mac：`cd mac && swift build`（单测 `swift test`）；打包 .app 的脚本随后补充

## 环境要求

- Android：JDK 17、Android SDK（platform 35 / build-tools 34+）
- Mac：Xcode Command Line Tools（Swift 5.9+）
