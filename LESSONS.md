# LESSONS.md — 经验库（Agent 自进化闭环）

跨应用、跨任务的**行为规则**沉淀处：记「下次还会用到的一行规则」，不记流水账。
每次迭代收尾、每次修完有普适性的 bug，先自问一句——**有没有下次还会踩的坑、还能省时间的做法？** 有则写进来。

## 怎么用（人 + AI 协作者通用）

- **读**：开工前按 [AGENTS.md](AGENTS.md) 上下文恢复清单必读本文件，先看规则再动手。
- **写**：迭代收尾时写回（AGENTS.md 流程第⑤步），**≤3 条**；每条必须是可执行的一行规则，附出处链接与年月。只收跨任务/跨应用可复用的教训，应用内部细节留在迭代文件里。
- **晋级（自进化核心）**：同一教训第 3 次出现、或已成全局铁律 → 升级到更高形态——流程类进 [AGENTS.md](AGENTS.md)，视觉类进 [DESIGN.md](DESIGN.md)，应用类进该应用常青 spec；能被脚本/CI 防住的写成自动检查（最高形态）。
- **淘汰**：条目失效（技术栈变化、机制已兜底）直接删除，不留墓碑。
- **容量**：每节 ≤ 15 条；满额先晋级或删除，再收新条目。

## 一条经验的一生

```
事件（迭代/报告里踩的坑） → 一行规则（本文件） → 固化（AGENTS.md / DESIGN.md / 常青 spec） → 自动化（脚本 / CI）
```

## 条目

### 流程与协作

- **超时判罚用「静默超时」（无输出时长），别用总时长**——LLM/agent 合法长思考可达数十分钟，总时长硬中断会把「想得久」误判成「挂死」。· [xiangqi it-001](xiangqi/specs/iterations/it-001-arena-mvp.md) · 2026-09
- **spec/文档不钉死易漂移的数字（测试用例数等）**——以 CI/测试套件实际结果为准，写死必漂移。· [wardrobe it-020](wardrobe/specs/iterations/it-020-arch-review-stabilize.md) · 2026-09
- **「本地全绿」≠「CI 绿」**——CI 配置入库后必须看首跑结果，未实跑的流水线视同未验证。· [wardrobe it-020](wardrobe/specs/iterations/it-020-arch-review-stabilize.md) · 2026-09
- **`loadThumbnail` 返回的是系统存的低质缩略（常见 512px JPEG）**——凡上屏宽度 >600px 或位图还要再进导出/视频管线的场景，必须 ImageDecoder 解码原图并 setTargetSize 降采样（记得 ALLOCATOR_SOFTWARE，android.graphics.Canvas 画不了硬件位图）；纯小图网格才用 loadThumbnail。· [darkroom it-012](darkroom/specs/iterations/it-012-album-first-ia.md) · 2026-09
- **Compose 的 `Modifier.weight` 只对直接父级 Column/Row 生效**——写在自定义组件的 content lambda 里能编译（外层作用域词法解析）但布局上无效，且失败模式阴险：不带 weight 的兄弟（如 pager）会吃满剩余高度、把底栏顶出屏幕，观感像「控件消失」——weight 一律挂到组件自身的 modifier 参数上，逐屏核对底栏可见性。· [darkroom it-012](darkroom/specs/iterations/it-012-album-first-ia.md) · 2026-09
- **每应用独占 AVD（wardrobe_* / darkroom_*），设备命令一律走 `tools/emu.sh` 或 `adb -s`，序列号按 AVD 名解析不硬编码**——裸 `adb`/`gradlew installDebug` 会打到所有在线设备，并行会话互抢前台、串包、截图混对方画面；`emulator-端口` 随启停漂移只是显示名，绑定看 AVD 名。· [specs/it-002](specs/iterations/it-002-emu-device-split.md) · 2026-09
- **走查采集前先显式复位持久化偏好并核对选中态（uiautomator dump 的 note/标签即证据）**——上一会话留下的偏好（显影模式等）会让整轮连拍全程拍错对象；另：夜模式切换后等 Activity 重建完再操作，重建风暴+连拍曾把 App 打成 ANR，冻结帧会被误读成 App 缺陷（force-stop 重走即可销项），uiautomator dump 在 ANR 后也会返回旧对话框帧。· [darkroom it-008](darkroom/specs/iterations/it-008-store-polish-motion.md) · 2026-09
- **成片/导出的「末态保真」必须精确归零，别留「极轻残留」**——blur 0.002×短边≈2px、黑位抬 13/255 这类「几乎看不见」的末态叠加，观感=蒙雾低清，会连环被误判成解码/缩略图问题；过程效果尽管猛，progress=1 一律数学上=单位阵。· [darkroom it-013](darkroom/specs/iterations/it-013-clean-pager-and-end-fidelity.md) · 2026-09
- **报「画质差/像预览图」先做像素级对照定性再改码**（成片 vs 原图同管线参考：mean/std/laplacian/黑位四指标，高频合成考题图+真实照片双轨）——肉眼截图会把显影中段、截图压缩误读成质量缺陷，三轮猜因不如一组对照。· [darkroom it-013](darkroom/specs/iterations/it-013-clean-pager-and-end-fidelity.md) · 2026-09

- **同一坑第二次出现就别再靠「下次记得」，直接上机制兜底**——版本号停更（ADR-021 记过 0.1.0 停更对齐）在 0.5.0 上原样复发，人肉 bump 必忘；构建版本身份改从 git 提交数自动生成，零维护。· [wardrobe it-067](wardrobe/specs/iterations/it-067-auto-versioning.md) · 2026-09

### 架构与数据

- **解析外部/持久化数据：未知字段一律忽略不崩**——向前兼容是硬要求，收紧解析前先确认不破坏旧数据。· [libs/agent 00-architecture](libs/agent/specs/00-architecture.md) · 2026-09
- **别拿状态「翻转」当提交信号，改用「到达/精确值 + 门禁」**——快速重复动作下 edge 信号会漏触发；配幂等门防重放。· [wardrobe it-048](wardrobe/specs/iterations/it-048-hotfix-deck-clip-continuity.md) · 2026-09
- **新入口要出同类内容，先搜同组件的既有调用点再决定复用还是新造**——导出/分享这类面板往往早已支持「无实体」参数（如 ExportSheet 的 `existingOutfit=null`，搭配页/心愿先例），签名够用就零新 UI 接线。· [wardrobe it-055](wardrobe/specs/iterations/it-055-chat-card-detail-and-export.md) · 2026-09

### Android / Compose

- **Lazy 容器内的子项不吃常规尺寸约束**——列表空态居中用 `fillParentMaxSize`，普通 `fillMaxSize`/gravity 不生效。· [wardrobe it-043](wardrobe/specs/iterations/it-043-newui-p1-fixes.md) · 2026-09
- **`LaunchedEffect(Unit)` 驱动的一次性入场动画，Activity 重建会整段重放**——进度已过半却还在「出纸」多半是它；改由状态标志（如 `UiState.ejecting`）驱动，重建后直接落位。· [darkroom it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md) · 2026-09
- **自研/手动驱动的动画不在官方动画跟踪内**——`isAnimationRunning` 这类官方标志会恒 false，判断「在动」须自带标志（try-finally 维护）。· [wardrobe it-048](wardrobe/specs/iterations/it-048-hotfix-deck-clip-continuity.md) · 2026-09
- **驱动 UI 的忙碌态别用 `Job.isActive` 推导**——Job 生命周期不是 Compose 状态，写 job 引用只触发一次重组，后续帧不跟随（实测图标不转）；用显式 `mutableStateOf<Boolean>` + try/finally 复位。· [wardrobe it-058](wardrobe/specs/iterations/it-058-motion-polish.md) · 2026-09
- **遮罩/渐隐色必须以落点容器实测底色为准，别按框架默认推导**——本仓页面真底是 activity `windowBackground=@color/paper`（实测 #F7F7F5/#111110），想当然取 M3 `surface` 深色下差 10/255 成可见脏带；改色前先像素采样。· [wardrobe it-063](wardrobe/specs/iterations/it-063-wardrobe-chrome-slim.md) · 2026-09

### Agent 协作与工具

- **连续取帧别每次都 `am start`**——反复启动会重建 Activity、重放一次性动画，制造「进度不动/动画卡住」的假 bug；取帧只用 `screencap`，`am start` 只在拉前台时用一次。· [darkroom it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md) · 2026-09
- **视觉模型评审的提示词里严禁出现预期值/预期结论**——模型会把喂进去的期望（尺寸、颜色、间距）镜像复述成「实测结果」，被带偏的评审比不评更危险；评审提示词只描述要看的区域，结论以像素采样 + 中性提示词双轨为准（it-063 靠此既纠了评审、也推翻了自己的错误初判）。· [wardrobe it-063](wardrobe/specs/iterations/it-063-wardrobe-chrome-slim.md) · 2026-09
- **浏览器/IAB 截图可能拿到旧帧**——关键状态截图连拍两次，取第二张。· [travel-rpg scenes 报告](reports/2026-09-24-travel-rpg-scenes/README.md) · 2026-09
- **DataStore/Flow 首读可能晚于用户点击，回填旧快照会把刚选的值冲回去**——即时选择先写会话态，启动回填只做一次且「用户已选则不回填」；常驻 `collect` 更是持续回冲源。· [darkroom it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md) · 2026-09
- **`cmd uimode night` 切了但 UI 没变：先查 `mGlobalConfiguration` 是否含 `night`，再 force-stop 冷启应用**——配置变更偶发不重建 Activity，热重启下 `isSystemInDarkTheme` 会拿旧值；冷启后仍未变才去查应用主题链路。· [wardrobe it-055](wardrobe/specs/iterations/it-055-chat-card-detail-and-export.md) · 2026-09
- **screencap 可能连续返回同一旧合成帧——「连拍取第二张」对 screencap 不成立，判新旧看帧内状态栏时钟与当前时间是否一致**；定界面状态以 `uiautomator dump`（content-desc 即状态证据）与 `dumpsys window | grep mCurrentFocus`（焦点归属）为判据，截图只作辅证。**旧帧最常见根因是 AVD 熄屏**（帧冻结在熄屏时刻）——取帧前 `svc power stayon true` + `KEYCODE_WAKEUP`。· [wardrobe it-066](wardrobe/specs/iterations/it-066-export-loop-closure.md) · 2026-09
- **验证 200ms 级动效：screenrecord 帧率不稳（2–4fps）拍不到中间态——先把系统动画缩放调 5× 慢放再录**，转场拉长到 1s+ 后低帧率也能抓 3–5 帧；判读不靠肉眼靠像素（纹理块轨迹判别共享元素 vs 整页 fade：前者有「两态之外」的中间位置/尺寸帧），视觉模型通道失效时的兜底。· [wardrobe it-058](wardrobe/specs/iterations/it-058-motion-polish.md) · 2026-09
