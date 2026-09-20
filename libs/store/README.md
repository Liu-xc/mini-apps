# store

mini-apps 各应用的本地存储 SDK：快照式持久化 + 媒体文件管理。

- **状态**：已实现 v0.1.0（[specs/00-architecture.md](specs/00-architecture.md)），wardrobe（ADR-012）与 eats（ADR-010）均已接入（composite build）
- **参考实现**：wardrobe it-001 数据层（原子写 / bak 恢复 / 迁移链 / SSOT 广播，15 单测验证）——抽取通用化，行为与各 app 备份格式契约不变
- **与 [libs/sync](../sync/) 的关系**：互不依赖的两半——store 是本地正本工作区，sync 是云端镜像；组合发生在各 app 的数据层（`mutate` 的 writeHook 接 sync 待推队列）

> 注：本目录为公共基础设施（非应用代码），属根 AGENTS.md 的 `libs/` 例外条款（依赖方向只能「应用 → libs」，不得反向）。
