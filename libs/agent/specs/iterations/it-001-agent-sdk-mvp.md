# it-001 · agent SDK MVP（BYOK 传输层 + Agent Loop）

- **状态**：**进行中——M0 spike 完成（2026-09-25，GLM/MiMo 真调四步全过，preset 已回填）；M1 传输层已完成（2026-09-23，32 单测全绿）；M2 提案见 [it-002-agent-loop-m2.md](it-002-agent-loop-m2.md)（待 Leo 确认）**。设计见 [../00-architecture.md](../00-architecture.md)，决策见 [../06-decisions.md](../06-decisions.md)。
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

- [x] core 为纯 Kotlin（无 `android.*` import），桌面 JVM 全量单测（SourcePurityTest 断言 + 63 测绿，2026-09-25）
- [ ] MockWebServer 契约测试：SSE delta 聚合 / tool_calls 分片累积 / `[DONE]` / usage / 错误体 / GLM 无 /v1 路径风格
- [x] FakeChatModel loop 测试：工具回喂 / maxSteps 熔断 / 错误恢复 / 取消（it-002 落地，AgentRunnerTest 11 例，2026-09-25）
- [x] M0 spike 完成：GLM+MiMo 真调各一发（非流式/流式/工具调用），baseUrl/模型名/quirks 回填 spec，脚本入 `tools/`（2026-09-25，见验证记录）
- [ ] key 红线断言：导出与日志路径不含明文 key（接口级测试）

## 影响范围

- 新增：`libs/agent/{core,android,tools,specs}`（composite includeBuild，接法同 store）
- 根 `gradle/libs.versions.toml`：+okhttp-sse（okhttp 4.12 已在表）
- 消费 app（M3 接入时）：settings includeBuild + 设置页/对话页，届时另开 app it-XXX，UI 走 DESIGN.md
- 不改动：store / sync / cutout / carddeck、两 app 现有代码

## 里程碑

M0 spike（半会话）→ M1 传输层 → M2 agent loop →（M3 归消费方 app it-XXX）

## 验证记录

### M1 传输层（2026-09-23）
- 单测 **32/32 绿**：StreamAssembler 6、SseDecoder 5、OkHttpChatModel（MockWebServer 契约）12、FakeChatModel 4、ProvidersAndKeys 5
- 覆盖：SSE 解析（多行 data / CRLF / 注释 / flush 兜底）、tool_calls 分片聚合、reasoning_content→ThinkingDelta（quirks 开关）、未知载荷容错、路径拼接（/v1 与无 /v1 两种 preset）、鉴权头、请求体序列化（视觉 parts / max_tokens / system 角色）、错误映射（401→Auth、429+Retry-After→RateLimit、400 余额→Quota、400→Schema、5xx→Provider）、未配 key→Auth、FakeChatModel 脚本回放与请求记录
- 构建：独立 composite 构建（store 同款接线）；根版本表新增 `okhttp-mockwebserver` 与 `leo-agent` 坐标；构建用 `JAVA_HOME=homebrew openjdk@17`
- 实现与设计的偏差：单模块落地未拆 android/（ApiKeyStore 平台实现归 app 层）；SSE 自解析未引 okhttp-sse——均已注记 ADR-003/004
- **M0 spike 顺延**：GLM 脚本就绪（`tools/spike-glm.sh`，key 在 island 钥匙串，`security` 读取授权被拒后不再自动尝试，待手动跑）；MiMo 待注册开放平台（`tools/spike-mimo.sh`）。preset「待校准」字段不阻塞 M2

### M0 spike（2026-09-25 完成）

- **输入**：Leo 提供 GLM plan key + MiMo Token 套餐 key（tp-）；均仅经环境变量传入，**未写入任何文件/仓库**（key 红线③：日志只出 mask）
- **GLM**（`tools/spike-glm.sh`，`GLM_API_KEY` 环境变量）：glm-4.6 / 4.5-air / 5 / 5.3 全部 1113「余额不足或无可用资源包」→ **glm-4-flash 四步全过**（非流式 ✓、SSE 流式含 usage 尾块 ✓、tool_calls 结构标准 finish_reason=tool_calls ✓、role=tool 回喂 ✓）
- **MiMo**（`tools/spike-mimo.sh` + 手动 python 复刻，`MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1`）：官方文档（mimo.mi.com llms.txt → static/docs）确认 **tp- key 专属 host `token-plan-cn.xiaomimimo.com/v1`**（走按量 host `api.xiaomimimo.com/v1` 实证 401 Invalid API Key）；mimo-v2.6-flash / pro 可用，pro-ultraspeed 此 host 400 Not supported；非流式含 `reasoning_content` ✓、SSE `delta.reasoning_content` 增量 ✓、tool_calls ✓、回喂 ✓、`thinking={"type":"disabled"}` 下 tool_calls 仍 ✓（官方建议调工具关 thinking，实测开着也稳定——暂不加传输层参数，演进候选）
- **发现并修复的脚本 bug 两处**：① GLM step3 把 `arguments` 裸嵌为 JSON 对象回喂 → GLM 1210 参数有误，必须 `json.dumps` 成字符串（OpenAI 契约）；② `curl | head -30` 在 pipefail 下 curl 56 断管中断脚本（MiMo 流式行数多必踩）→ 补 `|| true`
- **回填**：`Providers` preset（glm 档位注释去「待校准」、mimo 按量 host、新增 mimo-tp Token 套餐 preset + `Providers.all` 列表、contextTokens=1M）、`ProvidersAndKeysTest` 断言更新、00-architecture §0/§5/§11/§12
- **测试**：回填后 `./gradlew test` **32/32 绿**（JAVA_HOME=homebrew openjdk@17 libexec 路径）
- **遗留**：`response_format` json 支持度未校验（非阻塞）；GLM contextTokens 未回填；按量 host 无 sk- key 未实调
