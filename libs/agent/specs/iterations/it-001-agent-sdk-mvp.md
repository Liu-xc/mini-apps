# it-001 · agent SDK MVP（BYOK 传输层 + Agent Loop）

- **状态**：**已确认（2026-09-23 Leo 拍板自研），M0 进行中，未写 SDK 代码**。设计见 [../00-architecture.md](../00-architecture.md)，决策见 [../06-decisions.md](../06-decisions.md)。
- **类型**：新公共 SDK（libs/agent），跨 app（wardrobe / eats / clips 皆可消费）；**本迭代不含任何 app 接入**（M3 归消费方 app 的 it-XXX）。

## 背景与动机

- 各 app 想挂 AI 能力（穿搭顾问 / 吃啥参谋 / 剪贴板整理），缺的是统一模型接入层；BYOK（用户自带 API Key）让个人应用零成本提供 AI，无后端无运维。
- GLM（智谱）与 MiMo（小米）官方 API 均为 OpenAI 兼容：一层传输覆盖目标厂商，DeepSeek/OpenRouter/自定义端点免费获得。
- 开源框架调研结论：无「Android + BYOK 多厂商」的轻量开源件，自研薄核（ADR-001），Koog 当参考实现。

## 用户故事（US-A 编号，避免与 app 层 US 冲突）

### US-A1 配置模型连接
作为用户，我在设置里选择厂商（GLM / MiMo / 自定义）、粘贴 API Key、点「连通性自检」看到 ✓，即可使用；key 加密存本地。

验收：
- 厂商列表来自 preset 数据；自定义项可填 baseUrl + key + 模型名
- 自检失败给可读错误：401=key 无效 / 429=限流 / 网络=代理或断网
- key 落盘为密文（Keystore AES-GCM）；导出数据不含 key；日志只出 mask

### US-A2 流式对话
作为用户，我发出消息后立刻看到打字机输出与「思考中」状态，不必等完整回复。

验收：
- `Flow<AgentEvent>`：TextDelta 直渲染；ThinkingDelta（MiMo reasoning_content / GLM 混合推理）有独立 UI 态（归 app）
- 取消 = 协程取消，停止请求，半截消息标记中断不留脏状态
- 出错时 UI 呈现分类错误与重试按钮；重试从持久化的会话续跑

### US-A3 工具调用 agent
作为用户，我说「查我的衣橱配一套通勤装」，agent 自己调工具查数据后再回答。

验收：
- toolRegistry DSL 注册工具（name / description / JSON Schema / 闭包）
- loop 至多 maxSteps（默认 8）轮工具调用后强制终结；工具异常作为错误结果回喂模型自纠
- 全程事件可见（ToolRequested / ToolFinished），UI 可展开看调了什么

### US-A4 会话持久与恢复
作为用户，杀 app 重开后对话还在，能继续聊。

验收：
- SessionStore 默认文件实现（tmp→rename 原子写）
- ContextPolicy 超窗裁最老轮次，system 常驻
- 中断/出错后会话状态一致，可续跑

### US-A5 用量可见
作为用户，我能在设置里看到本机累计 token 用量（按厂商×模型分列）。

验收：
- UsageLedger 记 prompt/completion tokens，会话与累计两级
- SDK 不内置价格表；估算为可选（单价数据由 app 侧提供）

## 验收标准（整体）

- [ ] core 为纯 Kotlin（无 `android.*` import），桌面 JVM 全量单测
- [ ] MockWebServer 契约测试：SSE delta 聚合 / tool_calls 分片累积 / `[DONE]` / usage / 错误体 / GLM 无 /v1 路径风格
- [ ] FakeChatModel loop 测试：工具回喂 / maxSteps 熔断 / 错误恢复 / 取消
- [ ] M0 spike 完成：GLM+MiMo 真调各一发（非流式/流式/工具调用），baseUrl/模型名/quirks 回填 spec，脚本入 `tools/`
- [ ] key 红线断言：导出与日志路径不含明文 key（接口级测试）

## 影响范围

- 新增：`libs/agent/{core,android,tools,specs}`（composite includeBuild，接法同 store）
- 根 `gradle/libs.versions.toml`：+okhttp-sse（okhttp 4.12 已在表）
- 消费 app（M3 接入时）：settings includeBuild + 设置页/对话页，届时另开 app it-XXX，UI 走 DESIGN.md
- 不改动：store / sync / cutout / carddeck、两 app 现有代码

## 里程碑

M0 spike（半会话）→ M1 传输层 → M2 agent loop →（M3 归消费方 app it-XXX）

## 验证记录

（待回填：单测数、M0 spike 实调结论、构建产物）
