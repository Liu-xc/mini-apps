# sync

mini-apps 各应用的云端轻同步公共底座：**中立契约 + 后端适配器**。

- **状态**：已实现 v0.1.0（[specs/00-architecture.md](specs/00-architecture.md)），暂未接入应用（规划首个消费方：wardrobe it-002 云同步）
- **形态**：`contract/`（SyncValue 七值 / SyncSource 端口 / SyncEngine 引擎 / 待推队列 / 错误折叠）+ `bitable/`（feishu-bitable 适配器：自动建表、UI 行收编、WriteGate 串行写 + 429 退避）
- **核心承诺**：对 app 而言，同步后端只是可低成本切换的数据来源（换适配器不换 app 代码）；后续 LeanCloud / WebDAV 等按同一契约接入
- **首个消费方**：wardrobe it-002；后续 eats、clips（Mac SwiftUI 端按契约文档复刻）

> 注：本目录为公共基础设施（非应用代码），属根 AGENTS.md 的 `libs/` 例外条款（依赖方向只能「应用 → libs」，不得反向）。
