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

## ADR-005：胶囊落位用 CGWindowList 智能避让，不提供固定位置写死

- 背景：Leo 的菜单栏图标贴着刘海右侧排列（一云梯/ChatGPT/优酷/微信…挤到时钟），
  「贴刘海右缘」写死会压住前几个图标（it-001 首版实测翻车）。
- 决策：运行时扫描菜单栏带（layer 24/25，排除自家进程）的占用区间，
  右缘空隙 → 左缘空隙 → 刘海正下方悬浮 三级兜底；30s 定时 + 屏幕变化重算，
  固定展开/悬停中不挪窝。展开态跟随同锚点，允许交互时临时盖图标。
- 后果：胶囊位置随菜单栏排布漂移（有 30s 粒度）；M2 若要手动钉住位置，在此机制上加用户偏移。
