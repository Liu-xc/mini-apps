# mini-apps

个人小应用合集仓库。一个应用一个目录，各自独立可构建，互不依赖。

| 应用 | 目录 | 说明 | 状态 |
|---|---|---|---|
| 衣橱 | [`wardrobe/`](wardrobe/) | 安卓原生应用：多角色衣橱管理、滑动组合穿搭、录入去背景、导出生图素材给 AI 生图 Agent、成品效果图回录与单品双向关联、穿搭打卡与衣橱回顾、心愿清单 | 开发中 (it-020) |
| 吃啥 | [`eats/`](eats/) | 安卓原生应用：吃喝玩乐三类记录（堂食/外卖/自做）、地图标记 + 最近一次追踪、卡组快速决策、统计回顾、想吃想玩愿望清单 | 开发中 (it-009) |
| 剪贴盒 | [`clips/`](clips/) | 双端剪贴板历史（Android Kotlin/Compose + macOS SwiftUI），clips.json 文件格式为跨端契约，双端单测对齐 | 提案中（it-001 specs 已备，未开工） |

## 公共基础设施（libs/）

跨应用复用的 SDK，与各应用互不侵入（composite build 接入），同样 specs 先行：

| 库 | 目录 | 说明 | 状态 |
|---|---|---|---|
| 本地存储 SDK | [`libs/store/`](libs/store/) | 快照原子存储（tmp→rename + .bak + 三级恢复 + 迁移链）、SSOT 仓库基类（writeHook）、媒体文件管理 | 0.1.0 已实现，wardrobe / eats 均已接入 |
| 轻同步 SDK | [`libs/sync/`](libs/sync/) | 后端中立契约（SyncValue 七值 / 引擎 / 待推队列 / 错误折叠）+ feishu-bitable 适配器（自动建表、UI 行收编、串行写 + 429 退避） | 0.1.0 已实现，暂未接入应用 |
| 卡组 SDK | [`libs/carddeck/`](libs/carddeck/) | 对 compose-swipeable-cards 的薄封装（CardDeck + CardDeckController.drawRandom 纯随机节奏编排），转盘/穿搭记录翻卡共用 | 0.1.0 已实现，eats / wardrobe 均已接入 |
| 离线抠图 SDK | [`libs/cutout/`](libs/cutout/) | u2netp + ONNX Runtime 端侧抠图（RGBA bytes 进出、纯 JVM 双 runtime、会话惰性 + 空闲释放） | 0.1.0 已实现，wardrobe 已接入（it-016） |

## 仓库约定

- **本仓库是 spec-driven 的**：先写规格文档再写代码，详见 [AGENTS.md](AGENTS.md)。
- 每个应用的规格文档、迭代记录放在 `应用名/specs/` 下，是该应用一切迭代的上下文源。
- 新增应用：在根目录建 `应用名/` 子目录，复制 `wardrobe/specs/` 的文档骨架作为起点。
- 根目录不放任何应用代码；跨应用公共 SDK 放 `libs/`（属基础设施例外，见各库 specs）。

## 素材署名

各应用的应用图标与界面 3D 图标来自 [Thiings](https://www.thiings.co)（免费素材，个人非商业用途）。

## 环境要求

- 安卓应用（衣橱、吃啥）：JDK 17、Android SDK（platform 35 / build-tools 34+）。构建方式见各应用目录的 README：[wardrobe/README.md](wardrobe/README.md)、[eats/README.md](eats/README.md)。
