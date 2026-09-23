# it-003 · 去时限 + 过夜双局对抗 + 结果报告

> 状态：提案已确认（2026-09-24 Leo 指令原文：「去掉时间限制，提交并推送，不要提交我的任何 TOKEN。然后让 glm5.3flash 和 mimo 2.6flash、glm5.3 & mimo 2.6 pro 分别下两局，然后生成结果报告。我明天来验收」）→ 实施中

## 背景与动机

it-001/002 已跑通真模型对弈与胜率评估，但默认 120s 静默超时在前两局误杀过长思考（后修为静默超时仍可能误杀彻底慢流）。Leo 指令：**完全去掉回合时间限制**，并连跑两局固定对阵（过夜），明早验收带报告。

## 涉及范围

- **US-03 修订**：回合时限默认关闭（`TURN_TIMEOUT_MS=0` 无限等待，直到 `agent_settled`）；需要时限时显式设 env（smoke=30s）。超时判负路径保留但默认不可达。
- **US-07（新增）过夜双局与报告**：
  - AC-1：顺序自动跑两局（评估开启、不换先）：A `zai-coding-cn/glm-5.3-flash` vs `xiaomi-token-plan-cn/mimo-v2.6-flash`；B `zai-coding-cn/glm-5.3` vs `xiaomi-token-plan-cn/mimo-v2.6-pro`。
  - AC-2：每局终局后保存快照 JSON + 导出棋谱 txt；全部完成后生成 `reports/2026-09-24-xiangqi-showdown/report.html`（结果概览、每局记分/手数/时长/重试/评估均值与最大转折、胜率曲线 SVG、完整着法表含逐手评估、终局 ASCII）。
  - AC-3：进程崩溃防护——runner 断线重连轮询；单局硬上限 7 小时（防失控），超时记为未完成并继续/收尾。
  - AC-4：推送前对全部提交历史做 token 特征串扫描（三把 key 前缀），零命中才推。

## 影响范围

- `server.mjs`（默认时限 0）、specs 01（US-03/US-07）、03（limits）、04（说明）、06（ADR-004 时限条款）
- 新增 `tools/overnight.mjs`（过夜 runner）、`tools/report.mjs`（报告生成）
- `reports/2026-09-24-xiangqi-showdown/`（产物，含两局 JSON/棋谱/报告）

## 验证记录

> 2026-09-25 晨回填（runner 于 06:21 全部完成，`DONE` 标记落盘）。

**去时限**：`TURN_TIMEOUT_MS` 默认 0 生效——A/B 两局各跑 105/109 分钟无人工干预、零超时判负；mimo-v2.6-pro 单步长考 15+ 分钟（SSE 实测 10s/144 增量流式）不被掐断。**过程坑**：首跑两局 0 秒假判负——`timeoutMs=0` 时误用 `Promise.resolve()` 参与 `Promise.race` 立即放行，修复为直接 `await run`（`67e7213`），修复后局 A 即正常推进。

**两局结果（US-07 AC-1/AC-2 满足）**：

| 局 | 对阵（均 glm 红 / mimo 黑） | 结果 | 手数 | 用时 | 评估覆盖 | 重试 |
|---|---|---|---|---|---|---|
| A | glm-5.3-flash vs mimo-v2.6-flash | **黑方将死胜**（mimo-v2.6-flash） | 76 | 104m47s | 69/76（终局前黑 64% 预判吻合） | 2 |
| B | glm-5.3 vs mimo-v2.6-pro | **红方将死胜**（glm-5.3） | 53 | 108m47s | 52/53（终局前红 49%） | 2 |

**分档结论：Flash 档 mimo 胜、Pro 档 GLM 胜，四模型 1:1。**

**产物（AC-2）**：`reports/2026-09-24-xiangqi-showdown/` 含 `report.html`（21KB、2 section、胜率曲线×2、逐手评估表）+ `game-A/B.json` + `game-A/B.txt` + `DONE`。报告工具用合成双局干跑先行验证（曲线/统计/记分板分支全过）。

**兜底（AC-3）**：服务重启预案未触发；单局 7h 上限未触发；runner 全程无 FATAL。

**推送**：70+ 提交待 `workflow` scope 设备码授权（gh 轮询多次网络 EOF/过期，`expired_token` 实证 Leo 曾输码未果），07:30 验收 cron 与长窗口接力继续；token 三前缀全历史扫描历轮零命中（AC-4 持续满足）。
