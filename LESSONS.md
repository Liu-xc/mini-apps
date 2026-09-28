# LESSONS.md — 经验库（Agent 自进化闭环）

跨应用、跨任务的**行为规则**沉淀处：记「下次还会用到的一行规则」，不记流水账。
每次迭代收尾、每次修完有普适性的 bug，先自问一句——**有没有下次还会踩的坑、还能省时间的做法？** 有则写进来。

## 怎么用（人 + AI 协作者通用）

- **读**：开工前按 [AGENTS.md](AGENTS.md) 上下文恢复清单必读本文件，先看规则再动手。
- **写**：迭代收尾时写回（AGENTS.md 流程第⑤步），**≤3 条**；每条必须是可执行的一行规则，附出处链接与年月。只收跨任务/跨应用可复用的教训，应用内部细节留在迭代文件里。
- **晋级（自进化核心）**：同一教训第 3 次出现、或已成全局铁律 → 升级到更高形态——流程类进 [AGENTS.md](AGENTS.md)，视觉类进 [DESIGN.md](DESIGN.md)，应用类进该应用常青 spec；能被脚本/CI 防住的写成自动检查（最高形态）。
- **淘汰**：条目失效（技术栈变化、机制已兜底）直接删除，不留墓碑。
- **容量**：每节 ≤ 15 条；满额先晋级或删除，再收新条目。

## 一条经验的一生

```
事件（迭代/报告里踩的坑） → 一行规则（本文件） → 固化（AGENTS.md / DESIGN.md / 常青 spec） → 自动化（脚本 / CI）
```

## 条目

### 流程与协作

- **超时判罚用「静默超时」（无输出时长），别用总时长**——LLM/agent 合法长思考可达数十分钟，总时长硬中断会把「想得久」误判成「挂死」。· [xiangqi it-001](xiangqi/specs/iterations/it-001-arena-mvp.md) · 2026-09
- **spec/文档不钉死易漂移的数字（测试用例数等）**——以 CI/测试套件实际结果为准，写死必漂移。· [wardrobe it-020](wardrobe/specs/iterations/it-020-arch-review-stabilize.md) · 2026-09
- **「本地全绿」≠「CI 绿」**——CI 配置入库后必须看首跑结果，未实跑的流水线视同未验证。· [wardrobe it-020](wardrobe/specs/iterations/it-020-arch-review-stabilize.md) · 2026-09

### 架构与数据

- **解析外部/持久化数据：未知字段一律忽略不崩**——向前兼容是硬要求，收紧解析前先确认不破坏旧数据。· [libs/agent 00-architecture](libs/agent/specs/00-architecture.md) · 2026-09
- **别拿状态「翻转」当提交信号，改用「到达/精确值 + 门禁」**——快速重复动作下 edge 信号会漏触发；配幂等门防重放。· [wardrobe it-048](wardrobe/specs/iterations/it-048-hotfix-deck-clip-continuity.md) · 2026-09

### Android / Compose

- **Lazy 容器内的子项不吃常规尺寸约束**——列表空态居中用 `fillParentMaxSize`，普通 `fillMaxSize`/gravity 不生效。· [wardrobe it-043](wardrobe/specs/iterations/it-043-newui-p1-fixes.md) · 2026-09
- **自研/手动驱动的动画不在官方动画跟踪内**——`isAnimationRunning` 这类官方标志会恒 false，判断「在动」须自带标志（try-finally 维护）。· [wardrobe it-048](wardrobe/specs/iterations/it-048-hotfix-deck-clip-continuity.md) · 2026-09

### Agent 协作与工具

- **浏览器/IAB 截图可能拿到旧帧**——关键状态截图连拍两次，取第二张。· [travel-rpg scenes 报告](reports/2026-09-24-travel-rpg-scenes/README.md) · 2026-09
