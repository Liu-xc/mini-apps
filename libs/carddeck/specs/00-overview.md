# carddeck · 侧滑卡组 + 随机抽取 SDK

## 是什么

跨应用复用的通用交互 SDK（AGENTS.md `libs/` 规范）：**侧滑卡组浏览 + 老虎机式随机抽取**。
eats 用它替换转盘（食堂卡片带照片/标签/链接，可翻看也可随机抽），wardrobe 用它浏览已保存穿搭并随机抽一套。

## 选型（不自研手势动画）

手势与动画来自三方库 [compose-swipeable-cards](https://github.com/smartword-app/compose-swipeable-cards)
（Apache-2.0，JitPack 分发）：左右滑 + 堆叠缩放/旋转 + 程序化 `swipe()/moveNext()/setCurrentIndex()`。

备选 makzimi/SwipingCards（Maven Central）被否：minSdk 33 高于两应用基线 26，且无程序化接口、无法编排抽取。

本 SDK 是薄封装：`CardDeck` composable（渲染）+ `CardDeckController`（next / restart / drawRandom）。
`drawRandom` = 随机步数（4 + rand(size)，对 size 取模落点均匀、**无权重**）+ 按拍加速—减速地调用库的飞出动画，落定返回顶部卡片。

## 契约

```kotlin
@Composable
fun <T> CardDeck(
    items: List<T>,
    modifier: Modifier = Modifier,          // 卡组尺寸由调用方决定
    properties: SwipeableCardsProperties = SwipeableCardsProperties(),
    animations: SwipeableCardsAnimations? = null,   // it-046：飞卡动画注入，null=库默认
    circular: Boolean = true,
    onSwipe: ((item: T, toRight: Boolean) -> Unit)? = null,
    cardContent: @Composable (T) -> Unit,
): CardDeckController<T>

class CardDeckController<T> {
    val current: T?          // 顶部卡片
    val isDrawing: Boolean
    fun next()               // 程序化翻张（末尾回开头）
    fun restart()
    suspend fun drawRandom(onStep: (T) -> Unit = {}): T?   // 纯随机抽取
}
```

## 接入

各应用 `settings.gradle.kts`：`includeBuild("../libs/carddeck")`；
依赖：`implementation("com.leo.libs:carddeck:0.1.0")`（composite build 坐标替换）。

## 修订（2026-09-21，消费方反馈）：双向循环 + 堆叠上限

- 循环：`circular`（默认 true）——手势滑走末张后 `setCurrentIndex(0)` 自动回首张，修复库原生「滑到末尾卡组清空」（it-003 遗留）；`previous()` 在首张前回绕至末张。
- 双向：`previous()`/`next()` 程序化双向；手势往回翻依赖库原生 `canSwipeBack = index > 0`（首张的手势回翻不可用，用 ‹ 按钮/程序化补足）。
- ~~堆叠上限：`visibleStack` 参数透传库 `visibleCardsInStack`~~ **撤回（实验证伪）**：State 构造首个 int 实为初始索引类参数，误传任意值会把索引顶到越界（卡组空白、卡序胶囊 coerce 掩盖显示）；堆叠层数用库默认，等源码确认正确入口后再加。

## 修订（2026-09-26，it-046 动画丝滑化）

库 1.1.4 反编译实证：飞卡默认 `spring(dampingRatio=0.6, stiffness=100)`（落定 ≈1s、9% 过冲晃尾）；
`swipe()` 自带 `moveNext()` 但**末张不推进索引**、且不触发手势路径的回卷回调（该回调只在
用户手势提交时同步触发）；`moveNext()` 会把上一张从飞行集强制摘除——步距短于飞行时长时
卡片半空消失。修订：

- **`animations` 参数透传** `LazySwipeableCards`（库默认 = `spring(0.6,100)`）；wardrobe
  卡组注入 `spring(0.9, 500)`（≈0.32s 到位、<0.5% 过冲）。
- **`drawRandom` 节奏**：步数 `4 + rand(size)`（旧 12+rand 对大列表过长）；步距 420ms 起
  ×1.18 封顶 560ms（旧 55ms 起步远短于飞行时长 → 多张叠飞/半空摘除）；末张走
  「真实 `swipe` + 立即 `setCurrentIndex(0)`」组合步（飞行照常、复位即揭示，等价手势
  路径），不再纯瞬移；`size==1` 直接返回不甩；收尾 `delay(560)` 等落定。
- 落点均匀性不变（步数对 size 取模覆盖全剩余系）；eats 同步受益（同一 SDK 行为）。
