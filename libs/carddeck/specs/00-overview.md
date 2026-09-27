# carddeck · 侧滑卡组 + 随机抽取 SDK

## 是什么

跨应用复用的通用交互 SDK（AGENTS.md `libs/` 规范）：**侧滑卡组浏览 + 老虎机式随机抽取**。
eats 用它替换转盘（食堂卡片带照片/标签/链接，可翻看也可随机抽），wardrobe 用它浏览已保存穿搭并随机抽一套。

## 选型（it-047：官方 AnchoredDraggable 自研内核）

手势与动画为 SDK 自研，实现在 `libs/carddeck/src/main/java/com/leo/libs/carddeck/CardDeck.kt`：
底座是官方 `AnchoredDraggable`（foundation 1.8.3，零 experimental、@Stable、无需 OptIn）。
it-047 生态调研确认 Compose 生态无可直接接入的高口碑卡组库，已删除原三方
[compose-swipeable-cards](https://github.com/smartword-app/compose-swipeable-cards) 依赖与其 JitPack 仓库
（背景、候选对比与决策见 `wardrobe/specs/iterations/it-047-carddeck-official-rebuild.md` 与
wardrobe `specs/06-decisions.md` ADR-025）。

- **锚点模型** `DeckAnchor { Rest, Forward(-1.5W), Backward(+0.45W) }`（W = 卡组宽）；动态 anchors——
  非循环端点不建锚即手势自动禁用；循环回看在小牌堆（n ≤ 层数）时禁锚（tuck 几何撞车），`previous()` 退化为即时换位。
- **单一弹簧源**：手势落定（自研 performFling 内 animate）、程序化 `animateTo`、回中全走
  `DeckStyle.flyOutSpec = spring(0.9f, 500f)`（it-046 基准）；旋转 = 位移/50 派生、堆叠晋升 = 位移插值派生，
  无第二套动画。
- **自研 `flingTarget()`**：速度 ≥125dp/s 按方向甩，否则按 100dp 位置阈值——官方 computeTarget 在 v=0 时
  只取最近锚点（位置阈值不参与，实测丢甩出）是自研的直接动因。
- **提交管线**（it-048 修订为**到达帧模型**）：提交信号 = `offset` 精确到达非 Rest 锚点
  （±0.5px，所有落定路径——自研 performFling 残差补足 / `animateTo` / `snapTo` / 抽取 overload
  收尾——都有精确落锚帧）且三重落定门全开（`!pointerDown` 手势未按住、`!isAnimationRunning`
  程序化动画不在途、`!flingInProgress` 自研 fling 在途）。幂等门 `committedTarget` 挡同一落定重放；
  提交 = 索引推进 + `dispatchRawDelta(-offset)` 同步复位 + `applyAnchors()`（全部同步无挂起点）+
  挂起 `snapTo(Rest)` 兜底归位。~~settledValue 翻转观察~~已废：连滑同向落锚 settled 不翻转
  （Forward→Forward），旧模型把第二次甩出整单吞掉（实测「连续滑动滑不动」的直接根因）；
  `flingInProgress` 门必须独立存在——fling 的 spring animate 不在官方动画跟踪内
  （drag block 执行中 dragStatus=Dragging），缺它则飞越锚点即提交、复位被动画拉回、连环推进
  （实测一次甩卡 1/5→5/5）。
- **按下快进结算** `onDeckDown(target)`（it-048）：手势按下瞬间若 `flingTarget()` 决策出甩出/归入
  （fling 飞行中按下取其决策目标；停在锚上的落定窗口按位置阈值），立即同级 `anchoredDrag`
  拿锁原子提交并复位——新手势从干净 Rest 起步拖「新顶卡」，连滑间隔小于飞行动画时长也逐张推进。
  launch 内复查挡竞态（offset 已被其他路径改动则放弃，不抢用户的手）。
- **`drawRandom` 快速飞出** `flyForwardQuick()`：spring 动画**首达锚点即 cancel**（飞出目标在屏外，截尾无损）。
- **减弱动态** `rememberDeckReduceMotion()` 读 `Settings.Global.ANIMATOR_DURATION_SCALE`（单一入口）：
  落定 `snap(0)`、drawRandom 每步即时落位（保留步距节奏与每步 Confirm 震）、拖拽保持 1:1（输入非动画）。
- **触感**：拖拽越 100dp 阈值一次 `GestureThresholdActivate`（仅真实按压期，re-arm）；抽中落定 Confirm 由 app 层调
  （`HapticFeedback.performConfirm()` helper：API30+ Confirm / 低版本 LongPress，置于本 SDK 内）。

备选 makzimi/SwipingCards（Maven Central）被否：minSdk 33 高于两应用基线 26，且无程序化接口、无法编排抽取。

SDK 对外仍是 `CardDeck` composable（渲染）+ `CardDeckController`（next / previous / restart / drawRandom）。
`drawRandom` = 随机步数（4 + rand(size)，对 size 取模落点均匀、**无权重**）+ 按拍真实甩出，落定返回顶部卡片。

## 契约

```kotlin
@Composable
fun <T> CardDeck(
    items: List<T>,
    modifier: Modifier = Modifier,          // 卡组尺寸由调用方决定
    style: DeckStyle = DeckStyle(),         // it-047：SDK 自有参数类，替代旧 SwipeableCardsProperties/Animations
    circular: Boolean = true,
    reduceMotion: Boolean = rememberDeckReduceMotion(),   // 减弱动态单一入口
    onSwipe: ((item: T, toRight: Boolean) -> Unit)? = null,
    cardContent: @Composable (T) -> Unit,
): CardDeckController<T>

data class DeckStyle(
    val stackedCardsOffset: Dp = 14.dp,     // 堆叠层间偏移（每深一层向左下各偏移该值）
    val padding: Dp = 6.dp,                 // 卡组容器内衬
    val visibleCardsInStack: Int = 3,       // 参与堆叠渲染的顶层数量（不含手势回看的上一张）
    val swipeThreshold: Dp = 100.dp,        // 手势提交位置阈值：超过即甩出，否则回中
    val rotationDivisor: Float = 50f,       // 顶卡旋转 = 位移(px) / 该值
    val enableHapticOnThreshold: Boolean = true,          // 拖拽越阈值给一次 GestureThresholdActivate
    val flyOutSpec: AnimationSpec<Float> = spring(0.9f, 500f),  // 单一弹簧源（it-046 基准）
)

class CardDeckController<T> {
    val current: T?          // 顶部卡片
    val isDrawing: Boolean
    val currentIndex: Int    // 当前索引（卡序胶囊 ‹n/m› 数据源）
    fun next()               // 程序化翻张（末尾回开头）
    fun previous()           // 程序化回翻（首张前回绕至末张）
    fun restart()
    suspend fun drawRandom(onStep: (T) -> Unit = {}): T?   // 纯随机抽取
}
```

## 接入

各应用 `settings.gradle.kts`：`includeBuild("../libs/carddeck")`；
依赖：`implementation("com.leo.libs:carddeck:0.1.0")`（composite build 坐标替换）。

## 修订（2026-09-21，消费方反馈）：双向循环 + 堆叠上限

- 循环：`circular`（默认 true）——手势滑走末张后经动态 anchors（`updateAnchors`）自动回卷，修复原「滑到末尾卡组清空」（it-003 遗留）；`previous()` 在首张前回绕至末张。
- 双向：`previous()`/`next()` 程序化双向；手势回看 = 顶卡归入牌堆、上一张滑入（Backward 锚 +0.45W）——循环回看在小牌堆（n ≤ 层数）时禁锚（tuck 几何撞车），此时 `previous()` 退化为即时换位。
- ~~堆叠上限：`visibleStack` 参数透传库 `visibleCardsInStack`~~ **撤回（it-047 订正：原系误判）**：当初「State 构造首个 int 误传致索引越界」经 it-047 核实系 remember 辅助函数签名与 State 构造器不一致导致的误判，并非库无安全入口；该条已随三方库删除作废。新内核 `visibleCardsInStack` 为 `DeckStyle` 显式字段（默认 3），堆叠层数可直配。

## 修订（2026-09-26，it-046 动画丝滑化）

it-046 时对原三方库 1.1.4 反编译实证：飞卡默认 `spring(dampingRatio=0.6, stiffness=100)`（落定 ≈1s、9% 过冲晃尾）；
其 `moveNext()` 会把上一张从飞行集强制摘除——步距短于飞行时长时卡片半空消失。该次修订确定的**弹簧与节奏基准**
由 it-047 自研内核原样承接（注入面从 `animations` 参数改为 `DeckStyle.flyOutSpec`，三方库与
`SwipeableCardsProperties/Animations` 类型均已删除）：

- **弹簧基准** `spring(0.9, 500)`（≈0.32s 到位、<0.5% 过冲）：it-046 经 `animations` 参数注入 wardrobe 卡组，
  it-047 起为 `DeckStyle.flyOutSpec` 默认值，手势落定 / 程序化翻张 / 回中共用（单一弹簧源）。
- **`drawRandom` 节奏**：步数 `4 + rand(size)`（旧 12+rand 对大列表过长）；步距 420ms 起
  ×1.18 封顶 560ms（步与步起始间距，飞行时长计入步距内，超出则顺延）；每步真实甩出
  （it-047 `flyForwardQuick` 首达锚点截停，无半空摘除、无瞬移）；`size==1` 直接返回不甩；
  收尾自末步起算 560ms 等落定。
- 落点均匀性不变（步数对 size 取模覆盖全剩余系）；wardrobe/eats 同步受益（同一 SDK 行为）。

## 修订（2026-09-27，it-048 连滑提交与甩出裁剪 hotfix）

消费方（wardrobe W8 穿搭记录页）报障两交互问题，根因都在 SDK 提交管线与宿主容器：

1. **连滑吞提交/滑不动**：it-047 提交管线以「settledValue 翻转」为信号——快速连滑时第二次
   甩出落锚 Forward→Forward 不翻转、观察者不发射，提交整单丢失；滞留的 Forward settled 还会让
   `tryCommit` 尾部排队的 `snapTo(Rest)` 在用户下一次拖拽/甩出中抢锁，把手中卡片强拉回中位。
   修法：提交信号改**到达帧观察**（offset 精确到锚 + 三重落定门，详见「提交管线」条），并新增
   **按下快进结算** `onDeckDown`——按下瞬间未落定的飞出立即原子提交，新手势从 Rest 干净起步。
2. **甩卡被容器边距截断**：宿主（wardrobe RecordsScreen / eats SpinScreen）沿用 it-031 旧库时代
   的 `clipToBounds()` 包裹卡组——it-047 全路径甩卡是真实飞行（-1.5W 出屏），飞越容器边距即被
   裁成两截。修法：两处宿主删 `clipToBounds()`，并留「不得加回」注释。**SDK 契约补充：卡组容器
   不得裁剪（不 clip），甩出动画需要溢出边界的空间。**

验证（emulator-5554 实测）：单甩恰好 +1（1/5→2/5）；间隔 250ms 连滑两次恰好 +2（2/5→4/5）；
`previous()` 回卷正常；`drawRandom` 抽取/按钮态/落点无回归；eats 连甩逐张推进；飞行帧卡片完整
飞出屏幕无裁剪；双端单测绿、全程无崩溃。
