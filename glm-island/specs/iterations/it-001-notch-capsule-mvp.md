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

### 补充验证 2026-09-22 晚：落位修复（两轮）

- **第一轮（反馈「位置不对」）**：首版写死「贴刘海右缘 +6pt」，压住 Leo 贴刘海排列的状态图标
  （一云梯@893、ChatGPT@927…）。先试了 CGWindowList 智能避让（右→左→下方三级兜底），
  实机落在刘海左缘 522–640，图标零遮挡——但胶囊与刘海分离，Leo 二轮反馈「还是差很多，hover 一直闪」。
- **第二轮定稿（ADR-005）**：
  - **位置 = 刘海正中锚点**：刘海挖槽（645–825）天然无窗口冲突，紧凑态 676–794 融进刘海黑区、
    展开态 559.5–911.5 贴顶向下生长；菜单栏排布彻底解耦，30s 重算定时器删除。
  - **hover 闪烁根因**：窗口变形动画中窗口服务器补发成对 enter/exit → 展开/收起互相触发振荡。
    修复 = 假离开守卫（exit 时光标仍在面板 frame 内则忽略）+ enter 60ms / exit 180ms 可取消防抖。
- **实机复验**：NSLog 链路追踪证实假离开被吞、面板稳定展开 5.5s；CGEvent 模拟悬停 +
  CGWindowList 120ms 采样：一次展开（618→565→559 为弹簧收敛轨迹）→ 悬停期零变化 →
  真离开 0.2s 后一次收起至 676。swift test 14/14 仍绿。

### 补充验证 2026-09-22 深夜：移除触感

- Leo 反馈「不要震动」（更正自「不要动画」——动画保留）：hover 展开的
  NSHapticFeedbackManager(.alignment) 触感移除，其余不变。构建/14 测试全绿。

### 补充验证 2026-09-23：MCP 隐藏 + 紧凑态两行全信息

- Leo 反馈：不展示 MCP 余额；要展示重置时间（紧凑态也要能看到）。
- 实现：displayRows 过滤 zcodeMcp（解析/数据保留，接口字段仍可用于 M2）；紧凑态升级
  180×36 两行全信息「标签 + 迷你条 + 百分比 + 重置时间」，正好覆住刘海宽度（645–825）；
  展开态两档高度收紧 228→178（第三档 other 出现时 222）。
- 实机截图复验：紧凑态两行（5 小时 33%·02:12 蓝 / 每周 18%·9月29日 警戒橙）、无 MCP；
  展开态两行 + 页脚完整。displayRows 单测更新（MCP 被过滤 + 顺序保持），14/14 绿。

### 补充验证 2026-09-23 凌晨：刘海遮挡修复

- Leo 反馈「顶部不要圆角、要预留边距，现在被刘海挡住了」：v2 把胶囊放在刘海水平正中且贴顶，
  而刘海挖槽（645–825 × 0–32）是物理遮挡区——内容整个被刘海吃掉，仅底部 4pt 可见。
- 修复：**刘海下沿锚点**——两态顶边贴 `safeAreaInsets.top`，顶部两角直角、只圆下方
  （UnevenRoundedRectangle）；紧凑 180×36 成为"刘海长出的一截"，内容全可见。
- 实机复验：窗口实测 (645, 32, 180, 36)；真实桌面合成截图确认两行内容完整可见、
  与刘海底沿无缝衔接；构建 + 14 测试全绿。

### 补充验证 2026-09-23：默认隐藏 + 刘海触发（交互形态定稿）

- Leo 反馈：默认不该展示任何东西；hover 刘海才动画展开；且 v3 胶囊顶边与刘海之间有「缺角」缝隙。
- 实现（ADR-006）：**隐藏态窗口 = 刘海挖槽矩形本身**（180×32 全透明，不可见但收 hover）；
  展开卡片从刘海中心向下生长、**顶边贴屏幕顶沿**（y=0、顶部两角直角），与刘海/顶边零缝隙；
  动画保留，紧凑态两行视图删除（信息全部在展开卡片内）。
- CGEvent 实测闭环：默认 (645,0,180×32) 隐形 → hover 一次动画展开 (559,0,352×178) →
  悬停零振荡 → 移开一次收起归隐。构建 + 14 测试全绿。

### 补充验证 2026-09-23：动画方向 + 刘海避让 + UI 减法

- Leo 三点反馈：动画应从刘海**向下延伸**而非斜向生长；卡片顶部文字要避开刘海区；
  整体 UI 太花。
- 实现（ADR-007）：生长动画改 SwiftUI reveal（窗口瞬移就位 + 可见高度 32→全高、顶边钉死、
  clipped；先渲染起始帧再延迟 50ms 触发 spring，解决「初插不补间」）；内容顶边 = safeTop+6；
  UI 减法（删徽章/高光线/冗余提示，颜色只留进度条，行收敛两行）。
- 构建 + 14 测试全绿；终态截图确认简化后布局与刘海避让正确。

### 补充验证 2026-09-23：收起动画对称化

- Leo 反馈：收起时宽度也应一起缩，而不是先变矮再把黑条藏进刘海。
- 实现：reveal 遮罩改为宽高同步动画（刘海尺寸↔全尺寸），收起 = 对称吸回刘海，
  中间帧实测为对称缩小的小黑卡；窗口在动画结束后才瞬移归隐。14 测试仍绿。

### 补充验证 2026-09-23：去标题 + mock 数据说明

- Leo 反馈：去掉「剩余额度」标题；并问数据是否 mock——是（未配 Key，走查一直用
  GLM_ISLAND_DEMO=1 演示模式，页脚有「演示 ·」标记）。
- 实现：卡片删标题栏，只留明细行 + 页脚，高度公式同步收紧（两档 134）。
- 接真实数据的路径：设置窗口粘贴 API Key（存钥匙串）→ 自动刷新；或
  `GLM_API_KEY=xx ./tools/spike-usage.sh` 先看原始响应。14 测试仍绿。

### 补充验证 2026-09-23：M0 spike 完成，真实数据上线

- Leo 提供 API Key → `open.bigmodel.cn` 与 `api.z.ai` 双端点实测均 200、返回一致。
- 真实字段语义：`percentage`=已用%（剩余=100−x）、`remaining`/`currentValue`/`usage`=绝对 token 数、
  `unit` 3×5=5 小时 / 6×1=每周、`nextResetTime`=毫秒时间戳、`type` 恒为 CREDIT_LIMIT、
  `data.level`=套餐档位。
- 解析器收紧（保留宽松兜底）+ 真实响应 fixture 单测，**15/15 绿**。
- 修复 id 撞车（type 无区分度 → 两行 id 相同 → ForEach 重复渲染同一行）。
- 行内顺序调整：重置时间在前（灰小字）、百分比在后（粗体白）。
- Key 入钥匙串（App 自写 ACL，`GLM_ISLAND_SEED_KEY` 调试钩子注入，已提醒 Leo 可轮换）；
  真实模式运行实测：5 小时 100% · 06:04 / 每周 33% · 9月28日，与接口一致。**AC3 达成**。

