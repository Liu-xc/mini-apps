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

### 已知限制（如实）

- 菜单栏图标与系统 UI 不在 DebugShot 覆盖内，需人工目检（逻辑与卡片共用 `IslandTheme`，
  阈值已单测）；
- 「刷新失败」面板头/页脚错误态未做截图样张（无破坏真实凭证的途径），
  由 `FooterStatusTests` + 纯色取值代码路径覆盖；
- hover 展开/收起/固定/穿透（US-1 终极架构）本次未改代码路径，未重复人工悬停实测；
  截图流程验证了展开态渲染与布局同源（遮罩/hitTest/窗口帧同吃 `IslandLayout`）。
