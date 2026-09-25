# it-002 · agent loop（M2）——工具调用 + 会话 + 上下文 + 用量

- **状态**：**已实现（2026-09-25，/goal 批准实施）**——单测 63/63 绿（M1 32 + 本轮 31），见文末验证记录
- **类型**：libs/agent SDK 迭代（it-001 里程碑 M2 的承接迭代）
- **关联**：US-A3 / US-A4 / US-A5（it-001 定义，本迭代实现）；设计见 [../00-architecture.md](../00-architecture.md) §4/§7/§9/§11，决策见 [../06-decisions.md](../06-decisions.md) ADR-005

## 背景与动机

M1 传输层已落地（bd4f8c3，32 单测绿）：`ChatModel` / `ChatEvent`（4 枚）/ `OkHttpChatModel` / `SseDecoder` / `StreamAssembler` / `FakeChatModel`。但「让模型调工具查数据再回答」的整层还只有 spec 草图：

- `AgentRunner` + `AgentEvent`（8 枚）——loop 核心，未实现
- `ToolRegistry` / `jsonSchema` DSL——未实现
- `SessionStore` / `ContextPolicy` / `UsageLedger`——接口与默认实现均未实现

M3（首个 app 接入 = wardrobe it-041）以本迭代为硬前置：没有 loop，app 侧只能做「一问一答裸聊天」，做不了「查我的衣橱配一套通勤装」。

## 用户故事（沿用 it-001 的 US-A 编号，不新增）

- **US-A3**（本迭代实现）：工具调用 agent——toolRegistry DSL 注册工具；loop 至多 maxSteps（默认 8）轮工具调用后强制终结；工具异常作为错误结果回喂模型自纠；全程 `ToolRequested / ToolFinished` 事件可见。
- **US-A4**（本迭代实现）：会话持久与恢复——杀 app 重开对话还在；ContextPolicy 超窗裁最老轮次、system 常驻；中断/出错后会话状态一致可续跑。
- **US-A5**（本迭代实现）：用量可见——UsageLedger 按厂商×模型记 prompt/completion tokens，会话与累计两级；SDK 不内置价格表。

## 方案

### 1. 工具 DSL

```kotlin
val tools = toolRegistry {
    tool(
        name = "search_wardrobe",
        description = "按条件搜索衣橱单品",
        parameters = jsonSchema {
            string("category", "上装/下装/鞋/配饰")
            string("keyword", "名称或颜色关键词", required = false)
        },
    ) { args -> ToolResult.ok(payloadText) }   // 抛异常 = ToolResult.error(msg)
}
```

- `jsonSchema` 只做**扁平 properties + required**（string / number / boolean / string-enum），产出 OpenAI function-calling 认的 JSON Schema（`JsonElement`，直接喂已有的 `ToolSpec.parametersSchema`）。嵌套 object 暂不支持——真实工具参数都是扁平的，需要时再扩 DSL（只增不改名）。
- `ToolResult`：`ok(text)` / `error(text)`。两者的差别是给模型看的措辞，loop 不区分处理。
- `ToolRegistry`：`List<ToolSpec>` 导出 + `execute(name, argsJson): ToolResult`；**参数 JSON 解析失败 / 未知工具名 → `ToolResult.error(...)` 回喂**，不让 loop 崩（对应 AgentError `Schema` 语义，但按「错误结果回喂模型自纠」走，不中断循环）。

### 2. AgentRunner

```kotlin
class AgentRunner(
    private val model: ChatModel,
    private val config: AgentConfig,      // systemPrompt / maxSteps=8 / temperature / model
    private val tools: ToolRegistry,
) {
    fun run(history: List<Message>): Flow<AgentEvent>
}

sealed interface AgentEvent {   // ADR-005：只增不改名
    data object StepStarted
    data class TextDelta(val text: String)
    data class ThinkingDelta(val text: String)
    data class ToolRequested(val call: ToolCall)
    data class ToolFinished(val call: ToolCall, val result: ToolResult)
    data object StepFinished
    data class Completed(val message: Message, val usage: Usage)   // usage 为全程累计
    data class Failed(val error: AgentError)
}
```

循环规则：

1. 每步以 `ChatModel.stream` 发起，`ChatEvent` → `AgentEvent` 映射：`TextDelta/ThinkingDelta` 直通；`ToolCallDelta` 由**工具调用装配器**按 index 聚合成 `ToolCall`（复用 M1 `ToolCallAccumulator` 的聚合逻辑——internal 提升可见性或提炼共用，M2 定夺）。
2. `Completed(finishReason == "tool_calls")` → 依次发 `ToolRequested` → 同步执行 → 发 `ToolFinished` → `Message.toolResult(...)` 追加历史 → 下一步。同一 assistant 消息里的多个 tool_calls **全部执行并逐个回喂**（不并行执行工具，保序简单）。
3. 工具执行抛异常 → `ToolResult.error` 回喂（US-A3 自纠），不进 `Failed`。
4. **maxSteps 熔断**：达到 `maxSteps` 仍返回 tool_calls → 追加一条收尾提示并**以不带 tools 的请求再发一轮**，强制模型直接文本作答；若仍返回 tool_calls 则就地 `Completed` 截断。→ 见「待确认决策 1」。
5. 传输层 `AgentError`（Auth/RateLimit/Network/Provider/Quota/Schema）→ 发 `Failed(error)` 后结束 Flow；已完成步骤的历史**不回滚**（配合 SessionStore 落盘，UI 可续跑）。
6. 取消 = 协程取消 `collect`，`ensureActive` 已在 M1 流式层生效，半截文本不落会话。
7. 每步 `StepStarted` / `StepFinished` 成对包裹；`Completed` 的 `usage` 为全程各步累计。

### 3. 会话与上下文

```kotlin
interface SessionStore {
    suspend fun append(sessionId: String, message: Message)
    suspend fun messages(sessionId: String): List<Message>
    suspend fun clear(sessionId: String)
}

interface ContextPolicy {
    fun trim(history: List<Message>, system: Message): List<Message>
}
```

- **默认文件实现 `FileSessionStore`**：`filesDir/sessions/<id>.json`，kotlinx.serialization 序列化，**tmp → rename 原子写**（M1 已有的落盘先例同款）；每步完成即 append（中断后续跑一致）。
- **默认 `DefaultContextPolicy`**：system 常驻 → 保留近 12 轮 → 字符数/4 估算 token 超窗裁最老；`tool` 消息跟随其所属 assistant 轮次一起裁（不留孤儿 tool_call）。summarize 钩子留接口不实现（架构 §7 既定）。
- 会话 id 由 app 决定（wardrobe 按角色或全局单会话，it-041 定）；SDK 不建多会话管理 UI 概念。

### 4. 用量

```kotlin
interface UsageLedger {
    suspend fun record(presetId: String, model: String, usage: Usage)
    suspend fun totals(): Map<Pair<String, String>, Usage>   // 厂商×模型 → 累计
}
```

- 默认 `FileUsageLedger`（同一原子写工具），会话级累计由 loop 内存聚合、随 `Completed` 吐出。
- 不内置价格表（架构 §9 既定）；`FakeChatModel` 的 `Usage()` 为 0 值，测试可断言累加。

### 5. 测试（验证门槛 = FakeChatModel loop 测试绿）

| 组 | 用例 |
|---|---|
| loop·工具 | 单工具回喂后续跑 / 多 tool_calls 逐个回喂 / 未知工具名回喂自纠 / 工具抛异常回喂不中断 |
| loop·熔断 | maxSteps 纵深（每步都回 tool_calls → 收尾轮去 tools 强制作答） |
| loop·错误 | 各 AgentError → Failed 事件、历史不回滚 / 失败后新 run 可续 |
| loop·取消 | collect 中途取消 → Flow 终止、session 无半截消息 |
| loop·事件序 | StepStarted/Finished 成对、TextDelta 顺序、Completed usage = 累计 |
| Session | 序列化往返 / tmp-rename 原子性 / append 后 messages 顺序 |
| Context | system 常驻 / 近 12 轮 / 超窗裁最老 / tool 跟随 assistant 不成孤儿 |
| Ledger | record 累加 / totals 分厂商×模型 |
| 红线 | core 无 `android.*` import 编译断言（it-001 验收项，M2 一并落） |

M1 的 32 测回归不破。

## 影响范围

- 新增：`libs/agent/src/main/.../{AgentRunner, tool/ToolRegistry, tool/JsonSchemaDsl, session/FileSessionStore, session/DefaultContextPolicy, usage/FileUsageLedger}.kt` + 对应测试
- 修改：`specs/00-architecture.md`（§4 草图 → 实现定名回填、§0 状态行）、`README.md`（**顺手修掉「提案，未写码」陈旧表述**）、it-001 验收清单勾选、`06-decisions.md`（如 loop 定名与草图有偏差记修订）
- 不动：M1 传输层公共签名（`ChatModel/ChatEvent/OkHttpChatModel`）、store / sync / cutout / carddeck、两 app 代码

## 待确认决策（Leo）

1. **maxSteps 熔断策略**：我推荐「耗尽后去 tools 追问一轮强制收尾」（模型有台阶下，用户总能拿到文本答案）；备选 = 直接 Failed 截断（更简单但体验差）。
2. **M0 spike 时序**：M2 是纯 FakeChatModel 测试，不依赖真 key，**可与 M0 并行**；但 it-041 阶段 A（真调自检）前必须补跑 M0（`tools/spike-glm.sh` 需要你手动跑一次钥匙串授权，或直接给 `GLM_API_KEY` 环境变量）。
3. 无其他待拍板项——DSL/事件/存储形态均已在 00-architecture §4 与 ADR-005 定过。

## 验证记录 · 2026-09-25

- 构建：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test`（libs/agent 独立构建）→ **BUILD SUCCESSFUL，63/63 绿、0 失败 0 错误**（M1 的 32 全部回归通过 + 本轮新增 31）
- 新增源码：`AgentRunner.kt`（AgentConfig / AgentEvent 8 枚 / AgentRunner）、`tool/JsonSchema.kt`（扁平 DSL）、`tool/ToolRegistry.kt`（ToolResult + 注册执行）、`session/SessionStore.kt`（接口 + InMemory + File 原子写）、`session/ContextPolicy.kt`（DefaultContextPolicy）、`usage/UsageLedger.kt`（接口 + InMemory + File）；`ChatModel.kt` 的 `Usage` 补 `@Serializable` 与 `operator plus`
- 测试 4 文件 31 例：`AgentRunnerTest`（11：单工具回喂事件序与 system 常驻 / 多 tool_calls 逐个回喂 / 未知工具 / 工具抛异常 / maxSteps 熔断收尾轮无 tools / 收尾轮仍回 tool_calls 截断 / Failed 历史不回滚 / 失败续跑 / 取消即停 / usage 跨步累计+TextDelta 保序 / 历史 system 丢弃）、`ToolDslTest`（8：schema 结构/重名抛错/分发参数/未知工具/非法 JSON/非对象参数/空参=「{}」/异常转错误）、`SessionAndPolicyTest`（9：文件会话往返保真/路径穿越清洗/损坏文件容错/游离头部丢弃/近 12 轮窗口/token 超窗裁剪/单轮过大保底/无孤儿 tool/用量累计与文件往返）、`SourcePurityTest`（1：main 无 `import android.`）
- **实现与提案的偏差**：① ToolCallDelta 不在 loop 层二次装配——传输层 `Completed.message.toolCalls` 已是全量，loop 直接消费（提案预留的「internal 提升」不需要）；② `ContextPolicy.trim` 的 system 参数为 `Message?`（systemPrompt 可空）；③ **新增调用方契约**：用户输入消息由 app 在发送时自行 append 进 SessionStore，runner 只持久化自己产出的消息（首轮测试失败后定的，已写入 AgentRunner KDoc）；④ 待确认决策 1（maxSteps 熔断策略）按推荐方案「工具回喂后去 tools 追问收尾轮」实施
- 遗留：`response_format` 支持度未校验（与 M0 共享的遗留，非阻塞）
