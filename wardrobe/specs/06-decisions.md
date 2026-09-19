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

## ADR-004 Material 3 Expressive + 版本锁定
- **背景**：要求"最好看的组件库 + 炫酷动画"。
- **决策**：material3 1.4+ 的 `MaterialExpressiveTheme` + `MaterialMotionScheme.expressive()` 作为全局动效基调；已知 1.4.0+ BottomSheet 动效硬编码问题（Google issue 452071842），接受其默认表现，不强行自定义 bottom sheet 动画。
- **理由**：官方最新表现力体系，全局一致、免自造轮子。
- **后果**：升级 material3 时回归检查 bottom sheet/弹层动效。

## ADR-005 衬线标题用系统字体（暂不打包 Noto Serif SC）
- **背景**：编辑风需要衬线大标题；打包完整 CJK 衬线字体 +20MB。
- **决策**：`FontFamily.Serif`（中文安卓机普遍内置 Noto Serif CJK）。真机验证若无衬线，再打包单一 weight 的 Noto Serif SC 子集。
- **后果**：不同设备衬线细节略有差异（可接受）；APK 保持轻量。

## ADR-006 品类槽位 HorizontalPager（而非 Tinder 式卡堆）
- **背景**："滑动组合"有两种主流交互。
- **决策**：每品类一个横向 Pager 槽位（用户已确认），轮播效果 = graphicsLayer 缩放/透明/视差（业界标准做法，参考 sinasamaki 系列）。
- **理由**：组合语义清晰（每类独立选择）；Pager 吸附体验成熟。
- **后果**：暂无。

## ADR-007 导出双通道：剪贴板优先 + 系统分享兜底
- **背景**：安卓部分应用不支持粘贴剪贴板里的图片（平台限制，非 bug）。
- **决策**：主按钮同时写文本+图片（图片经 FileProvider content URI）到剪贴板；另有「分享」按钮 ACTION_SEND 图片直发目标应用。导出面板文案可编辑。
- **后果**：所有生图 Agent 至少有一条可用通路。

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
