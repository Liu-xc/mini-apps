# it-077 · AI 穿搭生图与多 Provider 生图网关（W7/W11/W13 + libs/agent）

> 状态：**提案已定向**（2026-09-30，Leo 需求：「接入图+文生图模型生成穿搭效果图；模型接入能力抽象封装，参考 OpenCode/cc-switch 多 provider 架构，不锁死 GLM/MiMo；问答与生图各用各的模型；生图两条触发路径——界面直达 + 顾问对话流自动识别」）
> **拍板记录（2026-09-30，Leo）**：① 应用主要自用、不分发线上用户、价格不敏感 → **开销类策略全部不做**（月度护栏、生成前确认、成本拦截一律砍掉）；改为**把各模型专属配置透出**给使用者调（高级参数面板）。② **provider 抽象覆盖聊天轨**——不只生图做多 provider，对话流文本大模型同样要能插任意厂商：聊天 preset 扩容 + 与生图共用统一模型目录。
> 调研报告：`reports/2026-09-30-wardrobe-imagegen-research/report.md`（模型横评/价格/抽象设计/风险全量）

## 背景与动机

衣橱的搭配目前只有静态成品图（手动添加的 `effectImages`），用户看不到「这套穿在自己身上什么效果」。国内图像编辑/试穿 API 已到 ¥0.1–0.5/张的可用价位（百炼 qwen-image-edit-plus ¥0.2、火山 Seedream ¥0.22、硅基流动 Kolors 免费），BYOK 模式下用户月成本 ≈ ¥12–24（每天 2 次×2 张）。

同时，现有 `libs/agent` 只有聊天单轨（`ChatModel`，OpenAI 兼容传输 + preset 数据化），没有图像生成抽象。若直接硬编码某一家生图 API，未来换模型/加厂商又要改代码——需要像 OpenCode（provider 声明协议 + 模型注册表）那样把「生图能力」也抽象成可插拔轨。

## 方案

### 总体：`libs/agent` 统一模型目录 + 双能力轨（拍板②：两轨同注册表）

**统一 `ProviderCatalog`**：一个 `ProviderSpec` 声明该厂商的聊天端点、生图端点与全部模型；`ModelSpec.capabilities`（CHAT/IMAGE_GEN）分轨 + `supportsToolCall`（顾问 loop 硬门槛）+ 生图专属声明（inputImages/supportsBase64/params…）。现有 `Providers`（glm/mimo/custom，含 quirks 数据）并入，provider id 稳定、已存偏好与 Key 无缝迁移。**一把 Key 双轨共用**：Key 按 providerId 存（现机制不变），聊天卡与生图卡选同一厂商时无需重复配置。

- **聊天轨 preset 扩容（纯数据零代码）**：新增 `deepseek`（api.deepseek.com）、`moonshot`（api.moonshot.cn/v1）、`dashscope`（compatible-mode/v1，qwen3 系）、`volc-ark`（豆包 seed 系）、`siliconflow`（聚合 DeepSeek/Qwen/GLM 等）——全部 OpenAI 兼容，`OkHttpChatModel` 一行不改（ADR-002 红利兑现）。
- 新生图轨 `image/` 包：`ImageModel` 接口 `generate(request): Flow<ImageGenEvent>`（Started/Progress/Completed/Failed，对齐 AgentEvent 风格；错误复用 `AgentError`）。
- v1 内置生图模型：`dashscope`/qwen-image-edit-plus（主力）、`volc-ark`/seedream-4.0、5.0-lite（多件）、`siliconflow`/Kolors（免费）+ Z-Image-Turbo、`glm`/cogview-4（纯文生图标「灵感图」，可选）。aitryon（异步任务型）v1 实现 SDK 传输但不放 UI，二期配图床后开启。
- 协议三枚举：`OPENAI_IMAGES_SYNC` / `DASHSCOPE_SYNC` / `DASHSCOPE_ASYNC_TASK`——LiteLLM 至今没归一异步任务型（issue #28763），我们显式建模，同步/异步在适配器内消化，上层只见事件流。
- 上传前 WebP→JPEG 转码（各家 WebP 支持不一）；输出 URL 24h 失效 → 适配器内即时下载，App 只见 bytes。
- **模型专属参数声明式透出**（拍板①）：`ModelSpec.params: List<ImageParamSpec>`——每个模型在目录里声明自己的专属参数（key/label/类型/取值域/默认值，如 Seedream 的 `watermark`、edit 系的分辨率与张数、负向词等）；适配器按声明透传进请求体。UI 侧据此**动态渲染高级参数面板**，不认识的 preset（自定义）透传 JSON 原文。用法（张数）照旧计入 `Usage`（纯信息展示，不做任何拦截）。
- 假实现 `FakeImageModel`（CI 不打真 API）；演示模式 `MockImageModel` 返回预置样张（**bump `MOCK_ASSET_REVISION`**，it-059 教训）。
- 密钥：复用 `KeystoreApiKeyStore`（presetId 为键，与聊天 key 并存不覆盖）；mask 红线延续。
- 假实现 `FakeImageModel`（CI 不打真 API）；演示模式 `MockImageModel` 返回预置样张（**bump `MOCK_ASSET_REVISION`**，it-059 教训）。

### 阶段 A：SDK 统一目录与生图轨（libs/agent）

`ProviderCatalog`（ProviderSpec/ModelSpec、capability 与 toolCall 声明、params 声明）+ 现有 Providers 并入 + **聊天 preset 扩容（deepseek/moonshot/dashscope/volc-ark/siliconflow，纯数据）** + 生图接口/同步适配器两枚（OpenAI images 兼容 / 百炼同步）+ DashScope 异步传输（单测覆盖、UI 不放）+ Usage 扩展 + Fake。spec：agent `00-architecture`（统一目录+生图轨）、`06-decisions` **ADR-007**（统一模型目录与能力双轨、ImageModel 抽象）。

### 阶段 B：界面直达生成（W7 + W11）

- W11 **「模型连接」卡（聊天）升级**：preset 下拉扩到统一目录全量（glm/mimo/deepseek/moonshot/dashscope/volc-ark/siliconflow/自定义）；模型改目录下拉（按 `supportsToolCall` 过滤 + 「自定义 id」兜底）；Key 逻辑不变。
- W11 新增**「生图模型」卡**（与聊天卡同形制）：preset 下拉 → 模型下拉（目录中 IMAGE_GEN 模型，切换时高级参数面板随之更新）→ Key（mask 显示）→ 清除；与聊天卡选同一厂商时显示「已复用 <厂商> 的 Key」。自检差异：生图无免费 ping，保存仅格式校验，首次生成即验证。用量卡展示张数（纯信息，不拦截）。
- PrefsStore：`aiImagePresetId`（默认 `siliconflow`）/`aiImageModel`/`aiImageLastParams`（上次参数选择按模型记忆）；演示模式 `mock_` 前缀照旧。
- W7 搭配详情新增「AI 试衣」入口 → 全屏生成 sheet 四态（it-040 W5 四态语言）：装配（参考图=Outfit 单品图+人物参考照 it-074，可勾选排除，缺人物照引导）→ 描述（prompt 模板按单品名称/颜色/品类自动拼装 + 场景 chips + **高级参数面板**：按 `ImageModelSpec.params` 动态渲染，记住上次选择；自定义 preset 透传 JSON）→ 生成（事件流进度，可取消）→ 结果（候选并排，选 1 转 WebP 落 `images/` → `addEffectImage`，成品卡带「AI」角标，可重新生成）。sheet 底部一行静态小字注明照片上传去向（信息提示，无弹窗）。
- **隐私与标识从简**（自用不分发）：不做首次确认对话框/月度护栏/成本拦截；保留「AI」视觉角标 + `source` 元数据（自用区分 AI 图与手动图也靠它）。
- wardrobe spec：**ADR-030**（生图 provider 注册表与用途分轨路由）。

### 阶段 C：顾问对话流生图（W13）

- 第 5 个工具 `generate_outfit_image`（`WardrobeTools`；写的是 `effectImages` 成品图域，只读门禁原则不破）：入参 `outfitId?/itemIds[]/sceneHint?/styleHint?`；行为=取图+拼 prompt+生成+落盘+`addEffectImage`；payload 只存引用（ADR-029 同款）：`{kind:"outfit_image", outfitId, imageFile, model, costYuan}`。
- `ToolResultCard` 新增 `GeneratedImage` 卡（`parseToolResultCard` 加 kind 分支）：本地 `imageFile` 渲染（不信任模型 URL，it-054 边界）、查看大图、重试；生成中映射 typing 指示「正在生成穿搭图…」；失败态带原因（审核拒绝/额度不足/180s 超时）。
- 系统提示词补充调用时机（用户问上身效果时调用）；对话流不做生成前确认（自用，无成本拦截），工具入参允许 `extraParams` 覆盖默认参数（高级玩家路径）。

## 用户故事（US-64）

**US-64a（W7/W11，直达生成）**：作为用户，我想在搭配详情一键生成这套穿搭的上身效果图，并能调模型参数。
- Given 已配生图 Key，When 点「AI 试衣」，Then 进入生成 sheet，参考图自动装配（Outfit 单品+人物参考照）可增删
- Given 当前模型声明了专属参数（分辨率/张数/负向词/水印等），Then 高级参数面板动态渲染可调，选择按模型记忆（下次进来还是上次的值）
- Given 生成中，Then 显示进度可取消；完成出候选（张数随参数）
- Given 选定候选，Then 存入该搭配成品图（带 AI 角标），杀进程不丢

**US-64b（W13，对话流）**：作为用户，我想问顾问「这身穿起来什么效果」直接得到试穿图。
- Given 已配生图 Key，When 顾问识别到试穿意图，Then 调用 `generate_outfit_image` 工具，对话流显示「正在生成穿搭图…」
- Given 生成成功，Then 对话内出现成图卡片（本地渲染），可查看大图
- Given 失败（审核/额度/超时），Then 卡片显示原因与重试按钮，会话不中断

**US-64c（W11，连接与选型）**：作为用户，我想给顾问聊天和穿搭生图各自任选厂商与模型，一把 Key 能两处用。
- Given W11 聊天卡，Then preset 可选统一目录全量（含 DeepSeek/Kimi/百炼/豆包/硅基流动/自定义），模型下拉只列支持工具调用的（+自定义 id 兜底）
- Given W11 生图卡，Then 可选目录中生图模型（含自定义 OpenAI images 兼容），高级参数面板随模型切换更新
- Given 两卡选同一厂商，Then 只需配一把 Key，生图卡显示「已复用」；选不同厂商则各配各的
- Given 已有 glm/mimo 配置，Then 升级后偏好与 Key 原样保留，无需重配
- Given 用量卡，Then 显示聊天 token 与生图张数/估算费用（纯信息）
- Given 演示模式，Then 生图走预置样张，不出网、不计费

## 验收标准

1. 阶段 A：SDK 单测全绿（统一目录与扩容 preset 数据、两协议适配器的请求/响应映射、超时、错误分轨、参数透传；Fake 覆盖事件序列），全量回归绿，不打真 API。
2. 阶段 B：模拟器四态走查 + 真机一次端到端截图回填；W11 聊天卡扩容后至少一家新厂商（如 deepseek）真机连通；共享 Key 逻辑实测；高级参数面板至少覆盖两类 preset（声明式 + 自定义 JSON 透传）且记忆生效；AI 角标实测；演示模式样张正常且 `MOCK_ASSET_REVISION` 已 bump。
3. 阶段 C：对话流端到端实测（工具调用→进度→成图卡片→保存到搭配）截图回填；失败注入（断网/key 错）显示正确。
4. `OutfitImage` 新字段向后兼容：旧数据加载不丢、缺省=manual。
5. spec 同步：01/02/03/04/05/06（ADR-030）+ agent 00/06（ADR-007）+ 两处 CHANGELOG。

## 影响范围

- `libs/agent`：+`ProviderCatalog.kt`（统一目录，现有 `Providers` 并入 + 聊天 preset 扩容）、+`image/` 包（接口/适配器/Fake）、`Usage` 扩展；`OkHttpChatModel` 传输不动。
- wardrobe：`AppContainer`（imageModel 工厂/MockImageModel）、`PrefsStore`（3 新键，含 `aiImageLastParams`）、`SettingsScreen/ViewModel`（聊天卡升级 + 生图卡 + 用量）、搭配详情生成 sheet（新文件，含动态参数面板）、`WardrobeTools`（+1 工具）、`ChatResultCards`（+1 卡型）、`Entities.OutfitImage`（+3 可选字段）。
- 不动：数据包格式（AI 图随 images/ 目录自然导出）、聊天轨行为、cutout/store/sync。
- spec：01（US-64a/b/c）、02（W7/W11/W13 增量）、03（OutfitImage 字段）、04（生图轨分层）、05（生成 sheet 视觉/动效）、06（ADR-030）；agent 00/06（ADR-007）；CHANGELOG×2。

## 拍板记录与默认执行项

**已拍板（2026-09-30，Leo）**：
- ① 应用主要自用、价格不敏感、不分发线上用户——❌ 砍掉：月度护栏、生成前确认开关、成本拦截、首次隐私弹窗（sheet 内一行静态提示替代）；✅ 新增：**模型专属参数透出**（`ModelSpec.params` 声明式 + 生成页动态高级面板 + 按模型记忆 + 自定义 preset JSON 透传；对话流工具允许 `extraParams` 覆盖）。
- ② **provider 抽象覆盖聊天轨**——聊天 preset 扩容（deepseek/moonshot/dashscope/volc-ark/siliconflow）并入统一 `ProviderCatalog`，一把 Key 双轨共用，聊天下拉按 tool call 过滤。

**默认执行项**（与拍板方向一致，如有异议随时改）：
1. 默认主力排序照报告 §2.3：百炼 edit-plus 主力 / 硅基流动尝鲜 / 火山进阶多件；cogview-4「灵感图」不进 v1。
2. 备援回退 v1 不做，接口留位。
3. `OutfitImage` 三字段（source/model/prompt）保留——参数透出与 AI 图区分都靠它。
4. aitryon（URL-only 专用试穿）放二期，SDK 传输 v1 实现并单测、UI 不放。
5. 对话流生图：顾问直接调用，无确认步骤。

## 实施记录

**2026-09-30 三阶段一次落地：**

**阶段 A · SDK（libs/agent）**
- 新增 `ModelCatalog.kt`：`ProviderSpec`/`ModelSpec`/`Capability`(CHAT/IMAGE_GEN)/`ImageProtocol` 三枚举/`ImageParamSpec`/`ParamType`；内置 glm（并入 cogview-4）/mimo/mimo-tp/deepseek/moonshot/dashscope（聊天 qwen3 系 + 生图 edit-plus/-edit/aitryon-plus）/volc-ark（豆包聊天 + seedream-4-0/5-0-lite）/siliconflow（聊天聚合 + Kolors/Z-Image-Turbo/Qwen-Image-Edit）；聊天 preset 扩容全为 OpenAI 兼容纯数据，`OkHttpChatModel` 零改动。
- 新增 `image/` 包：`ImageModel` 接口（`Flow<ImageGenEvent>`：Started/Progress/Completed/Failed，Failed 后流正常结束——与 AgentEvent 同语义）；`OkHttpImageModel`（同步双协议：OpenAI images 兼容 `{base}/images/generations` + 百炼 `multimodal-generation`；宽松解析 data[]/images[]/output.choices 三形响应；输出 URL 24h 失效 → 适配器内下载字节；长超时客户端 read 170s）；`DashScopeTaskImageModel`（异步任务型：`X-DashScope-Async` 提交 + 轮询 tasks/{id}，aitryon 入参按位映射 person/top/bottom；v1 传输+单测就绪、UI 未放）；`FakeImageModel`。
- 声明式预检（Rectifier）：图数超 `inputImages` 档位、base64 传给 URL-only 模型 → 本地 Schema 拦截不发请求。
- `Usage` +`images` 计数（文件记账向后兼容）；`ProviderSpec.customImage` 独立 id `custom-image`（与聊天 custom 分属不同 Keystore 槽位，Key 不互相覆盖）。

**阶段 B · wardrobe 直达路径**
- `data/gen/OutfitImageGenerator`：连接解析（Prefs+Keystore，同厂商一把 Key 双轨共用）→ 参考图装配（人物参考照在前 + 单品图，WebP→JPEG q90 base64，超档位截断）→ 事件流收集 → 候选字节在内存 → `putPackageImage` 落盘 → `addEffectImage(OutfitImage(source=ai, model, prompt))`；高级面板取值按模型记忆（`aiImageLastParams` JSON）；演示模式一律 `MockImageModel`（不出网，回放首张参考图字节——与聊天轨「有 Key 真连」不同，简化取舍已注记）。
- W11：聊天卡厂商下拉目录化（模型下拉按 `supportsToolCall` 过滤，deepseek-reasoner 正确被排除）；新增生图卡（厂商=有生图模型者+自定义、模型下拉带档位与参考价、同厂商 Key 共用标注、保存不做付费自检）；用量条目显示生图张数。
- W7：顶栏菜单项 + 「复制长图」旁等宽描边按钮双入口；`OutfitGenerateSheet` 四态（装配瓦片勾选/预填描述+场景 chips/高级参数面板五型控件/进度可取消/候选挑选）；轮播中 source=ai 成品图左上「AI」角标。
- 实施修正：**未新增 mock 资产**（假实现回放输入参考图），`MOCK_ASSET_REVISION` 无需 bump（对提案的偏差，已注记）。

**阶段 C · 对话流**
- `WardrobeTools` 第 5 个工具 `generate_outfit_image`（可选注入 `imageGen`，未注入不注册——旧调用方零影响）；入参 outfitId/itemIds(逗号分隔)/sceneHint，缺参友好报错回喂。
- `ChatViewModel.runAdvisorImageGen`：解析穿搭/单品 → BuildTryOnPrompt 共用口径 → 生成取首张保存 → payload `{kind:"outfit_image", outfitId, imageFile, model}`（ADR-029 只存引用）；工具条进度文案「生成中（约 20~60 秒）…」；系统提示词补调用时机约束。
- `ChatResultCards`：`ToolResultCard.GeneratedImage` 新卡型（重构：total/ids 从接口下放到 Items/Outfits；解析入口新增 outfit_image 分支）；成图卡本地渲染 + 点进 W7 + 穿搭删除占位。
- 数据层：`OutfitImage` +source/model/prompt 三可选字段（旧 JSON 缺字段读默认 manual）；`OutfitRepository.addEffectImage` 增 OutfitImage 重载（字符串变体接口默认委托），两实现同步。

**2026-09-30 二次修订（Leo 反馈「定位应为生成效果图录入成品图，而非试衣」）**：全局更名 AI 试衣 → 生成效果图（sheet 标题/W7 按钮/W8 角标/对话工具标签/toast「效果图已录入成品图」）；入口从 W7 单点扩为三处——W1 搭配页底部第三按钮（未保存组合直生成、保存自动建穿搭、愿望伪 id 过滤）、W8 卡组卡右上角「✨生成效果图」胶囊（录入成品图角标同语言）、W7 菜单+按钮保留；sheet 的 outfit 参数改可空。模拟器实测 W1 三按钮与 W8 角标渲染正确。

**2026-09-30 三次修订（Leo 反馈「两个按钮不可接受，合并」）**：撤 W1/W7 页面上的独立生成按钮（W1 恢复 复制长图+保存这套 两按钮），生成入口收进出图面板——ExportSheet 动作区顶部整行「✨ AI 生成效果图」主按钮（下方 复制/存相册/分享 三动作不变），点击关导出面板接生成 sheet；W8 卡片角标与 W7 菜单项保留。模拟器实测 W1 两按钮布局与面板内生成按钮渲染正确。

**2026-09-30 四次修订（Leo 反馈）**：①ExportSheet 移除「复制长图」按钮，存相册升默认主动作（it-066 回程提示条随之废止——外置生图链路正式下线）；②W1/W7 外露按钮更名「生成预览穿搭」（AutoFixHigh 图标），面板标题「导出生图素材」→「穿搭预览」；③确认包内零内置 Key（Key 仅存设备 Keystore，跨同签名升级保留）。

**2026-09-30 五次修订（Leo 反馈×4）**：新增 `rememberImageGenReady(vm)`（produceState 读 generator.connection()）——未配置生图连接时三处 AI 入口全部隐藏（ExportSheet 主按钮换为一行弱提示「到 设置 → 生图模型 配置后可用」、W8 卡片角标不渲染、W7 菜单项不显示），配置后自动出现。

**2026-09-30 六次修订（Leo 反馈×5：抽屉下滑交互怪——按钮区滚动/不连贯/分层）**：三根因齐修——①生成 sheet 由整列滚动改为「内容滚动区(weight1f) + 钉底动作栏(Surface)」两段式（各态按钮钉住不随滚，ExportSheet 同构）；②出图面板 → 生成 sheet 改顺序切换（先退场 delay 280ms 再进场，消除两个 ModalBottomSheet 动画叠加的分层残影）；③imageGenReady 就绪态提升到页面级 produceState 以参数传入（消灭 sheet 打开瞬间动作栏高度跳变）；ExportSheet 另有三处次要调用（聊天导出/单品详情/心愿）不传 onGenerate 时按钮与提示均不显示。模拟器实测：面板→生成顺序切换无残影、生成 sheet 按钮钉底。

## 验证记录

**2026-09-30：**
1. **单测**：agent 93 测全绿（新增 24：ModelCatalogTest 11 + OkHttpImageModelTest 10 + DashScopeTaskImageModelTest 3——含三协议 wire 断言、透传参数、错误分轨、本地预检、b64 直返、异步任务轮询）；wardrobe 106 测全绿（新增 7：BuildTryOnPromptTest 3 + ToolResultCardTest 增 4——outfit_image 解析契约、生图工具按需注册/缺参报错）。全部 CI 路径零真 API（Fake/MockWebServer）。
2. **构建**：`:app:assembleDebug` 成功。
3. **模拟器走查**（emulator-5554 wardrobe_test，演示模式，截图入 `reports/2026-09-30-wardrobe-imagegen-research/screenshots/`）：
   - W11 生图卡渲染正确（默认硅基流动 · Kolors/Kolors · 文生图 · 免费，参考价展示）；假 Key 保存后 mask `demo***-key` 显示正确；聊天卡厂商下拉目录全量。
   - W7「AI 试衣」入口 → sheet 未配置态（引导文案+知道了）→ 配 Key 后装配态（模型行/纯文生图防御提示「当前模型不吃参考图」/五件单品预填描述/场景 chips）→ 生成（mock 即时）→ Done 候选态「挑一张保存」→ 保存 → sheet 关闭、W7 轮播出现带「AI」角标新成品图、toast「AI 效果图已保存」。**演示模式全链路闭环**。
4. **待真机/体验包**：W13 对话流生图实测（顾问工具→成图卡，需聊天模型配合）；W7 生成 sheet 的真机手势体验（模拟器 `input swipe` 极易触发 ModalBottomSheet 关闭 + uiautomator 抓不到 Compose 节点坐标，UI 自动化在装配态后无法续推——演示模式闭环已覆盖同一 sheet 代码，live UI 留 Leo 真机）。

**2026-09-30 补：硅基流动 live 实测（Leo 提供 Key 自测）**

1. **wire 验证（curl/urllib 直打真端点）**：端点 `/v1/images/generations` ✓；`image` 字段**必须 data URI 前缀**（纯 base64 报 500）——适配器当前形态正确 ✓；响应 `images[].url` ✓；S3 预签名 URL 匿名下载 ✓（1.5MB PNG）；**`image` 只收字符串（单图），数组报 400 `image should be a string`** → 目录修正。
2. **目录修正**：`Kolors/Kolors`→`Kwai-Kolors/Kolors`、`Z-Image-Turbo`→`Tongyi-MAI/Z-Image-Turbo`（`/v1/models` 实查）；`Qwen/Qwen-Image-Edit(-2509)` 硅基通道 `inputImages` 标 1..1 并注明「多图请走百炼通道」——同一模型不同厂商能力不同，由 ModelSpec 数据承载（ADR-007 设计的直接兑现）。
3. **JVM live 冒烟**（`LiveImageSmokeTest`，App 同款 `OkHttpImageModel` 代码，Key 走 `SF_KEY` 环境变量、缺省跳过——CI 红线不变）：真实生成 1 张 372KB PNG，44.7s 端到端 ✓。
4. **模拟器（真实模式）UI 实测**：W11 生图卡真 Key 保存（mask `sk-a***jyba`）✓、聊天卡已有 GLM Key 两轨两厂商并存 ✓、W7 sheet 装配态 live 渲染（模型行/人物+6 衣物/超限红字「已选 7 / 上限 1」本地预检）✓——装配后的触发生成因模拟器手势限制未能在 UI 内完成，由 3 的同码 JVM 冒烟覆盖。
5. **live 联调补修（同日，403d963）**：①「取消」不中断 HTTP（阻塞 execute 不响应协程取消，取消后请求后台跑完并计费）→ 两适配器改 `Call.await()` 桥接，取消即断；②运行态增「已等待 Ns」计时，文案改「通常 1~2 分钟，高峰更久，约 3 分钟自动失败」（原「20~60 秒」与实测不符）。
6. **模拟器生图超慢根因（环境，非代码）**：宿主一云梯 TUN fake-ip 劫持模拟器流量（模拟器解析 open.bigmodel.cn → 198.18.0.10），SF 的 HTTPS 经代理绕行境外数分钟不归；Mac 直连实测同模型同图 29.6s 出图。处置：代理规则给 `*.siliconflow.cn` 加直连，或暂停 TUN，或真机移动网络实测。
