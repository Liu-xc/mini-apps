# 显影 darkroom

> 拍立得显影工具——传一张图，看它像相纸一样慢慢显影，定影成带白框与日期章的卡片，
> 一键导出成片或**显影过程视频**（含合成声轨），随时分享。全程离线。
> 定位与价值流见 [specs/00-overview.md](specs/00-overview.md)；迭代状态见
> [specs/iterations/](specs/iterations/)。

## 界面速览

（待 it-001 验证记录回填时补图）

| W1 选图 | W2 显影台 | W3 成片 | W4 设置 |
|---|---|---|---|
| 相册/拍照/示例三入口 | 三阶段显影+药水条+甩一甩 | 卡面编辑+图/视频双导出 | 速度/甩一甩/水印 |

## 构建

```bash
cd darkroom
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # 引擎层 JVM 单测
```

- 独立 Gradle 工程（与 wardrobe/eats 同构），版本表引用仓库根 `gradle/libs.versions.toml`。
- minSdk 31 / target 35 / Kotlin 2.1.21 / Compose BOM 2025.06.01 / M3 1.4.0；零第三方依赖。
- 分发：APK 不入 git，走 `~/Documents/mini-apps-apk`（见根 README）。

## 架构一句话

`DevelopClock`（唯一进度真源）→ `DevelopSpec.visualAt`（确定性画面映射）→
三渲染端共用：预览 `DevelopCard`（Compose）/ 位图 `PhotoCardPainter` /
视频 `VideoExporter`（EGL+MediaCodec 逐帧 + SfxSynth 声轨）。详见 [specs/04-architecture.md](specs/04-architecture.md)。

## 关键决策

ADR-001 minSdk 31 · ADR-002 App 内零音效、声轨只入视频 · ADR-003 EGL 输入面编码 ·
ADR-004 模糊近似（native 端缩小放大）· ADR-005 确定性优先。详见 [specs/06-decisions.md](specs/06-decisions.md)。
