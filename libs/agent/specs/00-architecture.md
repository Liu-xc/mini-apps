# 00 · agent 架构设计（BYOK 直连 + Agent Loop SDK）

- **状态**：**已确认，自研路线拍板**（2026-09-23 Leo 定）；**M1 传输层已落地**（2026-09-23，32 个 JVM 单测全绿，见 §0）。M0 spike 顺延：钥匙串授权被拒、MiMo key 未注册，preset 中「待校准」字段不阻塞 M2，spike 脚本（tools/）就绪随时手动可跑。选型调研见 §1，理由见 [06-decisions.md](06-decisions.md)。

## 0. 实现状态（M1 传输层，2026-09-23）

**单模块落地**（未拆 core/android，与 store/cutout 同例）：全部代码在 `src/main`，纯 Kotlin JVM；`ApiKeyStore` 的平台加密实现（Keystore/EncryptedSharedPreferences）归消费 app 层在 M3 注入（cutout ADR-002 同款取舍，ADR-003 已注记）。**SSE 自解析**拍板：未引 okhttp-sse，`SseDecoder` + `StreamAssembler` 纯 Kotlin 可单测（ADR-004 注记）；新增依赖仅测试期 mockwebserver。

已交付：Message/Part（含 image_url 视觉位）/ToolCall 模型、ChatModel 接口（complete + `Flow<ChatEvent>` 流式）、AgentError 六分类与 HTTP 映射、ProviderPreset/Quirks/Providers、SseDecoder/StreamAssembler（tool_calls 分片聚合、reasoning_content→ThinkingDelta、未知载荷容错）、OkHttpChatModel、FakeChatModel（testing 包——「CI 永不打真 API」的关键件，亦可作 app 演示模式离线模型）。

待 M0 校准回填：GLM/MiMo 确切模型可用性与名称、MiMo baseUrl（现留空占位）、tool_call_id 回喂稳定性、response_format 支持度、免费档是否存在。
- **一句话**：各 app 内嵌 AI Agent 的公共底座——用户自带 API Key（BYOK）直连模型厂商，单一 OpenAI 兼容传输层打天下，内置 agent loop（工具调用）、流式事件流、会话持久化、用量记账；零业务概念。

## 1. 调研结论：为什么自研薄核（2026-09 盘点）

开源 agent 框架很热，但**没有一个是为「Android 个人应用 + BYOK 多厂商」设计的轻量件**：

| 框架 | 语言/平台 | 一句话 | 对本仓库的适用性 |
|---|---|---|---|
| **Koog**（JetBrains） | Kotlin 多平台 | 唯一 Kotlin 正统 agent 框架：AIAgent/工具/MCP/流式/持久化/RAG/图工作流俱全，Apache 2.0，v1.2.0 | 功能面远超需要、依赖树重；内置 provider 列表无 GLM/MiMo，自定义 OpenAI 兼容端点非其主线；README 支持目标列 JVM/JS/WasmJS/iOS，Android 目标表述含糊。**当参考实现借鉴其事件模型与工具 DSL** |
| LangChain4j | JVM | JVM 生态最流行的 LLM 框架 | 服务端/Spring 生态取向，Android 非目标 |
| LangGraph | Python/JS | 2026 年各家榜单的事实标准编排 | 移动端不可用 |
| OpenAI Agents SDK / Google ADK | Python（ADK 另有 Java 版） | 官方出品、榜单头部 | 面向服务端，无 BYOK 多厂商诉求 |
| CrewAI / Mastra / Pydantic AI / MS Agent Framework | Python/TS | 角色编排 / TS 全栈 | 与 Android 无缘 |

**结论**：传输层本身就薄——OpenAI 兼容协议 = 一个 endpoint + SSE，自研 2~3k 行完全可控，且厂商 quirks 必须自己说了算。重能力（MCP/RAG/多 agent 编排）列入非目标，未来若需要重评 Koog（ADR-001）。

## 2. 定位与目标

| 目标 | 含义 |
|---|---|
| BYOK 直连 | 用户自备 key，App 直连厂商，**无后端无中转**；key 本地加密、永不导出 |
| 零业务概念 | 只认识「消息/工具/用量」，不知道衣物/餐厅/剪贴板 |
| 单传输层多厂商 | OpenAI 兼容协议一层；GLM（智谱）与 MiMo（小米）为一等公民 preset；DeepSeek/Kimi/OpenRouter/自定义 baseURL 免费获得 |
| 流式优先 | SSE → `Flow<AgentEvent>`，UI collect 即打字机；工具调用全程事件可见 |
| 纯 Kotlin 核心 | core 在桌面 JVM 全量可测（cutout ADR-002 同款取舍）；Android 侧只补 key 加密存储 |
| CI 永不打真 API | FakeChatModel 脚本回放测 loop；MockWebServer 测协议契约 |

**非目标**（MVP）：多 agent 编排/图工作流、RAG/向量检索、MCP 客户端、本地小模型推理、语音（ASR/TTS——MiMo 有，后置）、计费代理、任何 UI 组件（对话页归各 app，走 DESIGN.md 基准）。

## 3. 模块划分

```
libs/agent/
├─ src/      纯 Kotlin JVM 单模块（M1 落地形态，未拆 core/android，见 §0）：传输 + 事件流 + FakeChatModel
├─ tools/    M0 spike 脚本（真实实调校准契约）
└─ specs/    本设计 + 决策记录 + 迭代
```

依赖铁律：core 不 import Android/app 类型（单模块形态下同样成立）；**agent 与 store/sync/cutout/carddeck 互不依赖**——会话持久化经 `SessionStore` 接口注入，app 侧可用 SnapshotStore 实现，也可用 SDK 默认文件实现。

## 4. 核心 API（草图，定名以实现为准）

### 4.1 Provider 预设——数据，不是代码分支

```kotlin
data class ProviderPreset(
    val id: String,              // "glm" | "mimo" | "deepseek" | ...
    val displayName: String,     // 设置页展示名
    val baseUrl: String,         // glm: https://open.bigmodel.cn/api/paas/v4
    val models: List<ModelInfo>, // 名称 / 定位档 / 上下文窗口 / 是否视觉
    val quirks: Quirks = Quirks.NONE,
)
data class Quirks(
    val pathStyle: PathStyle,        // 路径是否含 /v1（GLM 不含）
    val reasoningField: Boolean,     // MiMo/GLM 推理内容走 reasoning_content → ThinkingDelta
    val toolCallIdStable: Boolean,   // 回喂时 tool_call_id 是否可原样透传
    val jsonMode: JsonModeSupport,   // response_format 支持度（不支持则 prompt 兜底+解析）
)
```

### 4.2 传输层（唯一的 HTTP 代码）

```kotlin
interface ChatModel {
    val preset: ProviderPreset
    fun stream(request: ChatRequest): Flow<ChatEvent>   // TextDelta | ThinkingDelta | ToolCallDelta | Completed(usage) | Failed(e)
    suspend fun complete(request: ChatRequest): ChatResponse  // 非流式（自检/兜底）
}
```

### 4.3 Agent loop

```kotlin
class AgentRunner(
    private val model: ChatModel,
    private val config: AgentConfig,   // systemPrompt / maxSteps=8 / temperature
    private val tools: ToolRegistry,
) {
    fun run(history: List<Message>): Flow<AgentEvent>
}

sealed interface AgentEvent {
    data object StepStarted
    data class TextDelta(val text: String)
    data class ThinkingDelta(val text: String)
    data class ToolRequested(val call: ToolCall)
    data class ToolFinished(val call: ToolCall, val result: ToolResult)
    data object StepFinished
    data class Completed(val message: Message, val usage: Usage)
    data class Failed(val error: AgentError)
}
```

### 4.4 工具 DSL（app 侧注册业务工具）

```kotlin
val tools = toolRegistry {
    tool(
        name = "search_wardrobe",
        description = "按条件搜索衣橱单品",
        parameters = jsonSchema {  // 小 DSL 生成 JSON Schema
            string("category", "上装/下装/鞋/配饰")
            string("occasion", "适用场合")
        },
    ) { args -> ToolResult.ok(search(args)) }
}
```

### 4.5 会话 / 上下文 / 用量 / key

```kotlin
interface SessionStore { suspend fun append(id: SessionId, m: Message); suspend fun replace(...); suspend fun messages(id: SessionId): List<Message> }
interface ContextPolicy { fun trim(history: List<Message>, system: Message): List<Message> }  // system 常驻 + 近 N 轮 + token 估算裁剪
interface UsageLedger { suspend fun record(preset: String, model: String, usage: Usage) }
interface ApiKeyStore { suspend fun get(preset: String): String?; suspend fun put(...); suspend fun delete(...); fun mask(key: String): String }
```

## 5. 一等公民预设：GLM 与 MiMo（M0 校准项加粗）

| 预设 | baseUrl（**待 M0 钉死**） | 模型档位（以控制台为准） | 已知 quirks |
|---|---|---|---|
| **glm** | `https://open.bigmodel.cn/api/paas/v4`（路径**无 /v1**） | 旗舰 glm-5 系（官网已见 GLM-5.3，1M 上下文）、轻量 glm-4.5-air 系、视觉 GLM-4.6V / GLM-5V-Turbo、**免费档是否存在待校准** | 第三方 OpenAI 客户端强拼 `/v1` 得 404 是知名坑——自研传输不受影响；`response_format` json 支持度待校验 |
| **mimo** | MiMo 开放平台 mimo.mi.com（2025-12 上线），`…/v1/chat/completions` 风格 | mimo-v2.6-flash（MoE，309B 总参/15B 激活）、mimo-v2.6-pro、mimo-v2.6-pro-ultraspeed、mimo-v2.5-pro；**注册免费额度待校准** | 推理内容走 `reasoning_content` delta → 透传 ThinkingDelta；图片/音视频理解与 ASR 后置 |

两厂官方口径均为 OpenAI Chat Completions 兼容；因此 DeepSeek / OpenRouter / 硅基流动等厂商 = 新增 preset 数据条目，零代码分支。若 MiMo 官方平台注册/计费不顺手，走第三方托管同样是换 preset 数据。

## 6. 错误、重试与 quirks

错误分类（UI 可据此给可读文案）：`AuthError`(401 key 无效) / `QuotaError`(欠费) / `RateLimit`(429) / `NetworkError`(含代理问题) / `ProviderError`(code,msg) / `ToolSchemaError`(工具参数不合法→作为错误结果回喂模型自纠)。

重试：429 与网络错误指数退避+抖动，仅对幂等步骤；agent loop 中途中断时因会话已持久化，可重试续跑同一步。

**未知字段一律忽略不崩**（island M0 教训：解析收紧但容错）。

## 7. 会话与上下文

- `Message` 模型与 OpenAI 对齐：role / content / tool_calls / tool_call_id，kotlinx.serialization 落盘。
- 默认 `ContextPolicy`：system 常驻 + 保留近 12 轮 + token 估算（字符数/4 量级）超限再裁最老；预留 summarize 钩子（后置）。

## 8. Key 管理与安全红线

App 设置页：选厂商 → 贴 key → 「连通性自检」（列模型或 1-token ping）→ 保存。

三条红线：① 数据包导出（PackageCodec）**永不携带 key**；② 演示模式**永不挂真 key**；③ 日志/异常消息只出 mask（`sk-***last4`）。

## 9. 用量记账

`UsageLedger` 按 厂商×模型 记 prompt/completion tokens，会话与累计两级；不内置价格表——估算为可选，单价数据由 app/用户提供，避免 SDK 钉死会过时的价格。

## 10. 测试策略

- **FakeChatModel**（testFixtures）：脚本化事件序列 → loop 测试（工具调用→回喂→终结 / maxSteps 熔断 / 中途错误恢复 / 取消 / 多工具并发）。
- **MockWebServer**：SSE 契约（delta 聚合、tool_calls 分片累积、`[DONE]`、usage 字段、错误体解析、GLM 无 /v1 路径风格）。
- 序列化往返：Message / Session。
- 纯 core 编译断言：core 无 `android.*` import。

## 11. 里程碑

| 里程碑 | 内容 | 验证门槛 |
|---|---|---|
| **M0 spike**（半会话） | 真调 GLM+MiMo 各一发：非流式/流式/工具调用；脚本入 `tools/` | preset 的 baseUrl/模型名/quirks 回填本 spec |
| **M1 传输层** | ChatModel + preset + SSE 流式 + 错误分类 + ApiKeyStore（+android 实现） | MockWebServer 契约测试绿 |
| **M2 agent loop** | ToolRegistry DSL + 多步循环 + 会话/上下文 + 用量 | FakeChatModel loop 测试绿 |
| **M3 消费方接入** | 首个 app 挂设置页+对话入口（UI 归 app it-XXX，走 DESIGN.md） | 实机走查 |

## 12. 开放问题（待 Leo 定）

1. **首个消费方**：eats「吃啥参谋」（数据面小、闭环快）vs wardrobe「穿搭顾问」（价值大、工具重）vs clips（剪贴板摘要整理）。
2. **MiMo 官方平台**（mimo.mi.com）注册门槛/计费是否顺手；不顺手则走第三方托管（仅换 preset 数据）。spike 脚本 `tools/spike-mimo.sh` 就绪：`MIMO_API_KEY` + `MIMO_BASE_URL` 两个环境变量即可跑。
3. **视觉输入**：MVP 只保留 content parts 结构（OpenAI 格式自带 image_url），识衣/识菜后置到消费方迭代——是否同意。
4. **目录惯例**：libs 首次出现 `specs/iterations/`（本 SDK 跨 app、无单一归属迭代）——是否认可。
