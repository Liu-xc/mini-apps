# 06 · agent 决策记录

> 状态：ADR-001 于 2026-09-23 由 Leo 拍板（自研）；ADR-002~005 已随 M1 落地生效（定名以实现为准）；M0 spike 顺延（见 [00-architecture.md](00-architecture.md) §0）。

## ADR-001 · 自研薄核，不引入 Koog / LangChain4j（2026-09-23，it-001）

- **备选否决**：
  - **Koog 1.2.0**（JetBrains，Apache 2.0）：唯一 Kotlin 正统 agent 框架，功能最全（工具/MCP/流式/持久化/RAG/图工作流），但功能面远超个人 app 需要、KMP 依赖树重；内置 provider 列表无 GLM/MiMo，自定义 OpenAI 兼容端点非其主线；README「Supported targets」列 JVM/JS/WasmJS/iOS，Android 目标支持表述含糊。→ 转用为参考实现，借鉴其事件模型与工具 DSL 形态。
  - **LangChain4j**：JVM 生态最热，但服务端/Spring 生态取向，Android 非目标。
  - **LangGraph / OpenAI Agents SDK / Google ADK / CrewAI / Mastra / Pydantic AI**：Python/TS 为主，移动端无人覆盖。
- **结论**：自研 2~3k 行薄核。OpenAI 兼容协议本身极薄（一个 endpoint + SSE），真正的复杂度在厂商 quirks，必须完全可控；风险用 M0 spike + MockWebServer 契约测试对冲。
- **演进**：未来需要 MCP / RAG / 多 agent 编排时重评 Koog（修订本 ADR）；`ChatModel`/`AgentEvent` 接口按可平移设计。

## ADR-002 · 单一 OpenAI 兼容传输 + ProviderPreset 数据化（2026-09-23，it-001）

- **决策**：唯一传输实现按 OpenAI Chat Completions 协议编写；厂商差异（baseUrl/模型表/路径风格/reasoning 字段/json 模式）全部落 `ProviderPreset` 数据 + `Quirks` 位标志。GLM（`open.bigmodel.cn/api/paas/v4`，路径无 /v1）与 MiMo（`/v1/chat/completions` 风格）官方口径均已兼容此协议（M0 实调复核后回填）。
- **动机**：新厂商 = 加一条 preset 数据，零代码分支；quirks 显式可见、可测。
- **后果**：厂商若实质偏离 OpenAI 协议（罕见）才需要第二个传输实现；preset 属数据，可随厂商模型线演进热修。

## ADR-003 · BYOK 直连无后端 + 密钥红线（2026-09-23，it-001）

- **备选否决**：自建代理后端（统一计费/换 key）——个人应用无运维预算，且多一层用户数据出域顾虑。
- **结论**：App 直连厂商。key 仅存本地：core 只见 `ApiKeyStore` 接口；SDK 单模块落地未含 android/（cutout ADR-002 同例），Keystore 加密实现（Keystore 主密钥 + AES-GCM 落盘）由消费 app 在 M3 提供并注入。红线三条：数据包导出永不带 key；演示模式永不挂真 key；日志只出 mask（`maskApiKey`）。
- **后果**：换机需重新贴 key（个人应用可接受）；用户网络环境直连厂商可能需自备代理（用户侧已知问题，错误分类给可读提示）。

## ADR-004 · OkHttp + SSE + kotlinx.serialization（2026-09-23，it-001）

- **备选否决**：Ktor client（多引擎配置与体积成本）；Retrofit（面向 REST 接口，SSE 流式非所长）。
- **结论**：OkHttp 4.12（根版本表现成条目，Coil 传递依赖已在）+ **SSE 自解析**（`SseDecoder`，纯 Kotlin 可单测、quirks 可控）——M1 拍板未引 okhttp-sse；协议契约用 mockwebserver 测试（仅测试依赖）。
- **后果**：core 纯 JVM 可跑（桌面全量自测、未来桌面端复用）；无新增大依赖。

## ADR-005 · 事件流 Flow&lt;AgentEvent&gt; + 会话存储接口注入（2026-09-23，it-001）

- **决策**：agent loop 全过程以 `Flow<AgentEvent>` 暴露（StepStarted / TextDelta / ThinkingDelta / ToolRequested / ToolFinished / StepFinished / Completed / Failed）；会话持久化走 `SessionStore` 接口，SDK 不依赖 libs/store——app 可用 SnapshotStore 实现，也可用 SDK 默认文件实现（tmp→rename 原子写，store 同款）。
- **动机**：Compose collect 直渲染打字机；依赖方向铁律（libs 互不依赖，组合发生在 app）。
- **后果**：解析与 UI 帧率解耦（flow buffer）；`AgentEvent` 枚举演进须向后兼容（只增不改名）。

## ADR-006 · ToolResult/Message 可选 payload 通道（2026-09-30，it-075/wardrobe）

- **决策**：`ToolResult.Ok(text, payload: JsonElement? = null)`；`Message.payload`（role=tool 携带，落盘随会话）。payload **不回喂模型、不上 wire**——`toWire()` 不映射该字段，厂商请求体零变化（测试断言锁定）；仅随 `AgentEvent.ToolFinished` 透传 UI + FileSessionStore 落盘供历史回放。缺字段反序列化为 null（createdAt 同款向后兼容）。
- **动机**：app 需要工具结果的结构化渲染（如命中的实体 id 列表出卡片），纯文本通道（it-054 式文本协议解析）不可泛化。
- **后果**：事件签名不变（ADR-005 只增不改名合规）；不关心 payload 的工具与 app 零改动；落盘 JSON 里 null 字段被默认 `Json`（encodeDefaults=false）省略。


## ADR-007 · 统一模型目录 + ImageModel 生图双能力轨（2026-09-30，it-077/wardrobe）
- **背景**：消费方（wardrobe it-077）需要「图+文→图」生成能力且不锁死厂商；provider 抽象须同时覆盖聊天与生图两轨（Leo 拍板②）；国内生图 API 大量异步任务型（百炼 aitryon/wan），LiteLLM 至今未归一（issue #28763）。
- **决策**：`ModelCatalog` 统一注册表——`ProviderSpec`（id/displayName/chatPreset/imageBaseUrl/models）+ `ModelSpec`（capabilities={CHAT,IMAGE_GEN} 分轨声明；supportsToolCall 门槛；inputImages/outputs/supportsBase64/params/costPerImage）。聊天轨零改动（OkHttpChatModel 不动，新厂商=目录数据）；生图新轨 `ImageModel`，协议显式三枚举（OPENAI_IMAGES_SYNC / DASHSCOPE_SYNC / DASHSCOPE_ASYNC_TASK），同步/异步差异在适配器内消化为统一事件流；输出字节在适配器内下载完毕（App 不接触 24h 失效 URL）。Key 按厂商 id 存取（一把 Key 双轨共用，消费方 ApiKeyStore 现机制）。模型专属参数（watermark/negative_prompt/batch_size…）按 `ImageParamSpec` 声明、透传进请求体，不为厂商开关建抽象字段。
- **理由**：结构抄 OpenCode（协议驱动 preset + 静态注册表）、能力字段抄 models.dev/OpenRouter（capability 与输入输出 modality 分开）、异步任务型显式建模是 LiteLLM 的空白；参数声明式透传（OpenRouter supported_parameters 思想）让新模型参数零代码暴露。
- **后果**：模型 id 漂移靠目录数据随版本更新（消费方 UI 保留自定义模型 id 兜底）；目录价格字段为展示快照不构成计费依据；上传图格式归一由消费方负责（wardrobe 统一 WebP→JPEG base64）。
