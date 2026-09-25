# 06 — 架构决策记录（ADR）

## ADR-001：SPM + CLT 构建，不建 Xcode 工程；swift-testing 替代 XCTest

- 背景：本机只有 Command Line Tools（Swift 6.1），无完整 Xcode；个人自用分发无 TestFlight 需求。
- 决策：`swift build` + `tools/make-app.sh` 手工 bundle（Info.plist + ad-hoc codesign）。
  CLT 无 XCTest 模块，测试用 Swift 6.1 自带 swift-testing（`import Testing` / `#expect`）。
- 后果：无 xcodebuild 归档/公证（Gatekeeper 首开需右键打开）；SMAppService 需 .app 形态才生效。
  若日后要上架/公证再装 Xcode。

## ADR-002：官方 monitor 接口 + API Key，而非抓 Cookie

- 背景：智谱提供官方用量端点（社区 openusage / opencode-glm-quota 等已验证），
  鉴权头 `Authorization: <key>` 不带 Bearer；Cookie 方案脆弱且要处理登录态续期。
- 决策：用户在设置页粘 API Key → 只存 Keychain；5 分钟轮询（查询不消耗套餐额度）。
- 后果：`limits[]` 字段无公开文档，解析器宽松 + 原始响应带回设置页诊断，待 spike 校准后收紧。

## ADR-003：双端点 + auto 探测记忆偏好

- 背景：Leo 的 ZCode 环境走 z.ai 国际线（ZCODE_BASE_URL=zcode.z.ai），但套餐 slug 是
  bigmodel-individual-coding-plan，Key 归属待 spike 确认。
- 决策：endpointMode = auto/bigmodel/zai；auto 按记忆的偏好端点优先逐个试，
  成功后把赢家写回 `preferredEndpoint`。

## ADR-004：NSPanel 双态 setFrame + NSHostingView 借鼠标事件

- 背景：灵动岛需要 hover 展开/移开收起，而 accessory App 常年不持焦点。
- 决策：面板尺寸两态切换（NSAnimationContext 近似 spring）；
  NSHostingView 子类 override mouseEntered/Exited + TrackingArea `.activeAlways`；
  点击外部收起用全局/本地 NSEvent monitor。
- 后果：全屏截图不合成该层窗口（用 `screencapture -l` 验证）；其余行为正常。

## ADR-005：胶囊以刘海水平中心为唯一锚点；hover 走防抖状态机

- 背景：v1 写死「贴刘海右缘」压住 Leo 贴刘海排列的状态图标（一云梯/ChatGPT）；
  v2 改 CGWindowList 智能避让（右缘→左缘→下方）后胶囊落在刘海左缘，视觉上与刘海分离，
  Leo 反馈「还是差很多」，且 hover 时窗口服务器在变形动画中补发成对 enter/exit 导致反复闪。
- 决策：**刘海挖槽本身就是永不冲突的锚点**——紧凑态 118×26 与展开态 352×228 都以刘海水平中心
  居中（紧凑态融进刘海黑区、展开态从刘海向下生长）；无刘海屏回退菜单栏之下顶部居中。
  hover 用防抖状态机：进入 60ms 延迟展开、退出 180ms 延迟收起（均可取消），
  且「退出时光标仍在面板 frame 内」判为假离开直接忽略——变形动画中的补发事件不再引发振荡。
  展开态临时盖过部分状态图标属于交互态行为，可接受（点外部/hover 离开即让位）。
- 后果：布局确定、与菜单栏排布彻底解耦（30s 重算定时器删除）；
  展开时会短暂遮挡刘海附近图标；CGEvent 模拟悬停 + CGWindowList 采样实测零振荡。

## ADR-006：默认完全隐藏，刘海挖槽即隐形触发区；展开卡片贴屏幕顶沿

- 背景：v3「刘海下沿悬挂胶囊」仍有两处不满意——常驻内容挡视线，且胶囊顶边与刘海底沿之间
  因挖槽圆角出现「缺角」缝隙。
- 决策：默认态窗口 = 刘海挖槽矩形本身（180×safeTop、全透明）：挖槽是硬件遮挡区，
  窗口放在那里天然不可见，却仍在屏幕坐标空间内可收 hover 事件——隐形触发区零成本获得。
  展开态从刘海中心向下生长且**顶边贴屏幕顶沿**（y=0、顶部两角直角），把顶边整段盖住，
  与刘海之间不存在任何可见缝隙。动画保留（Leo 澄清「不要的是震动不是动画」）。
- 后果：平时屏幕零占用；触发区固定为刘海矩形；隐藏/展开的窗口尺寸跳变由防抖状态机吸收，
  实测 hover 一次展开、移开一次收起，零振荡。

## ADR-008：更名「灵岛 / island」，定位为通用刘海功能入口容器

- 背景：原名「GLM 灵动岛」把应用与首个内容源（GLM）绑死。Leo 定位：这只是他的一个功能入口，
  当前承载 GLM TOKEN 信息，未来会扩展其它 TOKEN 厂商与其它能力。
- 决策：目录 `island/`、显示名「灵岛」、BundleID `com.spartapps.island`、
  钥匙串 service `com.spartapps.island`（迁移时旧条目作废，需用 GLM_ISLAND_SEED_KEY 重新注入）。
  内容源保持 `UsageProviding` 协议缝，厂商解析器独立成类（现为 GLM 的 QuotaResponseParser），
  it-002 起按 provider registry 演进。
- 后果：钥匙串旧条目（com.spartapps.glm-island）废弃需重注入；其余无破坏。

## ADR-007：生长动画在 SwiftUI 内做 reveal，不做窗口尺寸动画

> **2026-09-25 演进（终极架构，见 04/US-1）**：「窗口瞬移回挖槽矩形 / 50ms reveal 初始化」
> 已被**窗口永久常驻全尺寸**取代——窗口几何一次到位永不变化，遮罩动画独占显隐；
> 悬停改 30Hz 光标轮询、点击穿透改 hitTest，reveal 状态机删除。
> 本 ADR 的核心结论（动画在 SwiftUI 遮罩内做、窗口不参与动画）仍然成立。

- 背景：v4 展开时窗口矩形从挖槽插值到卡片，四边同时动，Leo 反馈像「从左下/右下角往上延伸」；
  且直接改高度动画在「内容全透明的隐藏态」上不生效（窗口服务器对无内容窗口跳过几何动画/
  与瞬移无法区分），SwiftUI 侧 reveal=false→true 若与卡片插入同帧提交也不补间（初插不动画）。
- 决策：窗口一次瞬移就位（内容此刻透明，不可见），生长动画交给 SwiftUI——卡片完整布局，
  可见区域由**动画化圆角遮罩**驱动（`reveal` 切换 刘海尺寸 180×safeTop ↔ 全尺寸 352×全高，
  **宽高一起 spring(0.32/0.9)**：展开从刘海长出、收起对称吸回，顶边钉死 + 圆角跟随遮罩）。
  先渲染 32pt 起始帧、50ms 后置 reveal=true（初插不补间坑）。收起动画结束后窗口瞬移回挖槽矩形。
  卡片内容顶边 = safeTop + 6，避开刘海挖槽；UI 减法：去掉演示徽章/顶部高光线/冗余提示块，
  颜色只保留在进度条（标签白色），行布局收敛为「标签+百分比·重置时间 / 进度条」两行。
- 后果：动画方向确定（展开向下长出、收起对称吸回，宽高同步）；窗口几何不参与动画，行为可预测；
  代价是展开期间窗口整体先于内容出现（透明无感知）。

## ADR-009：ProviderRegistry 单表驱动 + 按源状态 + IslandLayout 布局单一真源（it-003）

- 背景：ADR-008 承诺的 registry 未兑现——加第三个源要改 6+ 处按源硬编码
  （fetcher 分支 / store 4 组 save-clear / 设置两段分区 / 卡片 `ProviderPanel(kind:)` 手排两个，
  卡片根本不渲染新源）；`refreshAll` 全局单 status 吞掉失败源错误（it-002 AC4 违例）；
  352 宽 / 高度公式 / 180 触发宽在 RootView、WindowController、hitTest 各写一份，改布局必漏其一。
- 决策：
  1. **`ProviderRegistry.all` 单表**：凭证账户与文案、`makeProvider` 工厂、演示数据一条注册；
     卡片面板 / 设置分区 / 菜单摘要 / 凭证路径全部遍历本表——**新增源 = `ProviderKind`
     case + 注册表一条 +（新格式才需）解析器**，UI 与窗口代码零改动。
  2. **`states: [ProviderKind: SourceState]`**（快照/最近错误/在途按源独立）：
     失败只落在该源（面板头红字 + 页脚按注册表顺序取首个错误），他源照常展示、成功即清除；
     凭证清除同时删除磁盘快照（防重启幽灵数据）。
  3. **`IslandLayout` 唯一推导**：宽度/高度/面板度量/矩形全在一处，
     SwiftUI 遮罩、hitTest 穿透、窗口帧消费同一实例；面板高度公式与渲染分支严格对齐
     （`detailLineCount`），数据变化经 `store.objectWillChange → syncLayout()` 幂等 setFrame。
- 后果：加源成本收敛为注册表一条 + 解析器；布局调整只改 `IslandLayout` 常量；
  单测锁住注册表完整性、宽度/高度公式、页脚状态优先级。
  代价：注册表条目是新增源的必经清单（漏注册在设置/卡片处显式暴露，测试直接失败）。

## ADR-010：凭证迁出钥匙串，改本机 0600 限制文件（补录 it-002 增补 4，2026-09-25）

- 背景：本项目 ad-hoc 签名（`tools/make-app.sh` 每次重构建签名都变）→ 钥匙串条目 ACL 失效
  → 每版启动弹窗要密码，Leo 反馈「老弹钥匙串密码」。
- 决策：Key/Cookie 迁到 `~/Library/Application Support/island/credentials.json`
  （chmod 0600，原子写入），首启 `migrateFromKeychain()` 自动迁移并**删除钥匙串旧条目**；
  凭证绝不入仓库/日志（`GLM_ISLAND_SEED_*` 仅空时注入）。
- 后果：零弹窗、重构建/重启稳定；安全边界从钥匙串 ACL 降为文件权限位（单机个人工具可接受）。
  it-003 起清凭证同时删对应 snapshot-<kind>.json。

## ADR-011：DebugShot 自截图作为 UI 走查标准通路（it-003）

- 背景：系统截屏通路全部受阻——`screencapture` 全屏不合成 statusBar 层面板、
  `screencapture -l <id>` 与 ScreenCaptureKit 都要屏录 TCC 授权（开发机常拒/未授权，
  实测 -3801 / 空白图）。UI 迭代需要可脚本化的截图回归。
- 决策：**渲染本进程自己的窗口**——`DebugShot` 用 `NSBitmapImageRep + cacheDisplay`
  直接导出岛卡/设置窗 PNG（自己的视图不走 TCC），`GLM_ISLAND_SHOT=<目录>` 启动 ~2.2s
  截图后 `exit(0)`；可配 `GLM_ISLAND_SHOT_SETTINGS=1`（连设置窗）、
  `GLM_ISLAND_BLANK=1`（空态）、`GLM_ISLAND_DEMO/EXPAND`（演示/即展开）组合出全部走查状态。
- 后果：走查命令一行可复现（见 README）；不依赖任何系统授权。
  代价：只能截本 App 自己的窗口（菜单栏图标/系统 UI 仍需人工目检）。
