# 拾彩 lottery

> 情绪价值彩票 demo——错过直播的开奖，在这里用一场动画补回来：传一张图片当种子生成
> 双色球/大乐透号码（支持复式）、存进票夹，开奖日进入全屏剧场复现摇奖全过程，顺手验票，
> 票面一键导出成图去店里照着买。开奖数据本期为本地确定性演示数据（界面全程「演示数据 · 非官方」标注）。
> 定位与价值流见 [specs/00-overview.md](specs/00-overview.md)；迭代状态见
> [specs/iterations/](specs/iterations/)。

## 界面速览

| [W1 选号](specs/iterations/assets-it-001/01-generate-initial.png) | [W2 开奖复现](specs/iterations/assets-it-001/08-draw-screen.png) | [复现剧场](specs/iterations/assets-it-001/11-replay-verdict.png) | [票详情·导出](specs/iterations/assets-it-001/13-detail.png) |
|---|---|---|---|
| 玩法/单式复式 + 图片种子 + 生成/换批/存票 | 期号选择 + 诚实标注 + 一注复现 | 摇奖机物理翻滚 → 逐球出槽 → 验票揭晓 | 票面 + 开奖对照 + 导出 PNG 入相册 |

走查截图全集（15 张，含摇奖机/篮球/验票/reduce-motion 直落）在
[specs/iterations/assets-it-001/](specs/iterations/assets-it-001/)。

## 构建

```bash
cd lottery
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # 生成/奖级/日历/存储 JVM 单测
```

- 独立 Gradle 工程（与 wardrobe/eats/darkroom 同构），版本表引用仓库根 `gradle/libs.versions.toml`。
- minSdk 31 / target 35 / Kotlin 2.1.21 / Compose BOM 2025.06.01 / M3 1.4.0；零第三方依赖。
- 分发：APK 不入 git（根 `.gitignore`），走 `~/Documents/mini-apps-apk` 或本地 http（见根 README）。

## 模拟器（设备分治）

拾彩独占 `lottery_*` AVD（主用 `lottery_qa`），与其他应用各开各的、互不抢前台：

```bash
tools/emu.sh up        # 启动/复用拾彩专属模拟器，打印序列号
tools/emu.sh install   # assembleDebug 并安装到本应用设备
tools/emu.sh launch    # 拉起应用；走查卡住用 restart 复位
tools/emu.sh cap       # 截图（uadump 出无障碍树）
tools/emu.sh serial    # 打印序列号，裸 adb 用 adb -s $(tools/emu.sh serial) ...
```

序列号按 AVD 名解析、不硬编码 `emulator-端口号`（端口随启停漂移）；多设备在线时不要裸用
`adb install` / `./gradlew installDebug`。约定见 [specs/it-002](../specs/iterations/it-002-emu-device-split.md)。

## 架构一句话

`ImageSeed`（图片→SHA-256 指纹）→ `Generator`（指纹+玩法+批次 → SplitMix64 确定性号码）→
`TicketStore`（票夹 JSON 原子写）；开奖侧 `IssueCalendar`（开奖日历/期号）→ `DrawRepository`
（本期 `DemoDrawRepository` 确定性演示结果，留真实 API 插槽）→ `Verify`（官方奖级表逐注判定）→
`ReplayOverlay`（直播式剧场：2.5D 摇奖机舞台→出球轨道→大号读数→换机→验票；
  ui/draw3d 留 Filament 真三维实现，ADR-006）。
详见 [specs/04-architecture.md](specs/04-architecture.md)。

## 关键决策

ADR-001 平台安卓原生 Kotlin+Compose · ADR-002 渲染用 Compose Canvas+手写物理（不引 3D/Lottie）·
ADR-003 开奖数据本地确定性演示、留真实 API 插槽 · ADR-004 官方奖级表逐注判定 ·
ADR-005 票夹 JSON 单文件原子写（复用 libs/store 模式、不上 composite）·
ADR-006 剧场接入 Filament、默认 Canvas 2.5D 舞台交付（模拟器原生层不可验收，真机可切）。
详见 [specs/06-decisions.md](specs/06-decisions.md)。
