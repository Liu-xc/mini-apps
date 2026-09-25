package com.leo.libs.carddeck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spartapps.swipeablecards.state.SwipeableCardsState
import com.spartapps.swipeablecards.state.rememberSwipeableCardsState
import com.spartapps.swipeablecards.ui.SwipeableCardDirection
import com.spartapps.swipeablecards.ui.SwipeableCardsProperties
import com.spartapps.swipeablecards.ui.animation.SwipeableCardsAnimations
import com.spartapps.swipeablecards.ui.lazy.LazySwipeableCards
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 卡组控制器：浏览（程序化双向翻张）与纯随机抽取（无权重）。
 * 所有动画均来自底层 swipeable-cards 库，本层只做节奏编排与循环环绕。
 */
@Stable
class CardDeckController<T> internal constructor(
    internal val state: SwipeableCardsState,
    private val itemsProvider: () -> List<T>,
    private val circular: Boolean,
) {
    var isDrawing by mutableStateOf(false)
        private set

    /** 当前顶部卡片 */
    val current: T? get() = itemsProvider().getOrNull(state.currentCardIndex)

    /** 当前顶部卡片下标（可观察，用于 ‹ n/m › 卡序胶囊） */
    val currentIndex: Int get() = state.currentCardIndex

    val size: Int get() = itemsProvider().size

    fun restart() = state.setCurrentIndex(0)

    /** 程序化翻到下一张；[circular] 时末张之后回到第一张 */
    fun next() {
        val n = itemsProvider().size
        if (n == 0) return
        if (state.currentCardIndex < n - 1) {
            state.swipe(SwipeableCardDirection.Left)
        } else if (circular) {
            state.setCurrentIndex(0)
        }
    }

    /** 程序化翻回上一张；[circular] 时第一张之前回到末张（双向轮播） */
    fun previous() {
        val n = itemsProvider().size
        if (n == 0) return
        if (state.currentCardIndex > 0) {
            state.goBack()
        } else if (circular) {
            state.setCurrentIndex(n - 1)
        }
    }

    /**
     * 纯随机抽取：随机步数保证落点均匀分布；卡组按「加速—减速」节奏用库自带
     * 飞出动画翻张（老虎机式），落定后返回顶部卡片。抽取期间 [isDrawing] 为真。
     *
     * it-046 节奏修订（库 1.1.4 反编译实证）：
     * - 库 `swipe()` 自带 `moveNext()`，但末张 moveNext 不推进索引、也不会触发手势
     *   路径的回卷回调——末张必须「真实甩出 + 立即 setCurrentIndex(0)」组合步：
     *   飞行照常进行，复位只是揭示下一张（等价手势路径的甩出+揭示），不再瞬移断帧；
     * - 步距地板 420ms ≈ 新弹簧 spring(0.9, 500) 的落定量级：上一步飞完再走下一步，
     *   不再出现旧版 55ms 连发时「卡片半空被 moveNext 摘除/多张叠飞」的撕裂感；
     * - 步数 4 + rand(n)：对 n 取模落点仍均匀，单轮 4~n+3 步（n=1 直接返回不甩）。
     */
    suspend fun drawRandom(onStep: (T) -> Unit = {}): T? {
        val list = itemsProvider()
        if (list.isEmpty() || isDrawing) return null
        if (list.size == 1) return current
        isDrawing = true
        try {
            val steps = 4 + Random.nextInt(list.size)
            var delayMs = 420L
            repeat(steps) {
                val n = itemsProvider().size
                if (n == 0) return@repeat
                val atLast = state.currentCardIndex >= n - 1
                state.swipe(SwipeableCardDirection.Left)
                if (atLast) state.setCurrentIndex(0)
                current?.let(onStep)
                delay(delayMs)
                delayMs = (delayMs * 1.18).toLong().coerceAtMost(560L)
            }
            delay(560) // 等最后一张的飞出/晋升动画落定
            return current
        } finally {
            isDrawing = false
        }
    }
}

/**
 * 通用侧滑卡组（双向浏览 + 随机抽取）：
 * - 双向：中间任意位置可手势往回翻看上一张（库原生 canSwipeBack）；控制器另有
 *   [CardDeckController.previous]/[CardDeckController.next] 供按钮/程序化翻张。
 * - 循环：[circular]（默认 true）时手势滑走末张后自动回到第一张，可无限轮播——
 *   修复库原生行为「滑到末尾后卡组清空」。
 * - 堆叠层数使用库默认值：实测 State 构造的首个 int 并非堆叠数配置（误传会把索引顶到越界、
 *   卡组渲染空白），底层未暴露安全的堆叠数入口，故本层不做透传。
 * 卡组不自带尺寸——用 modifier 决定大小（如 fillMaxWidth(0.9f).height(460.dp)）。
 * properties 透传底层库配置（堆叠偏移/阈值/旋转等）。
 */
@Composable
fun <T> CardDeck(
    items: List<T>,
    modifier: Modifier = Modifier,
    properties: SwipeableCardsProperties = SwipeableCardsProperties(),
    /** it-046：飞卡动画注入（库默认 spring(0.6,100) 偏软偏弹，调用方可传更利落的规格） */
    animations: SwipeableCardsAnimations? = null,
    circular: Boolean = true,
    onSwipe: ((item: T, toRight: Boolean) -> Unit)? = null,
    cardContent: @Composable (T) -> Unit,
): CardDeckController<T> {
    val state = rememberSwipeableCardsState(itemCount = { items.size })
    val controller = remember(state, circular) { CardDeckController(state, { items }, circular) }
    LazySwipeableCards(
        modifier = modifier,
        state = state,
        properties = properties,
        animations = animations ?: SwipeableCardsAnimations(),
        onSwipe = { item, direction ->
            // 循环：末张被手势滑走后回到第一张，卡组永不枯竭（it-003 遗留修复）
            if (circular && items.isNotEmpty() && state.currentCardIndex >= items.lastIndex) {
                state.setCurrentIndex(0)
            }
            onSwipe?.invoke(item, direction == SwipeableCardDirection.Right)
        },
    ) {
        // 用 addItems（非 reified 接口方法）而非 inline items 扩展，保证本封装对泛型 T 透明
        addItems(items) { item, _, _ -> cardContent(item) }
    }
    return controller
}
