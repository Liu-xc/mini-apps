# 06 · 架构决策记录（ADR）

> 每条决策：背景 → 决策 → 理由 → 后果。不可逆或影响深远的决策必须在此登记。

## ADR-001 安卓原生 Kotlin + Compose，技术栈与 wardrobe 同基线
- **背景**：个人手机自用；本机已有 JDK 17 + Android SDK；wardrobe 已沉淀 JSON 原子存储 / 图片压缩 / 主题 / 构建配置等可复用模式。
- **决策**：Kotlin + Jetpack Compose 单 Activity；构建配置（AGP / Kotlin / SDK 版本）与 wardrobe 保持一致。
- **理由**：平台能力（Photo Picker、文件存储、剪贴板）一等公民；两个应用间经验与代码模式可互相搬。
- **后果**：只服务安卓；两应用升级需各自验证。

## ADR-002 地图用 osmdroid + 高德栅格瓦片（而非高德 SDK / Google Maps）
- **背景**：需要地图打点与长按选点；个人自用不想申请、保管 API Key。
- **决策**：osmdroid + 高德公开栅格瓦片（webrd，免 Key）；Compose 经 AndroidView 互操作；瓦片在线加载并落盘缓存。
- **修订**（2026-09-20）：原定 OpenStreetMap MAPNIK 瓦片实测在国内网络完全不可达（连接超时，非慢），按本 ADR 首选条件（免 Key、国内可用）切换为高德栅格瓦片，代码见 `ChinaTileSource`。
- **理由**：开源库 + 免 Key、无配额；离线时缓存底图仍可用；marker/事件 API 成熟；高德瓦片国内低延迟且有中文注记。
- **后果**：① 高德瓦片为 GCJ-02 坐标系，本应用所有坐标均来自同一底图上的长按选点，存储/展示/聚焦自洽，不做 WGS-84 换算（未来若需导出到其他地图再评估）；② 依赖高德公开瓦片接口，非官方承诺，存在变动风险（可接受，切换成本低）；③ 无 POI 检索/逆地理编码（见 ADR-005）。

## ADR-003 JSON 文件存储（而非 Room）
- **背景**：数据规模个人级（食堂 ≤ 数百、Visit ≤ 数千条），关系单一（Place 1—N Visit）。
- **决策**：单文件 `eats.json` + `images/*.webp`；原子写 + .bak + schemaVersion 迁移（复用 wardrobe 已验证的 JsonFileStore 模式）。
- **理由**：零迁移成本、可整包备份、调试直观；Room 对此规模过度设计。
- **后果**：全量读写（快照小，无虞）；并发写由单线程 Dispatcher 串行化。

## ADR-004 手动 DI（而非 Hilt/Koin）
- 同 wardrobe ADR-003：AppContainer 组合根 + 构造器注入；单模块小应用零注解处理器、构建快、依赖显式。

## ADR-005 位置录入 = 地图长按选点 + 手填地址文本（MVP 不做 POI 检索/地理编码）
- **背景**：POI 检索与逆地理编码都需要在线服务与 Key。
- **决策**：MVP 仅做「地图长按放 pin 得经纬度 + 地址文本手填」；自做菜可完全无位置。
- **后果**：录入略手工（个人自用可接受）；未来加 POI 搜索需引入在线服务并另行 ADR。

## ADR-006 ~~转盘按「距上次吃的天数」加权抽取~~（it-003 作废：转盘整体移除，抽取改为纯随机）
- **背景**：纯随机会反复抽到刚吃过的，体验差。
- **决策**：候选过滤（类型 / 忌口标签 / 最近 N 天开关）后按 `w = 1 + daysSinceLastVisit` 加权抽取（从未吃过按 30 天计）；参数集中在 SpinWheel 策略可调。
- **理由**：规则透明可解释、纯函数可单测；「越久没吃越容易被翻牌」。
- **后果**：新加的食堂天然高权重（引导尝新）；公式可迭代而不动数据。

## ADR-007 堂食/外卖/自做统一为 Place 一个实体
- **背景**：三类「吃饭选项」都要进转盘、列表、时间线。
- **决策**：单一 Place 实体 + PlaceKind 枚举区分；kind 仅影响图标 / marker 色 / 位置默认行为。
- **理由**：转盘与列表一视同仁，避免三套模型三套 UI；自做菜 = 名称为菜名的 Place。
- **后果**：统计若需按类拆分，用 kind 分组即可。

## ADR-008 派生统计不落盘
- **背景**：最近一次吃 / 次数 / 平均分若存字段，与 Visit 双写易不一致。
- **决策**：lastVisitAt / visitCount / avgVisitRating 全部由 Visit 实时派生，不写入 JSON。
- **后果**：读取时多一步聚合（数据量小，无虞）；数据永远一致。

## ADR-009 链接存原始 URL，来源识别为展示期派生，跳转走系统 ACTION_VIEW
- **背景**：美团/大众点评的分享链接（店铺页/套餐页）需要快速记录与一键跳回——转盘抽中后直达下单页。
- **决策**：Place.links 存 `url + 可选 label`；来源徽标（美团/大众点评/其他）由 `LinkSource.detect(url)`（域名映射常量）在展示时派生，不落盘；跳转统一 ACTION_VIEW，系统 App Links 自动拉起对应 App，无处理组件时 toast 兜底。
- **理由**：识别规则升级后旧数据直接受益；不引入 WebView 与任何平台 SDK；粘贴→识别→跳转全链路 MVP 零额外依赖。
- **后果**：域名映射表需随平台域名变化维护；少数自建浏览器的 App 内打开体验一般（可接受）。

## ADR-011 W1 随机交互采用三方卡组库封装（libs/carddeck，不自研手势动画）
- **背景**：用户要求移除转盘，改为「侧滑浏览 + 随机抽取」的卡片交互；明确不自研手势动画、不做抽取权重。
- **决策**：新建 `libs/carddeck` SDK 薄封装 [compose-swipeable-cards](https://github.com/smartword-app/compose-swipeable-cards)（Apache-2.0，JitPack 分发），暴露 `CardDeck` + `CardDeckController(drawRandom 纯随机)`；eats 与 wardrobe 经 includeBuild 复用。
- **理由**：该库提供左右滑/堆叠/弹簧动画与程序化 `swipe()/moveNext()`（抽取编排必需）；备选 makzimi/SwipingCards 因 minSdk 33 高于基线 26 且无程序化接口被否。抽取动画 = 按拍调用库自带飞出动画，SDK 零自研手势。
- **后果**：JitPack 仓库进入两应用与 SDK 的解析链（国内实测可达）；三方库维护偏冷，若失效可按同契约替换实现（SDK 层隔离）。

## ADR-010 数据层换用 libs/store（删除自研 JsonFileStore）+ 应用内演示模式（it-006）
- **背景**：`libs/store`（ADR-012 同款，wardrobe 已接入）与 eats 自研的 JsonFileStore/SSOT 样板语义完全同源（ADR-003 即 wardrobe 同模式），属重复实现；同时测试体验与 AI 走查需要不污染真实数据的丰富数据源（tools/demo-data.sh 直接覆盖真数据，用后要手动清理）。
- **决策**：① eats.json 持久化换 `SnapshotStore`、仓库继承 `SsotRepository`、图片文件管理换 `FileMediaStore`，删除 `data/json/`；磁盘格式不变，老数据无缝升级。② 新增 `data/mock/`：确定性种子（相对时间恒新鲜）+ 内存 Mock 仓库（不落盘）；`DemoMode` 开关在组合根构造时读取，切换重启进程生效；演示中顶部常驻横幅点按退出；入口仅 DEBUG 构建可见。SpinPrefsStore（DataStore 偏好）不迁移——键值偏好不属于快照存储。
- **理由**：删除重复代码；writeHook 缝为将来接 libs/sync 铺路；演示模式让走查可复现且真实数据零风险。
- **后果**：eats.json 生命周期交给 SDK（迁移链/恢复语义以 SDK 为准）；演示模式是一次设计上的「策略切换」，release 构建零痕迹。
