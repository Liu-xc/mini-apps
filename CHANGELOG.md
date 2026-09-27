# CHANGELOG

> AGENTS.md 迭代流程第⑤步要求的变更流水；本文件于 it-001 建立（此前仓库未落地该约定）。

## 2026-09-27

- **docs(readme)**: 赤峰环线/象棋竞技场标记**已归档**——做游戏的预期下调，经确认选「代码原地保留」方案：两目录与 specs 完整保留可随时复启，仅 README 状态列收口（travel-rpg 过期的「it-001 开发中」修至实况 it-012，xiangqi 修至 it-001~003 MVP 完成）；不拆仓不打 tag 不删代码。

- **fix(island)**: it-003 loading 图标永转+抖——埋探针日志证明状态机健康（fetch 0.3s/轮 准时停）→ 根因在动画层：`.animation(nil)` 与 `withTransaction(disablesAnimations)` **离散写入均打不断 in-flight `repeatForever`**（模板匹配 48 角度×对照实验 SAD=0 实证：修复前停转后 67.5°/255°/105°/300° 持续变）；终修 = **0.01s 有限动画覆盖同 keypath**（视觉=原地归零不倒转）；「不中心对称/抖」= 字形 ink 74×90 非方质心画 0.63pt 偏心圆（慢转被读成抖，停转即消）；验证在途 82.5° 在转、停后 6s/7.5s/9s 恒 0°；锚点实测正确（0°/180° bbox 重合，偏差 0.06pt 亚像素）；05 动效表同步禁用 nil 停转；swift test 49/49 绿。

## 2026-09-26

- **feat(wardrobe+libs/carddeck+eats)**: it-047 卡组换官方 API 自研内核（AnchoredDraggable，foundation 1.8.3 零 experimental）——甩卡/回看全路径动画+速度判定（自研 `flingTarget`：速度 ≥125dp/s 或 100dp 位置阈值，修官方 computeTarget v=0 丢甩出）、单一弹簧源 `spring(0.9,500)` 全路径收口 + `committedTarget` 幂等提交管线、drawRandom 首达截停保 420–560ms 步距、抽中落定 Confirm 震接线；删 `compose-swipeable-cards` 三方依赖与三处 JitPack 仓库声明；W1/W7 pager 参数收敛（snap/fling spec 显式注入 + `beyondViewportPageCount=1` + `animateScrollToPage` 传 spec）+ SlotGrid 落定轻弹 1→1.03→1；减弱动态单一入口降级（snap 即时落位、拖拽保 1:1）；eats SpinScreen 迁移连带修复（首次获得可注入动画）；双端 assembleDebug+test 绿、模拟器全路径实测；对抗评审（1 blocker/5 major/18 minor）全处置——W1/W7 减弱动态改 `EditorialMotion.pagerFling` 瞬时降级（官方 SnapFlingBehavior 吃不到系统缩放，字节码实证）、提交守卫三重条件修连按›吞步、n/m 下沉 `DeckCounter`、空牌堆 Rest 锚防越界、SDK 侧抽取中翻页 no-op、`runSettlePulse` 收口 Motion.kt；真机 60fps 与 Layout Inspector 计数器量化遗留。
- **fix(wardrobe)**: it-046 槽位卡撑满去黑边距——深色下旧衬纸（surfaceVariant@55%）+Fit 留边即 Leo 所见黑边距；改分档呈现：常规格（0.6/0.78/0.85）Crop 满格无留边，帽(1.0)/鞋(2.6) 极端比例保 Fit + 固定浅纸 #F2F3F5（主题无关；裁切伤害以内容包围盒实测证否）；W1 序号 n/m 末页回卷改即时落位。
- **fix(wardrobe+libs/carddeck)**: it-046 穿搭卡组动画丝滑化——库 1.1.4 反编译实证飞卡默认 `spring(0.6,100)`（落定≈1s、9% 过冲晃尾）且 drawRandom 55ms 连发远短于飞行时长（多张叠飞/moveNext 半空摘除）+ 末张纯瞬移；`CardDeck` 增 `animations` 参数透传（wardrobe 注入 `spring(0.9,500)`≈0.32s 落定），drawRandom 改步距 420→560ms、步数 4+rand(n)、末张「甩出+复位」组合步；specs 01(US-46)/05（槽位呈现+动效表#12+衬纸表）/it-046/carddeck 00 同步；模拟器像素探针+连拍+回卷实测全过，eats 编译兼容。
- **fix(island)**: it-003 对抗审计 5 CONFIRMED + 11 P2 全修——**P0-A hitTest 坐标系错配**（point 窗口基坐标 vs rect 屏幕全局恒不相交 → 展开态点击全死+穿透下层，`screenPoint` 换算修复）；**P0-B** 绝对量/aux/重置数字补 numericText（§5.9 残留）；**P1-C** 环 stroke 外溢 4pt（路径=外径−环宽，墨迹精确 66.0pt）；**P1-D** 0% 绘最小健康色弧、无数据整圈白 25%（与色点/菜单同值）；**P1-E** DESIGN §2.5 登记指针例外 ≥24pt + 空态按钮实测 25pt；P2 批：判定矩形随遮罩 0.45s 插值、圆帽交叠补偿（99% 留缝/100% 闭合）、环居中 W2 对齐、明细行 firstTextBaseline、`·`/`--%` 统一 55% 白、US-2 双源错误措辞自洽；swift test 48/48 绿（+7 纯函数回归 +4 渲染级 ImageRenderer 回归），三态截图像素断言全过，独立对抗核实 5/5 CONFIRMED（10/10 verdicts）。
- **fix(island)**: it-003 用户反馈两连——①「环内两种绿」：主环弧线去沿弧渐变+辉光改**实色**（一环一色一义，像素级验证弧上仅 1 种色值）；②「两环没对齐」：实为用户运行的 `dist/island.app` 是 it-003 前旧包（25 日 23:37 打包，双环 60/68 错位即 it-003 已修的 P1-7），新单环 UI 未达屏幕；dist 已重建，待退出旧实例重开生效。
- **feat(island)**: it-003 卡片改版 + 可扩展架构——UI 走查 P0/P1/P2 全修：**单环主锚+行式明细**（面板头=源名+主档重置/失败红字、环心=剩余%+档名、环下副档/绝对量行、空态虚线环+去设置），**ProviderRegistry 单表驱动**（凭证/面板/设置/菜单遍历注册表，新增源=case+一条+解析器），**按源状态 `SourceState`**（逐源成败不吞，修 it-002 AC4；清凭证删缓存），**`IslandLayout` 布局单一真源**（遮罩/hitTest/窗口帧同源、面板高度=渲染分支），页脚 TimelineView 真实时间，健康度配色收编 `IslandTheme`（US-4 修正、菜单图标同语义），numericText 数字过渡（§5.9），设置清除二次确认+逐源诊断+620 高度，菜单分组摘要；ADR-009/010/011 补录，specs 00–06 + README + DESIGN §1 全同步；swift test 37/37 绿，DebugShot 五状态截图走查回填验证记录。

## 2026-09-25

- **fix(wardrobe)**: it-045 顶栏↔内容间距全站统一 20dp——盘点实证 0/4/6/8/10/20dp 六种取值并存（W4/W5/W7/W11 内容贴死顶栏 0dp、W9 4dp、W10 6dp、W12 8dp、三 Tab 页 6/10dp 且标题行 top 8/12 不一），12 处逐屏改齐（W12 reverseLayout 下 contentPadding 属滚动内衬不可见，改容器级 padding 恒定生效），spec 05 补「顶栏↔内容节奏」条目、01 补 US-45；模拟器 dump 逐屏量化 19.8–20.2dp 全过、单测绿。
- **feat(wardrobe)**: it-045 演示体验包构建开关 `-PdemoDefault=true` → `BuildConfig.DEMO_DEFAULT` 注入 DemoMode 缺省值（显式偏好仍优先、常规包恒 false），出 mock 数据默认开的体验包，退出通道（设置/5 连点）不变。
- **fix(wardrobe)**: it-042 第五轮走查修复 C1–C8（P1×2/P2×6，C5 挂账）——W2「使用中」补 8dp 分读、W8 卡组 hero 成品图改衬纸 Fit 修裁头（05「成品图一律 Crop」条款作废）、OutfitThumb 日期并入 yyyy/MM/dd、W1 槽位长按 toast 读全名、W9 打卡 0 隐藏闲置冗余卡、W10 域名字距归零、W1/W3 滚动底缘 28dp 渐隐（共享 `fadingBottomEdge`）；specs 01/02/05 同步，编译+单测绿，模拟器复验回填验证记录。来源=2026-09-25 r5 走查（P0×0/P1×2/P2×6）。

- **docs(xiangqi)**: it-003 过夜双局结果——Flash 档 mimo-v2.6-flash 将死胜 glm-5.3-flash（76手/105分），Pro 档 glm-5.3 将死胜 mimo-v2.6-pro（53手/109分），四模型 1:1；报告+双局快照/棋谱入库 `reports/2026-09-24-xiangqi-showdown/`，it-003 验证记录回填（含无限等待 Promise.race 假判负坑）。

## 2026-09-24

- **feat(xiangqi)**: it-002 Jev 胜率评估与曲线——每手落子后异步问 TypeSafe Jev（Choice: 红胜/和/黑胜概率）回填 `Move.eval` 并广播，侧栏纯 SVG「胜率曲线」（红实黑虚双线+50%基线+置信图例），导出棋谱附评估值；key 入 `.env.local`（gitignore），`JEV_DISABLED` 关停冒烟；specs 01/02/03/04/05 同步，smoke 回归 PASS。网络坑：mac LibreSSL 访问 api.typesafe.ai 被掐、Node OpenSSL 正常。**修订**：评估按局可选（勾选框+`eval-toggle` 运行中切换，三态实测 PASS）、发给 Jev 的 prompt 全英文（state/历史走 ICCS）。

- **feat(xiangqi)**: it-001 真模型验证收口——GLM+MiMo 两把 key 入 pi `auth.json`（免 env、跳过会阻塞的钥匙串注入）；超时语义由总时长改**静默超时**（真局实证长思考被误杀后修复，ADR-004/US-03 同步）；Flash 对决第三局 60 手零判负达 AC 并最终**第 102 手困毙自然终局（黑方 mimo 胜，记分板 1:0）**、6 次非法重试自愈、上下文隔离实测；思考流同类增量合并成段；前端默认对阵 `glm-5.3-flash vs mimo-v2.6-flash`。

## 2026-09-23

- **feat(xiangqi)**: it-001 AI 象棋竞技场 MVP——仓库首个 web 应用：`server.mjs`（零依赖 http+SSE+POST）spawn 两个 `pi --mode rpc` 隔离 session 子进程，FEN+合法走法白名单+ICCS 协议驱动对弈（非法重试 ≤3、超时重试、将死/困毙/60 回合无吃子/150 回合上限裁定），前端 xiangqiboardjs+xiangqi.js 观战 UI（思考流/中文记谱/记分板/手动代走/棋谱导出），specs 全套 + 假模型全链路冒烟（`tools/smoke.mjs` 双阶段 PASS）。
