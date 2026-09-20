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
- **决策**：新增 `data/mock/`——确定性种子（两角色/17 件衣物/五套组合/三条笔记，照片为 assets/mock 内置 thiings 素材按需解包至 cacheDir，相对时间恒新鲜）+ 内存 Mock 仓库（实现全接口、绝不落盘）；`DemoMode` 偏好开关在组合根构造时读取，演示模式装配 Mock 仓库并把图片目录切至 cacheDir/mock-images；切换重启进程生效；入口仅 DEBUG 构建可见，按当前模式弹「进入/退出演示」确认（修订：去掉常驻横幅）。eats 于 it-006 同构落地（eats ADR-010）。
- **理由**：与 tools 脚本互补——脚本管「真实数据灌入压测」，演示模式管「可复现、零污染、离线」的走查/体验；同接口策略切换零 UI 感知。
- **后果**：APK 增加约 2.2MB 内置演示照片（可接受）；演示写操作进程结束即弃；release 构建无入口无横幅。

## ADR-017 形象参考照采用单图通道：拼入导出长图（it-017）
- **背景**：生图 Agent 需要本人照片做形象参考才能生成「像本人」的效果图；照片挂在 Person（每角色一张可选），但导出通道有三条（复制长图/存相册/分享），剪贴板多图与分享多选在豆包等目标 App 支持不稳，用户还要手动二次挑图。
- **决策**（2026-09-20）：参考照直接拼入导出长图顶部（等比居中限高 1100 + 「本人形象参考」标注条），文案同步追加形象还原句——三条导出通道天然全带，单图粘贴即可用；不做剪贴板/分享多图、不做「仅文案提醒用户自己补传」。开关「附形象参考照」仅在已设置时显示，默认开且状态不持久化（Leo 定调：上传一次长期有效、每次导出默认用，关闭仅本次）。
- **理由**：单图通道兼容性最好、链路最短；备选 B（双图导出）把选择负担推给用户，备选 C（仅提醒）每次导出都要手动补传。
- **后果**：长图总高增加（预览 Fit 全貌更窄，已知限制记入 02-wireframes it-017 注记，预览放大留待后续迭代）；标注条与还原句常驻导出物（对 AI 是有效信号，无害）。

## ADR-016 录入去背景接入 libs/cutout：u2netp 端侧推理、模型打包 assets（it-016）
- **背景**：录入衣物照片带杂乱背景，卡片与合成图观感差；调研结论「纯传统算法仅纯色底可靠」，选型 u2netp + ONNX Runtime 纯端侧（否决 ML Kit：GMS 依赖 + 模拟器不可用，阻塞本仓库评审工作流）。
- **决策**（2026-09-20）：依赖 `libs/cutout`（纯 JVM SDK，RGBA bytes 进出）+ `onnxruntime-android:1.20.0`（版本须与 SDK 编译期桌面版对齐，cutout ADR-002）；模型 u2netp.onnx 打包 assets、经 `suspend () -> ByteArray` 注入（cutout ADR-003，全离线，无下载面）；引擎挂在组合根（构造零副作用，onnxruntime 类与会话首次去背景才加载，冷启动零回归）；release abiFilters 仅 arm64-v8a + armeabi-v7a 控体积；原图生命周期=编辑会话内存锚点（还原可切），确认采用保存后原图即弃（Leo 定）。
- **理由**：装完即用、零网络依赖、与本地优先架构一致；项目顺序「SDK 先行自测（阶段 A，15 单测含真模型）→ 阶段 B 接入」隔离了 wardrobe 并行迭代冲突。
- **后果**：APK release 52.8MB（抠图相关 +33.6MB：双 ABI .so 33MB + 模型 4.6MB）；lintVital 与 Kotlin 2.1.21 不匹配崩溃（lint 工具 bug，`checkReleaseBuilds=false` 绕过，日常 lint 手动跑）；人穿衣服形态边界（连人抠出/弱显著部分削切）以 UI「还原」兜底，产品文案如实提示。


## ADR-018 穿着事件建模为 WearLog：打卡挂穿搭、单品统计派生（it-018）
- **背景**：衣橱沉淀了「存过哪些套」但没有「哪天真穿了」，年度回顾、利用率、闲置判断、「好久没穿」提醒都缺这块地基；备选是给单品逐件勾选打卡（重）或完全跳过数据层直接做展示页（无数据可用）。
- **决策**（2026-09-20）：新增 `WearLog(personId, outfitId, at)`，打卡入口=穿搭详情一个按钮（整套粒度，同日多套允许）；单品的穿着次数/最后穿着由「其所在全部穿搭的打卡」合并派生（不落盘，ADR-008 同思路）；删除 Outfit/Person 级联删 WearLog，加载时清洗悬空引用；`wearLogs[]` 为向后兼容字段不 bump schemaVersion（同 it-017 先例）。价格字段与 CPW 不在本迭代（可裁项，等打卡数据跑起来再上）。
- **理由**：一次点击的成本拿到结构化行为数据；整套粒度符合「今天穿了这套」的真实决策粒度，单品勾选把负担放大数倍且换装场景无法表达。
- **后果**：利用率/闲置为「结构指标」（全量）、打卡/出勤/照片墙为「行为指标」（随档位过滤）的双口径（见 WardrobeRecapCalculator 注释）；删穿搭即失打卡历史（语义合理：套没了打卡无从挂靠）。

## ADR-019 本地「好久没穿」提醒：WorkManager 周期任务（it-018）
- **背景**：lastWornAt 数据沉淀后需要主动触达才有价值；本仓库无服务端无推送通道，通知是唯一触达面。
- **决策**（2026-09-20）：引入 `androidx.work:work-runtime-ktx:2.10.0`，每日一次 PeriodicWork；候选=单品穿过 ≥2 次且距最后穿着 ≥N 天（默认 90，可选 60/90/180），每日至多 1 条、上次提醒过的单品先让位；开关默认关、开启时请求 POST_NOTIFICATIONS；文案带角色名；点击经 EXTRA 深链进衣物详情；演示模式不注册任务。与 eats it-007「好久没去」（ADR-013）同构各自实现——通知封装太薄不抽 libs。
- **理由**：WorkManager 免进程常驻、系统重启后自动恢复（RECEIVE_BOOT_COMPLETED）；每日至多 1 条 + 只推常穿单品，把打扰压到最低。
- **后果**：APK 增加 WorkManager 依赖（~2MB 量级）；通知权限被拒时静默失效（开关仍在，可随时重开）；提醒时点不可控（WorkManager 约束下系统自行调度）。

## ADR-020 心愿域建模：WishItem/WishOutfit 双实体，预览组合可存心愿不落 Outfit（it-019）
- **背景**：种草未购的单品与「买了搭现有衣服」的预览组合都没有承载地。初版方案禁止含愿望件的组合保存（保 Outfit 语义纯净），Leo 否决：「可以保存吧，属于心愿穿搭。不保存后面要再预览不好弄」。
- **决策**（2026-09-20）：新增 `WishItem`（照片可选、url/price、purchasedAt/purchasedItemId 回链）与 `WishOutfit`（itemIds+wishItemIds 双段引用、previewImages，不变量=至少一件愿望单品）。W1 槽位以 `wish:` 前缀伪 Item 混入（asSlotItem 适配，下游长图/文案/拼贴统一按 Item 处理、isWishSlot 判定）；含愿望件的组合存心愿穿搭而非 Outfit，导出面板「收藏」对心愿组合置灰。转正（purchaseWishItem）与一键升级（promoteWishOutfit，previewImages→effectImages）在 Repository 单事务内完成联动。存储 wishItems[]/wishOutfits[] 缺字段反序列化空表，不 bump schemaVersion。
- **理由**：独立实体让 Outfit 下游（打卡 WearLog/回顾统计/收藏去重）零改动零过滤；伪 Item 适配让混入预览复用全部既有渲染管线；可保存的心愿组合满足「反复预览」的核心诉求（Leo 拍板）。
- **后果**：伪 Item id 需在持久化边界处防御（WishOutfit 引用清洗、createOutfit 的无效 id 过滤已有）；心愿域与正式衣橱的语义边界靠 UI（角标/置灰/「仅预览」文案）与不变量共同维护。

## ADR-021 首个公开 release 版本对齐：0.5.0（versionCode 5）（发布）
- **背景**：gradle versionName 一直停在 0.1.0，而 CHANGELOG 已按 [0.4.x-itXXX] 记到 it-010；首个对外发布若沿用 0.1.0 会与历史版本序列冲突。
- **决策**（2026-09-21）：release 版本对齐 CHANGELOG 序列，取 0.5.0（versionCode 5，1+it-011~019 迭代数）；发布渠道为 GitHub Releases（tag `wardrobe-v0.5.0`），APK 用 debug keystore 本地 apksigner 签名（个人分发，无正式 keystore；后续若上应用市场再换正式签名并升 versionCode）。eats 同日首发，序列从 0.1.0 起。
- **理由**：版本号与既有 CHANGELOG 连续，用户可从版本号定位迭代；发布签名策略如实记录避免误以为有正式签名体系。
- **后果**：debug 包与 release 包签名不同，覆盖安装需先卸载；换正式 keystore 时属破坏性变更需再次记录。
