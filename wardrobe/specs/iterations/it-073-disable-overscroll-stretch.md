# it-073 · 全站禁用滚动 overscroll 拉伸效果

- 状态：**已实施**（2026-09-30；Leo 反馈「列表滚动区的弹簧绳效果去掉」）
- 提出日期：2026-09-30
- 涉及应用：wardrobe

## 背景与修法

Leo 不喜欢应用内滚动区（列表/网格/pager）到头后跟随手指的「弹簧绳」拉伸效果（Android 12+ 系统 overscroll stretch）。属纯视觉偏好修订，跳过①直接实施。

- **修法**：`WardrobeTheme` 根 `CompositionLocalProvider` 增加 `LocalOverscrollConfiguration provides null`（foundation 实验 API，`@OptIn(ExperimentalFoundationApi::class)`），一处生效全站——所有 LazyColumn/LazyGrid/pager/弹层列表到头即停。
- **不影响**：fling、pager 吸附（EditorialMotion.pagerFling）、嵌套滚动、滚动底缘渐隐（自绘）。
- **范围**：仅 wardrobe；eats/darkroom 未动（各自偏好各自立项）。

## 验证记录（2026-09-30）

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL。
- AVD（wardrobe_test，lastUpdateTime 先行核对 02:20:27 防快照回滚）：
  - ✅ W3 网格连续下拉钉死顶部（连拍 diff=0.0 确认到头）→ 1.5s 慢速顶部下拉中连拍 **diff=0.00**（禁用前拉伸会带动内容、剖面 diff 显著）；
  - ✅ 回归：fling 上滑 diff=23.7，滚动正常；
  - 结论：拉伸效果全站消失，滚动行为无回归。
- specs 同步：05-design-system（动效清单头注）。
