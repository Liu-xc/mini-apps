# 03 — 数据模型与存储

## 领域模型（Swift）

```swift
enum RowKind: String, Codable { case fiveHour, weekly, zcodeMcp, other }

struct QuotaRow: Codable, Equatable, Identifiable {
    var id: String              // kind.rawValue 或 other-<label>
    var kind: RowKind
    var label: String           // "5 小时" / "每周" / "ZCode MCP"
    var remainingPercent: Double?   // 剩余 0...100；解析不出为 nil（UI 显示 --）
    var resetDate: Date?
    var percentInferred: Bool   // 由「已用」字段反推的标记（spike 校准点）
}

enum ThresholdState { case healthy, warn, exhausted }   // 剩余 >20 / ≤20 / =0

struct UsageSnapshot: Codable, Equatable {
    var rows: [QuotaRow]
    var fetchedAt: Date
    var endpointHost: String
    var debugRawJSON: String?   // 截断 8KB，供诊断
}
```

显示顺序固定：fiveHour → weekly → zcodeMcp → other（`displayRows`）。

## 外部接口契约（2026-09-23 M0 spike 实测校准）

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
- **`percentage` = 已用百分比**，剩余 = 100 − percentage；`usage` / `currentValue` / `remaining`
  是**绝对 token 数**，不能当百分比读（percentage 缺失时可用 1 − currentValue/usage 反推）
- `nextResetTime` = 毫秒时间戳
- 该账号 limits 中**没有 MCP 档**（展示层也已按需求隐藏）
- 伴生接口（M2 再接）：`/api/monitor/usage/model-usage`、`/api/monitor/usage/tool-usage`

## 本地存储

| 数据 | 位置 | 内容 |
|---|---|---|
| API Key | Keychain：service `com.spartapps.glm-island` / account `api-key` | 仅 Key，绝不落文件 |
| 快照缓存 | `~/Library/Application Support/glm-island/snapshot.json` | UsageSnapshot（无 Key） |
| 偏好 | UserDefaults | `endpointMode` / `refreshMinutes` / `demoMode` / `consoleURLString` / `preferredEndpoint` |

## 调试环境变量

- `GLM_ISLAND_DEMO=1`：强制演示模式（假数据，不请求接口）
- `GLM_ISLAND_EXPAND=1`：启动即固定展开（截图/走查用）
