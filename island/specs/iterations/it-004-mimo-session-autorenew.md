# it-004 — MiMo 登录态自动续期（WKWebView 静默换新 serviceToken）

状态：**提案待 Leo 确认**（确认后才开始写代码，AGENTS.md 迭代流程②）
日期：2026-09-29

## 背景与动机

### 「tp- apikey 查用量」方案复核：不可行（2026-09-29 实测，终局结论）

Leo 提问「能否用 token plan apikey 查询、免每日更新」。本机 tp- key 实测复核：

- `platform.xiaomimimo.com/api/v1/tokenPlan/usage` × **6 种认证形态**
  （Bearer / 裸 Authorization / `x-api-key` / `ApiKey` / query `?key=` / Cookie `apiKey`）
  → **全部 401** 跳小米账号 SSO（loginUrl → account.xiaomi.com）
- `token-plan-cn.xiaomimimo.com` **10 个候选用量端点**（`/v1/usage`、`/v1/dashboard/billing/*`、
  `/v1/quota`、`/v1/key/usage`…）→ **全部 404**（同 host `/v1/models` 200，证明 key 本身有效）；
  按量 host 同样 404
- 官方文档 `mimo.mi.com/llms.txt` 全套 FAQ / API reference → **无任何 API key 查额度接口**

与 it-002 spike 结论一致，且今天复核后应视为**终局**：控制台用量接口只认小米账号登录态
（`api-platform_serviceToken` + `userId` + `api-platform_slh` + `api-platform_ph`），
tp- key 是模型调用鉴权，两套体系不互通。**不要再走 key 方案。**

### 真实痛点：Cookie ~24h 过期，手动更新成本高

- 现状：`mimo-cookie` 存 0600 文件（ADR-010），过期后设置页手动从浏览器复制粘贴；
  实测当前存量 Cookie 已 401（上次成功 2026-09-27），用户反馈「老是过期」「每天更新」
- 401 返回体带 `loginUrl`（account.xiaomi.com serviceLogin → 回调 platform `sts` 种新
  serviceToken）；用**旧 platform Cookie** 跟过去只会落到登录页（实测 200 无换新）——
  **没有账号级会话就续不了**
- 关键假设：serviceToken 短命（~24h），账号会话（account.xiaomi.com pass cookie）
  长命（浏览器里通常数周~数月）。若成立 → **登录一次，之后自动续期，数周/数月才重登一次**

### 方案：island 内嵌 WKWebView 承载账号会话，过期静默走 SSO 换新

1. 设置页 MiMo 分区新增「登录小米账号」入口 → 内嵌 WKWebView（持久化
   `WKWebsiteDataStore`，非 incognito）完成首次登录（扫码/账号密码）
2. `MiMoUsageProvider` 遇 401 → 通知续期协调器：用同一 data store **静默加载 loginUrl**；
   账号会话存活则 serviceLogin 自动回调 `sts` **种下新 serviceToken**（用户零操作）
3. 从 `WKWebsiteDataStore.httpCookieStore` 提取 `api-platform_*` 四件套 → 回写
   `credentials.json` account `mimo-cookie` → 重试 usage（接口侧不变，仍是 Cookie header）
4. 账号会话也失效时 → 不死循环，面板/页脚明示「MiMo 需重新登录」+ 一键唤起登录页；
   **手动粘贴 Cookie 路径完整保留为兜底**（US-8 现状不废）

## M1 spike（实施前必过，结果决定方案成立与否）

1. island WebView 完成一次真机登录，记录：登录页在 WKWebView 是否可用（UA 调整、
   验证码/滑块表现）
2. 存活期内触发 serviceToken 过期（或手动作废）→ 验证**静默 loginUrl 能种新 Cookie、
   usage 恢复 200**（AC1 的核心证据）
3. 观察记录 serviceToken 寿命与账号会话寿命（哪怕只观察到下界）

**Gate**：若账号会话寿命 ≤ 1 天，或登录页在 WebView 不可用 → 方案不成立，本迭代收窄为
「过期时一键打开浏览器登录页 + 过期预警」（在验证记录注明，提案修订后再实施）。

## 用户故事

- **US-9 MiMo 登录态自动续期（新增）**：设置页 MiMo 分区提供「登录小米账号」入口
  （WebView 内完成登录，状态行显示「已登录，自动续期中」/「需重新登录」）；
  serviceToken 过期时**静默换新、用户无感**（账号会话存活期内）；账号会话失效时
  卡片明示「需重新登录」并可一键唤起登录；清除凭证时同步清除 WebView 会话数据。
- **US-8（修订）**：手动粘贴 Cookie 保留为兜底路径；hint 文案更新为
  「推荐登录自动续期；也可粘贴 Cookie 手动维护」。
- **US-5（涉及）**：设置窗 MiMo 分区新增登录按钮与状态行，其余不动。

## 验收标准

- AC1 spike 实证：账号会话存活期内，静默走 loginUrl 换新后 usage 200（记录会话寿命观察值）。
- AC2 正常路径：serviceToken 过期 → 自动续期成功 → 卡片数据恢复，**全程无用户交互**；
  续期过程不产生额外 usage 请求风暴（节流：单次过期最多重试 1 次 + 常规刷新退避沿用）。
- AC3 账号会话失效：不崩溃、不死循环；面板头/页脚明示「MiMo 需重新登录」，设置页一键重登。
- AC4 安全：凭证/会话 Cookie 不入仓库与日志（日志只出脱敏后缀）；WebView **不注入 JS、
  不自动填密**，只经 `WKNavigationDelegate` 观察导航至 sts 回调；清除 MiMo 凭证同步清
  WebView 网站数据。
- AC5 回归：手动粘贴 Cookie（US-8）与 GLM 全链路不破；swift test 全绿 + 实机走查
  （截图/日志回填验证记录）。

## 影响范围

**代码**

- 新增：`Core/MimoSessionRenewer.swift`（401 → 静默 SSO → 提取回写 → 重试，可单测部分
  纯函数化）、`UI/MimoLoginWebView.swift`（WKWebView 封装 + 登录窗/sheet）
- 修改：`UsageProvider.swift`（MiMo 401 通知续期，保留现有文案路径）、
  `ProviderRegistry.swift`（mimo 条目 hint + 可选登录动作——按 ADR-009 保持注册表驱动，
  GLM 条目不变）、`SettingsView.swift`（登录按钮/状态行）

**specs 与文档**

- `01-user-stories.md`：新增 US-9、修订 US-8/US-5
- `02-wireframes.md`：W3 设置分区补登录入口与状态行
- `03-data-model.md`：本地存储表补 WebView 会话数据行、接口契约补 401→续期时序
- `04-architecture.md`：补续期协调器与 Provider 的通知关系（若涉分层变更）
- `06-decisions.md`：**拟新增 ADR-012**（登录态以持久化 WKWebsiteDataStore 承载，
  推翻「Cookie 只能手工粘贴」前提；接口凭证仍单源 = credentials.json）
- `README.md`、`specs/CHANGELOG.md`、仓库 `CHANGELOG.md`：实施后各补一行

**不在范围**：tp- key 查询（已证伪）；GLM 凭证链路；用量本地估算（多工具共享套餐，
估算必漂移）。

## 验证记录

（实施后回填：spike 观察值、AC1–AC5 逐条证据、截图/日志位置）
