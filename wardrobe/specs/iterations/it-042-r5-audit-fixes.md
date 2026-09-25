# it-042 · 第五轮走查修复（状态可读性 / 成品图完整呈现 / 文本与边缘一致性）

- **状态**：已实现，验收通过
- **来源**：[2026-09-25 第五轮走查（轻量）](../../../reports/2026-09-25-wardrobe-audit/) C1–C8（P0×0 / P1×2 / P2×6，逐条含源码或 dump 证据）
- **关联**：US-42；02-wireframes「it-042 交互注记」；05-design-system 底缘渐隐一条

## 背景与动机

r5 走查在 it-037～039 合入后的最新构建上复审 W1–W10 共 14 个状态，核心交互全部通过，但发现两处 P1 与六处 P2：

- **P1-1（W2）**：当前角色行「Leo使用中」零间距粘连。`PersonSheet` 中「使用中」Text 没有 start padding——it-037 删除 ✓ 符号后，原本由勾号撑开的间隔消失，dump 实测两文本 x 坐标 225 首尾相接。
- **P1-2（W8）**：卡组 hero 成品图裁掉头部。`OutfitDeckCard` 用 PhotoCard 默认 `ContentScale.Crop` 塞进 `weight(1f)` 宽盒（视觉宽高比 ≈1.1），0.8 左右的竖图居中裁掉约三成高度；同页网格缩略（OutfitThumb 0.86 近原比）头部完整——同页同图两种呈现。代码注释写「全幅展示」，与实现矛盾。
- **P2**：日期格式双轨（`OutfitThumb` 仍 `MM/dd`）；W1 极窄槽位名只显示 2–3 字；W6 长图预览占比（it-017 已知债务，本轮不动）；W9 打卡为 0 时「闲置清单 20/20」与打卡空态重复；W10 域名行受全局 `labelSmall letterSpacing=2sp` 影响字距过散；W1/W3 网格内容在视口底缘拦腰裁切、无渐隐提示。

## 用户故事

- **US-42a**：作为用户，在角色切换弹层里我能分读角色名与「使用中」状态，不把它们看成一个词。
- **US-42b**：作为用户，在穿搭记录卡组里看到的成品图与网格缩略一致，人物头部完整不被裁掉。
- **US-42c**：作为用户，全站日期格式与域名等小字呈现一致、不出现异常字距。
- **US-42d**：作为用户，滚动列表底缘有「下方还有内容」的渐隐提示；打卡为 0 时回顾页不出现「全部闲置」的冗余卡片；W1 窄槽位里长按能读到完整名称。

## 验收标准

- W2 当前角色行：角色名与「使用中」之间 ≥8dp 间距；行结构、浅底、切换行为不变。
- W8 卡组 hero：成品图人物头部完整可见；无成品图时的拼贴分支、标签行、卡片点击行为不变；与网格缩略不再出现同图两种裁切。
- `OutfitThumb` 日期改 `yyyy/MM/dd`（与 W7 顶栏、评论时间线一致）；W10 卡片域名行字距归零（不再受 2sp tracking 影响），颜色/字号不变。
- W1 槽位长按（照片区）Toast 显示当前单品完整名称；单击仍进 W5、格内左右滑换衣、分页角标与 ✕ 行为均不回退。
- W9 打卡次数为 0 时隐藏「闲置清单」卡（含其后 spacer）；`hasWearData` 为真时照常显示并可进二级页。
- W1 滚动列与 W3 网格：内容可继续滚动时底缘叠 28dp `透明→页面底色` 渐隐，滚到底/内容一屏内放下即隐；与 FadingScrollRow 同一语言。
- W6 长图预览占比本轮不改（it-017 挂账）。
- 更新 `01-user-stories.md`、`02-wireframes.md`、`05-design-system.md` 相关注记。
- 构建 + `./gradlew test` 通过；模拟器复验 W2/W8/W9/W10/W1/W3 各状态并回填本节验证记录。

## 影响范围

`ui/outfit/PersonSheet.kt` · `ui/records/RecordsScreen.kt` · `ui/detail/ItemDetailScreen.kt` · `ui/components/SlotGrid.kt` · `ui/components/ScrollFade.kt` · `ui/recap/WardrobeRecapScreen.kt` · `ui/wishlist/WishlistScreen.kt` · `ui/outfit/OutfitScreen.kt` · `ui/wardrobe/WardrobeScreen.kt` · `specs/01-user-stories.md` · `specs/02-wireframes.md` · `specs/05-design-system.md`。

## 验证记录

**2026-09-25 · 编译/单测/装机复验全部通过**

- `./gradlew compileDebugKotlin` 通过；`./gradlew test` exit 0 全绿；`installDebug` 装入 emulator-5554。
- 过程中修一处调用形态：`fadingBottomEdge { … }` 尾随 lambda 被绑定到最后一个参数（fadeColor），改具名实参 `fadingBottomEdge(active = { … })` 后编译通过。

**模拟器逐项复验（新构建 + dump/截图证据）：**

| 项 | 断言 | 结果 |
|---|---|---|
| C1 W2 状态分读 | dump bounds：`Leo` 右缘 225 →「使用中」左缘 246，间距 **21px ≈ 8.2dp**（原 0px 粘连） | ✅ |
| C2 W8 hero 裁头 | 复验截图：成品图人物**头部/面部/全身完整**，衬纸两侧填充；与网格缩略同图同完整度 | ✅ |
| C3 日期统一 | W8 网格 dump 出现 `2026/09/24` ×5，无 `MM/dd` 节点 | ✅ |
| C4 长按读全名 | `input swipe 700ms` 长按帽格 → dump 出现 toast 节点「黑色棒球帽」（全名，非省略形） | ✅ |
| C6 闲置卡隐藏 | W9 dump：`闲置清单` 0 次、`还没有穿搭打卡` 空态 1 次（打卡=0 时隐藏、空态保留） | ✅ |
| C7 域名字距 | W10 `example.com` 节点宽 **227px → 169px**（2sp tracking 消失），截图目检正常紧凑 | ✅ |
| C8 底缘渐隐 | W3 底缘全分辨率裁图：第二行标题「奶油色针织开衫/海军条纹针织」向页面底色**渐隐溶解**（原拦腰硬切）；W1 当前 6 槽一屏放下→内容不溢出，渐隐按「可滚才显示」正确不亮；像素探针确认无多余色带 | ✅ |

- 回归走查：W1 随机/单击进详情、W3 筛选切换、Tab 切换、W10 分段与 FAB 均正常；无新增崩溃。
- 截图证据：`reports/2026-09-25-wardrobe-audit/assets/v2-*.png`。
- C5（W6 预览占比）按提案保持挂账未动。
