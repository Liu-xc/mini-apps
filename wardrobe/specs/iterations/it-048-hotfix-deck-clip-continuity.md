# it-048 · hotfix：W8 卡组甩卡被容器裁剪 + 连续快速滑动滑不动

- **日期**：2026-09-27
- **类型**：纯 bugfix（跳过提案环节，按 AGENTS.md 回填问题与修法）
- **影响**：`libs/carddeck`（SDK 内核）+ wardrobe `RecordsScreen` + eats `SpinScreen`（同源容器问题顺带修）
- **上游**：it-047 自研内核的提交管线缺陷 + it-031 时代遗留的容器裁剪

## 背景与问题（用户报障原文）

1. 「卡片区域被前面的矩形边距截断了，需要预留些空间」——W8 穿搭记录页甩卡时，卡片飞出到
   标题栏 20dp 边距处被切成两截。
2. 「卡片连续滑动间隔比较小的时候滑不动」——快速连滑时第二次甩出无反应/卡片被弹回。

## 根因

### 问题 1：容器裁剪（wardrobe + eats 同源）

`RecordsScreen` 卡组外包一层 `Box(Modifier.fillMaxWidth().clipToBounds())`（it-031 C6 为旧三方库
时代版式收拢所加）。it-047 换自研内核后甩卡是**真实飞行**（顶卡飞向 -1.5W 屏幕外），卡片
（含 2dp 阴影）越过容器边距即被 `clipToBounds` 裁掉，观感即「被矩形边距截断」。
eats `SpinScreen` 卡组容器有完全相同的包裹（同源实例，顺带修）。

### 问题 2：提交管线以 settledValue 翻转为信号（it-047 结构性缺陷）

it-047 的提交观察是 `snapshotFlow { drag.settledValue }`，翻转（Rest→Forward）即 `tryCommit`。
两个连环坑：

1. **同向连滑吞提交**：快速连滑时第二次甩出落锚 Forward→Forward **不翻转**（settled 一直是
   Forward），观察者不发射 → 第二次甩出整单不提交，索引停在旧张。
2. **排队 snapTo 抢锁拉回**：第一次提交的 `tryCommit` 尾部 `snapTo(Rest)` 若恰逢用户下一次手势
   占锁则排队等待；等到执行时把 offset 强制归零——用户正在拖的卡片被瞬间拉回中位，或刚甩出的
   卡片被弹回。二者叠加的体感即「连续滑动滑不动」。

另有一个次级缺陷：fling 飞行被新手势打断后 index 未推进，用户拖着的是半途旧卡（两次滑动
只推进一张），与「滑不动」的体感合并。

## 修法（libs/carddeck/CardDeck.kt）

### 提交信号改为「到达帧观察」

```kotlin
snapshotFlow { controller.drag.offset }.collect { u ->
    if (u.isNaN() || pointerDown || controller.drag.isAnimationRunning ||
        controller.flingInProgress) return@collect
    // offset 精确到达 Forward/Backward 锚（±0.5px）→ tryCommit
}
```

- 所有落定路径都有**精确落锚帧**（自研 performFling 残差补足 / `animateTo` / `snapTo` /
  抽取 `anchoredDrag(target)` overload 收尾），到达必发射；连滑同向甩出每次落锚各自触发提交，
  不再依赖 settledValue 翻转。
- **三重落定门**（缺一不可，均实测踩坑）：
  - `pointerDown`：手势按住拖经锚点目标未定，等松手 fling 决策；
  - `isAnimationRunning`：程序化 `animateTo` 在途（官方动画跟踪内）；
  - `flingInProgress`（**it-048 新增 SDK 标志**）：自研 performFling 的 spring animate **不在
    官方动画跟踪内**（drag block 执行中 dragStatus=Dragging，isAnimationRunning 恒 false）——
    缺此门实测一次甩卡**连环提交 4 张**（飞越锚点即提交、`dispatchRawDelta` 复位被动画拉回、
    再过锚再提交，1/5→5/5）。标志由 performFling 用 `setFlingActive { }` try-finally 包裹维护。
- `tryCommit` 去掉 `settledValue != target` 拒绝前置（滞留场景会拒掉合法提交），幂等门
  `committedTarget` 独担防重放；拆出同步段 `commitCore`（可锁内调用）。

### 按下快进结算 onDeckDown(target)

`pointerInputGate` 的 down 瞬间（手势 slop 未过、不与手势抢锁）：

- 决策复用 `flingTarget()`：fling 飞行中按下取 `pendingFlingTarget`（performFling 的决策目标，
  与松手 fling 同款逻辑）；非飞行态（停在锚上的落定窗口）按位置阈值决策；决策 Rest 不打扰。
- 执行 `scope.launch { drag.anchoredDrag { commitCore(target) } }`——同级 anchoredDrag cancel
  在途动画拿锁，原子提交并复位；launch 内复查（offset 已被其他路径改动 >1px 则放弃）挡
  「手势已接管」竞态。被手势抢锁 cancel 时按协程取消语义静默结束，接管方负责提交，无双写。
- 效果：飞行中按下立即结算上一次甩出，新手势从干净 Rest 起步拖**新顶卡**——连滑间隔小于
  飞行动画时长（~320ms）也能逐张推进。

### 宿主容器（问题 1）

- `RecordsScreen`：删卡组 Box 的 `.clipToBounds()`（留防回归注释）。
- `SpinScreen`（eats）：同删。
- **SDK 契约补充**（carddeck `specs/00-overview.md`）：卡组容器不得裁剪，甩出动画需要溢出空间。

## 验证记录（emulator-5554，1080×2400，demo 数据 5 套）

| 用例 | 操作 | 期望 | 实测 |
|---|---|---|---|
| 单甩提交 | 慢速左甩一次 | 恰好 +1 | ✅ 1/5→2/5 |
| 连环提交回归 | 甩出后抓帧 | 无多张连推 | ✅ 修复过程曾回归 1/5→5/5，加 `flingInProgress` 门后消除 |
| 快滑连推 | 间隔 ~250ms 连滑两次（< 飞行动画 320ms） | 恰好 +2 | ✅ 2/5→4/5 |
| 飞行不截断 | 甩出中途抓帧（甩后 150ms） | 卡片完整飞出屏幕 | ✅ 无裁剪（eats 同） |
| 回卷 | 点 ‹ | -1 | ✅ 5/5→4/5 |
| 随机抽取 | 点「随机一套」 | 抽取态按钮置灰、步进真实、落点回卷 | ✅ 4/5→…→1/5 |
| eats 回归 | 连甩两次 | 逐张推进 | ✅ 巷子深火锅→…→沙县小吃 |
| 稳定性 | 全程 | 无崩溃 | ✅ logcat 无 FATAL（仅 uiautomator dump 自身注册冲突） |
| 单测/构建 | wardrobe+eats | 全绿 | ✅ `assembleDebug`+`testDebugUnitTest` 双端通过 |

## 遗留

- 无新增遗留；it-047 的「真机 60fps 量化」遗留项保持不变。
- 实测花絮：eats 进程曾被环境神秘拉到前台（非本修复代码路径，logcat 无对应记录），与本次
  修复无关，未深究。

## 提交

- `fix(wardrobe+libs/carddeck+eats): 甩卡不被容器裁剪+连滑改到达帧提交结算 (#it-048)`
