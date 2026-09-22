# 05 — 设计系统（视觉 token 与动效清单）

## 硬 token

| token | 值 | 用途 |
|---|---|---|
| island/hidden-size | 180×safeTop pt | 隐形触发区（= 刘海挖槽矩形，内容全透明） |
| island/expanded-size | 352×178pt（出现第三档 other 时 222） | 展开卡片（顶边贴屏幕顶沿） |
| island/radius | 顶部 0 / 底部 16（UnevenRoundedRectangle，continuous） | 顶部两角直角与顶边无缝衔接，只圆下方 |
| island/surface | `#000000` + 顶部 0.5pt 白 7% 高光 | 胶囊底（与刘海融为一体） |
| color/fiveHour | `#3B82F6` 蓝 | 5 小时档本色 |
| color/weekly | `#22C55E` 绿 | 每周档本色 |
| color/zcodeMcp | `#F59E0B` 橙 | MCP 档本色 |
| color/other | `#94A3B8` 灰 | 未知档位 |
| color/state-warn | `#FB923C` | 剩余 ≤20% 覆盖色 |
| color/state-exhausted | `#F87171` | 剩余 =0 覆盖色 |
| track | 白 13%（展开 6pt）/ 白 15%（紧凑 30×4pt） | 进度条底轨 |
| text-primary | 白（13 semibold 标题 / 14 bold rounded 数值） | |
| text-secondary | 白 55%（重置时间 10pt）/ 白 45%（页脚 9.5pt） | |

## 动效清单

| 动效 | 参数 |
|---|---|
| 展开/收起（窗口 frame） | NSAnimationContext 0.36s cubic(0.16, 1, 0.3, 1) |
| 展开/收起（SwiftUI 内容） | spring(response 0.36, dampingFraction 0.85) + opacity transition |
| 进度条填充 | easeOut 0.45s（紧凑）/ 0.5s（展开） |
| 刷新按钮 | loading 时 1s linear 无限旋转 |
| 触感 | **无**（Leo 明确不要震动，hover 展开不触发 NSHapticFeedbackManager） |

## 字体

系统字体：SF Pro（标题/正文）；百分比 `design: .rounded` 加粗，贴近 iOS 灵动岛数字感。

## 菜单栏图标

18×14pt 三根圆角小彩条（systemBlue/systemGreen/systemOrange），非 template（保色）。
