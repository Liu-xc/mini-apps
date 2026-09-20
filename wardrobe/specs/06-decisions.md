# 06 · 架构决策记录（ADR）

> 每条决策：背景 → 决策 → 理由 → 后果。不可逆或影响深远的决策必须在此登记。

## ADR-001 安卓原生 Kotlin + Compose（而非 Flutter/RN/Web）
- **背景**：用户明确要安卓原生应用，手机自用；开发者在本机有 Android SDK。
- **决策**：Kotlin + Jetpack Compose 单 Activity。
- **理由**：剪贴板图片、Photo Picker、分享、文件存储等平台能力为一等公民；Compose 动画体系（Expressive/共享元素）满足"好看+动画有趣"诉求；无需跨平台。
- **后果**：只服务安卓；未来若要 iOS 需另行开发。

## ADR-002 JSON 文件存储（而非 Room）
- **背景**：数据规模个人级（≤数百条），实体关系简单。
- **决策**：单文件 `wardrobe.json`（kotlinx.serialization）+ 图片文件目录；原子写（tmp+rename）+ .bak + schemaVersion 迁移。
- **理由**：零 schema 迁移成本、可整文件备份/导出、调试直观；Room 对此规模是过度设计。
- **后果**：全量读写（快照小，性能无虞）；并发写由单线程 Dispatcher 串行化保证。

## ADR-003 手动 DI（而非 Hilt/Koin）
- **背景**：单模块小应用。
- **决策**：`AppContainer` 组合根 + 构造器注入。
- **理由**：零注解处理器、构建快、依赖关系显式可读；测试时手工替换假实现。
- **后果**：新增依赖需手动装配（成本极低）。

## ADR-004 Material 3 稳定版 + 自建弹簧动效基调（Expressive 暂缓）
- **背景**：要求"最好看的组件库 + 炫酷动画"。
- **决策**（2026-09-19 修订）：material3 锁定 **1.4.0 稳定版** + 标准 MaterialTheme；全局动效基调由 `EditorialMotion`（自建统一弹簧参数：smooth/pop/bouncy）承担。1.4.0 稳定版中 MaterialExpressiveTheme/MotionScheme 仍为 internal；公开版本存在于 1.5.0-alpha28，但其依赖 compose 1.13.0-alpha01 全链 alpha 且需 platform 36，整体风险不可接受。
- **后果**：内置 M3 组件默认动效为标准曲线（差异细微）；我们的自定义动画（轮播/老虎机/共享元素/彩屑/staggered）全部走 EditorialMotion，观感不受影响。material3 1.5 稳定后回归评估替换。

## ADR-005 衬线标题用系统字体（暂不打包 Noto Serif SC）
- **背景**：编辑风需要衬线大标题；打包完整 CJK 衬线字体 +20MB。
- **决策**：`FontFamily.Serif`（中文安卓机普遍内置 Noto Serif CJK）。真机验证若无衬线，再打包单一 weight 的 Noto Serif SC 子集。
- **后果**：不同设备衬线细节略有差异（可接受）；APK 保持轻量。

## ADR-006 品类槽位 HorizontalPager（而非 Tinder 式卡堆）
- **背景**："滑动组合"有两种主流交互。
- **决策**：每品类一个横向 Pager 槽位（用户已确认），轮播效果 = graphicsLayer 缩放/透明/视差（业界标准做法，参考 sinasamaki 系列）。
- **理由**：组合语义清晰（每类独立选择）；Pager 吸附体验成熟。
- **后果**：暂无。

## ADR-007 导出以「单张长图」为主通道（it-002 修订）
- **背景**：it-001 把图片+文本同时放剪贴板，用户实际体验不佳；且安卓部分应用不支持粘贴剪贴板图片。
- **决策（2026-09-20 修订）**：主按钮只复制**一张纵向长图**——按品类槽位顺序从上到下拼单品照片，Prompt 绘制在图片底部并写入 EXIF ImageDescription；生图 Agent 只需粘贴一张图。辅助通道：只复制文本（含单品清单）、ACTION_SEND 分享长图。Prompt 由五维度预设选择器（场景/氛围/季节/光线/构图）+ 人物描述（持久化）组装。
- **后果**：EXIF 能否被读取取决于目标 Agent（文字已画进图片，不依赖 EXIF）；长图体积随单品数增大（JPEG q90，宽度固定 1024）。

## ADR-008 数据流 SSOT：Repository 快照 + StateFlow
- **背景**：三 Tab + 多屏共享同一份数据。
- **决策**：Repository 内存快照为唯一数据源，写操作「改快照→落盘→广播」；UI 一律订阅流。
- **后果**：任何页面改动即时全 UI 生效；需注意大 bitmap 不进快照（图片按文件懒加载）。

## ADR-009 minSdk 26 / compileSdk·targetSdk 35
- **背景**：个人手机自用；Photo Picker（PickVisualMedia）在旧版本自动回退。
- **理由**：26 覆盖绝大多数在用设备；35 为当前平台，material3 1.4 需要。
- **后果**：无。

## ADR-010 仓库采用 spec-driven 文档驱动（无 OpenSpec 框架）
- **背景**：希望未来迭代有充足上下文，人/AI 均可接手。
- **决策**：`specs/` 目录 + 常青文档 + iterations/ 迭代提案 + AGENTS.md 铁律（见仓库根）。
- **理由**：纯 markdown 零工具依赖，AI 会话可直接读取恢复上下文。
- **后果**：每次合入需人工保持 spec 同步（铁律②）。

## ADR-011 云同步底座：飞书多维表格 + 轻同步
- **背景**：多端数据同步与多人分发的真实诉求；对比过 LeanCloud、腾讯云开发 CloudBase、维格表、坚果云 WebDAV、Syncthing、Supabase/Firebase（见 it-002 §11）。
- **决策**（2026-09-20）：飞书多维表格为首个云端底座；轻同步语义——云端为正本、本地为缓存，启动/手动刷新拉取，记录级 LWW 整行覆盖，全量对账代替墓碑；不做实时推送；分发 = 三参数凭证二维码（凭证即身份，家人可经飞书 UI 直接编辑的 M2 通道并行）。
- **理由**：免费国内直连、自带家人协作通道、个人容量充裕（15GB/单表万级行）；用户确认实时性与并行编辑诉求极低，重型同步引擎可砍。
- **后果**：同步层需自建但工程量收敛；平台依赖由中立契约隔离（ADR-012）；App 接入前须跑完 it-002 实测清单（真机验证 API 行为）。

## ADR-012 公共 SDK 抽取：libs/store（已接入）+ libs/sync（契约先行）
- **背景**：本地存储与云同步被定位为 mini-apps 多应用公共底座，且后端须可低成本切换（多维表未必是长期方案）。
- **决策**（2026-09-20）：① 本地存储抽取为 `libs/store`（SnapshotStore 原子快照/SSOT 仓库/媒体管理/zip 备份），wardrobe 数据层自本迭代起接入，文件布局与备份格式不变；② 同步 SDK 采用「中立契约 + 适配器」：`libs/sync/contract`（SyncValue 七值、SyncEngine、待推队列、错误折叠）+ `libs/sync/bitable`（首个适配器），换后端 = 换适配器 + 换配置，后端私有能力（record_id、串行写）不外泄；③ 均经 composite build 接入，AGENTS.md 已补 `libs/` 例外条款。
- **理由**：it-001 数据层已验证，抽取为测试护栏下的纯重构；契约中立化先于第二消费方出现，eats/clips 可直接复用。
- **后果**：wardrobe 数据层不再自持持久化机制（职责上移）；libs/sync 已实现但按计划暂不接入 App（US-15a/b/c 留待后续迭代）；未来新增后端仅实现 SyncSource。

## ADR-013 穿搭记录采用 carddeck 卡组（it-007）
- **决策**：W8 顶部用 `libs/carddeck` 侧滑卡组浏览已保存穿搭 + 「随机一套」纯随机抽取，下方保留全量网格；与 eats it-003 共用同一 SDK（includeBuild 复用）。
- **后果**：与 eats 共享三方库依赖链（JitPack，见 libs/carddeck/specs）；交互升级由 SDK 层统一演进。

## ADR-014 衣橱列表滑动删除改长按删除（it-011）
- **背景**：W3 列表改两列网格（C6），网格内横向滑动手势与纵向滚动/横滑切卡冲突，SwipeToDismissBox 不再适用。
- **决策**（2026-09-20）：删除入口迁移为长按卡片 → 确认弹窗（保留原确认语义）；点卡片仍进编辑。
- **后果**：交互与 eats 列表（保留滑动删除的单列）分化，属形态差异非能力差异；如回归单列可换回。

## ADR-015 应用内演示模式：内存 Mock 仓库 + 组合根切换（it-015）
- **背景**：测试体验与 AI 走查需要内容丰富、状态可复现的数据源；既有 `tools/demo-data.sh` 直接覆盖真实 wardrobe.json 与 images/，走查期间的写操作会污染真数据，用后需手动清理。
- **决策**：新增 `data/mock/`——确定性种子（两角色/17 件衣物/五套组合/三条笔记，照片为 assets/mock 内置 thiings 素材按需解包至 cacheDir，相对时间恒新鲜）+ 内存 Mock 仓库（实现全接口、绝不落盘）；`DemoMode` 偏好开关在组合根构造时读取，演示模式装配 Mock 仓库并把图片目录切至 cacheDir/mock-images；切换重启进程生效；MainActivity 顶部常驻横幅点按退出；入口仅 DEBUG 构建可见。eats 于 it-006 同构落地（eats ADR-010）。
- **理由**：与 tools 脚本互补——脚本管「真实数据灌入压测」，演示模式管「可复现、零污染、离线」的走查/体验；同接口策略切换零 UI 感知。
- **后果**：APK 增加约 2.2MB 内置演示照片（可接受）；演示写操作进程结束即弃；release 构建无入口无横幅。
