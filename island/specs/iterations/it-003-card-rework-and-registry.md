# it-003 — 卡片改版（单环主锚+行式明细）+ 可扩展架构（ProviderRegistry / 按源状态 / 布局单一真源）

状态：**已完成**（Leo /goal 下单「进行 UI 审查并优化架构和 UI」，视觉方向三选一当轮确认为「单环主锚+行式明细」，按 it-002 先例视为提案确认；验证记录见文末）
日期：2026-09-26

## 背景与动机

### UI 走查结论（DESIGN.md §6 模板，2026-09-26 实机自截图对表）

**P0（违红线 / 功能违例）**

1. **§5.9 数字变化无过渡**——刷新后百分比文本直接跳变（环有 easeOut 补间，数字没有）。
2. **it-002 AC4 违例**——`refreshAll` 只要有任一源成功即 `status=.loaded`，失败源错误被整体吞掉：
   GLM 正常 + MiMo Cookie 过期时页脚永远不出现「请更新」提示。
3. **页脚时间说谎**——「x前已刷新」只在 store 发布时重算，两次刷新之间恒为「刚刚已刷新」。

**P1（违基线 / 信息设计）**

4. **§2.3 排版**——重置/绝对量 8pt、页脚 9pt、按钮 10pt，比基线 Label(11) 小两档；42–45% 白字灰感重。
5. **§2.5 交互目标**——⟳ ⚙ 纯 10pt 图标、无 hover 反馈、无命中区扩展。
6. **双环歧义**——GLM 双同心环无环心标识，「哪个环是哪档」靠图例反查，读起来像 loading spinner。
7. **两面板失衡**——环尺寸 60/68、行数 2/1、垂直轴线不对齐。
8. **源身份缺失**——卡片无任何「GLM」字样；菜单栏图标仍是身份三色（蓝/绿/橙=5h/每周/MCP），
   与卡片健康度配色两套语义，且 MCP 档早已不展示。
9. **spec 漂移**——面板底/描边/健康度配色/新动效全不在 `05-design-system`（AGENTS.md 判迭代未完成）。

**P2（打磨）**——环弧阴影偏重（§2.4）、「清除」破坏性操作无确认（§5.7，可粘贴恢复低危）、
设置窗 560 vs 视图 620 高度不一致、菜单摘要不按源分组。
合规项 ✓：空状态（图形+行动按钮）、无 emoji 图标、accent 无铺底。

### 架构问题（「不好扩展」的根因）

1. **没有 ProviderRegistry**——ADR-008 承诺「it-002 起按 provider registry 演进」未兑现。
   `ProviderUsageFetcher`/`DemoUsageProvider`/`UsageStore`（4 个按源 save/clear 方法）/
   `SettingsView`（两段硬编码分区）/**卡片视图（`ProviderPanel(kind:.glm…)(kind:.mimo…)` 硬编码两个）**
   ——加第三个源要改 6+ 处 switch，且卡片根本不会渲染它。
2. **UsageStore 上帝对象**：凭证 CRUD + 拉取分发 + 端点偏好读写 + 缓存 + 定时退避 + 全局单一 status。
3. **布局常量三处重复**：352 宽、`safeTop+6+132+10+14+14` 高度公式、180 触发宽在
   RootView / WindowController / hitTest 各写一份，改布局必漏其一 → 遮罩与穿透判定失同步。
4. **解析器复制粘贴**（`double()`/`pretty()` 两份）；端点偏好在刷新循环里直接读写 UserDefaults。
5. **specs 漂移**：01 US-4「恒定身份色不做预警变色」与代码健康度配色直接矛盾；04 架构图仍画
   setFrame 动画与 KeychainStore；05 尺寸/配色/动效全为 it-001 旧值；README「只存钥匙串」已改 0600 文件。

## 视觉方向（Leo 2026-09-26 当轮拍板）

三选一（精修双环 / 纯行式 / 单环主锚+行式明细），选定 **单环主锚+行式明细**：

- 每源一个面板：**面板头 = 源名 + 主档重置时刻**；**主档剩余% 入环心**（环内小字=档名）；
  **副档/绝对量 = 环下一行紧凑明细**（`● 每周 10月2日 · 18%` / `5.6B / 456B`）。
- 双环「哪个环是哪档」歧义消失、两面板天然平衡，保留 Apple 健康环质感。
- 面板按 registry 自动增减：第三源 = 第三个面板（宽度公式随之扩展）。

## 用户故事（涉及）

- **US-2 展开卡片**——结构改版为单环主锚+行式明细（本次视觉方向）。
- **US-3 自动刷新**——页脚时间每 15s 真实重算（TimelineView）。
- **US-4 剩余量语义**——修正为健康度配色（≥50% 绿 / 20–50% 橙 / <20% 红），
  与 it-002 增补 2 实况对齐，废止「恒定身份色」旧条款。
- **US-7/US-8 多内容源与 MiMo**——按源状态：任一源失败页脚明示该源错误（兑现 US-8 页脚承诺），
  他源数据照常展示；成功后错误清除。
- **US-5 设置与安全**——设置页分区由 registry 驱动；「清除」加二次确认（§5.7）。
- **US-1（不动）**——窗口常驻全尺寸 + 遮罩驱动 + 30Hz 悬停 + hitTest 穿透的终极架构保持。

## 验收标准

- **AC1** 卡片 = 单环主锚+行式明细：面板头（源名+主档重置）、主档 % 入环心（环内档名）、
  副档/绝对量为环下明细行；≤2 源宽 352 并排，第三源自动第三面板（宽度公式扩展）。
- **AC2** 字号与对比：辅助文字 ≥9.5pt 且 ≥55% 白；百分比数字变化有过渡（§5.9 修复）；
  图标按钮命中区 ≥24pt 且有 hover 反馈（§2.5/§3 修复）。
- **AC3** 按源状态：任一源失败不吞——页脚明示该源错误文案，他源数据照常展示，成功即清除（修 it-002 AC4）。
- **AC4** 页脚时间真实：TimelineView 周期重算，不滞留「刚刚」。
- **AC5** 扩展架构：新增源 = `ProviderKind` case + registry 条目 +（新格式才需）解析器；
  卡片/设置页/菜单/凭证路径遍历 registry，无硬编码源；布局尺寸单一真源
  （遮罩/hitTest/窗口帧同源，高度随明细行数自适应）。
- **AC6** 菜单栏图标 = 当前健康度色条（灰=无数据），摘要按源分组。
- **AC7** `swift build` + `swift test` 全绿（存量 17 + 新增 registry/布局/页脚状态测试）；
  DebugShot 自截图走查（demo + 真实数据 + 设置窗）回填验证记录。
- **AC8** specs 全量回填无漂移：01/02/03/04/05 + README + DESIGN.md §1 个性行 + ADR-009~011。

## 影响范围

- **Core**：新增 `ProviderRegistry`、`JSONLoose`（解析器公共工具）；`UsageProvider` 拆出
  `MiMoUsageProvider`、删 `ProviderUsageFetcher`/`DemoUsageProvider` 开关；`UsageStore` 重写为
  `states: [ProviderKind: SourceState]`（按源快照/错误/在途）+ 通用凭证 API；`AppSettings` 收编
  `preferredEndpoint`。
- **UI**：新增 `IslandLayout`（布局单一真源）；`IslandRootView` 改版（面板/主环/明细行/
  TimelineView 页脚/numericText 过渡/hover 按钮）；`IslandTheme` 收编度量与 NSColor 等价物。
- **App**：`IslandWindowController` 消费 IslandLayout + 监听 store 重定位；`MenuBarController`
  动态健康度图标 + 按源分组摘要；`SettingsView` registry 分区 + 清除确认；设置窗高度对齐 620。
- **Tests**：新增 registry 完整性、布局公式、页脚状态描述测试；`MiMoQuotaParserTests` 标签断言
  「MiMo」→「套餐」同步。
- **Specs**：01/02/03/04/05 回填、06 新增 ADR-009（registry/按源状态/布局真源）、
  ADR-010（凭证 0600 文件，补录 it-002 增补 4）、ADR-011（DebugShot 自截图走查）、
  README、DESIGN.md §1、根 CHANGELOG、specs/CHANGELOG。

## 验证记录

（2026-09-26 回填，实施完成）

### 构建与测试

- `swift build`：0 error。
- `swift test`：**37/37 绿**——存量 17（含 `MiMoQuotaParserTests` 标签断言
  「MiMo」→「套餐」同步）+ 新增 20：
  `ProviderRegistryTests`（注册表↔allCases 一一对应、凭证账户互斥、文案齐全、
  演示数据有效、descriptor 查找）、`IslandLayoutTests`（卡片宽 352/352/506/668、
  面板宽均分、面板高四分支 106/127/146/84、展开高 213、明细行数推导、矩形锚定）、
  `FooterStatusTests`（loading > error(注册表序) > age > idle、演示前缀、isError）、
  `ThresholdAndFormatTests` 增补（健康度阈值 50/20/nil 边界、rowReset 按档选格式）。

### 截图走查（DebugShot 自截图，ADR-011）

| 状态 | 命令要点 | 结果 |
|---|---|---|
| 演示数据卡片 | `GLM_ISLAND_DEMO=1 GLM_ISLAND_EXPAND=1 GLM_ISLAND_SHOT=…` | 704×426px（352×213pt）：GLM 33% 橙环+「5 小时」环心+「● 每周 10月2日 · 18%」，MiMo 96% 绿环+「5.58B/456B」，页脚「演示 · 刚刚已刷新」+⟳⚙ ✓ |
| 真实数据卡片 | 不带 DEMO（本机双源已配置） | GLM 80% 绿环、每周 2% 红点、MiMo 99%「6.03B/456B」，页脚「刚刚已刷新」（无演示前缀）✓ 高度公式与演示一致 |
| 空态卡片 | `GLM_ISLAND_BLANK=1` | 双面板 = 头 + 虚线空环 26pt + 「未配置 API Key/Cookie」+「去设置」按钮；卡片高 170pt（hint 分支 84）✓ |
| 设置窗（已配置） | `…_SHOT_SETTINGS=1` | 460×620：registry 驱动双凭证分区、绿色「已存入本机（0600 加密限制文件）」+清除、端点/刷新/系统/诊断分区 ✓（绿像素 7384） |
| 设置窗（空态） | + `GLM_ISLAND_BLANK=1` | 同结构无绿行（绿像素 0）✓ |

（Read 工具目检出现过缓存错图，最终以像素级数值/ASCII 亮度图核验文件字节为准。）

### DESIGN.md §2–§5 对表（走查清单闭环）

- **P0-1 §5.9 数字跳变** → 已修：环心%与明细% 均 `.contentTransition(.numericText())` + snappy。
- **P0-2 吞错（it-002 AC4）** → 已修：`SourceState` 逐源成败；面板头红字「刷新失败」；
  页脚按注册表序取首个错误（`FooterStatusTests` 锁优先级）；成功即清除。
- **P0-3 页脚时间说谎** → 已修：`TimelineView(.periodic 15s)` 重算相对时间。
- **P1-4 字号/对比** → 已修：全部辅助文字 ≥9.5pt 且 ≥55% 白（页脚 50%→55%），
  百分比 12pt bold rounded、环心 15pt。
- **P1-5 图标命中区/hover** → 已修：24×24pt 命中区（footerHeight=24）+ hover 高亮底。
- **P1-6 双环歧义 / P1-7 两面板失衡** → 已修：单环主锚，面板等高顶部对齐（截图核验）。
- **P1-8 源身份 + 图标语义两套** → 已修：面板头源名；菜单图标改健康度色条（同一阈值源）。
- **P1-9 spec 漂移 / P2 清除无确认、设置窗 560vs620、菜单不分组** → 全部修复
  （本批 00–06 + README + DESIGN §1 同步；`confirmationDialog`；620；分组摘要）。
- §2.5 破坏性操作二次确认 ✓；无 emoji 功能图标 ✓；§3 主运动只有「遮罩生长」一种
  （环填充仅数据变化时动、旋转仅在途时动），不叠加 ✓；§5.8 空态图形+按钮 ✓；
  §5.5 纯黑底已在 DESIGN §1 island 行作**偏移声明**（刘海 HUD 与硬件融合，非纸感应用）；
  触控 44dp 为 Android 基线，macOS 指针场景 24pt 命中区已在 05 声明。

### 用户反馈修复（2026-09-26 二轮）

Leo 实机反馈两点，逐一核实：

1. **「绿色环里面为什么有两种绿色」——属实，已修**：`HeroRing` 弧线原用
   `AngularGradient(0.55→本色)` 沿弧渐变 + 辉光阴影，单环上读出深浅两种绿、
   像两种状态。改为**实色弧**（一环一色一义，只有健康度一种颜色语义），
   辉光一并去除；像素级验证新截图弧上 24 点采样仅 1 种色值 `#30D158`。
   spec 05/ring 条款与 specs/CHANGELOG 同步。
2. **「两个环形图都没对齐」——旧包所致，非新代码缺陷**：实机跑的
   `dist/island.app` 是 25 日 23:37 打包（早于 it-003 全部代码），即 it-002
   旧双环 UI（60/68 错位 = it-003 已修的 P1-7）。新单环截图实测两环圆心
   y 完全一致（201.5px@2x）、面板等宽等高。dist 已重建，需退出旧实例重开。

### 对抗审计修复（2026-09-26 三轮：四镜头工作流 + 双反驳者核实）

四镜头（像素几何/色彩语义/布局代码/spec 对表）并行审查产出 16 条原始发现，
去重后严重度最高的 5 条各经 **2 名独立反驳者**核实：**5/5 CONFIRMED、0 REFUTED**；
其余 11 条 P2 一并修复。

| 级别 | 发现（根因） | 修复 | 验证 |
|---|---|---|---|
| **P0-A** | hitTest 穿透判定坐标系错配——point 是窗口基坐标、`visibleCardRect` 是屏幕全局，恒不相交 → **展开态整卡点击全死**（固定/刷新/设置/去设置全无响应，且点击穿透下层窗口；引入于 663f7e0，hover 走全局坐标所以正常，掩盖了问题） | `IslandContentView.screenPoint(forWindowPoint:windowFrame:)` 换算后再判定 | `AuditRegressionTests` 换算+包含关系；本地探针两份独立复现 |
| **P0-B** | 绝对量明细行「6.20B / 456B」裸 Text 无过渡，违 DESIGN §5.9（it-003 P0-1 只修了环心/明细百分比） | 绝对量行 + `DetailRow.auxText` + 面板头重置时刻补 `.contentTransition(.numericText())` | 44 测试全绿；05 动效表扩写覆盖 |
| **P1-C** | 环 stroke 以路径为中心向框外各溢 4pt——墨迹外径 74pt ≠ token 66，头↔环段距只剩 2pt、空态↔数据态环左跳 8px | 路径直径 = 外径 − 环宽（58+8=66） | 截图实测环墨迹 **66.0×66.0pt**、与面板中心 cx 重合 |
| **P1-D** | 0% 与「无数据」渲染成同一个无色环——最需要红色的 0% 没有健康色，spec 承诺的 unknown 白 25% 环色永不出现 | 有数据（含 0%）恒绘最小健康色弧；无数据整圈底轨升白 25%（与色点/菜单同值） | `arcTrimEnd`/`trackOpacity` 单测；`levelNSColor` unknown 改 0.25 |
| **P1-E** | 命中区 24pt（空态按钮实测 50×21pt 连 24 都不达）低于 DESIGN §2.5 ≥44dp，05 误称「§2.5 基线」且 DESIGN.md 未登记偏移 | DESIGN.md §2.5 增补 **it-003 指针场景例外 ≥24pt**；05 改「指针例外（已登记）」；去设置按钮 `minHeight: 24` | 空态截图按钮实测 **25pt**；`iconHitTargetMeets24pt` 单测 |
| P2 | 判定瞬时切换 vs 遮罩 0.32s 动画不同步（收起期可见卡点击穿透/展开期吞点击/收起途中移回不中止） | `visibleCardRect` 随 appearance 0.45s smoothstep 插值 | 04 动效表登记；实现于 WindowController |
| P2 | round cap 帽宽交叠盖死缝隙，99% 与 100% 无视觉差 | `arcTrimEnd` 从 trim 扣除帽宽占位 | `arcTrimCompensatesRoundCapOverlap`（99% 留 1% 缝、100% 闭合） |
| P2 | 主环左贴（右侧留白 65pt）与 W2 居中画法不一致 | 面板内容 `VStack(.center)`，环/单档绝对量行居中 | 截图 cx = 面板中心 185.5/517.5 |
| P2 | 明细行 12pt/9.5pt/10pt 三字号基线不齐（差 1.5pt） | `HStack(alignment: .firstTextBaseline)` + 色点 alignmentGuide | 像素行扫描：百分比与辅助文底同 y=303 |
| P2 | 「·」白 30% 对比 2.6:1 < §2.2 ≥3:1，且与 05 表头 ≥55% 矛盾 | 改白 55% | 05 排版表同步 |
| P2 | 「--%」占位两档亮度（环心 100% / 明细 40%）且明细破 AC2 | 统一白 55% | 05 排版表增补占位行 |
| P2 | 无数据灰两档（卡片 白25% vs 菜单 NSColor 白30%），与「同色值」自述矛盾 | `levelNSColor(.unknown)` → 0.25 | `unknownNSColorMatchesSwiftUIWhite25` 单测 |
| P2 | 双源同败时第二个源错误全文卡上不可见，与 US-2「错误全文见页脚」冲突 | US-2 措辞修正：页脚=注册表序**首个**错误全文，第二个见面板红字与菜单摘要 | 01 同步（US-7 本就规定单行首个，spec 自洽） |

**三轮回归验证**：`swift build` 0 error；`swift test` **48/48 绿**（+7 纯函数：坐标换算、
环 trim/底轨、命中区、unknown 同值、阈值；+4 渲染级 `RenderRegressionTests`：ImageRenderer
离屏渲染真实视图钉住 HeroRing body 守卫、0% 红弧、墨迹外径、按钮命中高度）；
截图三态（真实/演示/空态）像素断言——环 66.0pt ✓ 双环 cy=201.5 同轴 ✓ cx=面板中心 ✓
明细基线 y=303 对齐 ✓ 空态按钮 25pt ✓ 弧色采样全 `(48,209,88)` 单色 ✓。

**独立对抗核实**（工作流：5 项修复 × 2 独立反驳者 + diff 扫描）：**5/5 CONFIRMED、
10/10 verdicts real=true、0 REFUTED**——核实者各自用真实 NSPanel 探针端到端复测坐标换算
（hitTest 实收 (824,887)）、ImageRenderer 复现 0%/nil 双态、连通域复测按钮 25.0pt、
像素复测环墨迹 65.96×65.96pt；核实附带的测试覆盖建议（body 守卫/按钮高度无渲染级断言）
已落为 `RenderRegressionTests` 四测（48/48）。

### loading 图标永转修复（四轮，2026-09-27：日志诊断 + 模板匹配定位）

**用户反馈**：「loading icon 一直在转，而且不是中心对称的（有点抖）」。

**诊断链（全部实证）**：
1. 埋 `[island][boot/refresh/hover/hit/tap/spin/mask]` 探针重启实例 → 日志证明
   **状态机健康**：fetch 每 5 分钟完成、每轮仅 0.3s、`setSpinning(false)` 准时执行
   ——「一直转」不在数据层，在动画层。
2. 离屏 ImageRenderer 渲染图标链24 角度 + Kåsa 圆拟合 → **锚点正确**（0°/180°
   bbox 中心重合，旋转中心与 frame 中心差 0.06pt 亚像素）；「不中心对称/抖」的真相 =
   字形 ink 74×90 非方（圆+突出箭头），质心随旋转画 0.63pt 偏心圆——**慢速无限转时**
   该光学偏移被读成抖；且旧写法 `animation(nil)` 打不断 in-flight repeatForever，
   状态 false 后视觉永转（两者同根）。
3. **模板匹配**（48 角度×7.5° 步进、±6px 滑动，合成对照 SAD=0 验证匹配器可信）：
   - 修复前 3 张停转后抓图 = 67.5°/255°/105°/300° **持续变化** → 永转实锤；
   - 中途尝试 `withTransaction(disablesAnimations)` 同样无效（离散写入均打不断）；
   - 终修 = **0.01s 有限动画覆盖同 keypath**（公认可靠打断方式，视觉=原地归零）。
4. 终验（`GLM_ISLAND_SHOT_DELAY` 可配抓图）：在途 0.25s = 82.5° **在转**；
   停后 6s/7.5s/9s = **0°/0°/0° 全停** ✓。

**规格同步**：05 动效表刷新按钮行改写（禁用 nil/关动画事务停转，改有限动画覆盖）。

### 已知限制（如实）

- 菜单栏图标与系统 UI 不在 DebugShot 覆盖内，需人工目检（逻辑与卡片共用 `IslandTheme`，
  阈值已单测）；
- 「刷新失败」面板头/页脚错误态未做截图样张（无破坏真实凭证的途径），
  由 `FooterStatusTests` + 纯色取值代码路径覆盖；
- hover 展开/收起/固定（US-1 轮询+防抖状态机）未改判定逻辑，未重复 CGEvent 人工悬停实测；
  **点击路径三轮已改**（坐标换算 + 判定插值），由 `AuditRegressionTests` 与本地探针覆盖，
  真机点击（固定/刷新/设置）建议下次实机使用时顺手确认；
- 截图流程验证了展开态渲染与布局同源（遮罩/hitTest/窗口帧同吃 `IslandLayout`）。
