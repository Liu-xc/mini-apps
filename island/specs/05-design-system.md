# 05 — 设计系统（视觉 token 与动效清单）

> it-003 改版收编：单环主锚 + 行式明细。尺寸推导**唯一来源是 `IslandLayout`**
> （遮罩 / hitTest / 窗口帧 / 视图共用），本文表格是它的镜像，改布局两处同步。

## 硬 token

### 几何

| token | 值 | 用途 |
|---|---|---|
| island/notch | 180×safeTop pt（无刘海屏 safeTop 兜底 24） | 隐形触发区（= 刘海挖槽矩形，内容全透明） |
| island/card-width | `max(352, 32 + 150n + 12(n−1))`，n=源数 | 展开卡片宽（≤2 源恒 352，第三源起自动加宽） |
| island/card-height | `safeTop + 6 + panelHeight + 10 + 24 + 14` | 展开卡片高（顶边贴屏幕顶沿） |
| island/panel-height | `20 + 14 + 6 + (环66 \| 提示44) + [6 + 行数×15 + (行数−1)×4]` | 单源面板高（面板行等高取最高者；= 实际渲染分支） |
| island/radius | 顶部 0 / 卡片底部 16；面板 14（continuous） | 顶部两角直角与顶边无缝衔接 |
| island/surface | `#000000` 卡片底；面板 `白 4%` 填充 + `白 5%` 0.5pt 描边 | 深色 HUD（§1 个性偏移声明） |
| island/ring | 外径 66（= 描边路径 58 + 环宽 8，stroke 中心对称不外溢）、环宽 8；有数据底轨 `白 8%`，**无数据整圈 `白 25%`**（= health/unknown，与色点/菜单同值）；弧=健康度**实色**（无渐变/无辉光——单环只读出一种状态，Leo 反馈定稿）；round cap 帽宽从 trim 扣除（99% 留缝、100% 闭合）；环在面板内**水平居中**（W2 画法） | 每源主环（环心=剩余%+档名） |
| island/hit-target | 图标按钮 24×24 pt（页脚行高同值），hover 白 10% 圆角 6；空态「去设置」≥24pt 高 | DESIGN §2.5 **指针场景例外（it-003 增补登记，≥24pt）**——非触控 44dp 基线 |
| island/empty-glyph | 虚线圆 26pt、线宽 3、dash 3/3、`白 18%` | 未配置空态图形（§5.8） |
| island/detail-row | 行高 15、行距 4；健康度色点 6 | 环下明细行 |

### 色彩（颜色 = 健康度，阈值唯一定义于 `IslandTheme`）

| token | 值 | 用途 |
|---|---|---|
| health/good | `#30D158` | 剩余 ≥ 50% |
| health/warn | `#FF9F0A` | 剩余 20–50% |
| health/bad | `#FF453A` | 剩余 < 20%（含刷新失败红字、页脚错误） |
| health/unknown | `白 25%`（SwiftUI 与 NSColor **同值**，it-003 审计修正） | 无数据（环整圈底轨/色点）/ 菜单无数据灰条 `系统灰 55%` |

> it-001/002 的「档位身份色」（5h 蓝 / 每周绿 / MCP 橙）**已废止**（US-4 修正，
> 与 it-002 增补 2 实况对齐）；菜单栏图标同用健康度语义，不再另搞一套身份色。

### 排版（全部 SF Pro；辅助文字 ≥9.5pt 且 ≥55% 白，it-003 AC2）

| 层级 | 规格 |
|---|---|
| 面板头源名 | 10.5 semibold，白 92% |
| 面板头重置 / 错误 | 9.5 regular，白 55%（错误=health/bad） |
| 环心百分比 | **15 bold rounded**，白，numericText 过渡；占位「--%」白 55% |
| 环心档名 | 9.5 medium，白 55% |
| 明细行标签 | 10 medium，白 88% |
| 明细行辅助（重置/绝对量） | 9.5 regular，白 55%；分隔「·」白 **55%**（≥3:1，it-003 审计修正）；行内基线 = `firstTextBaseline`（12pt 百分比与 9.5pt 辅助文共基线） |
| 明细行百分比 | **12 bold rounded**，白，numericText 过渡（向下取整）；占位「--%」白 55% |
| 空态提示 | 10 regular，白 55%；按钮 10 medium 白 85%（底 白 8%） |
| 页脚 | 9.5 regular，白 55%（错误=health/bad），单行截断 |
| 演示前缀 | 「演示 · 」原样拼接 |

## 动效清单

| 动效 | 参数 |
|---|---|
| 展开/收起（遮罩尺寸） | spring(response 0.32, dampingFraction 0.9)（动画只挂遮罩，窗口零运动） |
| 环填充 | easeOut 0.6s（值=fill，改数据才动） |
| 环心/明细百分比、**绝对量、aux 辅助文、面板头重置**数字 | `.contentTransition(.numericText())` + snappy 0.3s / 0.25s（DESIGN §5.9；it-003 审计补齐绝对量等遗漏处） |
| 判定矩形（点击穿透/hover） | appearance 切换后 0.45s smoothstep 插值，与遮罩 spring 同步（判定不瞬时跳变——审计 P2 修复） |
| 刷新按钮 | 在途 1s linear 无限旋转；停止用 **0.01s 有限动画覆盖**同 keypath（视觉=原地归零不倒转）。**禁用** `.animation(nil)`/关动画事务停转——离散写入打不断 in-flight repeatForever（it-003 审计三轮：模板匹配实测停转后角度必须恒 0°） |
| hover 展开/收起防抖 | enter 60ms / exit 180ms（均可取消，光标仍在卡内不收） |
| 触感 | **无**（Leo 明确不要震动，hover 展开不触发 NSHapticFeedbackManager） |

## 空状态（DESIGN §5.8）

未配置面板 = **虚线空环图形 + 「未配置 XX」+「去设置」按钮**（横排：环占主环同位视觉锚，
文案与按钮右侧竖排，**整组水平居中与数据态主环同轴**），高度与 hintHeight（44）一致，
面板高度公式分支 `hasRing=false`；按钮命中区 ≥24pt（DESIGN §2.5 指针例外）。

## 菜单栏图标

18×14pt 三根圆角小彩条（x=index×7、4×10、圆角 1.5），**每源主档一根**，颜色 = 健康度
（`IslandTheme.levelNSColor`，与卡片同一套阈值）；无数据补灰条占位（轮廓不塌缩）。
非 template（保色）。摘要按源分组（源名头 + 缩进行 + 逐源错误/空态）。
