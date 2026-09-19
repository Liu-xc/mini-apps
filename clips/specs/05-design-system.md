# 05 · 设计系统（轻快工具风）

## 视觉基调

效率工具的气质：中性冷灰底、高信息密度、等宽字体点缀、唯一强调色。**速度感与可扫读性优先于装饰**——面板呼出必须像系统的一部分，而不是打开了一个应用。

## 色彩 Token（浅色 / 深色）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `bg` 背景 | `#F6F6F4` | `#151517` | 全局背景 |
| `surface` 面板 | `#FFFFFF` | `#1E1E22` | 列表/面板/弹层 |
| `ink` 主文字 | `#1A1A1E` | `#EDEDF0` | 条目预览/标题 |
| `inkFaint` 次级 | `#83838B` | `#93939C` | 时间戳/徽章文字/占位 |
| `accent` 强调 | `#2F6BFF` | `#5B8CFF` | 选中态/焦点环/主按钮 |
| `hairline` 分隔 | `#E6E6E3` | `#2A2A2F` | 行分隔/分组线 |

破坏性操作（删除/清空）用系统 error 色。

### 类型徽章（typeHint 专属，双端一致）

`URL` 蓝 `#2F6BFF` · `EMAIL` 青 `#0E9488` · `PHONE` 绿 `#16A34A` · `COLOR` 不用徽章色、直接渲染 12dp 色块（颜色 = text 本身） · `JSON` 橙 `#EA580C` · `CODE` 紫 `#7C3AED` · `TEXT` 无徽章。

徽章形态 = 12dp 圆点 + 11sp 标签（或纯 emoji 图标），不放彩色底块，保住行密度。

## 字体与排版

| 层级 | 字体 | 用法 |
|---|---|---|
| Title | Sans semibold 17sp | 页面标题「剪贴盒」、弹层标题 |
| Body | Sans 14sp | 条目预览（≤2 行，尾部省略号） |
| Mono | **等宽** 13sp | URL / 代码 / 颜色值 / JSON 条目的预览优先等宽 |
| Label | Sans 11sp | 相对时间、徽章文字、快捷键提示（`⏎ 复制`） |

相对时间规则：<1min「刚刚」；当天 `HH:mm`；昨天「昨天」；更早 `M/d`。

## 形状与间距

- Mac 快速面板：圆角 20dp、固定宽 420dp、高自适应（max 560dp）、elevation 6dp；Android 弹层顶角 24dp
- 条目行高 48–56dp（两行预览）；屏幕边距 Android 16dp；行间 hairline 分隔
- chip 全弧圆角；选中 chip = `accent` 描边 + 10% 底

## 动效清单（统一弹簧基调 ClipMotion）

> 大面积/生成式动画刻意不用——工具的第一美德是快。全部动效 < 250ms。

| # | 动效 | 实现要点 | 触发处 |
|---|---|---|---|
| 1 | 面板呼出/隐藏 | spring（中阻尼）scale 0.96→1 + fade ~150ms；隐藏反向 120ms | W1 |
| 2 | 键盘选中行 | 高亮背景 `animateColorAsState` + `animateScrollToItem` 跟随 | W1 |
| 3 | 复制成功 | 条目行背景闪 accent 8% → 回落；行尾 morph ✓（200ms） | 双端 |
| 4 | 新捕获入场 | fade + 上移 12dp；「新捕获」标记 1.5s 后淡出 | W3 |
| 5 | 滑动删除 | Material3 `SwipeToDismissBox` + `animateItem` 淡出回落 | W3 |
| 6 | 置顶位移 | `animateItem` 从原位置滑到置顶组 | 双端列表 |
| 7 | 空状态 | 图标 + 一句引导文案（插画留给后续迭代） | 列表空 |

## 组件清单（ui/components/）

`ClipRow`（两行预览 + 徽章 + 相对时间）、`SearchBar`（自动聚焦）、`TypeFilterChips`、`DayHeader`（今天/昨天/置顶 分组头）、`EmptyState`、`ConfirmDialog`（清空/删除二次确认）。
