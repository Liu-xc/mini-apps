# store

mini-apps 各应用的本地存储 SDK：快照式持久化 + 媒体文件 + 备份编解码。

- **状态**：架构设计中（[specs/00-architecture.md](specs/00-architecture.md)），尚未写码
- **参考实现**：wardrobe it-001 数据层（原子写 / bak 恢复 / 迁移链 / SSOT 广播 / zip 导出，15 单测验证）——抽取通用化，行为与各 app 备份格式契约不变
- **与 [libs/sync](../sync/) 的关系**：互不依赖的两半——store 是本地正本工作区，sync 是云端镜像；组合发生在各 app 的数据层（`mutate` 的 writeHook 接 sync 待推队列）

> 注：本目录为公共基础设施（非应用代码），根 AGENTS.md「不在应用目录外放代码」条款将在代码实际落地时补记例外。
