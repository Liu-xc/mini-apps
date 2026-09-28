# CHANGELOG

> AGENTS.md 迭代流程第⑥步要求的变更流水；本文件于 it-001 建立（此前仓库未落地该约定）。

## 2026-09-29

- **feat(darkroom)**: it-007 显影模式体系与拍立得还原——W1 新增「拍立得/数码相机/胶片」三模式选择（持久化），三模式各有独立卡面（白框相纸 / 深灰回放屏 + OSD 带 / 35mm 齿孔片条）、显现前沿（化学偏心 / 网格块 / 横向冲洗）与出纸动画（槽口升纸 / 开机扫描线 / 片盒卷出），曲线与印字配色按模式分派且三渲染端共用真源；拍立得按实物重修（滚轴入口偏心推进、分染料上色时序与窄动态、出纸分段顿挫 + 槽口下压、纸纹与成像区细线），并修掉定影末尾模糊硬跳变、模式偏好旧快照回冲、Activity 重建重放出纸三个缺陷；W2 的 Material Slider 换成自绘药水刻度条（5% 短刻 / 25% 长刻 / 阶段分界、阶段名上轨、拖动倒放语义不变）。56 单测全绿，AVD 走查三模式 W1–W3 与胶片原生导出，详 [it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md)。

- **feat(wardrobe)**: it-056 导出面板携带顾问场景——对话推荐卡「复制长图」打开 W6 面板时，标题/说明命中的五维预设选项（场景·办公室、季节·早秋 等）自动预选、一键可改，不再要求用户把 Agent 刚说过的场景重选一遍；提取走 `extractRecommendationSelections` 纯函数（长词优先包含匹配，半截词不命中），`ExportSheet` 新增 `presetSelections` 参数以记忆为底覆盖同 key，四处既有调用零变化；81 测全绿 + AVD 端到端走查，详 [it-056](wardrobe/specs/iterations/it-056-chat-export-scene-preset.md)。

- **feat(wardrobe)**: it-055 顾问卡片可点可导——W13 推荐卡已匹配单品 tile 点击进 W5 单品详情（未匹配不可点、中性提示保留），卡片底部「复制长图」与 W5 顶下新导出入口均复用 W6 导出面板（`existingOutfit=null`，搭配页/心愿同先例），整套与单件都能出「照片拼版+五维提示词」长图拿去外部生图；匹配逻辑抽 `matchRecommendationItems` 纯函数 +2 JVM 单测，77 测全绿；浅/深色走查 P0/P1=0，详 [it-055](wardrobe/specs/iterations/it-055-chat-card-detail-and-export.md)。

- **docs(repo)**: 建立 Agent 经验自进化闭环（仓库级元迭代 it-001）——新增根 `LESSONS.md` 经验库（头部读写/晋级/淘汰/容量规则 + 回填 8 条种子教训）；AGENTS.md 迭代流程新增第⑤步「经验沉淀」（原 CHANGELOG 步顺延为⑥）、上下文恢复新增必读 `LESSONS.md`；仓库级元迭代提案落档 `specs/iterations/it-001-agent-lessons-loop.md`，README 仓库约定补经验库一行。

## 2026-09-28

- **feat(wardrobe)**: it-054 顾问回复改为 Markdown 渲染，并将模型引用的当前衣橱单品解析为真实照片穿搭卡片；未匹配建议显式标注，复制保留原始 Markdown；新增解析器单测与 W13 浅/深色走查。

- **feat(wardrobe)**: it-052 全局黑白灰高级视觉与排版系统落地——主题 token、Material 色阶、全局 Typography、心愿占位、年度回顾、数据包状态符号、复制纸屑与导出长图均收口为中性灰阶；照片/成品图保留原色；同步更新 DESIGN.md 与 wardrobe 常青 spec。
- **feat(darkroom)**: it-006 黑白灰编辑式视觉——全局纸面/Surface/交互强调与 Snackbar 逆色改中性灰阶，重订 CJK 字级并限制字距用于短拉丁标签；作品标题改常规衬线最多两行、日期改 Sans 等宽数字灰阶排印；分享装饰同步去暖色，照片影调选项保留。`testDebugUnitTest assembleDebug installDebug` 通过，Android 14 AVD 浅/深色 W1–W4 走查，详 [it-006](darkroom/specs/iterations/it-006-monochrome-editorial-identity.md)。
- **feat(wardrobe)**: it-050 顾问升为第四个底部导航 Tab，新增多会话列表与详情；旧单会话自动进入历史索引，未配置 Key 时历史可读但不可新建/发送。Mock 环境启用独立的连接偏好、Keystore、会话、用量与 7 天/100 条/50 MB 响应缓存；整轮完成后才写缓存，失败/取消丢弃，W11 自检不走缓存。`:app:testDebugUnitTest` 70 项全绿、`:app:assembleDebug` 成功；模拟器确认四 Tab 与历史会话列表。Mock 真 Key 首发/缓存命中未做联网验收，避免调用未由用户在测试命名空间配置的凭证；详 [it-050](wardrobe/specs/iterations/it-050-chat-top-level-and-mock-cache.md)。
- **feat(darkroom)**: it-005 可选成片风格——W3 加入原色/暖片/银影/青幕/柔光横向缩略预览，效果即时应用到分享预览、静图和视频逐帧；柔光按亮部阈值生成确定性晕光并缓存，风格切换会使旧导出失效；压缩 W3 预览与编辑区，保持导出按钮首屏可见。Debug 构建及 Android 14 AVD W3 浅/深色检查通过，详 [it-005](darkroom/specs/iterations/it-005-selectable-photo-looks.md)。
- **feat(darkroom)**: it-004 暗房仪式与社交成片——W1 换成本地照片样片，W2 改为照片种子驱动的确定性药膜扩散并加入阶段短句与有重量的出纸回正，W3 将卡片预览提升为主视觉并新增 1:1/4:5/9:16 图片与视频统一构图（含故事安全区）；增加 `ShareLayout` 与 ADR-006，不引入依赖。Android 14 AVD 验证三画幅图片/视频均落库为 1080×1080、1080×1350、1080×1920，系统分享面板可打开；浅色/深色 W3 检查通过；`testDebugUnitTest` 38 项全绿，详 [it-004](darkroom/specs/iterations/it-004-emotional-darkroom-share.md)。

## 2026-09-27

- **fix(darkroom)**: it-003 功能收口与成片页信息架构——速度档即选即生效（原 `clock` 固定 STANDARD，慢洗/快显仅改标签：startSession 按所选档重建，斜率实测 8.0/12.5/27 %·s⁻¹ 三档对表；SLOW 导出 MP4 `mvhd`=13.999s 与 ExportPlan 精确一致）；成片页进度条上贴 CTA、无滚动 dump 到「冲洗中」、存图/存视频 y2148–2197 入首屏（追加成片卡高上限 360dp + 编辑卡/段间距压缩，对齐改版线框）、分享×2 与再洗一张收为一行；W1 移除重复「设置」chip（dump 实测入口 1 处）、footer 常驻视口底；33 单测 0 失败，验证记录回填 it-003（详 specs/iterations/it-003-result-ia-speed.md）。

- **fix(darkroom)**: it-002 稳定性与卡面几何——新增 `ui/PageInsets.kt` 四页统一让出状态栏+手势条（顶行控件中心 y111 死区→239 可点，⚙/← 中心 tap 实测双向通过）；`CardLayout.solve(width, maxH)` 按可用高度反解卡宽 + 签名域互斥 0.52/0.54w + 日期章超域缩字（Compose/native 双端），像素断言 W2/W3 签名区溢出 0px（原 +32/+100px）、长标题间距 45px 无重叠（原撞 ~140px）；`ManifestGuardTest` 锁 VIBRATE 权限防 P0 回归；CardLayoutTest 9 测试（含 maxH 反解/无界等价/任意宽度互斥）、`testDebugUnitTest` 33 绿；01/02/04 常青 spec 同步，验证记录回填 it-002。

- **fix(darkroom)**: it-001 首轮 UI 审查 hotfix——P0 定影落定必崩（manifest 缺 `VIBRATE`，`Haptics.confirm` 抛 SecurityException，logcat 复现 3 次）补 normal 权限即修、全流程回归通过；同轮完成 4 页走查 + 11 项功能自测（药水条 seek/甩一甩注入/双导出落盘/三入口全通），遗留 5×P1（速度档只改标签、页头无 insets 触摸死区、卡面溢出与重叠、首屏 CTA 裁切）与 5×P2，含 4 张改版线框与证据链，详 `reports/darkroom-ui-audit/` 与 it-001 验证记录。

- **feat(posthouse)**: it-001 驿站首次落地——macOS 菜单栏「烽火台 + 自动哨兵 + 邸报」全量实施（M1/M2/M3）：三态图标（全绿/狼烟 flame+计数/熄火）逐仓明细与对账级探测（status.json 与 `git status`/`rev-list` 逐值相等，实拍菜单栏火焰+1）；手动/CLI 推送注入 `http.version=HTTP/1.1` + `postBuffer=512MB` 并落日志；自动补推双闸门**默认全关**，硬规则「永不 force、永不自动 pull/rebase/merge、远端不通不推」全部落 `PushDecision` 纯函数单测——沙盒 e2e 断网→恢复 **4 秒自动补推**、白名单外仓库零触碰、non-FF 被拒只通知不动手、分叉每仓一次提醒、网络失败指数退避 30s→600s + 疑似代理故障提醒；22:30 邸报定时（改钟实测 16:06:05 自动产出）Markdown 战报与 git 真值逐项对账（6 提交/fix2 feat1 docs3/积压 1 仓/连续活跃 9 天）+ 成就规则引擎 6 枚纯函数可回放（首日点亮/连修三坑/深夜修罗/大部队/清仓大吉/千军一发，档案去重）；SwiftPM+build-app.sh 组装 .app 零 Xcode 工程，swift-testing **39 测试全绿**；实测踩坑四连入库档：子目录 `.git` 判定（rev-parse 会把 dist/gradle 误判成仓）、engine 串行队列自死锁、未 resume timer 释放 SIGTRAP（CLI exit=133）、TCC 文稿授权挂起 + 15s 扫描超时兜底；附 CLI 三旗标 `--probe/--push/--gazette` 脚本化入口与 network-rescue 一键诊断入口、M3 可选 LLM 润色开关（默认关，llm.key 0600，失败回退规则版；详 posthouse/specs/iterations/it-001 验证记录）。

- **fix(wardrobe+libs/carddeck+eats)**: it-048 卡组 hotfix——W8 甩卡被容器 `clipToBounds` 截断（it-031 旧库时代遗留，it-047 真实飞行后暴露）wardrobe/eats 两处已删并留防回归注释；连续快滑「滑不动」根因 = it-047 提交管线以 settledValue 翻转为信号（同向二次落锚不翻转即吞提交 + 排队 snapTo 抢锁拉回手中卡片），改**到达帧提交**（offset 精确到锚 + pointerDown/isAnimationRunning/flingInProgress 三重落定门——fling 的 spring animate 不在官方动画跟踪内，缺独立门实测一次甩卡连环推进 4 张）+ 按下快进结算 `onDeckDown`；实测单甩 +1、250ms 间隔连滑 +2、回卷/抽取/eats 连甩全过，双端单测绿（详 it-048-hotfix-deck-clip-continuity.md）。
- **docs(readme×5)**: 全仓应用说明文档配图——各 README 新增「界面速览」图集（图片入各应用 `docs/img/`，共 20 张 JPEG/PNG ≈4.6MB）：wardrobe 7 屏 + eats 6 屏（模拟器演示模式逐屏实拍，W 编号与线框对应）、travel-rpg 三站 4 场景（dev5199 + `__game.tp` 摆机位，乌兰布统用 sunset 时段）、xiangqi 终局对局全景（含着法表/胜率曲线）、island DebugShot 卡片（演示数据，GLM 橙环展示健康度配色）+ 设置窗；顺带修正 eats README 过期状态行与 wardrobe 构建注记；clips 无 UI 不配图。

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
