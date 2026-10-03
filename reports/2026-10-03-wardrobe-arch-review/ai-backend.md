# 分报告二：AI / 网络后端链路深度审查

> 审查通道 B · 全部结论基于逐文件精读，P1 级发现经主审复核源码确认。
> 路径约定：`AGENT=` libs/agent/src/main/kotlin/com/leo/libs/agent，`APP=` wardrobe/app/src/main/java/com/leo/wardrobe。

## A. 事实基线

### A.1 链路拓扑（文字版）

```
【聊天轨】
ChatScreen/ChatListScreen (W13)
  → ChatViewModel (APP/ui/chat/ChatViewModel.kt, 406 行)
      ├─ resolveConnection(): PrefsStore(aiPresetId/aiModel/custom) + ModelCatalog.byId().chatPreset + apiKeyStore.get(preset.id)
      ├─ AppContainer.chatModel(preset) → OkHttpChatModel(preset, apiKeyStore)
      │    └─ 演示模式再包一层 CachedMockChatModel(OkHttpChatModel, MockChatCache, SHA-256(key) 指纹)
      ├─ wardrobeTools(4 只读工具 + 可选 generate_outfit_image) → ToolRegistry
      └─ AgentRunner(model, config, tools, session=FileSessionStore, sessionId)
            └─ model.stream() → SSE 自解析(SseDecoder/StreamAssembler) → 工具循环(maxSteps=8)
                  └─ 每条产出消息 persist → FileSessionStore(filesDir/agent-sessions/<id>.json, 原子整表重写)
      会话摘要 → ChatSessionIndex(agent-sessions/index.json) → W12 列表
      用量 → FileUsageLedger(agent-usage.json)

【生图轨】
W7/W8/W1 出图面板 → OutfitGenerateSheet/GenerateWorkbench (APP/ui/records, 578 行)
  → OutfitImageGenerator (APP/data/gen, 230 行)
      ├─ connection(): PrefsStore(aiImagePresetId/aiImageModel/custom) + ModelCatalog / ProviderSpec.customImage + apiKeyStore.get(spec.id)
      ├─ buildRefs(): OutfitImageComposer.composeToExportFile(合成长图 JPEG) → fileToDataUri(WebP/JPEG→base64 data URI)
      ├─ 演示模式 → MockImageModel(回放 effect-weekend.png)；真实 → OkHttpImageModel(同步双协议)
      └─ 事件流 Started/Progress/Completed/Failed → 候选字节内存 → save() → putPackageImage(WebP) + addEffectImage(source=ai)
  顾问对话流第 5 工具 generate_outfit_image → ChatViewModel.runAdvisorImageGen → 同一 generator.run()

【两轨共用】KeystoreApiKeyStore(AndroidKeyStore AES-GCM, 按 provider id 一把 Key)、FileUsageLedger、AgentError 错误分轨
```

### A.2 各文件行数

SDK main（共 2286 行）：ModelCatalog 394、OkHttpImageModel 245、Wire 209、AgentRunner 159、DashScopeTaskImageModel 177、OkHttpChatModel 110、ProviderPreset 102、UsageLedger 101、Sse 119、SessionStore 87、ToolRegistry 76、ChatModel 73、FakeChatModel 78、Message 64、ImageModel 59、AgentError 59、JsonSchema 53、ContextPolicy 52、FakeImageModel 42、ApiKeyStore 27。
SDK test（共 1687 行）：AgentRunnerTest 273、OkHttpChatModelTest 233、OkHttpImageModelTest 222、SessionAndPolicyTest 148、ToolDslTest 135、DashScopeTaskImageModelTest 134、PayloadTest 129、SseTest 117、ModelCatalogTest 100 等。
wardrobe 侧接线（共 1798 行）：ChatViewModel 406、SettingsViewModel 309、OutfitImageGenerator 230、WardrobeTools 192、OutfitRecommendationParser 184、MockChatCache(CachedMockChatModel 同文件) 157、ChatSessionIndex 117、KeystoreApiKeyStore 78、MockImageModel 49、ImageParamEncoder 28、DemoChatModel 29、ImageSourceFileResolver 9。相邻：OutfitGenerateSheet 578、AppContainer 147。

### A.3 协议/适配器清单

| 协议 | 适配器 | 端点 | 覆盖厂商 |
|---|---|---|---|
| OpenAI Chat Completions（SSE 流式 + 非流式） | OkHttpChatModel（唯一聊天 HTTP 实现） | `{base}/chat/completions` | glm/mimo/mimo-tp/deepseek/moonshot/dashscope/volc-ark/siliconflow/custom |
| OPENAI_IMAGES_SYNC | OkHttpImageModel | `{imageBaseUrl}/images/generations` | 火山 Seedream×2、硅基 Kolors/Z-Image-Turbo/Qwen-Image-Edit×2、智谱 cogview-4、custom-image |
| DASHSCOPE_SYNC | OkHttpImageModel | `/api/v1/services/aigc/multimodal-generation/generation` | 百炼 qwen-image-edit-plus/-edit |
| DASHSCOPE_ASYNC_TASK | DashScopeTaskImageModel | 提交 `image2image/image2image`(X-DashScope-Async) + 轮询 `/api/v1/tasks/{id}`(3s 间隔/180s deadline) | 百炼 aitryon-plus（**app 侧无任何实例化**，见 A-2） |

## B. 问题清单

**A-1 ｜ P1 ｜ 参考图装配静默降级——it-079 同类根因仍有残留**
`APP/data/gen/OutfitImageGenerator.kt:201-216`：`buildRefs` 里 `composeToExportFile` 返回 null、`decodeFile` 返回 null、外层 `runCatching{}.getOrNull()`——三条失败路径全部落到 `emptyList()`，不报错；`run()` 随后照常发出**无 image 字段**的纯文生图请求。it-079 修的正是这条链（绝对路径误拼→解码 null→静默空列表→SF 报 `Missing required key: image`），但修法只加了路径解析（`ImageSourceFileResolver.kt`），没有加「模型吃参考图（inputLimit>0）但 refs 为空 ⇒ 必须失败」的不变量。影响：人物参考照缺失/合成失败/存储满时，用户按图文编辑价付费却得到一张无参考的文生图，且 UI 无任何提示。修复建议：`run()` 入口校验 `inputLimit(connection) > 0 && refs.isEmpty()` 时抛 `AgentError.Schema("参考图装配失败…")`。

**A-2 ｜ P1 ｜ aitryon-plus 可被选中但永远无法生成——目录声明与 UI 过滤、适配器接线三处脱节**
- `AGENT/ModelCatalog.kt:253-266`：aitryon-plus 带 `Capability.IMAGE_GEN`，会进 `imageModels`；
- `APP/ui/settings/SettingsScreen.kt:476` 与 `APP/ui/records/OutfitGenerateSheet.kt:235`：模型下拉均直接 `imageModels.forEach`，**无 imageProtocol 过滤**；
- `APP/data/gen/OutfitImageGenerator.kt:84-93`：真实模式只实例化 `OkHttpImageModel`，全仓库 grep 无 `DashScopeTaskImageModel` 引用——选中后每次生成必被 `OkHttpImageModel` 拦为 `AgentError.Schema("异步任务型模型请使用 DashScopeTaskImageModel")`。
这违反 it-077 自己的拍板「aitryon v1 SDK 实现、UI 不放（二期）」。修复建议：下拉过滤 `imageProtocol != DASHSCOPE_ASYNC_TASK`（或 ModelSpec 加 `uiHidden` 声明位），一改两处。

**A-3 ｜ P1 ｜ 聊天轨取消传播缺口：it-077 的「取消即断」补丁只修了生图两适配器**
`AGENT/OkHttpChatModel.kt:43-58,60-84`：`complete()` 与 `stream()` 都用阻塞式 `call.execute()`，取消仅在 `body.charStream()` 逐行循环的 `ensureActive()`（:71）处被观察——连接/DNS/响应头阶段（OkHttp 默认 10s×2）取消不会中断 HTTP，请求在后台跑完并计费。对照 `AGENT/image/OkHttpImageModel.kt:150-165` 的注释「此前阻塞式 execute 不响应取消，用户点取消后请求仍在后台跑完并计费」——同一个 bug 在聊天轨没修（git d022d73 只改了两个 image 适配器）。用户退出 W13 点 stop() 后，慢厂商的长回答仍在后台完成。修复建议：把 `Call.await()`（enqueue + `invokeOnCancellation { cancel() }`）下沉到聊天传输，或抽到共享 internal。

**A-4 ｜ P1 ｜ SSE「干净断连」中途截断被装配成正常完成——半截回答静默落会话**
`AGENT/internal/Sse.kt:79-89`：`completed()` 只在 `!sawDelta && finishReason == null` 时抛 Provider；若已收到部分 TextDelta 后连接被对端**正常关闭**（移动网络切换/代理掐流，无 IOException），`lineSequence()` 正常结束 → `Completed(半截文本, finishReason=null)` → AgentRunner 视为最终回答落盘（`AgentRunner.kt:117` 的 `finish=="tool_calls"` 判断对 null 短路）→ 用户看到被截断的回答且无任何错误/重试提示。修复建议：`completed()` 对 `sawDelta && finishReason == null` 且未收到 `[DONE]` 的情形给出可恢复错误（或至少标记截断）。

**A-5 ｜ P2 ｜ 零重试策略，429 的 Retry-After 解析后无人消费**
`AGENT/AgentError.kt:18,40`：`RateLimit(retryAfterSeconds)` 从头里解析出来，但全仓库 grep 无任何消费者；SDK 无退避重试，`APP/ui/chat/ChatScreen.kt:465` 只给手动「重试」按钮，且不显示「请等 N 秒」。对自用 app 可接受，但限流场景体验是「失败→手点→再失败」。

**A-6 ｜ P2 ｜ 流式 usage 记账系统性偏低**
`AGENT/internal/Wire.kt:36-43`：请求体无 `stream_options: {"include_usage": true}`；OpenAI 严格语义下流式响应不带 usage。GLM/DeepSeek/百炼在末 chunk 自带 usage，但 OpenAI 本尊及部分兼容厂商不会 → `ChatViewModel.kt:287` 记账 `ev.usage` 得 0。用量卡（W11）展示的 token 数跨厂商不可比。修复：quirks 或全局加 stream_options（注意个别国产厂商对未知字段敏感，需按 quirks 数据开关）。

**A-7 ｜ P2 ｜ 生图事件流的 catch 列表不全：非 IO 运行时异常会击穿「终态只有两种」契约**
`AGENT/image/OkHttpImageModel.kt:65-71`、`DashScopeTaskImageModel.kt:63-69`：只捕 `AgentError/IOException`。`dashscopeSync` 里 `root["usage"]?.jsonObject`（:135，usage 若为非对象抛 IAE）、`Base64.getMimeDecoder().decode`（:103，坏 b64 抛 IAE）等都会以 Flow 异常逃逸；`APP/data/gen/OutfitImageGenerator.kt:145-147` 的 `run()` 只重抛 CancellationException → W7 直达路径 `OutfitGenerateSheet.kt:178` 的 `scope.launch` 无兜底 → **app 崩溃**（顾问路径被 `ToolRegistry.execute` 的 runCatching 兜住成 error 回喂，见 `AGENT/tool/ToolRegistry.kt:52-53`）。修复：适配器 catch 改 `catch (e: Exception)` 归一为 Failed（Cancellation 除外），与 ImageModel.kt:41-43 自述的契约对齐。

**A-8 ｜ P2 ｜ OkHttpClient 每次调用新建**
`APP/di/AppContainer.kt:76-77`：每次 `chatModel(preset)` new 一个 `OkHttpChatModel`（默认 `OkHttpClient()`，独立连接池/线程池）；`ChatViewModel` 每次 `send()/retry()/regenerate()` 都调它，`OutfitImageGenerator.imageModel()`（:84-93）每次生成 new 一个 `defaultClient()`，自检同理。连接池线程为 daemon、60s 回收，非硬泄漏，但属资源 churn + 取消窗口内多条连接并存。修复：AppContainer 持单例 client（生图与聊天可各一），适配器注入。

**A-9 ｜ P2 ｜ 会话持久化的三个薄弱点**
`AGENT/session/SessionStore.kt`：(1) `append` 每条消息全量读+全量重写（:44-49），长会话 O(n²)（几百条消息时尚可，但 regenerate 的 clear+重放 `ChatViewModel.kt:176-177` 把它放大为逐条重写）；(2) `regenerate` 的 `clear → 逐条 append` 之间崩溃会**丢整会话**（窗口小但存在）；(3) 损坏 JSON 按空会话处理（:68-70）→ 下一次 append 直接以空表覆写原文件，损坏被「治愈」成数据丢失。建议：损坏文件先改名隔离（`.corrupt`）再重建；regenerate 改为「写新文件成功后原子替换」。

**A-10 ｜ P2 ｜ ChatSessionIndex 一致性：删除未实现、孤儿文件不可见**
`APP/data/chat/ChatSessionIndex.kt`：it-076（会话长按删除）至今是「提案待拍板」、无实施记录——代码中无 `remove`，会话与索引只增不减。另外：index 写失败回退到 `file.writeText`（非原子）后 `readLocked` 得空 → 全部会话从列表消失（消息文件仍在但不可达，`ensure()` 只在逐个 open 时补登记）；`index.json` 放在 `agent-sessions/` 内与消息文件同目录，`FileSessionStore.fileOf("index")` 理论上会解析到同一文件（现状 uuid 命名不触发，属埋雷）。建议：index 移出会话目录或用保留字过滤。

**A-11 ｜ P2 ｜ maxSteps 熔断的收尾提示以 user 角色落盘**
`AGENT/AgentRunner.kt:140-146`：`Message.user(CLOSING_PROMPT)` 被 persist 进会话。后果：历史里出现一条用户没说过的话（W13 按角色渲染会显示成用户气泡）；`ChatViewModel.regenerate()`（:174）取 `indexOfLast { Role.User }` 为重放边界时可能落在收尾提示上，重跑语义漂移。建议：收尾提示不入 session（只进本轮 messages），或引入内部 role 标记。

**A-12 ｜ P2 ｜ ChatViewModel 会话切换不打断运行中的 loop**
`ChatViewModel.kt:119-134`：`open()` 换 sessionId 但不 cancel `job`；`_streaming/_thinking/_toolNotices` 是 VM 级状态——旧会话的流式文本与工具条会串台渲染进新会话视图直到跑完（消息本体因 AgentRunner 构造时固化 sessionId 不会写错文件，但新会话的 index 在 finally 里被用新 id refresh，旧会话 index 停在 send 时刻）。建议：open() 先 stop()。

**A-13 ｜ P2 ｜ 高级参数非法值静默丢弃**
`APP/data/gen/ImageParamEncoder.kt:14-27`：INT 填 "abc"、FLOAT 填 "inf" 等 `return@mapNotNull null` 直接丢参——请求发出时少一个参数且无提示（对必填参数如硅基某模型 size 缺失会变成服务端 400 才暴露）。建议：非法值回退到 `spec.default` 并打点，或 UI 侧校验。

**A-14 ｜ P2 ｜ 参考长图双重 JPEG 编码与内存峰值（无 OOM 实据，但无守护）**
链路：`OutfitImageComposer.composeToExportFile`（合成 1024×~3400 ARGB ≈ 14MB，落 JPEG q90）→ `OutfitImageGenerator.fileToDataUri`（:209-216）**再全量 decode**（又 14MB）→ 再 q90 压缩（二次 JPEG 有损+白做一遍）→ `Base64.encodeToString`（1.33× 字节 → UTF-16 String ≈2×）→ `"data:image/jpeg;base64," + …` 拼接再复制一份 → `body.toString()`（JsonPrimitive 转义 base64 无膨胀，又一份 String）→ `toRequestBody` UTF-8 bytes。峰值同时驻留约 25–30MB 瞬时（导入侧有 MAX_SIDE=1440 封顶，`ImageFileStore.kt:192`，构图高度有界，故现代设备安全；老设备 128MB 堆+大参考照时偏紧）。建议：compose 直接返回 bitmap/bytes 免二次编解码；base64 用流式写入 RequestBody 避免双份字符串。

**A-15 ｜ P2 ｜ MockChatCache 版本号未随语料演进**
`APP/data/mock/MockChatCache.kt:82`：`MOCK_DATA_VERSION = "mock-wardrobe-v1"` 自 it-050（git 7117c92, 2026-09-28）后未动过；it-060 演示语料扩容后，同一问题在 Mock 衣橱上的旧缓存回答仍会命中 7 天 TTL。建议：语料变更迭代同步 bump。

**A-16 ｜ P2 ｜ 两处低风险健壮性/规范缺口（合并列出）**
(1) `AGENT/internal/Sse.kt:23-25`：多行 `data:` 按 SSE 规范应以 `\n` 拼接，现为直接 append（SseTest「多行 data 按 SSE 规范拼接」锁定的其实是无分隔拼接）——主流 chat 流单行发 JSON 不触发，但厂商/网关一旦分行即拼坏（好在 `StreamAssembler.feed` 容错返回空事件，不崩）。(2) `AGENT/AgentRunner.kt:117`：`hasCalls = finish=="tool_calls" && toolCalls.isNotEmpty()`——若厂商在 `finish_reason=stop` 下仍给 toolCalls（个别兼容网关有此行为），工具被静默丢弃、回答为空文本。建议改为 `msg.toolCalls.isNotEmpty()` 为主判据。

**A-17 ｜ 备忘 ｜ it-077 提案的「自定义生图 preset JSON 透传面板」未实现**
提案§阶段B 与拍板①均写「自定义 preset 透传 JSON 原文」，`OutfitGenerateSheet` 的参数面板只按 `modelSpec?.params` 声明渲染，custom-image 无 params 声明 ⇒ 无任何高级参数入口（`ImageParamEncoder` 对未声明 key 也只会出字符串）。文档-代码漂移，建议在 it-077 文档注记或补一个 JSON 输入框。

### 审查点专项结论（未成条的）
- **Key 安全（点5）：合规。** `APP/data/prefs/KeystoreApiKeyStore.kt`：AndroidKeyStore 不可导出主钥 + AES-GCM（IV+密文 base64 存 `agent_secrets`/`mock_agent_secrets` 两个 SharedPreferences），解密失败按未配置处理。SDK 侧 grep 零 `Log.`/`println`；`maskApiKey` 在 `SettingsViewModel.kt:58,249` 两处 keyMask 使用；`MockChatCache` 指纹为 `MessageDigest SHA-256(key)`（AppContainer.kt:80-83），缓存文件不含明文 Key；`AgentError.detailOf` 只取厂商 body 不含 Key；`SettingsViewModel.kt:176` 打的异常栈为 AgentError（消息为厂商 detail），无 Key 泄漏面。ADR-024/026/029/030 与代码一致（ADR-029 已核验：`Message.toWire()` 不映射 payload 字段，`Wire.kt:137-166`；payload 只随 `Message.serializer()` 落盘）。
- **WardrobeTools 只读门禁（点6）：成立。** search_items/search_outfits/wear_stats/current_person 全部只读 `data()` 快照；截断策略合理（items 取 20、outfits 取 10、闲置 10 + 「共 N 件」提示）；payload 全量 ids 只落盘不回喂（不耗 token，`ToolRegistry`/`AgentRunner.kt:130-134` 只把 `result.asText()` 上 wire）。
- **Mock 隔离（点8）：彻底。** prefs `mock_` 前缀（AppContainer.kt:58）、Keystore `mock_agent` namespace（:69）、`filesDir/mock-agent-sessions` + `cacheDir/mock-agent-cache`（:73,91）、`MOCK_ASSET_REVISION` 已随 it-078 bump（:144）。`CachedMockChatModel` 的 commit/discard 与 runner 生命周期严格对齐（Failed→discardPending、Completed→commitPending、取消 finally→discard），`complete()` 故意绕过缓存防「缓存假健康」。

## C. 亮点

1. **数据驱动抽象是真兑现，不是口号**：it-077 一口气扩容 5 家聊天厂商，`OkHttpChatModel` 零改动（git 可证）；厂商差异（GLM 无 /v1、MiMo 双 host、硅基 image 只收字符串、aitryon 只收 URL）全部落在 ProviderPreset/ModelSpec 数据位（`inputImages`/`supportsBase64`/`params`），硅基 Qwen-Image-Edit 与百炼同模型不同能力由两条 ModelSpec 表达——ADR-007 的设计红利真实可见。
2. **ImageModel 事件流归一质量高**：同步/异步三协议对上层只暴露 `Flow<ImageGenEvent>` 同构终态语义（Failed 后流正常结束，collector 无需 try/catch）；异步任务的 PENDING/RUNNING/PREVIEWABLE 映射、deadline 语义（超时文案明示「任务可能仍在厂商侧执行」不重试）体现了对计费边界的理解；24h 失效 URL 在适配器内下载成字节，App 全程不碰临时 URL。
3. **取消即断的 Call.await 桥接**（`OkHttpImageModel.kt:150-165`）：enqueue + `invokeOnCancellation { cancel() }`，配合注释里对「后台跑完并计费」的根因记录——只是没回移到聊天轨（A-3），但模式本身正确且有单测锁。
4. **声明式本地预检（Rectifier 思想）**：`validateAgainstSpec` 把图数超档/URL-only 误传在本地拦下不烧请求（`OkHttpImageModel.kt:74-83`），配合 GLM `@EncodeDefault` type 字段（Wire.kt:55-59）这类「实调换来的 quirk 沉淀」，错误在到达厂商前就被消化。
5. **持久化模式统一且原子**：FileSessionStore/ChatSessionIndex/MockChatCache/FileUsageLedger 四处全部 tmp + `ATOMIC_MOVE` + renameTo 三级回退，sessionId 白名单清洗防路径穿越；AgentRunner 每条消息产出即 append，崩溃最多丢在途流式那一条。
6. **测试护栏选点准**：SourcePurityTest 强制 core 无 android import（桌面 JVM 可测）；LiveImageSmokeTest 缺环境变量即跳过守住「CI 不打真 API」红线；SSE/装配器/三协议 wire 形态均有契约测试（含「多图走数组形态」「b64 直返」「401 归 Auth」这类实调换来的断言）。

## D. 量化

### D.1 网络端点清单（全部 BYOK 直连，无自建服务端）

| 用途 | 端点 |
|---|---|
| 聊天 ×8 厂商 + custom | `https://open.bigmodel.cn/api/paas/v4`、`https://api.xiaomimimo.com/v1`、`https://token-plan-cn.xiaomimimo.com/v1`、`https://api.deepseek.com`、`https://api.moonshot.cn/v1`、`https://dashscope.aliyuncs.com/compatible-mode/v1`、`https://ark.cn-beijing.volces.com/api/v3`、`https://api.siliconflow.cn/v1`（各 + `/chat/completions`）+ 用户自填 base |
| 生图 OPENAI_IMAGES_SYNC | 上述 base 中 glm/dashscope/volc-ark/siliconflow 的 imageBaseUrl + `/images/generations`（+ custom-image） |
| 生图 DASHSCOPE_SYNC | `https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation` |
| 生图 DASHSCOPE_ASYNC_TASK | 提交 `…/api/v1/services/aigc/image2image/image2image`（Header X-DashScope-Async）+ 轮询 `…/api/v1/tasks/{id}` |
| 生成图下载 | 厂商返回的预签名 URL（免鉴权 GET） |
| 超时 | 聊天：OkHttp 默认（connect/read 10s）；生图：connect 15s / read 170s / write 60s（收敛在 `defaultClient()`，LESSONS 有案） |

### D.2 行数分布
- SDK：main 2286 行 / test 1687 行（测试:main ≈ 0.74:1）；最大文件 ModelCatalog 394 行且纯数据。
- wardrobe 侧 agent 链路接线：1798 行（核心 12 文件），相邻 OutfitGenerateSheet 578 + AppContainer 147；ChatViewModel 406 行承担会话列表+连接解析+loop 驱动+生图工具执行体四职，尚可维护但已接近该应用其他 VM 的规模上限（SettingsViewModel 309）。

### D.3 it-077 系时间线与 hotfix 结构性归类
- **2026-09-30**：it-077 主落地（0573c45），同日 20 个 commit、文档记录十五次修订——含三枚真 bug 修复：纯文生图误带 image 字段（3effaf6，11235 必败）、取消不中断 HTTP（d022d73）、生成页连接解析回归（21e3b40）；外加硅基模型 id 实测校准（0573c45）。
- **2026-10-01**：it-078（5d0be4b）蓝色占位图 hotfix；it-079（805a9c1）真实生图参考图丢失 + seed 类型 hotfix；同日补 it-077 端到端验证（a88d1b4）。
- **结构性归类**（两次 hotfix + it-077 内三枚真 bug 的共性）：
  1. **「不能失败的假实现」**（it-078）：MockImageModel 曾用 1×1 蓝色 PNG 兜底——能被 Coil 解码的占位把失败伪装成成功。修法是「样张缺失即显式 Failed」，方向正确。
  2. **「静默降级吞掉契约违反」**（it-079 前半 + 3effaf6 + A-1 残留）：绝对路径误拼→decode null→空列表→请求照发；纯文生图带 image。同一形态三次出现，说明链路缺「所发即所见」的显式不变量——这正是 A-1 建议在 `run()` 加 refs 非空硬校验的依据。
  3. **「stringly-typed 参数管道」**（it-079 后半）：UI 收集一律 String→JsonPrimitive(string)，靠事后 Encoder 按声明转型，且转不动就静默丢（A-13）。
  4. 附带暴露的第四类：**「环境噪声掩盖代码问题」**（TUN 代理 fake-ip 劫持模拟器大请求体，it-077 验证记录第 6 条）——团队用 Mac 直连 wire 验证剥离了该噪声，方法正确，但也说明真实链路的 live 验证仍高度依赖单机手工环境，自动化护栏只到 Fake/MockWebServer 层（这正是 hotfix 密集的土壤）。

**总评**：SDK 抽象质量与安全红线执行是这段链路的强项（数据驱动、终态事件流、Key 治理、原子落盘均达生产水准）；当前主要债务集中在**网络韧性的另一半**——聊天轨取消传播（A-3）、流截断静默完成（A-4）、零重试（A-5），以及 it-079 已暴露但只修了表象的**静默降级族**（A-1/A-13）。四个 P1 都有清晰的小切口修法，建议合并为一个小迭代一次清掉。
