# it-081 · 架构审查落地：精简重构（arch-review-refactor）

- **日期**：2026-10-03
- **前置**：[全仓架构深度审查报告](../../../reports/2026-10-03-wardrobe-arch-review/)（五分册，P0×0 / P1×14 / P2×30+，编号 D/A/U/B/O 沿用该报告）
- **拍板**：Leo `/goal`——「review 完成之后，精简冗余设计，按最终方案实施重构；重构后模拟器自测，UI、功能不受损（底线）」
- **范围**：libs/agent + wardrobe 数据层/AI 接线/UI 修正 + CI 门禁；**一次迭代分逻辑提交**

## 背景

五分册审查确认架构骨架健康（分层零违规、SSOT/原子写/Key 红线/测试选点均达标），债务集中在边界行为（静默降级、跨会话状态、取消传播）与若干弱类型/死代码。本迭代把其中**小切口、高价值、低回归风险**的修复一次清掉；所有需要大改动的结构性建议**明确砍除**（见「精简决策」），留待真有痛点时再动。

## 精简决策（砍除清单——本迭代不做，含理由）

| 砍除项 | 出处 | 理由 |
|---|---|---|
| ID 值类型化（value class 全铺）/ WishSlotId | oo-modeling §2.3 | 改动面横跨全部实体与序列化边界，自用规模收益低；`wish:` 伪 id 已有 createOutfit 过滤兜底 |
| AppViewModel 拆 WishlistViewModel、SettingsScreen 拆分 | U-4/U-7 | 大型结构重构，回归风险与「UI 不受损」底线冲突；本迭代以死 API 清理减负即可 |
| agent 双轨终态语义统一（ChatModel 异常式→事件式） | O-3 | 行为变更影响两处 collect 模式与既有测试，收益是一致性审美，非缺陷 |
| 数据包 apply 全程持锁 | D-9 | 导入为低频操作且 UI 已有 Running 遮罩；分钟级持锁会阻塞一切写，正确解法（快照基线 diff）复杂度不成比例 |
| OutfitImageGenerator 抽纯函数补测 | B-5 部分 | 校验逻辑仅一行 if，抽函数+测试属仪式化；以模拟器端到端验证兜底（仅保留 JpegXmp/PickRandomOutfit 两个白捡测试） |
| 流式渲染 StreamingBubble/delta 节流 | U-6 | 现状可用，仅低端机可感；不动 |
| 参考长图双重 JPEG 编码消除 | A-14 | 现代设备无 OOM 实据，改 compose 返回值影响导出链路 |
| MockChatCache 版本 bump | A-15 | it-060 后语料未再变更，无事可 bump |
| 会话 index 移出会话目录 | A-10 部分 | 埋雷未触发（sessionId 全 uuid），只做损坏隔离不做目录迁移 |
| 重试框架/stream_options usage | A-5/A-6 | 自用手动重试够（审查「明确不建议」清单已含） |

## 实施清单（保留项）

### A. libs/agent（6 项）
1. **A-3+O-4 聊天轨取消传播**：`Call.await()`（enqueue + invokeOnCancellation）抽为 internal 共享（生图两适配器逐字重复的实现一并收敛），`OkHttpChatModel.complete/stream` 改用之——连接/DNS/响应头阶段取消即断，不再后台跑完计费。
2. **A-4 SSE 截断检测**：`Sse.kt completed()` 对「已收 delta、无 finishReason、未收 [DONE]」抛可恢复 AgentError，半截回答不再静默落会话。
3. **A-7 生图适配器 catch 放宽**：`AgentError/IOException` → `Exception`（CancellationException 除外），运行时异常归一为 Failed 终态，不再击穿 collector。
4. **O-2 ImageModel.of(spec) 工厂**：按 `imageProtocol` 路由 OkHttpImageModel/DashScopeTaskImageModel，「选实现类」不再泄漏给消费方。
5. **O-1 双事实源 assert**：`ProviderSpec` init 校验 chatPreset.id==id 且双清单模型 id 一致（存在 CHAT 条目时），补契约测试。
6. **O-5 备忘**：resolution 与 params.size 的覆盖优先级在 KDoc 注明（不改行为）。

### B. wardrobe AI 接线（4 项）
1. **A-1 refs 空硬校验**：`OutfitImageGenerator.run()` 入口——连接的模型吃参考图（inputLimit>0）而 refs 装配为空 ⇒ 显式 Failed（AgentError.Schema），终结 it-079 形态的静默降级。
2. **A-2 UI 过滤**：SettingsScreen 与 OutfitGenerateSheet 两处模型下拉过滤 `DASHSCOPE_ASYNC_TASK`（可选即可用）。
3. **A-13 参数非法回退**：`ImageParamEncoder` 非法值回退 `spec.default`（不再 mapNotNull 静默丢参）。
4. **A-12 会话切换先停**：`ChatViewModel.open()` 先 `stop()` 再切换。

### C. 数据层（6 项）
1. **D-1 WishOutfit 状态机修复（本迭代核心）**：
   - `cleaned()` 保留条件改 `wishItemIds.isNotEmpty() || itemIds.isNotEmpty()`（「购齐待升级」为合法态）；
   - `deleteWishItem` 级联语义与 purchase 统一：只移除引用、**不再删除**「变空」的心愿穿搭（其 itemIds 段仍为已有件组合，用户可升级或手动删）；
   - `MockWardrobeRepository` 同步对齐；spec 03 不变量句同步修订；
   - 回归测试：购入→cleaned()→实体仍在→promote 可用；删最后一件愿望件→实体保留。
2. **D-2 损坏隔离（精简版）**：SnapshotStore load 双失败时把主/bak 改名 `.corrupt-<ts>` 隔离再返回 default，暴露 `loadFailed` 信号；AppContainer→AppViewModel 启动 toast 一次（不再无声覆盖最后恢复线索）。FileSessionStore 损坏 JSON 同款 `.corrupt` 隔离（A-9 保留部分）。
3. **D-3 precheck 防护**：包内图片读取 runCatching，损坏计入 reasons（不再崩溃）。
4. **D-11 超大图拒绝**：precheck 用 inJustDecodeBounds 校验包内图片边长上限（8192），超限计入 reasons；putPackageImage 按 inSampleSize 预降采样后转码。
5. **D-5 锁内检查**：ensureDefaultPerson/addWearLog/purchaseWishItem/promoteWishOutfit 四处存在性检查挪进 mutate transform（消除 TOCTOU）；ADR-002 措辞同步（「单线程 Dispatcher」→「Mutex 串行」）。
6. **D-8 死代码删除**：WardrobePackages MERGE 分支恒空的旧文件回收逻辑删除（误导维护者）。

### D. UI 正确性（5 项）
1. **U-1**：ItemDetailScreen/OutfitDetailScreen 组合期 `onBack()` 移入 `LaunchedEffect` + 轻量占位。
2. **U-2**：ItemEditScreen 表单态 `rememberSaveable` 化（含已导入文件名，联动孤儿文件问题）；WishEditSheet/OutfitDetailScreen 编辑态顺手同款。
3. **U-3 精简版**：`OutfitThumb` 改为参数传入预派生 outfit+items（消除列表 item 内的整库流 collectAsState）；不动 domain 实体注解（domain 零 Android 依赖红线优先于 @Immutable）。
4. **U-5**：saveWishItem/updateOutfitItems/createOutfit/duplicateOutfit 异常路径补 `onDone(false)`（launchSafely 增加 onError 形参）。
5. **死 API**：AppViewModel 删 `exportSelections/exportSelectionsReady/setExportSelections`（it-061 后无消费方）、`repo` 改 private（grep 已证零使用）。

### E. 门禁/文档/测试（4 项）
1. **B-1**：CI 增加 `libs/agent` 单测步骤。
2. **spec 回填**：04-architecture（W6 FullscreenSheet、SettingsScreen→DemoMode 例外登记）、03（WishOutfit 不变量句）、ADR-002（Mutex 措辞）、catalog（store 0.2.0、七构建注释）。
3. **测试补强**：JpegXmp（纯 JVM）+ PickRandomOutfit（Random 注入现成）+ D-1 回归×2。
4. **CHANGELOG + 分逻辑提交**。

## 影响范围

libs/agent（OkHttpChatModel/image 两适配器/Sse/ImageModel/ModelCatalog/ProviderPreset + 测试）；wardrobe data（repo/mock/packages/prefs）+ ui（chat/records/detail/settings/wardrobe/wishlist + AppViewModel）+ MainActivity（无）；CI workflow；specs 03/04/06 + CHANGELOG。

## 验收标准

- [ ] libs/agent 全部测试绿（含新增契约测试）；wardrobe 全量单测绿（含 D-1 回归）
- [ ] 模拟器（wardrobe_test AVD）全页面走查：W1–W13 逐页 UI 与既有行为一致；演示模式生图闭环、聊天对话、打卡/评论/删除、心愿购入→升级全链路无回归（**底线：UI、功能不受损**）
- [ ] 真实路径抽查：W11 模型下拉不再出现 aitryon-plus；聊天发送中退出页面→请求即断（logcat 无后台完成痕迹）
- [ ] spec 与代码一致（铁律②）

## 验证记录（2026-10-03）

### 单元测试
- libs/agent：全绿（含新增 `聊天双清单一致`、`协议路由工厂`、`SSE 中途断开按可恢复错误抛出` 三例；两处既有 Completed 用例随新语义显式传 `sawDone=true`）
- wardrobe `testDebugUnitTest`：全绿（112 → 122，新增 D-1 回归 ×2、JpegXmp ×2、PickRandomOutfit ×3；`invalid declared numeric parameter` 用例语义翻转为「回退 default」）
- 白捡测试抓到真 bug：**JpegXmp 段长 off-by-2**——JPEG 段 LEN 字段按规范含自身 2 字节，it-002 以来一直少写（按 LEN 跳段的解析器会错位 2 字节）。已修（`payloadLen + 2`）并锁定回归。

### 模拟器走查（wardrobe_test AVD，0.5.0.137-dirty，演示+真实双模式）
- **W1–W13 逐页**：W1 搭配（槽位/混入心愿/随机）✓ · W8 记录（卡组 1/8、标签筛选、缩略卡 U-3 改造后渲染正常）✓ · W7 详情（双 Tab/清单/打卡+撤销/评论/生成入口）✓ · W3 衣橱（36 件网格、筛选、长按菜单）✓ · W5 详情（去背景状态条/补抠/相关穿搭/评论新增）✓ · W10 心愿（列表/购入表单）✓ · W9 回顾（统计/品类分布/引导）✓ · W11 设置（双卡/用量/外观/演示开关）✓ · W12 列表 + W13 会话（消息回放 + 工具徽章，A-12 改造后正常）✓ · W4 编辑（表单/品类 chips/更新）✓
- **写路径实测**：打卡→toast+按钮态翻转→撤销 ✓；评论新增→列表即时出现 ✓
- **A-2 验证**：W11 与生成页两处生图模型下拉，阿里百炼仅列 qwen-image-edit-plus/-edit（aitryon-plus 不再出现），硅基仅列 4 同步模型 ✓
- **U-2 验证**：W4 修改名称后 `cmd uimode night yes` 触发 Activity 重建，表单与名称（含手改标记）完整保留（旧代码 remember 会清空）✓
- **演示生图闭环**（硅基 Qwen-Image-Edit 图文编辑）：预览面板→生成模式→参考长图合成（A-1 路径）→Mock 回放→候选挑选→「保存到这套穿搭」→ toast「效果图已录入成品图」并挂回 W7 ✓
- **真实失败态**（意外覆盖）：真实模式 + 无网 AVD 下生成，Failed 事件正确渲染错误分类文案（网络不可达/DNS）+「重试/返回调整」按钮（A-7 归一后路径）✓
- 走查后 AVD 已还原初始态（真实模式、清除误存的测试 Key）

### 补丁轮（2026-10-03 深夜，Leo 质询「确定完成了吗」后的完成度审计）
审计发现四个缺口，已全部补齐并重验：
1. **U-2「顺手同款」漏做** → WishEditSheet 七字段 + OutfitDetailScreen editingItems/draftItemIds/tagDraft 补 saveable 化。诚实边界：WishEditSheet 为 ModalBottomSheet，宿主开关态（editOpen）非 saveable、重建即关，表单 saveable 的「重建保持」收益受限（无害、方向正确，未来 sheet 宿主态 saveable 化后自然生效）；OutfitDetailScreen 为页面级状态，收益直接。模拟器验证：两处表单/标签编辑打开与渲染正常（深色渲染同帧确认无回归）。
2. **A-3 取消传播零测试锁定** → 新增 `取消即断 - 挂起等响应期间协程取消立刻返回`（MockWebServer setHeadersDelay 拦响应头——setBodyDelay 下响应头立即返回、取消点消失，首版教训；阻塞式实现退回时 5s 断言窗炸出）。验收标准第 3 条（退出页面 logcat 无后台完成痕迹）的端到端真机验证仍留待有真实 Key 的场景，以本单测+it-077 生图轨同模式为据。
3. **A-8 夹缝遗漏** → AppContainer 持 chatClient/imageClient 单例（聊天默认超时/生图长超时各一），chatModel 与 ImageModel.of 注入，连接池不再每次新建。
4. **PrefsStore 死成员残留** → exportSelections/exportSelectionsReady/saveExportSelections 删除（AppViewModel 三件死 API 的同批收尾；旧 DataStore key 残留无害）。

补丁后：wardrobe 123 / agent 99 / store 24 三套件全绿（compileDebugKotlin + assembleDebug 通过）。

### 环境备注
- 走查中途 AVD uiautomator 出现假帧/卡死一次、ANR 一次（`FocusEvent` 派发超时型，发生在进程后台恢复瞬间；`emu kill` 重启后全程不复现，冷启与正常操作均无复现）——判为环境性（LESSONS 有同型先例），与本次改动路径无关；若后续真机复现再归因。
- 演示模式「购入→重启→升级」的跨进程链路由单测锁定（`purchaseAllThenReloadKeepsWishOutfitAndPromoteWorks` 模拟同 store 新实例=重启语义）；UI 端购入表单需先拍实物照（必填），模拟器未实操，无回归面（该表单本次未改）。
