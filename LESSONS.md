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

- **迭代编号在提交前 re-check `git log`（提案落盘到提交之间并行会话可能占号）**——本仓 it-016/it-002/it-074 三次编号被并行会话抢占，撞号时对「自己动过的文件集」统一 sed 顺延（spec/ADR/代码注释/测试注释/CHANGELOG 行），别全局替换（会扫走对方的同号引用）。· [wardrobe it-075](wardrobe/specs/iterations/it-075-chat-result-cards.md) · 2026-09
- **超时判罚用「静默超时」（无输出时长），别用总时长**——LLM/agent 合法长思考可达数十分钟，总时长硬中断会把「想得久」误判成「挂死」。· [xiangqi it-001](xiangqi/specs/iterations/it-001-arena-mvp.md) · 2026-09
- **生图/长耗时 API 的 OkHttp 客户端必须显式拉长 readTimeout（默认 10s 必超时）**——同步生图一次 20~120s，超时配置收敛在 `OkHttpImageModel.defaultClient()` 一处别各适配器自建；异步任务型（提交+轮询）绝不长阻塞同步等，轮询间隔 + deadline 才是对的形态。· [wardrobe it-077](wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md) · 2026-09
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

- **跨进程页面返回结果不等于子进程已退出，重开单实例引擎前检查并等待旧进程释放，不用固定延时猜测**——否则旧Activity退出可能连带终止已重开的场景；本次第二轮复现，释放门禁后十轮通过。· [lottery it-004](lottery/reports/2026-09-30-it004/README.md) · 2026-09

- **嵌入 3D 渲染时，视口必须使用实际 Surface 缓冲尺寸，跨层投影共用同一几何参数；实体 ID 不能当 Transform 实例句柄**——先验证球体大小与管轨坐标，再看动画；分辨率降采样后尤其要重验。· [lottery it-003](lottery/specs/iterations/it-003-physical-draw-and-refined-ui.md) · 2026-09

- **Lazy 容器内的子项不吃常规尺寸约束**——列表空态居中用 `fillParentMaxSize`，普通 `fillMaxSize`/gravity 不生效。· [wardrobe it-043](wardrobe/specs/iterations/it-043-newui-p1-fixes.md) · 2026-09
- **`LaunchedEffect(Unit)` 驱动的一次性入场动画，Activity 重建会整段重放**——进度已过半却还在「出纸」多半是它；改由状态标志（如 `UiState.ejecting`）驱动，重建后直接落位。· [darkroom it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md) · 2026-09
- **自研/手动驱动的动画不在官方动画跟踪内**——`isAnimationRunning` 这类官方标志会恒 false，判断「在动」须自带标志（try-finally 维护）。· [wardrobe it-048](wardrobe/specs/iterations/it-048-hotfix-deck-clip-continuity.md) · 2026-09
- **驱动 UI 的忙碌态别用 `Job.isActive` 推导**——Job 生命周期不是 Compose 状态，写 job 引用只触发一次重组，后续帧不跟随（实测图标不转）；用显式 `mutableStateOf<Boolean>` + try/finally 复位。· [wardrobe it-058](wardrobe/specs/iterations/it-058-motion-polish.md) · 2026-09
- **遮罩/渐隐色必须以落点容器实测底色为准，别按框架默认推导**——本仓页面真底是 activity `windowBackground=@color/paper`（实测 #F7F7F5/#111110），想当然取 M3 `surface` 深色下差 10/255 成可见脏带；改色前先像素采样。· [wardrobe it-063](wardrobe/specs/iterations/it-063-wardrobe-chrome-slim.md) · 2026-09
- **`snapshotFlow` 只对「计算块内读过的状态」的变更重触发**——门/条件必须一并读进块内（如 `Triple(offset, gateA, gateB)`），在 collect 体外读则「终值恰在门关闭期写入、门随后翻开」永远唤不醒流，观察者饿死（冷启动首滑提交丢失实测，直到二次按下才被兜底路径补上）。· [wardrobe it-070](wardrobe/specs/iterations/it-070-card-view-toggle-theme-swipe.md) · 2026-09
- **「跟手段 1:1 + 终点在起点另一侧」的归入动画必须两段式**——单段插值跟随手指再收敛到左侧终点必中途倒车（插值导数中途变号）；先跟至分界点 `min(阈值, 0.55·归入距离)` 再 smoothstep 归入，单调无回拉。· [wardrobe it-070](wardrobe/specs/iterations/it-070-card-view-toggle-theme-swipe.md) · 2026-09
- **Compose 符号的 import 归属别靠猜**——`collectAsState` 在 `androidx.compose.runtime`（lifecycle 包只有 `collectAsStateWithLifecycle`）、`withFrameNanos` 也在 compose.runtime 而非 kotlinx.coroutines、M3 `NavigationBarItem` 是 `RowScope` 扩展（顶层裸调编译不过）、`BoxWithConstraintsScope` 不实现 Density（size→px 要 `with(density){}`）；报 unresolved/不适用先查官方包再改代码。· [lottery it-001](lottery/specs/iterations/it-001-emotional-lottery-demo.md) · 2026-09
- **别把 `animator_duration_scale` 当 Compose 动画的减速旋钮**——它只缩放 ValueAnimator，Compose 的 animateTo/spring 不吃；排查「动画慢/提交延迟」先排除这个假因（本仓曾据此误判），慢放验证走录屏逐帧或程序化插桩。· [wardrobe it-070](wardrobe/specs/iterations/it-070-card-view-toggle-theme-swipe.md) · 2026-09
- **懒网格别钉「固定高度 + `userScrollEnabled=false` 再套外层 `verticalScroll`」**——视口被撑成全数据集，懒加载整页失效、所有卡一次性组合常驻，页面滚动在拖一棵已全量组合的树；改单容器 LazyGrid，页头做 span 项。· [wardrobe it-071](wardrobe/specs/iterations/it-071-scroll-performance.md) · 2026-09
- **`androidx.baselineprofile` 插件会自建 benchmarkRelease/nonMinifiedRelease 测试变体（debug 默认禁用，不存在 plain `release`）**——在测试模块手写 `buildType=="release"` 过滤会把它们全关，生成任务秒级空跑且只报 “No baseline profile rules were generated”；变体交给插件，别手筛。· [wardrobe it-071](wardrobe/specs/iterations/it-071-scroll-performance.md) · 2026-09

### Agent 协作与工具

- **连续取帧别每次都 `am start`**——反复启动会重建 Activity、重放一次性动画，制造「进度不动/动画卡住」的假 bug；取帧只用 `screencap`，`am start` 只在拉前台时用一次。· [darkroom it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md) · 2026-09
- **视觉模型评审的提示词里严禁出现预期值/预期结论**——模型会把喂进去的期望（尺寸、颜色、间距）镜像复述成「实测结果」，被带偏的评审比不评更危险；评审提示词只描述要看的区域，结论以像素采样 + 中性提示词双轨为准（it-063 靠此既纠了评审、也推翻了自己的错误初判）。· [wardrobe it-063](wardrobe/specs/iterations/it-063-wardrobe-chrome-slim.md) · 2026-09
- **浏览器/IAB 截图可能拿到旧帧**——关键状态截图连拍两次，取第二张。· [travel-rpg scenes 报告](reports/2026-09-24-travel-rpg-scenes/README.md) · 2026-09
- **DataStore/Flow 首读可能晚于用户点击，回填旧快照会把刚选的值冲回去**——即时选择先写会话态，启动回填只做一次且「用户已选则不回填」；常驻 `collect` 更是持续回冲源。· [darkroom it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md) · 2026-09
- **`cmd uimode night` 切了但 UI 没变：先查 `mGlobalConfiguration` 是否含 `night`，再 force-stop 冷启应用**——配置变更偶发不重建 Activity，热重启下 `isSystemInDarkTheme` 会拿旧值；冷启后仍未变才去查应用主题链路。· [wardrobe it-055](wardrobe/specs/iterations/it-055-chat-card-detail-and-export.md) · 2026-09
- **screencap 可能连续返回旧合成帧——「连拍取第二张」不成立，且幻帧可能带新时钟、甚至显示已崩进程的「正常运行」画面**；判 UI 以 dump/dumpsys 为据、**判原生层稳定性只认 logcat 崩溃计数 + pidof**（lottery it-002 曾被幻帧骗过三轮「3D 正常」）。旧帧常见根因是 AVD 熄屏——取帧前 `svc power stayon true` + `KEYCODE_WAKEUP`。· [wardrobe it-066](wardrobe/specs/iterations/it-066-export-loop-closure.md) / [lottery it-002](lottery/specs/iterations/it-002-broadcast-3d-replay.md) · 2026-09
- **判 Gradle 绿不绿别看管道退出码**——`./gradlew … | tail` 返回的是 `tail` 的 0，BUILD FAILED 照样 0 且旧 test-results 会伪装成「全绿」；用 `${pipestatus[1]}` 或 grep `BUILD SUCCESSFUL` 作判据。· [wardrobe it-071](wardrobe/specs/iterations/it-071-scroll-performance.md) · 2026-09
- **AVD 重启（快照恢复）可能把 streamed install 回滚成旧 APK——`install Success` ≠ 重启后还在**：走查前先 `dumpsys package <pkg> | grep lastUpdateTime` 核对安装时间或 dump 一条新语义作证据；tap 落点与页面跳转结果对不上、输入队列不消费新事件＝同一类僵死征兆，直接 `emu kill` 重启并**重装**再查，别把旧 APK 行为当新 bug。· [wardrobe it-072](wardrobe/specs/iterations/it-072-slot-picker-discoverability.md) · 2026-09
- **验证 200ms 级动效：screenrecord 帧率不稳（2–4fps）拍不到中间态——先把系统动画缩放调 5× 慢放再录**，转场拉长到 1s+ 后低帧率也能抓 3–5 帧；判读不靠肉眼靠像素（纹理块轨迹判别共享元素 vs 整页 fade：前者有「两态之外」的中间位置/尺寸帧），视觉模型通道失效时的兜底。· [wardrobe it-058](wardrobe/specs/iterations/it-058-motion-polish.md) · 2026-09
- **接不熟的 native/SDK：API 签名先 `javap` 解开缓存里的 jar 对真相，别按记忆猜**——Filament 一晚上被猜错 7 处签名（`Texture$PixelBufferDescriptor` 嵌套、`IndexType`、`uniformParameter`、`samplerParameter` 四参型优先、`MaterialBuilder.platform(MOBILE)` 默认按桌面编 shader、`Box` 无四参构造、`castShadows` 单数）；猜一次错一轮编译，javap 一次全对。· [lottery it-002](lottery/specs/iterations/it-002-broadcast-3d-replay.md) · 2026-09
- **auto 模式会拦「起本地 http.server 分发 APK/预览」这类网络服务命令——不换工具绕，直接把可跑的一行命令交给用户自己执行**（含完整端口与目录），并在交付里说明被拦原因；生成好的 APK 路径与二维码照常给。· [lottery it-001](lottery/specs/iterations/it-001-emotional-lottery-demo.md) · 2026-09
- **ModalBottomSheet 内滚动区禁 `weight(1f)`/`fillMaxSize()` 撑满**——视口被强制拉到上限高度，表单短于视口时钉底动作栏上方悬出空白带，拖动时「表面在动、内容不动」的分层感即来源于此；滚动区应贴合内容 + `heightIn(max≈屏高×0.74~0.80)` 给动作栏留预算。同坑姊妹：sheet 内异步解析状态（先转圈后弹内容）应上提调用方 produceState 以参数传入，且重构 if/else 分支时检查布尔赋值是否被整段带走（gen6「永久加载条」回归）。· [wardrobe it-077](wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md) · 2026-09
- **模拟器验证不了「拖动跟手性」**——重图形 app 在 AVD 上连续拖动帧渲染近乎冻结（p50=400ms/帧），同一 `input swipe` 在桌面启动器却大幅实时跟手；手势流畅度类问题只能真机验收，模拟器证据只可用来定位结构性缺陷（截图空白带/布局跳变），帧间差分全 0 ≠ 手势路由 bug。· [wardrobe it-077](wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md) · 2026-09
- **M3 Button/OutlinedButton 的 contentPadding 是整只 PaddingValues 覆盖、不是与默认值合并**——只想收窄横向内距时只写 `PaddingValues(horizontal=…)` 会把默认 vertical 一并抹成 0，按钮高度当场塌成矮条；覆盖时四个方向都要显式给。· [wardrobe it-077](wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md) · 2026-09
- **模拟器长会话做 UI 点击验证：每步先 uiautomator dump 拿真实 bounds 再点**——按钮位置随内容滚动/版本变化漂移，加上 AVD 自动熄屏后 input tap 全打空、screencap 还你旧帧，两重假象叠加能让「功能没实现」的误判持续半小时以上；熄屏先 `svc power stayon true` + WAKEUP。· [wardrobe it-077](wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md) · 2026-09
- **92% 屏高的工作流面板直接做全屏页，别塞 BottomSheet**——sheet 的自管高度/嵌套滚动接交/多 sheet 接力动画是「分层·不跟手·抖动」的整个问题类；全屏 Dialog（usePlatformDefaultWidth=false）+ 内部双模式 AnimatedContent 切换 + 48dp 硬等高动作栏，结构上消灭之。· [wardrobe it-077](wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md) · 2026-09
