# 剪贴盒 clips

Mac + Android 双端本地剪贴板历史：自动记录复制过的文本，热键呼出搜索，一键复制回。纯本地 JSON 存储，无任何网络权限。

> **状态**：提案中（it-001 待确认，未开工）。规格文档见 [specs/](specs/)。

## 技术栈

Compose Multiplatform（Kotlin）：`composeApp` 单模块，commonMain 共享 domain/data/UI；androidMain 与 desktopMain 各自实现平台能力（剪贴板、磁贴、托盘、全局热键）。

## 构建

> 脚手架搭建后本节回填实测命令。

- Android APK：`./gradlew :composeApp:assembleDebug`
- Mac 桌面运行：`./gradlew :composeApp:run`；打包 `.dmg`：`./gradlew :composeApp:packageDmg`
- 共享逻辑单测（纯 JVM）：`./gradlew :composeApp:desktopTest`

## 环境要求

JDK 17；Android SDK（platform 35 / build-tools 34+）；桌面端仅需 JDK 17。
