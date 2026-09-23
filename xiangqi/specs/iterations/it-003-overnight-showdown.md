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

（实施后回填：两局结果、报告路径、推送状态。）
