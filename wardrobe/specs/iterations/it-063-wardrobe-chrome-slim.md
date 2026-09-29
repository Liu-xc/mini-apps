# it-063 · W3 衣橱页 chrome 减负——渐隐修色、FAB 化新增入口、底距回收、卡菜单钮降噪

> 状态：**实施中**（2026-09-29，Leo 体验包四点反馈）
> 来源：①「顶部滚动栏的阴影没处理好，有一部分还是透出的」；②「底部的添加衣物按钮过于大了，应该做类似 float button 的效果」；③「底部预留空间太大（按钮上面那块白的），屏幕效率低」；④「每个衣物卡右上角的菜单按钮 UI 太突兀，需要优化」。

## 诊断

1. **渐隐带宽盖不住图标（①的真因，含一次勘误）**：初判「渐隐色 paper 与页面底色 surface 错配致脏带」——实机像素采样推翻：页面真实底色由 activity 主题 `windowBackground=@color/paper` 决定（实测 #F7F7F5），渐隐色 paper 本来就对。真因是 W3 品类图标为 44dp 圆钮，28dp 渐隐带窄于图标：被裁图标前段全亮直到硬切，且残影贴近「筛选」钮——即「有一部分还是透出的」。修法＝带宽按元素宽取值 + 渐隐提前封满，色不动。
2. **全宽大按钮 + 底部留白（②③）**：it-037 把新增入口从 FAB 改为全宽按钮（当时理由：FAB 盖卡片）。全宽 48dp 按钮占满底栏上方一整条，视口末卡与按钮之间还剩一段纯 paper 死区；Leo 现明确要回浮动样式——it-037 该条验收由本迭代正式替代。
3. **卡菜单钮（④）**：28dp 40% 黑底圆 + 白 MoreVert，每卡一枚重复出现，在浅色衬纸上视觉权重过重。

## 方案

### 修 1 · W3 品类行渐隐加宽提前封满（色不变）

- `FadingScrollRow` 新增 `opaqueStop: Float = 1f` 参数：渐隐在带宽的该比例处提前到达全遮盖（默认 1f = 原线性，其他落点行为不变）。
- W3 品类行：`fadeWidth 28 → 44dp`（一整枚图标宽）+ `opaqueStop = 0.8f`——带尾 ~9dp 已是纯底色，被裁图标在距「筛选」钮 ≥17dp 处彻底消隐，不再贴身。

### 修 2/3 · 新增入口 FAB 化 + 底距回收

- 移除全宽「＋ 添加衣物」按钮，改 **56dp FloatingActionButton** 右下角浮动：`containerColor = primary`（浅 #111111 / 深 #F2F2EF）、图标 Add 24dp、contentDescription「添加衣物」；边距 end 20dp（与卡列右缘对齐）/ bottom 16dp（底部导航上方，M3 常规）。
- 网格（含空态容器）占满内容区落到底部导航；`contentPadding.bottom 12 → 96dp`，静止位末卡整卡（含标签行）完全脱离 FAB 区，滚动中穿过 FAB 属标准 Material 语义（替代 it-037「任何滚动位置 CTA 不覆盖卡片」验收）。
- 空态文案：「点下方『添加衣物』拍照录入第一件」→「点右下角 ＋ 拍照录入第一件」；空态/筛选空态 FAB 常驻。

### 修 4 · 卡右上菜单钮降噪（编辑风印刷点）

- 28dp 40% 黑底圆 + 白 MoreVert 18dp → **26dp paper 圆片 + 1dp hairline 描边 + ink MoreHoriz 16dp**：浅色下如纸面印刷点融入衬纸，深色下圆片自带对比；任意照片背景均由不透明圆片保证可读。
- 热区维持显式 48dp（it-033 基线不回退）、菜单项（编辑/删除）与长按快捷不变；图标 MoreVert → MoreHoriz 对齐线框「···」字形。

## 验收标准

- 品类行：被裁图标呈连续淡出，带宽覆盖完整图标；渐隐带颜色与页面底色无可见色差（深浅两模式）；带尾至「筛选」钮之间无图标残影贴边。
- 新增入口：右下角 56dp 圆形 FAB，网格静止位末卡不被 FAB 遮挡；空态与筛选空态 FAB 可见可用，点击直达 W4。
- 底部：无全宽按钮与死区白块，卡片网格落至底部导航上缘，仅剩滚动底缘渐隐。
- 卡菜单钮：浅色下视觉权重显著降低（无黑底圆），深浅两模式与任意照片底上 ··· 可读；点击弹「编辑/删除」行为不回归。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿。
- 同步 02-wireframes（W3 布局/菜单钮/渐隐）与 05-design-system（渐隐规范值）。

## 实施记录

- `ScrollFade.kt`：`FadingScrollRow` 新增 `opaqueStop: Float = 1f`（gradient stops `0f→透明, opaqueStop→fadeColor`，默认行为不变）；fadeColor 维持 paper（勘误见诊断）。
- `WardrobeScreen.kt`：品类行 `fadeWidth=44dp, opaqueStop=0.8f`；内容区重构为 `Box(weight 1f){网格/空态 + FAB(BottomEnd, end 20dp/bottom 16dp, primary/onPrimary, 56dp, Add 24dp)}`；删除全宽按钮；网格 `contentPadding.bottom 12→96dp`；空态文案改「点右下角 ＋ 拍照录入第一件」；ItemCard 菜单钮改 26dp paper 圆片 + 1dp hairline + ink MoreHoriz 16dp（热区 48dp 不变，DropdownMenu 不变）。
- 同步：02-wireframes（W3 描述/it-033 注记/C11/it-037 全宽按钮条目替代/交互路径图）、05-design-system（渐隐规范增补带宽规则与底色实测规则、it-037 固定操作入口条目划改）。

## 验证记录（2026-09-29，AVD wardrobe_qa 独占实测）

- 构建/测试：`:app:assembleDebug :app:testDebugUnitTest` 全绿（含并行会话 it-061 在途改动同树编译）。
- 亮色 W3（36 件演示语料）像素实测：FAB 黑盘 (882,1990)-(1026,2082) 宽 144px≈56dp ✓；菜单钮 disc=(247,247,245) paper 精确、卡面 #FFF、glyph 25 暗像素 ✓；渐隐带 x742–892 纯 paper、无色带、被裁图标残影仅阈值级（x870-880 微弱边缘）✓；末卡静止位（滚到底）白像素止于 y≈1874，FAB 盘 y1937-2084 区内除盘体与白色＋笔画外全为纸底 ✓；底部导航与网格间无死区白块（早前误报实为导航栏本身）✓。
- 暗色（cmd uimode night）：页底 #111110=paper_dark、FAB 亮盘 #F2F2EF 同位、菜单 disc=paper_dark+25 亮 glyph、渐隐带六采样点全纯底 ✓。
- 交互：FAB 点按 → W4 编辑页（拍照占位/品类 chips/保存栏）✓；卡 ··· 点按 → 编辑/删除下拉 ✓；慢拖滚到底净空如上 ✓。
- 视觉模型中性提示词复核（不喂预期值）：四项均判正常，无异常/不平衡报告。
- 过程教训：共享 AVD（wardrobe_test）被并行会话连续抢占致两轮截图作废，改起独占 headless AVD（wardrobe_qa）后全程无干扰；首轮视觉评审因提示词包含预期值被镜像复述，改用「像素采样 + 中性提示词」双轨后既纠正了评审、也推翻了本人「渐隐色错配」的错误初判（见诊断勘误）——均已沉淀 LESSONS。

