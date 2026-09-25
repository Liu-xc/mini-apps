# it-041 · AI 模型接入（libs/agent M3）——设置页 + 穿搭顾问对话

- **状态**：**阶段 A 已实现并验收（2026-09-25，/goal 批准实施）**——构建+单测绿、模拟器端到端自检通过，见文末验证记录；**阶段 B（W12 对话页）进行中**
- **前置**：libs/agent **M2**（it-002 agent loop）已完成；**M0 spike**（真调校准）建议先行，不阻塞阶段 A
- **关联**：新增 **US-41a~d**；新增线框 **W11（设置页）/ W12（对话页）**；新增 **ADR-024**（INTERNET 权限 + BYOK 接入定位修订）
- **SDK 侧**：libs/agent it-001 影响范围原文「消费 app（M3 接入时）：settings includeBuild + 设置页/对话页，届时另开 app it-XXX，UI 走 DESIGN.md」——本迭代即该接棒点

## 背景与动机

00-overview 的核心价值流目前有一段**人工搬运**：「切换到生图 Agent（豆包/ChatGPT/Kimi 等）→ 粘贴长图与文案 → 生成效果图」。而「查我的衣橱、给穿搭建议」这类对话能力，libs/agent（BYOK 直连、单传输层、GLM/MiMo 一等公民）已经把底座铺好：M1 传输层 32 单测绿，M2 loop（it-002）补上工具调用与会话。本迭代把它们接进 wardrobe：

- **设置页**（US-A1 映射）：选厂商、粘 API Key、连通性自检、看用量
- **对话页**（US-A2/A3 映射）：流式穿搭顾问，agent 能**真的查衣橱**（工具调用）再回答

### 必须先拍板的定位变化

**衣橱当前没有 INTERNET 权限**（Manifest 仅 POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED），纯离线是既有定位。BYOK 直连厂商意味着：

1. Manifest 新增 `android.permission.INTERNET`；
2. 00-overview「数据全部存本机」表述修订为「数据全部存本机；唯一联网场景为可选的 BYOK 模型直连（用户自备 Key，ADR-024）」；
3. 仍然**无账号、无服务端、无遥测**——联网只是点对点调厂商 API。

→ 见「待确认决策 1」，这是本提案的头号拍板项。

## 用户故事

- **US-41a · 配置模型连接**：作为用户，我在设置页选择厂商（GLM / 自定义）、粘贴 API Key、点「连通性自检」看到 ✓，即可使用；Key 加密存本地，导出不含 Key，界面只显示 mask。
  - 验收：自检失败给可读分类错误（401=Key 无效 / 429=限流 / 网络=代理或断网，接 `AgentError` 分类）；自检成功给一次 Confirm 触感（DESIGN.md §4）；Key 落盘 Keystore AES-GCM 密文。
- **US-41b · 流式穿搭顾问**：作为用户，我在对话页问「配一套通勤装」，agent 流式回复，且会先调工具查我的衣橱再回答（不是空编）。
  - 验收：打字机输出 +「思考中」态（ThinkingDelta 有独立 UI 态）；`ToolRequested/ToolFinished` 可展开查看调了什么；取消即停、半截消息不脏；错误呈现分类文案 + 重试按钮。
- **US-41c · 会话持久**：作为用户，杀掉 app 重开，对话还在，能继续聊。
  - 验收：SessionStore 落盘恢复；`ContextPolicy` 超窗裁最老（用户无感）。
- **US-41d · 用量可见**：作为用户，我在设置页看到本机累计 token 用量（厂商×模型）。
  - 验收：数字有 count-up 过渡（DESIGN.md 红线⑨，复用 W9 CountUp 组件）。

## 方案

### 接线（一次性）

| 文件 | 改动 |
|---|---|
| `wardrobe/settings.gradle.kts` | +`includeBuild("../libs/agent")` |
| `wardrobe/app/build.gradle.kts` | +`implementation(libs.leo.agent)`（坐标 `leo-agent` 已在根版本表，无需改 toml） |
| `AndroidManifest.xml` | +`<uses-permission android:name="android.permission.INTERNET" />`（ADR-024） |

### W11 设置页（阶段 A，仅依赖 M1）

- **入口**：W3 衣橱页标题行 + ⚙ 图标（与既有 📊 / 🌟 同排）；路由 `settings` 登记进 `Routes` 与 NavHost。
- **顺手修**：`WardrobePackages.kt:85` 文案「请先在设置中退出演示模式」目前指向一个不存在的页面——设置页落地后该文案有了真实落点（演示模式状态行放设置页）。
- **内容**（上→下）：
  1. **模型连接卡**：厂商单选（GLM 智谱 / 自定义 BaseURL+模型名）、Key 输入（mask 展示已存值、「连通性自检」按钮、保存）。
     - **MiMo 首版不列**：`Providers.mimo.baseUrl` 为空占位，M0 校准前构造即抛（it-001 记录）；M0 回填后免改代码自动出现（preset 数据驱动）。
     - 自检 = `ChatModel.complete()` 发 1-token ping，按 `AgentError` 分类给可读错误。
  2. **用量卡**（US-41d，阶段 B 随 loop 的 ledger 一起上）：厂商×模型累计 prompt/completion tokens，count-up 呈现。
  3. **关于/状态行**：当前模式（正常/演示）。
- **Key 安全三条红线**（SDK 架构 §8 + ADR-003）：① 数据包导出永不带 Key（PackageCodec 本就不碰 prefs，加接口级断言测试）；② **演示模式永用 `InMemoryApiKeyStore`，不挂真 Key**；③ 日志/异常只出 `maskApiKey`。

### 阶段 A · AppContainer 挂载

- `apiKeyStore: ApiKeyStore`——app 层 Keystore AES-GCM 实现（ADR-003 归 app 层的那半个），注入 `OkHttpChatModel`。
- `chatModel` 惰性 getter（与 `cutoutEngine` 同款「构造零副作用、首用才建」）；demo 分支 → `InMemoryApiKeyStore` + 见阶段 B 的 FakeChatModel。
- 未配置 Key 时设置页可进、对话入口置灰提示「先在设置里配置模型」。

### W12 对话页 + 工具（阶段 B，依赖 M2）

- **入口**：W11 设置页「开始对话」行（MVP 单入口；后续可加 W1 的快捷入口，不在本迭代）。
- **结构**：新 `ui/chat/ChatScreen.kt`(W12) + `ChatViewModel.kt`（域 ViewModel 先例 = RecapViewModel，**不往 AppViewModel 堆**）。
- **消息流**：`AgentRunner.run(history): Flow<AgentEvent>` → StateFlow 渲染；TextDelta 打字机、ThinkingDelta 折叠「思考中」、ToolRequested/Finished 渲染成可展开的「已查衣橱：品类=上装（8 件）」小条、Failed → 分类错误气泡 + 重试（续跑持久化会话）。
- **注册给模型的工具**（只读，全部走 `repo` 快照，零写路径）：
  | 工具 | 作用 | 返回 |
  |---|---|---|
  | `search_items` | 按品类/标签/颜色/关键词查单品 | 精简列表（id、名称、品类、颜色、标签） |
  | `search_outfits` | 按标签/单品反查已存穿搭 | 精简列表（名称、含单品、标签） |
  | `wear_stats` | 回顾数据摘要 | 各品类件数、近 30 天出勤、闲置清单（≥90 天未穿） |
  | `current_person` | 当前角色与形象参考照有无 | 角色名、参考照状态 |
- **system prompt**：衣橱顾问人设 + 数据只读声明 + 输出格式（建议引用具体单品名）。
- **会话**：单会话（全局一条对话线），`FileSessionStore` 落盘；杀进程恢复（US-41c）。
- **演示模式**：挂 SDK 的 `FakeChatModel`（`src/main/testing` 包，脚本回合：文本/工具回喂/错误）——**对话页在演示模式下离线可走查**，也是走查工作流的福音。

### UI 走 DESIGN.md（新页面红线自查）

- 版式：W12 页标题衬线、消息正文 Sans 15sp、说明文字不进 11sp Label；W11 卡片 20dp 圆角、屏幕边距 20dp。
- 动效：消息追加 smooth（禁每条 bouncy）、打字机是流式渲染非入场编排、W11 二次进入走快路径（>200ms 编排仅首屏）；一屏自主运动 ≤1。
- 触控：发送/自检/删除 Key 等 ≥44dp 推荐 48dp；emoji 不作功能图标。
- 破坏性：「清除 Key」需二次确认（或 snackbar 撤销，二选一不可都无）。
- 对比度：ink/paper ≥7:1、白字/accent ≥4.5:1（it-038 token 语义）。

## 分阶段与依赖

```
阶段 A（只依赖 M1，可立即开工）
  接线 + INTERNET(ADR-024) + ApiKeyStore(Keystore) + W11 设置页 + 自检 + W3 ⚙ 入口
  依赖：无（真调自检需要 GLM Key → 建议先跑 M0 spike；没 Key 也能开发，自检失败路径可走查）

阶段 B（依赖 it-002 M2）
  AgentRunner 接线 + W12 对话页 + 4 工具注册 + 会话持久 + 用量卡(US-41d)
  依赖：M2 合入；演示模式 FakeChatModel 走查不依赖真 Key
```

## 验收标准（整体）

1. 阶段 A：设置页全流程走查——选厂商、粘 Key、自检 ✓/分类错误、重启后 Key 还在（mask 展示）；导出数据包产物无 Key；演示模式下 Key 输入不可用。
2. 阶段 B：真实提问「配一套通勤装」→ 工具事件可见 → 回复引用了真实单品名；杀进程重开对话恢复；断网提问 → Network 分类错误 + 重试。
3. 权限与定位：INTERNET 权限仅 BYOK 对话使用；00-overview 修订、ADR-024 落档；除模型 API 外无任何网络请求（可断言：设置页/对话页外全程无联，走查抓包或代码审查记录）。
4. specs 同步：01（US-41a~d）、02（W11/W12 线框 + 路由清单，顺手勘正 04-architecture 路由表 W9/W10 归属笔误）、04（ui 目录表 + composite build 行 + 路由）、05（如新增组件）、06（ADR-024）、CHANGELOG。
5. UI 基线：DESIGN.md 红线自查清单逐条过（见上节），模拟器走查截图落验证记录。

## 影响范围

- 修改：`AndroidManifest.xml`、`settings.gradle.kts`、`app/build.gradle.kts`、`di/AppContainer.kt`、`MainActivity.kt`（Routes + NavHost）、`ui/wardrobe/WardrobeScreen.kt`（⚙ 入口）、`WardrobePackages.kt`（文案落点）
- 新增：`ui/settings/SettingsScreen.kt`(W11)、`ui/chat/ChatScreen.kt`(W12) + `ChatViewModel.kt`、`data/prefs/KeystoreApiKeyStore.kt`、工具注册类
- specs：00（价值流/边界修订）、01、02、04、05、06（ADR-024）、CHANGELOG
- 不动：libs/agent 的 M1 公共签名、数据模型、store/cutout/carddeck、eats

## 待确认决策（Leo）

1. **INTERNET 权限 + 定位修订（头号）**：BYOK 直连必然联网，00-overview「无联网」要改写（ADR-024）。你此前把「云端同步」划在范围外——本提案**不做云同步、不做遥测、无服务端**，只是点对点调厂商 API，但联网这层皮要拍板。
2. **M0 / Key**：阶段 A 真调自检需要一个 GLM Key（`tools/spike-glm.sh` 手动跑一次，钥匙串授权或环境变量）；MiMo 未注册则首版只上 GLM + 自定义。Key 没到位前我可以先开发、用失败路径和 FakeChatModel 走查。
3. **入口层级确认**：W3 ⚙ → W11 设置页 → W12 对话页（对话只挂设置页一处）。若想让对话更顺手，可加 W1 顶栏快捷入口——是否进本迭代。
4. **会话粒度**：我推荐全局单会话（MVP，自用够了）；备选按角色分会话（多角色各聊各的，复杂度 +）。
5. **阶段拆分**：A/B 两阶段同一 it 编号、两次提交（先例：it-016）——是否同意。

## 验证记录

### 阶段 A · 设置页与连通性自检（2026-09-25 完成）

- **构建/测试**：`./gradlew assembleDebug testDebugUnitTest` BUILD SUCCESSFUL；libs/agent 侧 `./gradlew test` **63/63 绿**（含 it-002 M2 回归）。最新包已装 `emulator-5554`。
- **模拟器端到端实录**（emulator-5554，AVD wardrobe_test）：
  1. W3 标题行 ⚙（941,200 热区）→ W11 打开，白顶栏沉浸、卡片渲染符合 DESIGN.md（截图 [reports/2026-09-25-it041-stage-a/](../../../reports/2026-09-25-it041-stage-a/)）；
  2. 演示模式下 Key 输入禁用 + 「演示模式不保存 Key」提示 ✓ → 页内「退出演示模式」→ 确认弹窗 → 进程重启 → 状态变「正常 · 本机真实数据」✓；
  3. 厂商「智谱 GLM」、模型下拉切 `glm-4-flash`（M0 校准：plan 仅覆盖免费档）、粘贴 Key → 「保存并自检」→ **「✓ 连通正常 · glm-4-flash」** + 确认触感 ✓；
  4. 重启进程后 Key mask 仍在（`已保存 1b0d***EW0f · 留空保持不变`）→ Keystore 密文持久化 ✓；「清除 Key」按钮按需出现 ✓；
  5. 错误路径实测：网络断开时显示分类文案「网络不可达：Unable to resolve host …」（AgentError.Network → userMessage）✓。
- **走查中发现并修复**：① `OkHttpChatModel.complete` 在 Main 线程同步 execute → `NetworkOnMainThreadException`（M1 遗留，`stream` 有 flowOn(IO) 而 `complete` 漏切）→ SDK 补 `withContext(Dispatchers.IO)`；② 环境侧：模拟器 default network 丢失（Active default: none、路由表缺省）→ 重启模拟器恢复，与应用无关。
- **红线自查**：① agent_secrets 独立于数据包导出链路（PackageCodec 只读 wardrobe.json+images）✓ ② 演示模式 InMemoryApiKeyStore + UI 禁用 ✓ ③ UI 只显 mask、失败日志仅异常堆栈无 Key ✓（全仓 grep 无明文 Key）。
- **specs 同步**：00（联网边界句）、01（US-41a~d）、02（W11 注记 + W1–W12 编号）、04（ui/settings、路由、composite、权限表）、06（ADR-024）、CHANGELOG。
- 走查备注：模拟器 screencap 通道重启后短暂返回旧帧（连续两帧相同 + uiautomator 新鲜 dump 交叉验证后触摸刷新恢复），后续走查如遇截图不动先 touch 一下再截。
