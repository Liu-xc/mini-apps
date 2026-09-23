# 06 · agent 决策记录

> 状态：ADR-001 已于 2026-09-23 由 Leo 拍板（自研）；其余随 M0/M1 落地转正，定名/参数以实现为准。

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
- **结论**：App 直连厂商。key 仅存本地：`android/` 提供 `KeystoreApiKeyStore`（Keystore 主密钥 + AES-GCM 落盘），core 只见 `ApiKeyStore` 接口。红线三条：数据包导出永不带 key；演示模式永不挂真 key；日志只出 mask。
- **后果**：换机需重新贴 key（个人应用可接受）；用户网络环境直连厂商可能需自备代理（用户侧已知问题，错误分类给可读提示）。

## ADR-004 · OkHttp + SSE + kotlinx.serialization（2026-09-23，it-001）

- **备选否决**：Ktor client（多引擎配置与体积成本）；Retrofit（面向 REST 接口，SSE 流式非所长）。
- **结论**：OkHttp 4.12（根版本表现成条目，Coil 传递依赖已在）+ `okhttp-sse`（或自解析 event-stream，M1 定）+ kotlinx.serialization（store 同款）。
- **后果**：core 纯 JVM 可跑（桌面全量自测、未来桌面端复用）；无新增大依赖。

## ADR-005 · 事件流 Flow&lt;AgentEvent&gt; + 会话存储接口注入（2026-09-23，it-001）

- **决策**：agent loop 全过程以 `Flow<AgentEvent>` 暴露（StepStarted / TextDelta / ThinkingDelta / ToolRequested / ToolFinished / StepFinished / Completed / Failed）；会话持久化走 `SessionStore` 接口，SDK 不依赖 libs/store——app 可用 SnapshotStore 实现，也可用 SDK 默认文件实现（tmp→rename 原子写，store 同款）。
- **动机**：Compose collect 直渲染打字机；依赖方向铁律（libs 互不依赖，组合发生在 app）。
- **后果**：解析与 UI 帧率解耦（flow buffer）；`AgentEvent` 枚举演进须向后兼容（只增不改名）。
