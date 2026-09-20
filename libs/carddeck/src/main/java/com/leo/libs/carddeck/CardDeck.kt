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
import com.spartapps.swipeablecards.ui.lazy.LazySwipeableCards
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 卡组控制器：浏览（程序化翻张）与纯随机抽取（无权重）。
 * 所有动画均来自底层 swipeable-cards 库，本层只做节奏编排。
 */
@Stable
class CardDeckController<T> internal constructor(
    internal val state: SwipeableCardsState,
    private val itemsProvider: () -> List<T>,
) {
    var isDrawing by mutableStateOf(false)
        private set

    /** 当前顶部卡片 */
    val current: T? get() = itemsProvider().getOrNull(state.currentCardIndex)

    val size: Int get() = itemsProvider().size

    fun restart() = state.setCurrentIndex(0)

    /** 程序化翻到下一张（到末尾回到第一张） */
    fun next() {
        val n = itemsProvider().size
        if (n == 0) return
        if (state.currentCardIndex >= n - 1) restart() else state.swipe(SwipeableCardDirection.Left)
    }

    /**
     * 纯随机抽取：随机步数保证落点均匀分布；卡组按「加速—减速」节奏用库自带
     * 飞出动画翻张（老虎机式），落定后返回顶部卡片。抽取期间 [isDrawing] 为真。
     */
    suspend fun drawRandom(onStep: (T) -> Unit = {}): T? {
        val list = itemsProvider()
        if (list.isEmpty() || isDrawing) return null
        isDrawing = true
        try {
            val steps = 12 + Random.nextInt(list.size)
            var delayMs = 55L
            repeat(steps) {
                val n = itemsProvider().size
                if (n == 0) return@repeat
                if (state.currentCardIndex >= n - 1) {
                    state.setCurrentIndex(0)
                } else {
                    state.swipe(SwipeableCardDirection.Left)
                }
                current?.let(onStep)
                delay(delayMs)
                delayMs = (delayMs * 1.24).toLong().coerceAtMost(340)
            }
            delay(260) // 等最后一张的飞出/晋升动画收尾
            return current
        } finally {
            isDrawing = false
        }
    }
}

/**
 * 通用侧滑卡组（浏览 + 随机抽取）：
 * 卡片可左右滑走（浏览下一张），返回的 controller 支持 next()/drawRandom()。
 * 卡组不自带尺寸——用 modifier 决定大小（如 fillMaxWidth(0.9f).height(460.dp)）。
 * properties 透传底层库配置（堆叠偏移/阈值/旋转等）。
 */
@Composable
fun <T> CardDeck(
    items: List<T>,
    modifier: Modifier = Modifier,
    properties: SwipeableCardsProperties = SwipeableCardsProperties(),
    onSwipe: ((item: T, toRight: Boolean) -> Unit)? = null,
    cardContent: @Composable (T) -> Unit,
): CardDeckController<T> {
    val state = rememberSwipeableCardsState(itemCount = { items.size })
    val controller = remember(state) { CardDeckController(state, { items }) }
    LazySwipeableCards(
        modifier = modifier,
        state = state,
        properties = properties,
        onSwipe = { item, direction ->
            onSwipe?.invoke(item, direction == SwipeableCardDirection.Right)
        },
    ) {
        // 用 addItems（非 reified 接口方法）而非 inline items 扩展，保证本封装对泛型 T 透明
        addItems(items) { item, _, _ -> cardContent(item) }
    }
    return controller
}
