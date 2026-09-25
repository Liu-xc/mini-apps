# 03 — 数据模型与存储

## 领域模型（Swift）

```swift
enum ProviderKind: String, Codable, CaseIterable { case glm, mimo }   // 内容源（ADR-008/009）

enum RowKind: String, Codable { case fiveHour, weekly, zcodeMcp, mimo, other }

struct QuotaRow: Codable, Equatable, Identifiable {
    var id: String              // kind.rawValue 或 mimo-<name> / other-<label>-<i>
    var kind: RowKind
    var label: String           // "5 小时" / "每周" / "ZCode MCP" / "套餐"
    var remainingPercent: Double?   // 剩余 0...100；解析不出为 nil（UI 显示 --）
    var resetDate: Date?
    var percentInferred: Bool   // 由「已用」字段反推的标记（spike 校准点）
    var usedTokens: Double?     // 已用绝对量（MiMo 展示 billion 用；nil=不展示）
    var limitTokens: Double?    // 额度绝对量
}

/// 每内容源的运行时状态（it-003；仅内存，快照另有磁盘缓存）
struct SourceState: Equatable {
    var snapshot: UsageSnapshot?   // 最近成功快照（失败保留旧值 = 陈旧数据继续展示）
    var lastError: String?         // 最近失败原因（成功即清除；面板头/页脚明示）
    var isFetching: Bool           // 该源在途（页脚 spinner / 状态推导）
}

struct UsageSnapshot: Codable, Equatable {
    var rows: [QuotaRow]
    var fetchedAt: Date
    var endpointHost: String
    var debugRawJSON: String?   // 截断 8KB，供诊断
}

/// 健康度级别（it-003；`IslandTheme.Level`，阈值唯一定义在 IslandTheme）
enum Level { case good, warn, bad, unknown }   // 剩余 ≥50 / 20–50 / <20 / 无数据
```

显示顺序（`displayRows`）：fiveHour → weekly → 其余（zcodeMcp 按需求过滤隐藏）。
标签（`displayLabel`）：5 小时 / 每周 / ZCode MCP / **套餐**（MiMo 主档，it-003——面板头
已标源名不重复厂商名；month 档 = "MiMo 当月"、补偿包 = "MiMo 补偿包"）。

## 内容源注册表（ProviderRegistry，it-003 ADR-009）

```swift
struct ProviderDescriptor {          // 每源一条，凭证/面板/设置/菜单全由本表驱动
    kind, title, sectionTitle,
    credentialAccount, credentialLabel, credentialNoun, credentialHint, unconfiguredText,
    makeProvider: (AppSettings) -> any UsageProviding,
    demoRows: (Date) -> [QuotaRow]
}
ProviderRegistry.all = [glm, mimo]   // 新增源 = ProviderKind case + 本表一条 +（新格式才需）解析器
```

## 外部接口契约

### GLM monitor（2026-09-23 M0 spike 实测校准）

- `GET {base}/api/monitor/usage/quota/limit`，请求头 `Authorization: <key>`（**无 Bearer 前缀**）
- base：国内 `https://open.bigmodel.cn`；国际 `https://api.z.ai`（同一 Key 两边都 200、返回一致）
- 响应（真实样例见单测 fixture）：

```json
{ "code": 200, "success": true, "msg": "...",
  "data": { "level": "pro",
    "limits": [ { "type": "CREDIT_LIMIT", "unit": 3, "number": 5,
                  "usage": 12000, "currentValue": 0, "remaining": 12000,
                  "percentage": 0, "nextResetTime": 1790114662670 }, ... ] } }
```

- **档位识别靠 `unit`**：3（配 number 5）= 5 小时窗口，6（number 1）= 每周；`type` 恒为
  CREDIT_LIMIT，无区分度
- **5 小时档是滚动窗口**：统计最近 5 小时用量，`remaining` 会随旧用量滑出窗口而**自动回升**、
  随新用量增长而下降——0% 只代表此刻窗口占满，并非数据错误
- **`percentage` = 已用百分比**，剩余 = 100 − percentage；`usage` / `currentValue` / `remaining`
  是**绝对 token 数**，不能当百分比读（percentage 缺失时可用 1 − currentValue/usage 反推）
- **显示口径**：优先精确比值 (usage−currentValue)/usage×100，**向下取整**显示
  （99.88% → 99%，与控制台一致；`percentage` 整数近似有截断误差，只做兜底）
- `nextResetTime` = 毫秒时间戳
- 该账号 limits 中**没有 MCP 档**（展示层也已按需求隐藏）
- 伴生接口（M2 再接）：`/api/monitor/usage/model-usage`、`/api/monitor/usage/tool-usage`

### 小米 MiMo TOKEN Plan（it-002 spike 实测）

- `GET https://platform.xiaomimimo.com/api/v1/tokenPlan/usage`，
  请求头 `Cookie: <登录态整段>` + `referer: …/console/plan-manage` + 浏览器 UA + `x-timezone: Asia/Shanghai`
  （**只认浏览器登录态**；`tp-` 开头的 plan key 是模型调用 key，控制台接口一律 401）
- 响应信封 `{code, message, data}`；`code != 0` 抛错，body 含 `"code":401` → Cookie 过期
- `data.usage`（主档）/ `data.monthUsage`（兜底）内 `items[]`：
  `percent` 为**小数比例**（0.0118 = 已用 1.18%，剩余 = (1−percent)×100）；
  `limit=0` 条目（未购买的补偿包）跳过；**无重置时间字段**
- 每块取主档一条：`plan_total_token` → 标签「套餐」；`used`/`limit` 为绝对 token 数

解析公共工具：`JSONLoose`（double 宽松转换 / firstString / pretty 截断），两解析器共用。

## 本地存储

| 数据 | 位置 | 内容 |
|---|---|---|
| GLM Key | `~/Library/Application Support/island/credentials.json`（chmod 0600）account `glm-key` | 不入仓库/日志 |
| MiMo Cookie | 同上，account `mimo-cookie`（**会过期**，设置里更新） | 同上 |
| 快照缓存 | `~/Library/Application Support/island/snapshot-<kind>.json`（每源一份） | UsageSnapshot（无凭证）；清除凭证即删除 |
| 偏好 | UserDefaults | `endpointMode` / `refreshMinutes` / `demoMode` / `consoleURLString` / `preferredEndpoint`（auto 探测赢家记忆，ADR-003） |

## 调试环境变量

- `GLM_ISLAND_DEMO=1`：强制演示模式（假数据，不请求接口）
- `GLM_ISLAND_EXPAND=1`：启动即固定展开（截图/走查用）
- `GLM_ISLAND_SEED_KEY=xx` / `GLM_ISLAND_SEED_MIMO_COOKIE=xx`：首启注入凭证文件（空时才写）
- `GLM_ISLAND_SHOT=<目录>`：DebugShot 自截图，~2.2s 渲染岛卡 PNG 后退出（ADR-011）
- `GLM_ISLAND_SHOT_SETTINGS=1`：连同设置窗一起截图
- `GLM_ISLAND_BLANK=1`：视作全未配置（空态走查用，不加载凭证与缓存）
