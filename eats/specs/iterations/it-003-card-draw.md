# it-003 · 转盘 → 侧滑卡组（今天吃啥交互重塑）

- **状态**：已完成
- **提案日期**：2026-09-20
- **范围**：W1 首页交互由转盘整体替换为「侧滑卡组 + 纯随机抽取」，引入 `libs/carddeck` SDK

## 背景与动机

用户反馈：转盘信息密度低（扇区放不下菜名与信息）、观感不佳；期望「炫酷的随机卡片」——
把每个候选做成信息卡（照片/类型/评分/上次/标签/下单链接），既能**侧滑快速浏览全部选项**，
也能**动画随机抽取**；且该交互要在衣橱复用（穿搭记录同样浏览+随机抽），故沉淀为 SDK。

约束（用户明确）：不自研手势动画（采用三方库薄封装）、随机抽取**不做权重**。

## 方案

- 新增 `libs/carddeck`（0.1.0）：封装 [compose-swipeable-cards](https://github.com/smartword-app/compose-swipeable-cards)
  （Apache-2.0，JitPack），提供 `CardDeck` composable + `CardDeckController(next/restart/drawRandom)`；
  `drawRandom` = 随机步数 + 按拍加速—减速地调用库的飞出动画（老虎机式），落点均匀无权重。
  选型对比 makzimi/SwipingCards（minSdk 33 超基线、无程序化接口）见 `libs/carddeck/specs/00-overview.md`。
- W1 重写：过滤（类型/忌口/排除最近）沿用 it-002；卡组为信息卡（照片 hero 或 3D 插画、
  名称/类型/菜系、评分胶囊、上次·次数、标签、美团/点评链接直达、记一笔/详情按钮）；
  「随机抽一张」→ 落定彩屑 + 结果条（就吃这个 → 记一笔 / 再抽）；「换一张」程序化翻张。
- 移除：WheelCanvas、SpinWheel（权重抽取用例及其单测）、ADR-006 权重方案随之作废。

## 验收标准

- 候选 ≥1 即可浏览/抽取；抽取期间按钮防重入；抽取落点均匀（随机步数）
- 卡片信息齐全（US-01 录入的所有字段在卡上可达）；链接直达下单
- `./gradlew assembleDebug` / `test` 通过；模拟器截图对照验收

## 验证记录

- 构建：assembleDebug ✅ / test ✅（SpinWheelTest 移除后全绿）
- 模拟器实测：`assets-it-002/eats-10-deck-light.png`（卡组+信息卡+双按钮）、
  `eats-11-deck-drawn.png`（抽取落定：结果条「就吃 猪脚饭？」+ 彩屑 + 就吃这个/再抽），
  视觉模型验收通过（卡片信息齐全、无布局错乱）

## 遗留

- 卡组滑到末尾后 `next()` 回到第一张为瞬时跳转（库无循环模式），可后续在 SDK 内补循环
