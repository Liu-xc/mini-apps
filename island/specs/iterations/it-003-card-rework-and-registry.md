# it-003 — 卡片改版（单环主锚+行式明细）+ 可扩展架构（ProviderRegistry / 按源状态 / 布局单一真源）

状态：**实施中**（Leo /goal 下单「进行 UI 审查并优化架构和 UI」，视觉方向三选一当轮确认为「单环主锚+行式明细」，按 it-002 先例视为提案确认）
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

（实施后回填：构建/测试/截图走查/交互实测）
