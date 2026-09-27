# 03-data-model — 驿站（posthouse）

## 存储位置

| 文件 | 路径 | 内容 |
|---|---|---|
| config.json | `~/Library/Application Support/Posthouse/` | 用户配置 |
| status.json | 同上 | 最近一次探测快照 |
| push-events.json | 同上 | 按日推送事件 `{ "yyyy-MM-dd": [PushEvent] }` |
| achievements.json | 同上 | 成就档案 `{ achievementId: "解锁日" }` |
| gazette-dates.json | 同上 | 已生成邸报的日期数组（同日去重） |
| posthouse.log | 同上 | 文件日志（证据链） |
| llm.key | 同上（chmod 600） | LLM 润色密钥，不入 config.json |
| gazette-yyyy-MM-dd.md | `gazetteOutputDir`（默认 `~/Documents/daily-gazette/`） | 每日战报 |

JSON 日期编码：默认 `deferredToDate`（2001 epoch 秒）——仅内部消费；日志时间戳为本地可读格式。

## config.json（PosthouseConfig）

```jsonc
{
  "scanRoots": ["…/mini-apps", "…/mini-games"],  // 扫描根：根自身 + 一层内子目录
  "extraRepos": [],                               // 手动补充的单仓
  "pollIntervalSeconds": 60,                      // 轮询周期（下限 10s）
  "remoteProbeTimeout": 8,                        // ls-remote 超时（秒）
  "pushArgs": ["-c", "http.version=HTTP/1.1", "-c", "http.postBuffer=524288000"],
  "autoPushEnabled": false,                       // 总开关，默认关
  "autoPushWhitelist": { "/path/repo": true },    // 仓库白名单，默认全关
  "gazetteHour": 22, "gazetteMinute": 30,
  "gazetteOutputDir": "/Users/leo/Documents/daily-gazette",
  "notificationsEnabled": true,
  "gazetteLLMPolish": false,                        // M3 可选润色，默认关
  "gazetteLLMBaseUrl": "https://open.bigmodel.cn/api/paas/v4",
  "gazetteLLMModel": "glm-4-flash"
}
```

`isWhitelisted(path) = autoPushEnabled && whitelist[path] == true`（双闸门）。

## status.json（WatchEngine.Snapshot）

```jsonc
{
  "probedAt": <Date epoch>,
  "repos": [{
    "path": "/abs/repo", "name": "mini-apps", "branch": "main",
    "ahead": 0, "behind": 0, "dirtyCount": 2,
    "lastCommitAt": <Date?>,
    "remoteConfigured": true, "remoteReachable": true,  // null=未探测
    "lastError": null, "probedAt": <Date>
  }]
}
```

`beacon`（三态）为计算属性不落盘：
`unreachable` = remoteConfigured && remoteReachable==false；否则 `backlog` = ahead>0 || behind>0；否则 `ok`。

## push-events.json（PushEvent）

```jsonc
{ "repoName": "testrepo", "at": <Date>, "commitsPushed": 2,
  "branch": "main", "automatic": true, "success": true }
```

## 成就枚举（achievementId）

| id | 名称 | 触发（纯规则） |
|---|---|---|
| first-gazette | 首日点亮 🏮 | 档案为空时首封邸报 |
| triple-fix | 连修三坑 🛠️ | 当日全仓 fix ≥ 3 |
| night-owl | 深夜修罗 🦉 | 存在 23:00–05:00 提交 |
| army | 大部队 ⚔️ | 当日提交 ≥ 10 |
| clean-sweep | 清仓大吉 🧹 | 当日有提交且收官积压 0（至少一仓配了远端） |
| mass-push | 千军一发 🚀 | 单次成功推送 ≥ 20 提交 |

## DailyGazette（内存结构 → 渲染 Markdown）

`date / generatedAt / repoSummaries[] / totalCommits / pushEvents[] / backlogAtClose / unlocked[] / activeStreak`
——渲染章节：今日概览、分仓战况（表）、烽火传递（推送列表）、今日成就。
