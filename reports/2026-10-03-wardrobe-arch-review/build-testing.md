# 分报告四：构建 / 模块化 / 测试策略深度审查

> 审查通道 D · 全部结论基于配置与测试文件逐读 + dependencies 树实测，P1 级发现经主审复核源码确认。

## A. 事实基线

### A1. 构建与模块配置摘要

| 项 | 事实 |
|---|---|
| Gradle | 8.9（腾讯镜像分发包），`org.gradle.parallel=true`、`caching=true`，**configuration-cache 未启用**，`-Xmx3g` |
| 版本目录 | 根 `gradle/libs.versions.toml` 供 7 个构建（wardrobe/eats/store/sync/carddeck/cutout/agent）`from(files(...))` 共享，`FAIL_ON_PROJECT_REPOS` 兜底 |
| 模块 | `:app`（AGP 8.7.3，compileSdk 35 / min 26 / target 35，Java 17）+ `:baselineprofile`（com.android.test，min 28，仅 release 变体，self-instrumenting） |
| composite build | 4 个 `includeBuild`（store/agent/carddeck/cutout），坐标 `com.leo.libs:*` 依赖替换，实测解析正常（dependencies 树确认替换生效） |
| release | R8 minify + shrinkResources，`proguard-android-optimize.txt` + 3 行注释的空规则文件；debug keystore 签名；abiFilters 仅 arm64/armeabi（debug 留 x86_64） |
| 仓库顺序 | aliyun 镜像 → google() → mavenCentral()（镜像优先） |
| 无 | dependency verification metadata、convention plugins（7 处构建各自手写 compileOptions/jvmTarget 17）、lint 在 CI 的任何门禁 |

### A2. 依赖清单（releaseRuntimeClasspath 实测解析，直接依赖 24 项 / 去重唯一构件约 130+，含版本分支 157 行）

| 依赖 | 版本 | 时代评估 |
|---|---|---|
| AGP / Kotlin | 8.7.3 / 2.1.21 | 2024 末 / 2025 上半年，落后约 1-2 个大版本 |
| Compose BOM / material3 | 2025.06.01 / **1.4.0 显式覆盖**（ADR-004） | 内部自洽（BOM 映射 1.3.x，覆盖有注释有 ADR） |
| coil / lottie | 2.7.0 / 6.7.1 | coil 未上 3.x 线；与 okhttp 4.12 对齐无冲突 |
| okhttp（经 agent+coil 传递） | 4.12.0 | 全树单一版本，无冲突 |
| kotlinx-serialization / coroutines | 1.8.0 / 1.9.0 | 偏旧但全树单版本 |
| onnxruntime（桌面+android） | **同一 catalog key `onnxruntime="1.20.0"`** | cutout ADR-002 的「版本须对齐」由单一 version.ref 结构性保证，设计好 |
| work / lifecycle / navigation / core-ktx | 2.10.0 / 2.8.7 / 2.8.5 / 1.15.0 | 2024 末波次 |
| profileinstaller / baselineprofile 插件 | 1.4.1 / 1.3.3 | it-071 新增，链路齐 |

整体是一个 2024Q4–2025H1 的连贯成熟快照，无重复/冲突依赖；未发现强制对齐之外的传递坑。

### A3. 测试文件 → 被测对象映射（19 个测试文件 + 1 个 FakeImageStore 辅助；共 112 个 @Test，约 346 处 assert 调用；最近一次本地执行 112/0 失败）

| 测试文件 | 被测对象 | @Test | asserts |
|---|---|---|---|
| data/repo/WardrobeRepositoryImplTest | WardrobeRepositoryImpl（14 个方法全测：级联删除、悬空清洗、effectStale、refPhoto、旧 JSON 兼容、跨实例持久化） | 14 | 36 |
| data/repo/WishRepositoryTest | 心愿域（purchase/promote/去重查询/slot adapter） | 8 | 23 |
| data/repo/WearLogRepositoryTest | 穿着日志（同日去重、范围删除、级联、悬空清洗） | 6 | 8 |
| data/chat/ChatSessionIndexTest | ChatSessionIndex | 2 | 8 |
| data/gen/ImageParamEncoderTest | ImageParamEncoder | 2 | 9 |
| data/gen/ImageSourceFileResolverTest | ImageSourceFileResolver | 2 | 4 |
| data/mock/MockChatCacheTest | MockChatCache（借 agent 的 FakeChatModel） | 6 | 16 |
| data/mock/MockImageModelTest | MockImageModel | 2 | 8 |
| data/mock/MockWardrobeRepositoryTest | Mock 仓库与 Impl 语义一致性 | 7 | 21 |
| domain/model/QueriesTest | Queries | 5 | 9 |
| domain/model/DataPackageMergeTest | DataPackageMerge | 6 | 20 |
| domain/usecase/BuildOutfitPromptTest | BuildOutfitPrompt | 7 | 13 |
| domain/usecase/BuildTryOnPromptTest | BuildTryOnPrompt | 3 | 12 |
| domain/usecase/StaleItemSelectorTest | StaleItemSelector | 4 | 6 |
| domain/usecase/WardrobeRecapCalculatorTest | WardrobeRecapCalculator | 8 | 19 |
| export/PersonReferenceTest | PersonReference | 2 | 8 |
| ui/chat/OutfitRecommendationParserTest | 文本协议反向解析 | 12 | 40 |
| ui/chat/ToolResultCardTest | payload 卡片 | 10 | 34 |
| ui/chat/MarkdownPreviewTextTest | ChatMarkdown 纯文本部分 | 6 | 9 |

### A4. 主源码结构（89 文件 / 18,917 行）

`ui` 14,708（78%）｜`data` 2,207｜`domain` 842｜root 447｜`export` 440｜`di` 146｜`platform` 127。包纪律实测干净：domain 零 import data/ui；data 零 import ui；export 零 import ui——但这是**纯纪律，无 Gradle 层面 enforce**（单模块无从 enforce）。

### A5. CI（`.github/workflows/ci.yml`，push main + PR）

跑 wardrobe / eats / store / sync / cutout 的单测 + carddeck 的 build；**不含 libs/agent、不含 lint、不产 APK**；统一借 wardrobe 的 Gradle 8.9 wrapper；checkout@v4 默认浅克隆。

### A6. libs 四 SDK

store（JVM，v0.2.0，4 测试文件/24 测，specs 有 00-architecture）、agent（JVM，v0.1.0，**13 文件/94 测**为全仓最大套件，含 SF_KEY 门控的 live 冒烟）、carddeck（Android library，v0.1.0，**0 测试**）、cutout（JVM，v0.1.0，5 文件/15 测，compileOnly 桌面 onnxruntime，ADR-002 与消费方版本对齐由 catalog 单 key 保证）。

### A7. 迭代节奏

wardrobe 首提交 2026-09-19，最新 2026-10-01：13 天 it-001→it-079（约 6 迭代/天），it-077 单迭代 15 次修订。CHANGELOG 纪律完整，每条带 it-XXX 链接。

## B. 问题清单

**B-1（P1）libs/agent 的 94 个测试不在 CI 运行。** `.github/workflows/ci.yml:33-43` 列了 store/sync/cutout/carddeck，唯独漏掉 agent。agent 是 wardrobe 的网络协议核心（SSE 解析、生图同步/异步归一、wire 隔离——ADR-029/030 都靠它的测试锁契约），却是唯一不上 CI 的有测 SDK。且其 `LiveImageSmokeTest` 已用 `assumeTrue(SF_KEY)` 门控（`libs/agent/src/test/.../image/LiveImageSmokeTest.kt:27`），加进 CI 无打真 API 风险。影响：wardrobe 最大外部依赖的回归全靠本地自觉。修复：CI 加一步 `wardrobe/gradlew -p libs/agent test --no-daemon`。

**B-2（P2）CI「libs/sync 单测」是无声空转。** `ci.yml:37` 声称跑 sync 单测，但 `libs/sync/src/` 下根本没有 `test/` 目录，`test` 任务 NO-SOURCE 直接绿。影响：CI 状态给出「sync 有测试保护」的假象。修复：要么补测试，要么把步骤改名 `build` 并注释现状。

**B-3（P2）libs/store 版本契约漂移：实际 0.2.0，catalog 记 0.1.0。** `libs/store/build.gradle.kts`（version = "0.2.0"）与 `libs/store/specs/00-architecture.md:3`（「已实现 v0.2.0」）vs 根 `gradle/libs.versions.toml:34`（`leoStore = "0.1.0"`，注释声称「与 libs/store version 一致」）。composite 替换按 GAV 坐标忽略版本所以不炸（dependencies 树实测正常解析），但注释契约已失真；将来若把某 lib 发布到本地 maven 或移除 includeBuild，会静默解析到不存在的 0.1.0。修复：catalog 同步 0.2.0，或去注释改由构建脚本从 catalog 读。

**B-4（P2）ADR-027 配置期 git 调用的三个隐患。** `wardrobe/app/build.gradle.kts:12-21`：
- 每次 build 配置阶段（含 `gradlew tasks`）同步 spawn 2 个 git 进程（`providers.exec` 结果 `.get()` 急切求值），单次 ~100-200ms，13 天 79 迭代的节奏下累积可感但非致命；
- **与 configuration cache 不兼容**：值在配置期固化为字面量，未来若开 `org.gradle.configuration-cache`，commit 数变化不会使 CC 失效 → versionCode 冻结在旧值（当前 CC 未启用，行为正确，但这是一颗埋雷，ADR 未记载该约束）；
- **CI 浅克隆陷阱**：`actions/checkout@v4` 默认 fetch-depth=1，若 CI 未来加 assemble 任务，`rev-list --count` 恒得 1，版本号静默错误。当前 CI 只跑单测无影响。
修复：注释里写明「启用 CC 前须改造此段」；CI 加 `with: fetch-depth: 0` 或改用 `GITHUB_SHA` 环境变量。

**B-5（P2）零测试关键模块清单（按风险排序）。** 详见 D 节。最痛的一处：`data/gen/OutfitImageGenerator.kt`（230 行，run/save/buildRefs 零测试）恰是 it-077→078→079 连续三个 hotfix 迭代的修 bug 主战场——出过「纯文生图必败 11235」「演示蓝占位图」「参考图漏传+seed 编码错误」三次事故，修复全靠模拟器实测而非单测锁住。其中 `buildRefs`/参数编码策略是纯决策逻辑，可仿照已测的 ImageParamEncoder/ImageSourceFileResolver 抽纯函数补测。同理 `export/JpegXmp.kt`（64 行，纯 `java.io`，唯一 import 是 `java.io.File`）零成本可测却零测试——二进制段拼接正是回归高发区。

**B-6（P2）PickRandomOutfit 零测试。** `domain/usecase/PickRandomOutfit.kt`（构造器已注入 `Random`，可测性现成）是 domain 五个 usecase 中唯一没测的，而它决定「随机一套」的核心分配语义。

**B-7（P2）release lint 全面失守。** `app/build.gradle.kts:74-79` `checkReleaseBuilds=false`（有 ADR 级注释：lint 工具与 Kotlin 2.1.21 Analysis API 崩溃），且 CI 完全不跑 lint。影响：除单测覆盖的纯逻辑外，业务代码的 lint 类问题（资源泄漏、API 级别误用）零门禁。修复：CI 加 `lintDebug`（debug 变体不走 lintVital 分析路径），把「工具崩溃」与「无门禁」解耦。

**B-8（P2）R8 空规则文件 = 政策可行但有一个未记录的监控点。** `proguard-rules.pro` 只有注释（「三方自带 consumer rules，禁全局 keep」）。kotlinx-serialization（1.5+ 自带）、okhttp、coil、compose 均自带 consumer rules，该政策成立，且 it-071 有「R8 冒烟全页通过 + 25,098 规则」的验证记录兜底。但 **onnxruntime-android 的 AAR 不含 proguard 规则**，其存活当前仅靠 cutout SDK 代码的直接引用链——将来 cutout 改为反射加载或裁剪 SDK 时是首个可能炸的点，建议在 proguard-rules.pro 注释里点名这个 watch item。

**B-9（P3）tools/mock-data zip 与解压目录双份入库。** `wardrobe/tools/mock-data/wardrobe-ai-mock-20260924.zip` 与同名解压目录（30+ PNG）同时被 git 跟踪（tools 共 47 个跟踪文件），体积翻倍。修复：二选一（保 zip 或保目录）。

**B-10（P3）reports/ 验证截图无增长上限。** 18 张 PNG / 9.5MB 已入库（`.gitignore` 只挡了 `dist/`，375MB 本地产物正确未入库）。截图是 AGENTS.md 要求的验证证据，入库合理，但按 6 迭代/天的节奏一年会到几百 MB。修复：按 it-XXX 定期归档压缩或迁出 git。

**B-11（P3）catalog/各 settings 注释陈旧：写「六个构建」，实际七个。** agent（it-041 加入）已用 `../../gradle/libs.versions.toml` 但 `libs.versions.toml:2` 与各 settings 的注释仍枚举六个。纯文档漂移，但这是「注释即契约」仓库里少数失守处。

## C. 亮点

1. **单一 version catalog 管 7 个构建**（含 onnxruntime 桌面/移动双坐标共用一个 version.ref），「版本须对齐」这类约定被结构性消除而非靠记忆——cutout ADR-002 的对齐诉求是教科书式落地。
2. **测试选点极准**：全部 19 个文件打在纯逻辑层（repo 不变量、解析器、编码器、prompt 构造、Mock↔Impl 语义一致性），ViewModel/Compose 一律不硬测；连 ui/chat 里的文本协议反向解析都测了 12 例。零「为覆盖率而测」的痕迹。
3. **ADR 记录质量罕见地高**：ADR-027/028 连「回退路径极端场景 versionCode=5 拒绝覆盖装，个人自用接受」、「x86 CI 跑不了 baseline profile 生成」这类负效应都写了后果与接受理由，决策可逆性思考完整。
4. **baselineprofile 模块配置一次到位**：仅 release 变体、self-instrumenting、不手写变体过滤（注释还记了手写过滤会关掉插件自建变体的坑），采集场景锚定 it-071 优化的两条滚动路径而非泛泛启动。
5. **composite build 边界设计**：4 个 SDK 各自 specs 先行、纯 JVM 模块（store/agent/cutout）可在 CI 用桌面 JVM 完整自测（cutout 用真 ONNX 模型），Bitmap 桥留在消费方——模块形态选择直接服务了可测性。
6. **数据层不变量测试是安全网核心**：级联删除、悬空引用清洗、旧 JSON 向后兼容（3 个 legacy-JSON 用例）锁住了这个「仓库是唯一 SSOT」架构最怕坏的性质。

## D. 量化

| 指标 | 值 |
|---|---|
| wardrobe 测试文件 / @Test / assert 调用 | 19（+1 Fake 辅助）/ **112** / **~346**（grep 口径下界） |
| 最近一次本地执行 | 112 通过 / 0 失败（build/test-results 实测） |
| 直接依赖 / 解析唯一构件 | 24 / ~130+（releaseRuntimeClasspath） |
| 主源码 | 89 文件 18,917 行（ui 78%、data 11.7%、domain 4.4%） |
| 零测试关键模块（风险降序） | ① OutfitImageGenerator（230 行，it-077~079 三连 hotfix 主战场）② JpegXmp（64 行纯 JVM，白捡的测试）③ PickRandomOutfit（Random 已注入，白捡）④ OutfitImageComposer（289 行，Bitmap/StaticLayout → 需抽布局数学才可 JVM 测）⑤ ImageFileStore（200 行，ContentResolver/Exif → 需 Robolectric/仪器）⑥ AppViewModel 560 行 / ChatViewModel 406 行（AndroidViewModel → 构造即绑 Application，需去 Android 化才可测）⑦ ReminderScheduler（127 行，通知/WorkManager → 仪器）⑧ WardrobePackages（仅 import android.util.Log，微调即可 JVM 测）⑨ KeystoreApiKeyStore / ShareClipboard / ImageEditStore（强 Android API → 仪器）⑩ DemoMode/DemoChatModel/MockWardrobeData（演示夹具，低险） |

### 模块化判断（审查点 4 的直接回答）

**不建议现在拆**。证据：① 18.9k 行里 14.7k 是 ui，真正的领域复杂度（domain+data = 3k 行）很小且边界已经过测试固化；② 包依赖纪律实测零违规（domain/data/export 三向干净），只是没有 Gradle enforce——拆模块的主要收益（强制边界 + 增量编译）前者目前用纪律+单测已兑现，后者在单人 6 迭代/天、无 CI assemble、无构建耗时记录（未找到任何 build-profile 或时长记载）的用法下缺乏痛点证据；③ composite 已把最重的外围（agent/store/cutout）隔离出增量图。若未来 ui 再翻倍或引入第二人协作，第一刀应是抽 `:feature:*` + convention plugins（现在 7 处手写 compileOptions/jvmTarget 重复本身就是 convention plugin 的信号），而非按 layer 拆。

### 总评
这是一套「个人高速迭代 + spec 驱动」下的高质量工程：依赖治理（单 catalog、镜像+官方双源、无冲突）和 ADR 记录达到团队级水准，测试选点策略成熟。最值得动手的三件事按序是：**B-1（agent 进 CI，一行成本堵最大缺口）、B-5 的 OutfitImageGenerator 纯函数抽取补测（hotfix 三连的地区）、B-4 的 CC/浅克隆注释（防未来埋雷）**。
