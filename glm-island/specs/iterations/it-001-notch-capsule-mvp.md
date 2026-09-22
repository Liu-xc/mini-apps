# it-001 — glm-island：GLM Coding Plan 灵动岛监控（刘海胶囊 MVP）

状态：**已确认并实施（2026-09-22，Leo：「实施」）**
日期：2026-09-22

## 背景与动机

Leo 订阅了 GLM Coding Plan（个人版），控制台展示三档用量：**5 小时窗口**、**每周**、**ZCode MCP**，
每档带剩余百分比与重置时间。目前每次都要开浏览器登录控制台才能看。

目标：做一个 macOS **灵动岛风格**常驻控件——胶囊贴合 MacBook 刘海右缘，紧凑态三条迷你进度条一眼扫完，
hover/点击 spring 展开成与控制台一致的三行明细面板。

机器：MacBook Air M2 13"（2560×1664，**有刘海**），macOS 15.7。

## 数据源调研结论（2026-09-22 已验证）

智谱提供官方用量监控接口，社区多个监控工具（openusage、opencode-glm-quota、VS Code Z.ai Usage Tracker 等）均在用：

| 项 | 结论 |
|---|---|
| 国内端点 | `GET https://open.bigmodel.cn/api/monitor/usage/quota/limit` |
| 国际端点 | `GET https://api.z.ai/api/monitor/usage/quota/limit` |
| 鉴权 | 请求头 `Authorization: <api-key>`，**不带 `Bearer` 前缀**（与常规对话调用不同） |
| 返回 | `limits[]` 数组，每元素为一条配额（用量比例、重置时间等） |
| 伴生接口 | `/api/monitor/usage/model-usage`、`/api/monitor/usage/tool-usage`（M2 再用） |
| 可达性 | 本机 curl 直连 bigmodel.cn 已通（无 Key 返回 `{"code":401,...}`，路径正确） |

**风险与对策**：接口无公开文档，`limits[]` 具体字段名未最终锁定（GitHub 当前从本机不可达，未能读到社区解析源码）。
对策：M0 spike 拿真实 Key 抓一次响应 → 固化为 fixture + Codable 模型；解析字段全部 optional 宽松处理，接口变更不崩溃。

**两个 spike 顺带确认项**：
1. 百分比语义：截图表头为「剩余额度」，初判数值 = **剩余**（MCP 100% = 未动用）。以真实数据对照控制台页面确认。
2. monitor 查询本身不计费/不耗套餐额度（默认 5 分钟轮询 ≈ 288 次/天，量级极小）。

注：Leo 的 ZCode 环境 `ZCODE_BASE_URL=https://zcode.z.ai` 走国际线，但套餐 slug 为 bigmodel-individual-coding-plan；
App 按「双端点 + 自动探测」设计，spike 时用真实 Key 确定主平台。

## 用户故事

- **US-1 常驻胶囊**：胶囊贴合主屏刘海右缘常驻，紧凑态显示三档各一条迷你进度条（5h=蓝 / 每周=绿 / MCP=橙，与控制台同色）；
  不抢键盘焦点、切空间/全屏 App 时仍在。
- **US-2 展开详情**：hover spring 展开、移开自动收起、点击可固定；展开面板三行 = 与控制台一致
  （档位名 / 剩余% / 进度条 / 重置时间点或倒计时），底部显示「x 分钟前已刷新」+ 手动刷新按钮 + 设置按钮。
- **US-3 自动刷新**：默认 5 分钟轮询官方接口；请求失败降级显示上次快照并标注陈旧；401 明确提示 Key 失效。
- **US-4 状态预警**：剩余 ≤20% 该行转橙、=0% 转红显示「已用完 · HH:mm 重置」；紧凑胶囊对应迷你条同步变色，
  全部健康时用三档本色。
- **US-5 设置与安全**：API Key 存 **Keychain**（不入日志/缓存）；设置窗口可配：端点（国内/国际/自动探测）、
  刷新间隔、开机自启（SMAppService）；菜单栏图标提供 刷新 / 打开控制台 / 设置 / 退出。
- **US-6 离线韧性**：断网、超时、解析失败均不崩溃：胶囊灰显 + 保留陈旧数据 + 恢复后自动重试（指数退避）。

## 验收标准

- AC1 胶囊贴齐刘海右缘，重启 App 后位置正确；展开/收起全程不抢焦点（打字不中断）。
- AC2 hover 展开 → 移开收起 → 点击固定/取消固定，三种交互可用，spring 动画流畅（无跳帧、无残影）。
- AC3 展开面板三档数据与控制台页面一致（同刻 ±1%）；重置时间以本地时区展示。
- AC4 断网时显示陈旧快照 + 「x 分钟前」；网络恢复后 ≤1 分钟内自动恢复；Key 无效时展开面板明示。
- AC5 Key 仅存 Keychain：快照缓存文件、日志、二进制 strings 中均无明文 Key。
- AC6 `swift build` 全绿；数据层单测覆盖：spike fixture 的 Codable 解析、阈值状态机、倒计时计算（固定时钟注入）。
- AC7 开机自启开/关即时生效。

## 技术方案

- **栈**：Swift 6.1 + SwiftUI + AppKit 混合，SPM 构建（本机已有 CLT，**无需完整 Xcode**）；零第三方依赖。
- **窗口**：无边框 `NSPanel`，`level = statusBar`，non-activating，`canJoinAllSpaces + fullScreenAuxiliary`；
  有刘海屏（`safeAreaInsets.top > 0`）贴刘海，无刘海屏回退顶部居中悬浮。
- **分层**：`IslandWindowController`（窗口/动画）· `UsageProvider`（URLSession + Codable）·
  `UsageStore`（@Observable 状态 + 快照磁盘缓存，Application Support）· `SettingsWindow`（SwiftUI Form）· `KeychainStore`。
- **动画**：spring（response≈0.35 / damping≈0.8）展开收起；进度条数值 count-up；展开/刷新完成给
  `NSHapticFeedbackManager` 轻触一下（对齐 DESIGN.md 触感基线精神）。
- **签名分发**：ad-hoc codesign 手工打 .app 包；构建产物不入 git（dist/ 已在根 .gitignore）。

## 里程碑

- **M0 spike（先行，~半小时）**：Leo 提供 API Key → curl 双端点 → 锁定 `limits[]` 真实 JSON 与百分比语义 → fixture 入仓（脱敏）。
- **M1 MVP**：数据层 + 双态胶囊 + 设置窗口 + 菜单栏最小菜单 + 上述全部 AC。
- **M2 打磨（另开 it-002）**：重置倒计时进紧凑态、将耗尽/重置完成本地通知、外部显示器跟随鼠标、
  临时隐藏快捷键、model-usage/tool-usage 明细页。

## 影响范围

- 新目录 `glm-island/`（specs + Package.swift + Sources），不触碰现有 Android 应用与 CI；
  实施时补 `glm-island/README.md`、常青 spec 00/01/05/06、仓库根 README 总览加一行、CHANGELOG 一行。

## 待确认点

1. 提案整体（形态=刘海胶囊、仓库=mini-apps/glm-island、M1 范围）。
2. 你的 API Key 属于 bigmodel.cn 还是 z.ai（有自动探测不阻塞，知道更好）。
3. hover 即展开是否默认开启（也可设为「仅点击展开」）。
4. M2 的通知提醒是否需要（先不做也行）。

## 验证记录

2026-09-22 实施（M0 spike 除外，待 Leo 的 Key）：

- **构建**：`swift build` 全绿（Swift 6.1.2 / CLT，SPM，零第三方依赖）。
- **单测**：`swift test` **14/14 通过**（swift-testing；注意 CLT 无 XCTest 模块，见 ADR-001）。
  覆盖：宽松解析（ratio/usage/remaining/毫秒时间戳/顺序兜底）、envelope 业务错误、
  阈值边界（20/1/0）、时间格式化（固定时钟）、快照缓存 round-trip。
- **打包**：`tools/make-app.sh` → `dist/glm-island.app`（ad-hoc 签名）启动正常。
- **实机走查**（演示模式，M2 Air 刘海屏 1470×956）：
  - 定位正确：窗口 bounds=(811,0,352,216→228)，即刘海右缘 825-14，statusBar 层；
  - 展开态截图（`screencapture -l`）：三行明细/配色/演示徽标/页脚刷新时间与按钮全部正常，
    初版页脚被裁 → 展开高 216→228 修复；
  - 紧凑态截图：三根迷你条 + 警戒色（MCP 4% 橙）正常；
  - ⚠ 系统全屏截图不合成该层窗口（macOS 15 screencapture 怪癖），走查须用 `-l <windowID>`，
    已记入 specs/04「已知怪癖」，不影响真实显示。
- **待办**：Leo 提供 Key → 跑 `tools/spike-usage.sh` → 校准 `limits[]` 字段与百分比语义 →
  真实数据对照控制台（AC3）；开机自启在 .app 常驻形态下复验（AC7）。

### 补充验证 2026-09-22 晚：落位修复

- **问题（Leo 实机反馈「位置不对」）**：首版写死「贴刘海右缘 +6pt」，而 Leo 的菜单栏状态图标
  恰好贴刘海排列（一云梯@893、ChatGPT@927、优酷@965…），胶囊（831–949）压住了前两个图标。
- **修复**：智能避让（ADR-005）——CGWindowList 扫描菜单栏带占用，右缘/左缘空隙/刘海正下方三级兜底，
  30s 定时 + 屏幕变化重算，悬停与固定展开中不挪窝。
- **实机复验**：右侧空隙 62pt 不足 118 → 自动落到**刘海左缘**（窗口实测 522–640，
  贴 notchLeft=645 左侧 6pt），状态图标区（893 起）与自家菜单图标（851）零遮挡；
  swift test 14/14 仍绿。落位示意图按实测坐标绘制核对通过。

