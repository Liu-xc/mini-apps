# 00 · 总览 —— AI 象棋竞技场（xiangqi）

> 状态：it-001 起草（2026-09-23）

## 一句话

本地 web 应用：两个大模型通过各自隔离的 **pi agent session** 对弈中国象棋，人观战、选模型、记胜负，看谁比较聪明。

## 业务背景

- 仓库首个 web 应用；动机是**趣味性横向对比模型**——同一个规则游戏、同一套走法协议，模型的棋力与稳健性（合法率、纠错能力）可直接比较。
- 调用形态遵循 BYOK 红线：key 只存在于本机 pi 配置与系统钥匙串，永不进前端、日志、git。

## 核心价值流

```
选双方模型 → 开局（服务端 spawn 2 个 pi RPC 子进程，独立 session）
  → 回合循环：FEN+合法走法白名单 → 当前方模型出招（ICCS）
     → 非法则纠错重试 ≤3 → xiangqi.js 落子校验 → 终局判定
  → 前端实时观战：棋盘 / 双方思考流 / 中文记谱
  → 终局：记分板累计 W/D/L，可换先连战、导出棋谱
```

## 范围

- **in（it-001）**：对弈编排、模型任选（含自定义 OpenAI 兼容 endpoint）、观战 UI、暂停/单步、记分板、棋谱导出。
- **out**：联网对战、人机对弈打磨（留手动代走入口即可）、长将/长捉复杂裁定（it-002 候选）、移动端适配。

## 相关文档

- 用户故事：[01-user-stories.md](01-user-stories.md)
- 线框：[02-wireframes.md](02-wireframes.md)
- 数据模型：[03-data-model.md](03-data-model.md)
- 架构：[04-architecture.md](04-architecture.md)
- 设计系统：[05-design-system.md](05-design-system.md)
- 决策：[06-decisions.md](06-decisions.md)
