# carddeck · 侧滑卡组 + 随机抽取 SDK

## 是什么

跨应用复用的通用交互 SDK（AGENTS.md `libs/` 规范）：**侧滑卡组浏览 + 老虎机式随机抽取**。
eats 用它替换转盘（食堂卡片带照片/标签/链接，可翻看也可随机抽），wardrobe 用它浏览已保存穿搭并随机抽一套。

## 选型（不自研手势动画）

手势与动画来自三方库 [compose-swipeable-cards](https://github.com/smartword-app/compose-swipeable-cards)
（Apache-2.0，JitPack 分发）：左右滑 + 堆叠缩放/旋转 + 程序化 `swipe()/moveNext()/setCurrentIndex()`。

备选 makzimi/SwipingCards（Maven Central）被否：minSdk 33 高于两应用基线 26，且无程序化接口、无法编排抽取。

本 SDK 是薄封装：`CardDeck` composable（渲染）+ `CardDeckController`（next / restart / drawRandom）。
`drawRandom` = 随机步数（12 + rand(size)，落点均匀、**无权重**）+ 按拍加速—减速地调用库的飞出动画，落定返回顶部卡片。

## 契约

```kotlin
@Composable
fun <T> CardDeck(
    items: List<T>,
    modifier: Modifier = Modifier,          // 卡组尺寸由调用方决定
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
