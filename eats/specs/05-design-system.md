# 05 · 设计系统（餐牌手帐风）

## 视觉基调

食谱卡/餐牌的气质：暖米纸底、墨色衬线标题、三枚稳定的类型语义色，照片和地图是主角，装饰克制。与 wardrobe 同一家族的排版语言（衬线 Display + Sans Body），但更暖、更轻松。

> 素材来源（2026-09-20 起）：应用图标、类型 3D 图标（堂食餐厅 / 外卖袋 / 炒锅）与空态插画取自 [thiings.co](https://www.thiings.co) 免费素材（个人非商业用途，署名见 README）。

## 色彩 Token（浅色 / 深色）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `paper` 背景 | `#FBF7EF` 暖米 | `#161412` 墨纸 | 全局背景 |
| `surface` 卡片 | `#FFFFFF` | `#201D1A` | 卡片/弹层 |
| `ink` 主文字 | `#211D19` | `#F2EDE4` | 标题/正文 |
| `inkFaint` 次级 | `#8C8478` | `#9C9488` | 相对时间/说明 |
| `accent` 强调 | `#C8502E` 番茄红 | `#E07B54` | 主按钮/FAB/选中态/转盘指针 |
| `hairline` 分隔 | `#EAE2D4` | `#2D2925` | 细分隔线 |

### 类型语义色（marker / 图标 / 转盘扇区共用）

| 类型 | 浅色 | 深色 |
|---|---|---|
| `kindRestaurant` 堂食 | `#C8502E` | `#E07B54` |
| `kindTakeout` 外卖 | `#D99A2B` 琥珀 | `#E5B054` |
| `kindHome` 自做 | `#6B8F4E` 松绿 | `#8FB073` |

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
- 层次靠留白与 hairline，阴影极轻或无

## 动效清单（弹簧参数复用 EditorialMotion 基调：smooth / pop / bouncy）

| # | 动效 | 实现要点 | 触发处 |
|---|---|---|---|
| 1 | 转盘旋转 | Canvas 扇形 + rotate 动画：快速起转 → 减速 → 轻过冲落定（2.5–4s 随机时长/圈数）；指针落定轻弹；中心文字随掠过扇区切换 | W1 |
| 2 | 结果卡弹入 | spring pop：scale 0.9→1 + fade，accent 描边 | W1 |
| 3 | 记一笔成功 | 弹层收起 + snackbar「落账 ✓」，详情统计数字变化高亮 | W6→W5 |
| 4 | 地图摘要卡 | ModalBottomSheet slide-up；marker 点击轻微 bounce | W2 |
| 5 | 列表入场 | LazyColumn `animateItem()` + 首屏 staggered fade+上移 | W3 |
| 6 | 详情进入 | 列表卡片 → 详情头部共享元素放大 | W3→W5 |
| 7 | 空态 | 插画 + 文案 + 行动按钮（「先加一家食堂」/「直接转一把」） | W1/W2/W3 |

## 组件清单（ui/components/）

`WheelCanvas`（转盘）、`KindChip`（类型 icon+色）、`RatingStars`（展示/输入两用）、`TagChipInput`、`PhotoStrip`（横滑照片条）、`VisitTimeline`（吃过记录时间线）、`EmptyState`、`RelativeTimeText`（相对时间）。
