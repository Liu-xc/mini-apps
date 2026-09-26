package com.leo.libs.carddeck

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.random.Random

/**
 * 卡组堆叠与手势的视觉/物理参数（it-047：替代旧 SwipeableCardsProperties，SDK 自有参数类）。
 *
 * @property stackedCardsOffset 堆叠层间偏移：每深一层向左下各偏移该值（复刻旧库层叠观感）
 * @property padding 卡组容器内衬（旧库 LazyLayout padding 语义：end = padding、top = padding/2）
 * @property visibleCardsInStack 参与堆叠渲染的顶层数量（不含手势回看的上一张）
 * @property swipeThreshold 手势提交的位置阈值（旧库同款 100dp：超过即甩出，否则回中）
 * @property rotationDivisor 顶卡旋转系数：旋转角 = 水平位移(px) / 该值（旧库 offset.x/50 同款）
 * @property enableHapticOnThreshold 拖拽越过阈值时给一次 GestureThresholdActivate 触感
 * @property flyOutSpec **单一弹簧源**：甩出/回中/程序化翻张/落定共用的动画规格——
 *   手势 settle 与程序化 animateTo 走同一 spec（DESIGN.md「弹簧不叠加」）。默认 it-046 实测基准
 *   `spring(0.9, 500)`（≈0.32s 到位、<0.5% 过冲）。
 */
data class DeckStyle(
    val stackedCardsOffset: Dp = 14.dp,
    val padding: Dp = 6.dp,
    val visibleCardsInStack: Int = 3,
    val swipeThreshold: Dp = 100.dp,
    val rotationDivisor: Float = 50f,
    val enableHapticOnThreshold: Boolean = true,
    val flyOutSpec: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 500f),
)

/** 卡组锚点：Rest=静止；Forward=顶卡向左甩出（下一张）；Backward=顶卡归入牌堆（上一张）。 */
internal enum class DeckAnchor { Rest, Forward, Backward }

/** 顶卡「归入牌堆」的拖拽行程占卡宽比例——行程终点顶卡恰好落在堆叠第 1 层，提交零跳变。 */
private const val TUCK_FRACTION = 0.45f

/** 读取系统「移除动画」状态（ANIMATOR_DURATION_SCALE ≤ 0）。 */
private fun readReduceMotion(context: android.content.Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) <= 0f
} catch (t: Throwable) {
    false
}

/**
 * 系统「移除动画」（开发者选项动画时长缩放 = 0）感知——DESIGN.md §3 降级红线的入口之一
 * （wardrobe 侧对应 EditorialMotion.reduceMotion，读同一 Settings 事实源）。
 * 注册 ContentObserver 实时响应开关切换，无需重启应用。
 */
@Composable
fun rememberDeckReduceMotion(): Boolean {
    val context = LocalContext.current
    val reduce = remember(context) { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce.value = readReduceMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce.value
}

/** 抽中落定触感：API 30+ 用 Confirm，低版本 LongPress 兜底（DESIGN.md §4）。 */
fun HapticFeedback.performConfirm() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackType.Confirm
        else HapticFeedbackType.LongPress,
    )
}

/** 单张卡的绘制位姿（由 [CardDeckController.placement] 计算，draw 阶段读取，不触发重组）。 */
internal data class DeckPlacement(
    val x: Float,
    val y: Float,
    val rotationDeg: Float,
    val alpha: Float,
)

/**
 * 卡组控制器：浏览（程序化双向翻张）与纯随机抽取（无权重）。
 *
 * it-047 起为官方 API 自研内核（foundation `AnchoredDraggable`），对外契约与旧封装完全一致：
 * - **单一 spec 源**：手势落定（fling snap）与程序化 [animateTo][AnchoredDraggableState.animateTo]
 *   共用 [DeckStyle.flyOutSpec]；旋转/堆叠晋升均由位移驱动派生，不存在第二套动画
 *   （旧库「飞出弹簧 + 晋升 tween」双体系即为 it-047 要消灭的结构性缺陷）。
 * - **速度参与判定**：甩出由官方 fling 行为按 125dp/s 速度阈值 + 100dp 位置阈值决定目标锚点。
 * - **全路径真实动画**：提交在锚点落定后同步完成（`dispatchRawDelta` 原子复位 + 索引推进），
 *   无 `setCurrentIndex` 式瞬移分支；中断（动画中再按住）由 AnchoredDraggable 原生接管。
 */
@Stable
class CardDeckController<T> internal constructor(
    private val itemsProvider: () -> List<T>,
    private val circular: Boolean,
    internal val style: DeckStyle,
    private val scope: CoroutineScope,
    private val reduceMotion: () -> Boolean,
    private val pulseConfirm: () -> Unit,
    private val onSwipe: ((T, Boolean) -> Unit)?,
) {
    internal val drag = AnchoredDraggableState(DeckAnchor.Rest)

    /** 卡组容器宽度（px）；由 onSizeChanged 写入并同步刷新锚点。 */
    internal var widthPx: Int = 0
        private set

    /** 程序化操作串行化：next/previous/restart/drawRandom 逐个执行，不产生并发动画撕裂。 */
    private val opMutex = Mutex()

    /** 上次构建锚点时的宽度（宽度变化必须重建锚点，位置随 -1.5W / 0.45W 平移）。 */
    private var anchorWidth = 0

    /**
     * 提交幂等门：一次落定（settledValue == target）至多提交一次。观察者与程序化路径可能在
     * 「settled 已翻转但 settle 协程尚未释放互斥」的窗口内各进来一次（trySnapTo 抢锁失败时
     * settled 滞留 Forward）——此门挡住第二次；settled 归 Rest 时解除。
     */
    private var committedTarget: DeckAnchor? = null

    /** settled 归位 Rest 时由观察者调用，解除幂等门。 */
    internal fun onSettledRest() {
        committedTarget = null
    }

    var index: Int by mutableIntStateOf(0)
        private set

    var isDrawing: Boolean by mutableStateOf(false)
        private set

    val size: Int get() = itemsProvider().size

    /** 当前顶部卡片下标（可观察，用于 ‹ n/m › 卡序胶囊） */
    val currentIndex: Int get() = index

    /** 当前顶部卡片 */
    val current: T? get() = itemAt(0)

    /**
     * 深度 [d] 处的卡片：0 = 顶卡；正数 = 堆叠层；-1 = 上一张（手势回看/回卷用）。
     * [circular] 时按下标取模回卷，卡组永不枯竭。
     */
    fun itemAt(d: Int): T? {
        val n = size
        if (n <= 0) return null
        val list = itemsProvider()
        val raw = index + d
        return if (circular) list[((raw % n) + n) % n] else list.getOrNull(raw)
    }

    // ---------------------------------------------------------------- 锚点

    internal fun onSizeChanged(width: Int) {
        if (width != widthPx) {
            widthPx = width
            applyAnchors()
        }
    }

    /**
     * 按当前宽度/索引/循环性重建锚点（**动态 anchors**：非循环端点不出锚 = 手势自动禁用；
     * 循环回卷靠索引取模，不走已废弃的 confirmValueChange 否决）。
     * 位置无变化时跳过 `updateAnchors`，避免打断进行中的手势（restartable 会因锚点变更重启）。
     */
    internal fun applyAnchors() {
        val n = size
        if (n > 0 && index >= n) index = n - 1
        val w = widthPx
        if (w <= 0) return
        val canForward = n >= 2 && (circular || index < n - 1)
        val canBackward = n >= 2 && if (circular) {
            // 循环回看的「上一张」若已出现在堆叠里（小牌堆 n ≤ 层数），tuck 几何会撞车，禁用回看手势
            n >= style.visibleCardsInStack + 1
        } else {
            index > 0
        }
        if (n == 0) {
            // 空牌堆必须装上 Rest 锚：初始为 emptyAnchors 时手势松手会走 closestAnchor 空表索引崩溃
            if (drag.anchors.size != 1 || drag.anchors.hasPositionFor(DeckAnchor.Forward)) {
                drag.updateAnchors(DraggableAnchors<DeckAnchor> { DeckAnchor.Rest at 0f })
            }
            return
        }
        if (anchorWidth == w &&
            drag.anchors.hasPositionFor(DeckAnchor.Forward) == canForward &&
            drag.anchors.hasPositionFor(DeckAnchor.Backward) == canBackward
        ) {
            return
        }
        drag.updateAnchors(
            DraggableAnchors {
                DeckAnchor.Rest at 0f
                if (canForward) DeckAnchor.Forward at -1.5f * w
                if (canBackward) DeckAnchor.Backward at w * TUCK_FRACTION
            },
        )
        anchorWidth = w
    }

    /**
     * fling 落点决策：速度 ≥ 125dp/s 按方向甩出，否则按 [thresholdPx]（100dp）位置阈值；
     * 目标锚点缺失（非循环端点）时回中。官方 computeTarget 在 v=0 时只取最近锚点，
     * 位置阈值不参与——本方法保证「慢速拖过阈值松手」也能提交。
     */
    internal fun flingTarget(
        offsetPx: Float,
        velocity: Float,
        thresholdPx: Float,
        velocityThresholdPx: Float,
    ): DeckAnchor {
        val canForward = drag.anchors.hasPositionFor(DeckAnchor.Forward)
        val canBackward = drag.anchors.hasPositionFor(DeckAnchor.Backward)
        return when {
            velocity <= -velocityThresholdPx && canForward -> DeckAnchor.Forward
            velocity >= velocityThresholdPx && canBackward -> DeckAnchor.Backward
            offsetPx <= -thresholdPx && canForward -> DeckAnchor.Forward
            offsetPx >= thresholdPx && canBackward -> DeckAnchor.Backward
            else -> DeckAnchor.Rest
        }
    }

    // ---------------------------------------------------------------- 几何

    /**
     * 深度 [d] 的绘制位姿。全部由单一驱动量 [u]（顶卡位移 px）派生：
     * - u < 0（向前甩）：顶卡飞出，堆叠按 qL = -u/W 整体上移一层（晋升与飞出同源，无第二套动画）；
     * - u > 0（向后甩/归入）：顶卡沿 qR = u/(0.45W) 归入第 1 层，上一张从左侧停驻位滑入，
     *   最深层以 (1 - qR) 淡出（提交后它退出渲染，淡出使其无痕）；
     * - 堆叠层位姿 = base(d) → base(d±1) 的线性插值，base(k) = (s·(V-1-k), -s·(V-1-k))。
     */
    internal fun placement(d: Int, u: Float, stackPx: Float): DeckPlacement {
        val w = widthPx.toFloat().coerceAtLeast(1f)
        val v = style.visibleCardsInStack
        val s = stackPx
        fun base(k: Int): Offset {
            val o = s * (v - 1 - k)
            return Offset(o, -o)
        }
        val qL = if (u < 0f) (-u / w).coerceIn(0f, 1f) else 0f
        val tuckDist = (w * TUCK_FRACTION).coerceAtLeast(1f)
        val qR = if (u > 0f) (u / tuckDist).coerceIn(0f, 1f) else 0f
        val b0 = base(0)
        return when {
            d == 0 && u <= 0f ->
                DeckPlacement(b0.x + u, b0.y, u / style.rotationDivisor, 1f)
            d == 0 -> {
                // 归入：终点 = 堆叠第 1 层（V=1 时无堆叠层，直接收出右边界）
                val tuck = if (v > 1) base(1) else Offset(b0.x + w + s, b0.y)
                DeckPlacement(
                    x = (b0.x + u) * (1f - qR) + tuck.x * qR,
                    y = b0.y * (1f - qR) + tuck.y * qR,
                    rotationDeg = (u / style.rotationDivisor) * (1f - qR),
                    alpha = 1f,
                )
            }
            d == -1 -> {
                // 上一张：停驻位右缘恒为 -s（恒在卡外），回看时随 qR 滑入顶位
                val parkX = b0.x - w - s * v
                DeckPlacement(parkX * (1f - qR) + b0.x * qR, b0.y, 0f, 1f)
            }
            else -> {
                val from = base(d)
                val to = if (u > 0f) base(d + 1) else base(d - 1)
                val t = if (u > 0f) qR else qL
                // 向后甩时最深层被推出渲染范围，先淡出避免提交瞬间整张消失
                val alpha = if (u > 0f && d == v - 1 && v > 1) 1f - qR else 1f
                DeckPlacement(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, 0f, alpha)
            }
        }
    }

    // ---------------------------------------------------------------- 提交

    /**
     * 锚点落定后的**原子提交**：索引推进 + `dispatchRawDelta` 同步复位 + 重建锚点（同步链，
     * 中间无挂起点，不会渲染到半提交状态）。[drag.settledValue] 经 updateAnchors 的
     * trySnapTo 同步归位 Rest；仅当拖拽互斥被占用时补一次挂起 snapTo 兜底。
     */
    internal suspend fun tryCommit(target: DeckAnchor): Boolean {
        if (target == DeckAnchor.Rest || drag.settledValue != target) return false
        if (committedTarget == target) return false
        val n = size
        if (n <= 0) return false
        if (target == DeckAnchor.Forward && index >= n - 1 && !circular) return false
        if (target == DeckAnchor.Backward && index <= 0 && !circular) return false
        committedTarget = target
        val swiped = itemsProvider().getOrNull(index)
        index = when (target) {
            DeckAnchor.Forward -> if (index >= n - 1) 0 else index + 1
            else -> if (index <= 0) n - 1 else index - 1
        }
        val u = drag.offset
        if (u != 0f && !u.isNaN()) drag.dispatchRawDelta(-u)
        applyAnchors()
        if (drag.settledValue != DeckAnchor.Rest) {
            // 同步归位（suspend）：本路径（程序化 animateTo 刚返回 / 观察者落定后）互斥空闲时瞬时完成；
            // 若被 settle 拆锁占用则排队等待。**不得改为高优先级异步 launch**——
            // 后到的 PreventUserInput 会取消正在进行的下一步 Default animateTo，
            // 使 drawRandom 静默死在步间（CancellationException 不崩 app，实测坑）
            runCatching { drag.snapTo(DeckAnchor.Rest) }
        }
        if (drag.settledValue == DeckAnchor.Rest) committedTarget = null
        // 甩出回调：提交时同步触发（供上层状态接线；方向 = 顶卡飞出方向）
        if (swiped != null) onSwipe?.invoke(swiped, target == DeckAnchor.Backward)
        return true
    }

    /**
     * 抽取专用快速飞出：spring 首次到达锚点即截停（目标在屏外，截去 settle 尾段视觉无损），
     * 使步距回到 it-046 基准 420–560ms（完整 settle ≈700ms 会使步距超限）。
     * 锚点 overload 在 block 结束后会精确 dragTo 锚位并置 settledValue —— 截停后由它收尾。
     */
    private suspend fun flyForwardQuick(): Boolean {
        if (!drag.anchors.hasPositionFor(DeckAnchor.Forward)) return false
        if (reduceMotion()) {
            drag.snapTo(DeckAnchor.Forward)
            return tryCommit(DeckAnchor.Forward)
        }
        // anchoredDrag(targetValue) overload：block 结束后由其精确 dragTo 锚位并置 settledValue=Forward。
        // 目标取闭包常量而非 restartable 传入的 target：锚点重建（旋转/数量边界）触发 block 重启时，
        // latestTarget 可能被 updateAnchors 改道回 Rest，闭包常量保证动画与最终落位恒为 Forward。
        drag.anchoredDrag(DeckAnchor.Forward) { anchors, _ ->
            val to = anchors.positionOf(DeckAnchor.Forward)
            if (!to.isNaN()) {
                kotlinx.coroutines.coroutineScope {
                    var job: kotlinx.coroutines.Job? = null
                    job = launch {
                        var prev = if (drag.offset.isNaN()) 0f else drag.offset
                        androidx.compose.animation.core.animate(
                            initialValue = prev,
                            targetValue = to,
                            initialVelocity = 0f,
                            animationSpec = style.flyOutSpec,
                        ) { v, _ ->
                            dragTo(v)
                            // 前向飞出（to 更负）：首次下穿锚点即终止（最终精确落位由 overload 收尾）
                            if (prev > to && v <= to) job?.cancel()
                            prev = v
                        }
                    }
                    job.join()
                }
            }
        }
        return tryCommit(DeckAnchor.Forward)
    }

    // ---------------------------------------------------------------- 程序化操作

    private suspend fun throwTo(target: DeckAnchor) {
        if (size < 2) return
        if (!drag.anchors.hasPositionFor(target)) {
            // 非循环端点：无锚即禁用，与旧库边界行为一致（no-op）。
            // 循环但无锚 = 小牌堆回看几何撞车（见 applyAnchors）→ 退化为即时换位（旧库 goBack 同款观感）
            if (target == DeckAnchor.Backward && circular) {
                index = if (index <= 0) size - 1 else index - 1
                applyAnchors()
            }
            return
        }
        if (reduceMotion()) {
            drag.snapTo(target)
            tryCommit(target)
        } else {
            drag.animateTo(target, style.flyOutSpec)
            tryCommit(target)
        }
    }

    /** 程序化翻到下一张；[circular] 时末张之后回到第一张（甩出+揭示，非瞬移）。抽取中忽略。 */
    fun next() {
        if (isDrawing) return
        scope.launch { opMutex.withLock { if (!isDrawing) throwTo(DeckAnchor.Forward) } }
    }

    /** 程序化翻回上一张；[circular] 时第一张之前回到末张（双向轮播）。抽取中忽略。 */
    fun previous() {
        if (isDrawing) return
        scope.launch {
            opMutex.withLock { if (!isDrawing) throwTo(DeckAnchor.Backward) }
        }
    }

    /** 即时回卷到首张（契约：与旧 setCurrentIndex(0) 一致，即时落位）。 */
    fun restart() {
        scope.launch {
            opMutex.withLock {
                drag.snapTo(DeckAnchor.Rest)
                index = 0
                applyAnchors()
            }
        }
    }

    /**
     * 纯随机抽取老虎机：步数 `4 + Random.nextInt(n)`（对 n 取模落点均匀）；步距从 420ms 起
     * ×1.18 封顶 560ms（**步与步的起始间距**，飞行时长计入步距内，超出则顺延）；收尾自末步
     * 起算 560ms 等落定（it-046 基准）。每步都是真实飞出动画（无半空摘除、无瞬移）；
     * [reduceMotion] 时每步即时落位并给一次 Confirm 震（DESIGN.md §3 降级）。
     * 抽取期间 [isDrawing] 为真（手势由调用方以 enabled 关闭）。
     *
     * 注：落点均匀依赖每步前进一格，非循环牌堆末张会原地踏步——本仓库两处调用均为 circular。
     */
    suspend fun drawRandom(onStep: (T) -> Unit = {}): T? {
        if (size == 0 || isDrawing) return null
        if (size == 1) return current
        isDrawing = true
        try {
            opMutex.withLock {
                val steps = 4 + Random.nextInt(size)
                var gapMs = 420L
                var lastElapsed = 0L
                repeat(steps) { i ->
                    val t0 = System.nanoTime()
                    if (size >= 2 && drag.anchors.hasPositionFor(DeckAnchor.Forward)) {
                        val ok = flyForwardQuick()
                        if (ok && reduceMotion()) pulseConfirm()
                    }
                    current?.let(onStep)
                    lastElapsed = (System.nanoTime() - t0) / 1_000_000L
                    // 末步不再补节奏（否则与收尾 560 叠加成 2×(560-elapsed) 超时）
                    if (i < steps - 1) {
                        val pause = gapMs - lastElapsed
                        if (pause > 0) delay(pause)
                    }
                    gapMs = (gapMs * 1.18).toLong().coerceAtMost(560L)
                }
                // 收尾：自末步起算 560ms 等落定（飞行已 await，剩余部分补足）
                val tail = 560L - lastElapsed
                if (tail > 0) delay(tail)
                return current
            }
        } finally {
            isDrawing = false
        }
    }
}

/**
 * 通用侧滑卡组（双向浏览 + 随机抽取），it-047 起为官方 API 自研内核：
 * - **手势**：`AnchoredDraggable` 锚点模型——1:1 跟手（touch-slop 门控）、速度参与甩出判定、
 *   动画中途可被再次按住无缝接管；向前甩 = 顶卡飞出（真实飞出动画，非瞬间消失），
 *   向后甩 = 顶卡归入牌堆、上一张滑入（真实回看，非瞬移）。
 * - **循环**：[circular]（默认 true）时向前甩末张自动回卷首张，卡组永不枯竭。
 * - **降级**：系统「移除动画」时全部落定瞬时完成（拖拽仍 1:1，属输入非动画），抽取连震保留。
 * - 卡组不自带尺寸——用 modifier 决定大小（如 fillMaxWidth(0.9f).height(380.dp)）。
 * - [style] 承载堆叠/阈值/弹簧参数（替代旧 SwipeableCardsProperties/Animations，不再泄漏三方类型）。
 */
@Composable
fun <T> CardDeck(
    items: List<T>,
    modifier: Modifier = Modifier,
    style: DeckStyle = DeckStyle(),
    circular: Boolean = true,
    reduceMotion: Boolean = rememberDeckReduceMotion(),
    onSwipe: ((item: T, toRight: Boolean) -> Unit)? = null,
    cardContent: @Composable (T) -> Unit,
): CardDeckController<T> {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val itemsState = rememberUpdatedState(items)
    val onSwipeState = rememberUpdatedState(onSwipe)
    val scopeState = rememberUpdatedState(scope)
    val hapticsState = rememberUpdatedState(haptics)
    val reduceState = rememberUpdatedState(reduceMotion)
    val controller = remember(style, circular) {
        CardDeckController(
            itemsProvider = { itemsState.value },
            circular = circular,
            style = style,
            scope = scopeState.value,
            reduceMotion = { reduceState.value },
            pulseConfirm = { hapticsState.value.performConfirm() },
            onSwipe = { item, toRight -> onSwipeState.value?.invoke(item, toRight) },
        )
    }
    // 数据规模变化（如换筛选）时同步锚点并夹紧索引
    LaunchedEffect(controller, items.size, circular) {
        controller.applyAnchors()
    }

    // 手势落定提交：fling/snap 落到 Forward/Backward 锚点后原子推进索引并复位。
    // tryCommit 有 committedTarget 幂等门，程序化路径直接调用与此观察者对同一次落定至多提交一次；
    // settled 归 Rest 时解除幂等门。
    LaunchedEffect(controller) {
        snapshotFlow { controller.drag.settledValue }.collect { v ->
            if (v == DeckAnchor.Rest) {
                controller.onSettledRest()
                // 兜底：手势用非 target 重载（无 finally 清 dragTarget），锚点重建抢锁失败路径可能
                // 残留 dragTarget → isAnimationRunning 恒 true（后续触摸零 slop 即起拖）。
                // 三重条件防误杀：targetValue==Rest 排除在途飞行（其 dragTarget=Forward/Backward）；
                // offset≈0 排除飞行首帧（offset 尚未动）；isAnimationRunning 锁定残留本身。
                // 任一缺失都会在「Rest 发射延迟到新飞行启动后才被处理」时取消新飞行（实测吞掉连按 › 第二步）。
                if (controller.drag.isAnimationRunning &&
                    controller.drag.targetValue == DeckAnchor.Rest &&
                    abs(controller.drag.offset) < 0.5f
                ) {
                    runCatching { controller.drag.snapTo(DeckAnchor.Rest) }
                }
            } else {
                controller.tryCommit(v)
            }
        }
    }

    // 速度阈值触感：仅真实按住拖拽越阈时触发一次（程序化飞行/甩出惯性阶段不触发）
    var pointerDown by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thresholdPx = remember(style, density) { with(density) { style.swipeThreshold.toPx() } }
    LaunchedEffect(controller, pointerDown, thresholdPx) {
        if (!pointerDown || !style.enableHapticOnThreshold) return@LaunchedEffect
        var armed = true
        snapshotFlow { controller.drag.offset }.collect { u ->
            // 程序化飞行/抽取期间手指静置也会越阈——那不是用户拖拽，跳过并重新武装
            if (controller.isDrawing || controller.drag.isAnimationRunning) {
                armed = true
                return@collect
            }
            val magnitude = abs(if (u.isNaN()) 0f else u)
            if (armed && magnitude >= thresholdPx) {
                armed = false
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            } else if (!armed && magnitude < thresholdPx) {
                armed = true
            }
        }
    }

    // 自研 fling 决策 + 落定动画（单一 spec 源）：官方 computeTarget 在 v=0 时只看最近锚点、
    // 位置阈值不参与（实测 v=0.0 直接回 Rest），慢速抬手/注入手势下会吞掉已过阈值的甩出——
    // 本实现：速度 ≥125dp/s 按方向甩出，否则按 100dp 位置阈值判定，目标缺失（非循环端点）回中。
    val flingSpec: AnimationSpec<Float> = if (reduceMotion) snap(0) else style.flyOutSpec
    val fling = remember(controller, thresholdPx, flingSpec) {
        object : androidx.compose.foundation.gestures.TargetedFlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(
                initialVelocity: Float,
                onRemainingScrollOffset: (Float) -> Unit,
            ): Float {
                val target = controller.flingTarget(
                    offsetPx = controller.drag.offset,
                    velocity = initialVelocity,
                    thresholdPx = thresholdPx,
                    velocityThresholdPx = with(density) { 125.dp.toPx() },
                )
                val targetOffset = controller.drag.anchors.positionOf(target)
                if (!targetOffset.isNaN() && targetOffset != controller.drag.offset) {
                    var prev = controller.drag.offset
                    androidx.compose.animation.core.animate(
                        initialValue = prev,
                        targetValue = targetOffset,
                        initialVelocity = initialVelocity,
                        animationSpec = flingSpec,
                    ) { value, _ ->
                        scrollBy(value - prev)
                        // prev 取 state 实值：spring 过冲会被锚点边界钳位，若 prev 跟随未钳的
                        // 动画值，后续 delta 基准错位会使 state 反向漂移（实测漂 1.33px 导致
                        // |offset-anchor|<0.5px 落定检查不过、settledValue 不翻、提交丢失）
                        prev = controller.drag.offset
                    }
                    // 残差补足（含 spring 结束容差），保证精确落锚
                    if (prev != targetOffset) scrollBy(targetOffset - prev)
                }
                onRemainingScrollOffset(0f)
                return 0f
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size -> controller.onSizeChanged(size.width) }
            .pointerInputGate(controller) { down -> pointerDown = down }
            .anchoredDraggable(
                state = controller.drag,
                orientation = Orientation.Horizontal,
                enabled = !controller.isDrawing,
                flingBehavior = fling,
            ),
    ) {
        // 容器内衬：复刻旧库 LazyLayout padding(end=padding, top=padding/2) 语义
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = style.padding / 2, end = style.padding),
        ) {
            // 渲染集：堆叠层优先占位，上一张仅在不与堆叠项重复时渲染（小牌堆防同卡双影）
            val stack = ArrayList<Pair<Int, T>>(style.visibleCardsInStack)
            val seen = HashSet<T>()
            for (d in 0 until style.visibleCardsInStack) {
                val item = controller.itemAt(d) ?: continue
                stack.add(d to item)
                seen.add(item)
            }
            val prev = controller.itemAt(-1)
            val rendered = if (prev != null && prev !in seen) stack + (-1 to prev) else stack
            rendered.forEach { (d, item) ->
                val z = when (d) {
                    0 -> 3f
                    -1 -> 2f
                    else -> 1f - d
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(z)
                        .graphicsLayer {
                            val u = controller.drag.offset
                            if (u.isNaN()) return@graphicsLayer
                            val p = controller.placement(
                                d = d,
                                u = u,
                                stackPx = style.stackedCardsOffset.toPx(),
                            )
                            translationX = p.x
                            translationY = p.y
                            rotationZ = p.rotationDeg
                            alpha = p.alpha
                        },
                ) {
                    cardContent(item)
                }
            }
        }
    }
    return controller
}

/** 只为维护 pointerDown 标志的手势门（不消费事件，真实拖拽由 anchoredDraggable 处理）。 */
private fun Modifier.pointerInputGate(
    controller: CardDeckController<*>,
    onDown: (Boolean) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(controller) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onDown(true)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                onDown(false)
            }
        }
    },
)
