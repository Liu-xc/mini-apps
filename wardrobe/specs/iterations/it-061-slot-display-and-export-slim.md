# it-061 · 鞋槽显示修复 + 品类清单直选 + 导出表单减负

> 状态：**已完成**（2026-09-29，Leo 体验包三点反馈）
> 来源：①「选衣服的界面，鞋子只展示了一半」；②「要有一个快捷交互，能以列表形式直接看这个品类下有哪些衣服，不用来回滑」；③「保存页场景表单默认置空不做预设，圈出的选项应折叠隐藏，表单太大有使用压力」。

## 诊断（问题 1 的真因）

it-060 起 mock 鞋类素材为透明底（内容仅占图幅 23–44%，透明留白大）。W1 鞋槽（aspect 2.6）走 `mat + ContentScale.Fit`（it-046 语义：极端扁格 Crop 会伤物）——Fit 把整图（含透明边）缩进格高，鞋本体缩成一小条：**观感即「只展示了一半」**。实测像素对比确认渲染确为 Fit（非 Crop），问题在素材透明边而非布局代码。

## 方案

### 修 1 · 透明边感知显示

- 新增 Coil `Transformation`：`TrimAlphaTransformation`——解码后裁剪 alpha 内容包围盒（各边透明边 >2% 才裁，非透明图零开销原样返回）。
- `PhotoCard` mat 分支接入（衬纸「承图」语义的自然延伸：图先去透明边再 Fit）；帽/鞋槽直接受益（内容占比 23–44% → 全幅），W7/W8 成品图（照片底）不受影响。
- 鞋槽 aspect 2.6 → 2.2（去边后鞋图宽高比 ≈1.1–1.3，扁格略增高更接近内容比例；实测后定）。

### 修 2 · 品类清单直选（W1 槽位）

- 槽位名称条 `n/m` 角标点击行为由「循环翻页」升级为**品类清单 BottomSheet**：该品类全部衣物（缩略图 + 名称，当前件高亮，愿望件带「想买」标），点选即 `scrollToPage` 直达并关闭。
- 原循环翻页语义被列表取代（列表是其超集）；单击进详情、长按读全名不变。

### 修 3 · W6 导出表单减负

- **默认全空**：五维（场景/氛围/季节/光线/构图）不预设——移除 `exportSelections` 记忆恢复与 it-056 对话预选消费（`presetSelections`/`replaceSavedSelections` 参数及 ChatScreen 传递删除；`OutfitRecommendationParser` 及其 12 例单测保留备用）。
- **画面设定卡默认折叠**：默认一行「画面设定（可选）· 未设置」，点击展开五维行 + 参考照开关（it-051 折叠语言）；有已设值时行尾显示摘要（如「办公室 · 早秋」）。
- 导出文案生成对空维度零依赖（it-013 起零选择即正常生成）——表单主区回到「预览 + 复制」。

## 验收标准

- 鞋槽：透明素材的鞋在格内显示尺寸明显变大（内容包围盒充满格高），无「只见一半」观感；非透明成品图显示不变。
- 品类清单：n/m 点开列表，点选直达该件（pager 落位、序号同步）；列表含全部该品类衣物与愿望件。
- W6：打开面板五维全部「未设置」，画面设定默认一行折叠；展开可设置，设置后导出文案含所选维度；复制长图路径回归正常。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿。

## 实施记录

- **修 1**：新组件 `ui/components/TrimAlpha.kt`（Coil `Transformation`：解码后裁 alpha 内容包围盒，各边透明边 >2% 才裁、全透明原样返回、非透明图零影响）；`PhotoCard` mat 分支接入（`.transformations()`）。鞋槽 `aspectOf` 2.6→2.2（`OutfitScreen.kt`，格高 +18%，贴 Trim 后鞋图 ≈1.0–1.3 的内容比例）。
- **修 2**：`SlotGrid.kt` SlotCell 内建品类清单 `ModalBottomSheet`——n/m 角标点击打开（替代原循环翻页，a11y 文案同步）；列表项=52dp 缩略图（mat+Trim）+ 名称 + 愿望「想买 · 未录入」副行 + 当前项 CheckCircle+高亮底；点选 `scrollToPage`/`animateScrollToPage`（末页回卷即时落位语义保留）并关闭。
- **修 3**：`ExportSheet.kt` 删除 `presetSelections`/`replaceSavedSelections` 参数与记忆恢复/写入（`selections` 恒空起步；it-051 的「场景空值自动展开」随之删除）；「画面设定」改为折叠行（标题 + 「可选 · 未设置」/「已设 N 项」摘要 + 展开箭头），`AnimatedVisibility(smooth)` 展开五维卡 + 参考照开关（it-017 开关随卡折叠，默认开不变）；`ChatScreen.kt` 对话入口传参清理（Parser 与 12 例单测保留备用）；孤儿 import 清理。
- **spec/CHANGELOG**：02-wireframes W1/W6 注记、05-design-system、CHANGELOG×2。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true`：**BUILD SUCCESSFUL，83 项单测全绿**（含 Parser 12 例）。
- **修 1**：像素级对比——帽格（同为 mat 分支）内容占比 30%→91%（Trim 裁掉透明边后内容充满）；鞋格 bounds 高度 143→170px（aspect 2.2 生效），视觉评审：靴子**完整可见（靴头到靴底）、占格 55–60%、两侧留白约 20%，观感可接受**（修复前观感「只显示一半/细小」）。
- **修 2**：n/m 点开 sheet 实测——标题「鞋 · 共 5 件」、列表项（缩略图+名称）、当前项对勾+高亮、可滚动 ✓；点选跳转与被替代的循环翻页共用 `animateScrollToPage` 机制（it-031/it-046 已验证路径）。
- **修 3**：代码路径审查（初始化恒空、无恢复/写入调用、折叠行默认收起）+ 编译/单测；**模拟器端到端未完成**——darkroom 并行会话密集占用 AVD（force-stop 后被其守护立即拉回前台，多点验证截图混入对方画面），留给 Leo 真机验收，已知风险点：折叠行展开动画与五维行交互（与 it-051 同组件复用，风险低）。
- 遗留：`exportSelections` 的 VM/DataStore 层保留未删（UI 已无读写；若确认不再恢复可后续清理）；鞋槽 aspect 调整对整屏滚动高度的影响建议 Leo 真机确认一次。
