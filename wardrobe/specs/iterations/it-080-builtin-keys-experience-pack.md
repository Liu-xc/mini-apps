# it-080 体验包内置 BYOK Key（免手动配置）

- 状态：已实施
- 日期：2026-10-03
- 类型：开发基建 / 构建链路（ADR-031）
- 关联：it-045（demoDefault 体验包）、it-041（W11 BYOK 接入）、it-077（生图链路）、it-050（Mock 独立 namespace）

## 背景与动机

体验包（`-PdemoDefault=true`）此前装上后仍需进 W11 手动粘贴 Key 才能用真实聊天/生图，体验断层。Leo 于 2026-10-03 提供 GLM / SiliconFlow / MiMo TOKEN plan 三把真实 Key（**明文只存本地 `wardrobe/keys.local.properties`，不入库，见 AGENTS.md 密钥红线**），要求内置到体验包，装上即用。

## 方案

三层链路，密钥永不进 git：

1. **本地文件**：`wardrobe/keys.local.properties`，条目名 = libs/agent presetId（`glm` / `siliconflow` / `mimo-tp`），根 `.gitignore` 已忽略 `keys.local.properties`。
2. **构建注入**：`app/build.gradle.kts` 仅当 `demoDefault=true` 时读取该文件注入 `BuildConfig.BUILTIN_KEY_{GLM,SILICONFLOW,MIMO_TP}`；文件缺失或常规构建恒空串（行为零变化）。
3. **运行补缺**：`WardrobeApp` 新增 appScope，首启调用 `BuiltinKeysSeeder.seed(container.apiKeyStore)`——对当前 namespace（演示 `mock_agent` / 真实 `agent`）未配置的 presetId 预填；**只补缺不覆盖**（W11 配过/清除过的槽位不被触碰）。演示↔真实切换经进程重启，新 namespace 下次启动自然再补。

## 验收标准

- [x] `git check-ignore wardrobe/keys.local.properties` 命中；该文件永不出现在 git status
- [x] demoDefault 构建：BuildConfig 三字段为注入值（掩码核对前后缀）
- [x] 常规构建：三字段恒空串
- [x] 补缺语义单测：空 store 全填 / 已配不覆盖 / 重复幂等
- [x] 全量单测绿
- [x] 体验包装机：小米 14 Pro（adb 覆盖装）冷启后 `run-as` 实测——`mock_agent_secrets.xml` 与 `agent_secrets.xml`（写入 demo_mode=false 切真实模式重启后）三槽位 glm/siliconflow/mimo-tp 密文齐全；验证毕已删 pref 恢复演示默认

## 影响范围

- `wardrobe/app/build.gradle.kts`（demoDefault 分支 + BuildConfig 注入）
- `wardrobe/app/src/main/java/com/leo/wardrobe/WardrobeApp.kt`（appScope + seed 挂钩）
- 新增 `data/prefs/BuiltinKeysSeeder.kt` + 单测
- 根 `.gitignore`、根 `AGENTS.md`（密钥红线）、`wardrobe/keys.local.properties`（本地，不入库）
- 常青 spec：`06-decisions.md` ADR-031

## 安全边界

- Key 明文仅存在于：本地 properties 文件、demoDefault 构建的 BuildConfig/APK 内。APK 本身即含密钥，**体验包不得分发给不可信对象**（自用定位，ADR-031）。
- spec / CHANGELOG / 日志 / 提交信息一律不出现明文。

## 验证记录

- 2026-10-03：BuildConfig 两态（demo 包三 key 掩码核对 ✓ / 常规构建空串 ✓）；`BuiltinKeysSeederTest` 3 测通过；`./gradlew test` 全绿。
- 2026-10-03 装机：小米 14 Pro it080 包覆盖装，冷启后 run-as 实测 `mock_agent_secrets.xml` 三槽位密文齐全；写 demo_mode=false 重启后 `agent_secrets.xml` 同样齐全（两 namespace 独立共存互不干扰）；收尾删 pref 恢复体验包演示默认。W11「已保存」UI 态与真实生图留 Leo 真机体验。
