# 05 · 设计系统（清新草绿 × 餐牌手帐风 · R6 主题重塑）

## 视觉基调

清亮明快的淡绿纸感：绿为主调，照片和地图是主角，装饰克制。与 wardrobe 同一家族的排版语言（衬线 Display + Sans Body）。R6（2026-09-20）应用户反馈由暖橙（番茄红/琥珀）整体切换为清新绿系；it-004（同日）UX 评审落地：三枚类型语义色回归 spec W2 基线 红/琥珀/绿（原 青绿/湖蓝/草绿 三色全挤冷色区，地图堂食/自做难分）。

> 素材来源（2026-09-20 起）：应用图标、类型 3D 图标（堂食餐厅 / 外卖袋 / 炒锅）与空态插画取自 [thiings.co](https://www.thiings.co) 免费素材（个人非商业用途，署名见 README）。

## 色彩 Token（浅色 / 深色）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `paper` 背景 | `#F2F7EF` 淡绿纸 | `#101711` 墨绿纸 | 全局背景 |
| `surface` 卡片 | `#FFFFFF` | `#1B241C` | 卡片/弹层 |
| `ink` 主文字 | `#1E2822` | `#E9F2E7` | 标题/正文 |
| `inkFaint` 次级 | `#828E7D` | `#93A493` | 相对时间/说明 |

> it-015：inkFaint 浅色由 `#84907F` 微调至 `#828E7D`（对 paper 3.08→3.23:1，补 DESIGN.md §2.2 对比度余量；色相与观感不变）。
| `accent` 强调 | `#3FA265` 嫩芽绿 | `#7BCD93` | 主按钮/FAB/选中态 |
| `hairline` 分隔 | `#E2ECDF` | `#263229` | 细分隔线 |

评分星固定金色 `#E0A93E`（惯例语义，不随主色走）；转盘指针用 `ink`（绿扇区上保持对比）。

### 类型语义色（marker / 图标 / 卡组共用；it-004 回归 W2 基线，拉开色相）

| 类型 | 浅色 | 深色 |
|---|---|---|
| `kindRestaurant` 堂食 | `#D25446` 红 | `#E58377` |
| `kindTakeout` 外卖 | `#DA9A2B` 琥珀 | `#E7B566` |
| `kindHome` 自做 | `#4C9E5F` 绿 | `#86C795` |

标签 chip：paper 底 + ink 字；破坏性操作用系统 error 色。

## 字体与排版

| 层级 | 字体 | 用法 |
|---|---|---|
| Display | FontFamily.Serif（系统 Noto Serif CJK）· 28-34sp · semibold | 页面大标题「今天吃啥」、食堂名 |
| Title | Serif 20sp | 卡片标题 |
| Body | 系统 Sans 15sp | 笔记/感想 |
| Label | Sans 11sp · letterSpacing 0.2em | 相对时间「3 天前」、类型小标签 |

## 形状与间距

- 卡片圆角 20dp；弹层顶角 28dp；chip 8dp；屏幕边距 20dp；照片宽高比 4:3
- 触控目标 ≥44dp（it-016）：评分星输入命中区 coerceAtLeast(44dp)（视觉星尺寸不变）、Visit 删除钮 44dp；照片移除角标命中区 36dp（媒体角标例外档，DESIGN.md §2.5）
- 层次靠留白与 hairline，阴影极轻或无

## 动效清单（弹簧参数复用 EditorialMotion 基调：smooth / pop / bouncy）

| # | 动效 | 实现要点 | 触发处 |
|---|---|---|---|
| 1 | 卡组抽取（it-003；it-016 庆祝单一化） | libs/carddeck：侧滑飞出/堆叠晋升；「随机抽一张」按拍加速—减速翻张落定 + **落定单份彩屑**（原双发已修）+ 结果条 | W1 |
| 2 | 结果卡弹入 | spring pop：scale 0.9→1 + fade，accent 描边 | W1 |
| 3 | 记一笔成功 | 弹层收起 + snackbar「落账 ✓」，详情统计数字变化高亮 | W6→W5 |
| 4 | 地图摘要卡 | ModalBottomSheet slide-up；marker 点击轻微 bounce | W2 |
| 5 | 列表入场 | LazyColumn `animateItem()` + 首屏 staggered fade+上移 | W3 |
| 6 | 详情进入 | 列表卡片 → 详情头部共享元素放大 | W3→W5 |
| 7 | 空态 | 插画 + 文案 + 行动按钮（「先加一家食堂」/「直接转一把」） | W1/W2/W3 |
| 8 | 共享元素（it-002） | 列表缩略图 → 详情 hero 无缝放大 | W3→W5 |
| 9 | 瀑布入场 + 滑删（it-002；it-015 改仅首进播放） | 列表 24ms 错峰上移淡入，返回/二次进入即时显示（DESIGN.md §3 入场预算）；SwipeToDismiss 删除 | W3 |
| 10 | 落账编排（it-002） | 按钮 ✓ 形变 + 四色彩屑 900ms + 延迟收起 | W6 |
| 11 | （已随转盘移除，it-003） | — | — |
| 12 | 时间线母题（it-002） | 日期左列 + 竖线 + 节点卡，末条竖线截止 | W5 |
| 13 | 统计数字 count-up（it-015） | `CountUpText`/`CountUpFloatText`：首进 0→N 起数、档位切换旧值过渡，`EatsMotion.smooth()`，三格 60ms 错峰；空花费仍显示「—」 | W7 三大数字 |

## 触感反馈（it-015 · DESIGN.md §4 基线）

`ui/components/Haptics.kt`（与 wardrobe 同构）：`confirm()`（API 30+ CONFIRM，低版本回退 LONG_PRESS）/ `error()`（REJECT / VIRTUAL_KEY）/ `tick()`（CLOCK_TICK），无声音。接线：记一笔落账 ✓、随机抽中落定、保存/更新食堂成功 = **confirm**；名称必填等未就绪提示 = **error**。滚动/导航/输入不加触感。

## 组件清单（ui/components/）

`CardDeck`（libs/carddeck 卡组）、`KindChip`（类型 icon+色）、`RatingStars`（展示/输入两用）、`TagChipInput`、`PhotoStrip`（横滑照片条）、`VisitTimeline`（吃过记录时间线）、`EmptyState`、`RelativeTimeText`（相对时间）。
