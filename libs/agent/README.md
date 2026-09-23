# libs/agent

各 app 内嵌 AI Agent 的公共底座：BYOK（用户自带 API Key）直连模型厂商，单一 OpenAI 兼容传输层 + provider 预设（GLM/智谱、MiMo/小米为一等公民），内置 agent loop（工具调用）、流式事件流、会话持久化、用量记账。零业务概念，纯 Kotlin 核心 + 薄 Android 层（key 加密存储）。

- 设计与调研：[specs/00-architecture.md](specs/00-architecture.md)
- 决策记录：[specs/06-decisions.md](specs/06-decisions.md)
- 当前迭代：[specs/iterations/it-001-agent-sdk-mvp.md](specs/iterations/it-001-agent-sdk-mvp.md)（**提案，待确认，未写码**）

接入方式与 libs/store 同（composite includeBuild）；agent 与其他 libs 互不依赖，组合发生在 app。
