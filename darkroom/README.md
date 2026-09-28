# 显影 darkroom

> 拍立得显影工具——传一张图，看它像相纸一样慢慢显影，定影成带灰阶印字的相纸卡片，
> 一键导出成片或**显影过程视频**（含合成声轨），随时分享。全程离线。
> 定位与价值流见 [specs/00-overview.md](specs/00-overview.md)；迭代状态见
> [specs/iterations/](specs/iterations/)。

## 界面速览

| [W1 选图](specs/iterations/assets-it-006/darkroom-01-w1-light.png) | [W2 显影台](specs/iterations/assets-it-006/darkroom-02-w2-light.png) | [W3 成片](specs/iterations/assets-it-006/darkroom-03-w3-light.png) | [W4 设置](specs/iterations/assets-it-006/darkroom-04-w4-light.png) |
|---|---|---|---|
| 相册/拍照/离线照片样片 | 药膜扩散+阶段文案+药水条 | 五种照片风格+卡面编辑+1:1/4:5/9:16 图与视频分享 | 速度/甩一甩/水印 |

显影关键帧和竖屏故事预览也收在 `specs/iterations/assets-it-004/`；W3 五种成片风格与同一预览构图同步到图片、视频导出。
it-006 的浅色/深色四屏截图在 `specs/iterations/assets-it-006/`；卡面作品标题最多两行，界面与卡面印字采用黑白灰，照片保留所选影调。

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
`ChemicalDiffusion`（稳定药膜遮罩）→ 预览 `DevelopCard`（Compose）/ 位图与分享预览 `PhotoCardPainter` /
视频 `VideoExporter`（EGL+MediaCodec 逐帧 + SfxSynth 声轨）。`ShareLayout` 统一三种画幅的卡片位置与安全区。
详见 [specs/04-architecture.md](specs/04-architecture.md)。

## 关键决策

ADR-001 minSdk 31 · ADR-002 App 内零音效、声轨只入视频 · ADR-003 EGL 输入面编码 ·
ADR-004 模糊近似（native 端缩小放大）· ADR-005 确定性优先 · ADR-006 药膜扩散与分享构图共用纯函数。
详见 [specs/06-decisions.md](specs/06-decisions.md)。
