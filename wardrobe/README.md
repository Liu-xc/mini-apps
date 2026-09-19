# 衣橱（Wardrobe）

个人用安卓原生应用：把衣物拍照录入，按品类槽位滑动组合穿搭，一键复制「合成图 + 描述文案」交给能生图的 AI Agent 生成真人穿搭效果图；生成的效果图再录回 App，与单品双向关联——看一件衣服知道它能配哪几套，看一套穿搭知道它由哪些单品组成。支持一台设备多角色（我 / 家人）各自的衣橱，单品与穿搭均可打标签、写评论。

完整产品与技术规格见 [`specs/`](specs/)（建议从 [`00-overview.md`](specs/00-overview.md) 读起）。

## 构建

```bash
# 首次：确保 JDK 17 与 Android SDK(platform 35) 可用，local.properties 指向 SDK
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk（当前版本 it-001，2026-09-19 构建）
```

安装到连接的设备：`./gradlew installDebug` 或 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。

单元测试：`./gradlew test`

## 技术栈速览

Kotlin · Jetpack Compose (Material 3 Expressive) · MVVM + 单向数据流 · Repository + JSON 文件存储（kotlinx.serialization）· Coil · Lottie。详见 [specs/04-architecture.md](specs/04-architecture.md) 与 [specs/06-decisions.md](specs/06-decisions.md)。
