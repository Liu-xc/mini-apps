# sync

mini-apps 各应用的云端轻同步公共底座：**中立契约 + 后端适配器**。

- **状态**：架构设计中（[specs/00-architecture.md](specs/00-architecture.md)），尚未写码
- **形态**：`contract/`（SyncSource 接口 + 后端无关引擎）+ `backends/feishu-bitable/`（首个适配器）+ `android/`（平台适配）
- **核心承诺**：对 app 而言，同步后端只是可低成本切换的数据来源（换适配器不换 app 代码）；后续 LeanCloud / WebDAV 等按同一契约接入
- **首个消费方**：wardrobe it-002；后续 eats、clips（Mac SwiftUI 端按契约文档复刻）

> 注：本目录为公共基础设施（非应用代码），根 AGENTS.md「不在应用目录外放代码」条款将在代码实际落地时补记例外。
