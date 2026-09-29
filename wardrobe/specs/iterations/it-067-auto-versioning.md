# it-067 · 构建版本号自动生成（基线 + wardrobe 提交数）

状态：**已实施**（2026-09-29；提案方向于会话中获用户确认后同日落地）

## 背景与动机

版本号停更**二次复发**：ADR-021（2026-09-21）把首发公开版对齐到 0.5.0（versionCode 5）后，it-020~it-066 数十次迭代再未 bump 过 `versionName`/`versionCode`——设置页永远显示「版本 0.5.0」，装到手机上的构建无法区分「这是哪一版」。CHANGELOG 里的 it-XXX 流水只存在于文档，不进 APK；ADR-021 的背景一节就记过同样的坑（当年停在 0.1.0），靠「下次记得 bump」已被证伪。

结论（用户 2026-09-29 会话确认）：**人肉同步的版本号改为机制兜底**——构建期从 git 自动生成，与代码状态一一对应，零维护。

## 涉及的用户故事

- 无 US 变更（构建机制，不动功能与交互）。
- 可见面受益：设置页版本行（it-043 O4 形制）与回顾页版本读取（`packageManager.versionName`）从「永远 0.5.0」变为可辨识「装的是哪一版」。

## 验收标准

### 版本生成规则（wardrobe/app/build.gradle.kts，配置期）

- `versionName = "<VERSION_BASE>.<wardrobe 提交数>"`，如 `0.5.0.105`：
  - `VERSION_BASE = 0.5.0`（手动基线，对外声明新 release 序列时抬它，如 0.6.0；提交数后缀继续单调不重置）；
  - 提交数 = `git rev-list --count HEAD -- .`，workingDir 限定 `wardrobe/`（pathspec `.`）——**只随衣橱代码提交增长**，darkroom/eats 提交不影响衣橱版本；
- 工作树有未提交改动（`git status --porcelain -- .` 非空，同样限定 wardrobe/）时追加 `-dirty`，如 `0.5.0.105-dirty`——自测期「装的哪版」不失真；
- `versionCode = ward 提交数`（Int、天然单调递增；从现值 5 跳到 105+ 是合法升级）；
- git 不可用（无 git / 非仓库，如源码 zip 构建）→ 回退 `versionName = 0.5.0`、`versionCode = 5`，**构建不失败**；
- 实现走 `providers.exec`（配置缓存友好），不引新依赖、不加脚本文件。

### 消费端零改动

- 设置页「版本 ${BuildConfig.VERSION_NAME}」与回顾页 `packageManager.versionName` 自动反映新值，UI 代码不动。

## 实施方案

1. `wardrobe/app/build.gradle.kts`：`defaultConfig` 前加 git 读取（count + dirty 两个 `providers.exec`，带 try/catch 回退），`versionCode`/`versionName` 按上述规则生成；`VERSION_BASE` 提为常量。
2. spec 同步：`04-architecture.md` 构建配置小节加版本生成一条；`06-decisions.md` 新增 ADR-027（含与 ADR-021 发布流程、GitHub Releases 打 tag 的衔接说明）。
3. `CHANGELOG.md` 记一行；LESSONS.md 沉淀「二次复发改机制」一条。

## 影响范围

- **代码**：仅 `wardrobe/app/build.gradle.kts`。
- **常青 spec**：`04-architecture.md`（构建配置）、`06-decisions.md`（ADR-027）。
- **不涉及**：darkroom/eats 版本策略（各自沿用手动 bump；要推广另开迭代）、发布签名/渠道（ADR-021 不变）、UI 代码与数据模型。
- **已知取舍**：版本号形制不再是整洁的 `0.5.0`，设置页版本行变长（Text 自动换行）；回退路径 versionCode=5 在「git 缺失的拷贝源码装覆盖装过自动生成版的设备」极端场景会拒绝覆盖装——个人自用场景接受，记入 ADR-027 后果。

## 验证记录

**构建与测试（2026-09-29）**

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：**BUILD SUCCESSFUL**，89 个单测全过（0 failure）。
- 生成版实证（BuildConfig / APK badging）：提交前工作树（含并行会话改动）构建出 `versionName=0.5.0.105-dirty`、`versionCode=105`——提交数取到、脏树 `-dirty` 实证；本迭代提交后复构建 `0.5.0.106-dirty`（提交数 +1 实证；dirty 因并行会话 ScrollFade/Tags 改动仍在，非本迭代产物）。
- 回退路径：未实跑（无 git 环境）；代码为 try/catch → 基线常量，风险低。
