# mini-apps

个人小应用合集仓库。一个应用一个目录，各自独立可构建，互不依赖。

| 应用 | 目录 | 说明 | 状态 |
|---|---|---|---|
| 衣橱 | [`wardrobe/`](wardrobe/) | 安卓原生应用：多角色衣橱管理、滑动组合穿搭、录入去背景、导出生图素材给 AI 生图 Agent、成品效果图回录与单品双向关联、穿搭打卡与衣橱回顾、心愿清单 | 开发中 (it-020) |
| 吃啥 | [`eats/`](eats/) | 安卓原生应用：吃喝玩乐三类记录（堂食/外卖/自做）、地图标记 + 最近一次追踪、卡组快速决策、统计回顾、想吃想玩愿望清单 | 开发中 (it-009) |
| 显影 | [`darkroom/`](darkroom/) | 安卓原生应用：拍立得显影工具——传图看三阶段显影动画（潜影/浮现/定影）、药水条倒放、甩一甩加速、黑白灰编辑式卡面、成片位图与**显影过程视频**（EGL 逐帧编码+合成声轨）双导出，全程离线 | 开发中 (it-001) |
| 拾彩 | [`lottery/`](lottery/) | 安卓原生应用：图片种子选号、收藏票夹、真实三维球群碰撞与连续管轨出球的开奖剧场、同源票面导出；本地演示数据明确标注 | it-003 物理摇奖与视觉重做；真机性能待验 |
| 剪贴盒 | [`clips/`](clips/) | 双端剪贴板历史（Android Kotlin/Compose + macOS SwiftUI），clips.json 文件格式为跨端契约，双端单测对齐 | 提案中（it-001 specs 已备，未开工） |
| 灵岛 | [`island/`](island/) | macOS 刘海功能入口容器：默认隐形，hover 刘海动画展开内容卡片；多内容源按 ProviderRegistry 单表并排展示（GLM + 小米 MiMo TOKEN 用量：单主环剩余%·健康度配色·行式明细），凭证 0600 本机文件、按源状态独立、布局单一真源 | it-003 已完成（单环卡片 + 可扩展架构） |
| 驿站 | [`posthouse/`](posthouse/) | macOS 菜单栏工具：本地 git 仓库烽火台（三态报警 + 明细菜单 + 带网络参数的手动推送）、白名单自动补推（默认全关、永不 force 永不自动 pull）、每晚邸报战报 + 成就系统 | it-001 已完成（M1 烽火台 + M2 自动哨兵 + M3 邸报） |
| 象棋竞技场 | [`xiangqi/`](xiangqi/) | 本地 web 应用（仓库首例）：两个大模型各开一个隔离的 pi agent session 对弈中国象棋（ICCS + 合法走法白名单协议），实时观战思考流、中文记谱、换先记分板、棋谱导出 | 已归档（it-001~003，MVP 完成） |
| 赤峰环线 | [`travel-rpg/`](travel-rpg/) | 网页游戏：Three.js 塞尔达式 3D 探索 RPG，把赤峰环线自驾（石林/达里湖/乌兰布统）做成可探索世界，PC + mobile web 一套代码 | 已归档（it-001~012 后暂停开发，代码保留可复启） |

## 公共基础设施（libs/）

跨应用复用的 SDK，与各应用互不侵入（composite build 接入），同样 specs 先行：

| 库 | 目录 | 说明 | 状态 |
|---|---|---|---|
| 本地存储 SDK | [`libs/store/`](libs/store/) | 快照原子存储（tmp→rename + .bak + 三级恢复 + 迁移链）、SSOT 仓库基类（writeHook）、媒体文件管理 | 0.1.0 已实现，wardrobe / eats 均已接入 |
| 轻同步 SDK | [`libs/sync/`](libs/sync/) | 后端中立契约（SyncValue 七值 / 引擎 / 待推队列 / 错误折叠）+ feishu-bitable 适配器（自动建表、UI 行收编、串行写 + 429 退避） | 0.1.0 已实现，暂未接入应用 |
| 卡组 SDK | [`libs/carddeck/`](libs/carddeck/) | 自研卡组内核（官方 AnchoredDraggable，it-047；CardDeck + CardDeckController.drawRandom 纯随机节奏编排），转盘/穿搭记录翻卡共用 | 0.1.0 已实现，eats / wardrobe 均已接入 |
| 离线抠图 SDK | [`libs/cutout/`](libs/cutout/) | u2netp + ONNX Runtime 端侧抠图（RGBA bytes 进出、纯 JVM 双 runtime、会话惰性 + 空闲释放） | 0.1.0 已实现，wardrobe 已接入（it-016） |

## 仓库约定

- **本仓库是 spec-driven 的**：先写规格文档再写代码，详见 [AGENTS.md](AGENTS.md)。
- 每个应用的规格文档、迭代记录放在 `应用名/specs/` 下，是该应用一切迭代的上下文源。
- **跨应用经验沉淀在根目录 [LESSONS.md](LESSONS.md)**（一行一条的行为规则库）：迭代收尾时写回，开工前必读；反复出现的教训会晋级为 AGENTS.md / DESIGN.md / 常青 spec 中的铁律。
- 新增应用：在根目录建 `应用名/` 子目录，复制 `wardrobe/specs/` 的文档骨架作为起点。
- 根目录不放任何应用代码；跨应用公共 SDK 放 `libs/`（属基础设施例外，见各库 specs）。

## 素材署名

各应用的应用图标与界面 3D 图标来自 [Thiings](https://www.thiings.co)（免费素材，个人非商业用途）。

## 环境要求

- 安卓应用（衣橱、吃啥、显影、拾彩）：JDK 17、Android SDK（platform 35 / build-tools 34+）。构建方式见各应用目录的 README：[wardrobe/README.md](wardrobe/README.md)、[eats/README.md](eats/README.md)、[darkroom/README.md](darkroom/README.md)、[lottery/README.md](lottery/README.md)。
- 网页应用（象棋竞技场、赤峰环线）：Node.js 20+。构建方式见各应用目录的 README：[xiangqi/README.md](xiangqi/README.md)、[travel-rpg/README.md](travel-rpg/README.md)。
