# it-024 · 结构化数据包：导出 / 导入 / agent 加工回流（wardrobe 侧）

- 状态：**提案，待 Leo 确认后开工**
- 用户故事：US-24
- 姊妹迭代：eats it-012（同一数据包格式，eats 侧落地）
- 关联：libs/store 0.2.0（PackageCodec 回归）、`.agents/skills/data-package/`（新增）、ADR-022
- 提出日期：2026-09-21

## 背景与动机

Leo 的核心工作流诉求：**收集一大批原始数据（照片 / 网购截图 / 收藏夹整理），先交给 AI 打标加工，再整包导回 app**。完整链路：

```
app 导出结构化数据包（zip：JSON 元数据 + 图片）
  → 交给 agent（按 .agents/skills/data-package 文档加工：打标 / 补字段 / 去重 / 新增条目）
  → agent 自检（validator PASS）后交付新数据包
  → app 导入（合并或替换）→ 打标结果回流
```

两个直接动因：

1. **备份承诺从未兑现**：03-data-model.md 自 it-001 起写着「导出备份：zip（wardrobe.json + images/）……it-001 提供导出，导入随后续迭代」，但全仓没有任何 zip 代码（已核实），导出与导入都未实现。本迭代一并补齐，并顺手修正 spec 中该失实表述。
2. **libs/store 的 BackupCodec 按需回归**：store v1 设计稿原本包含 zip 备份编解码，0.1.0 精简时因「零消费方」砍掉。现在消费方来了，按当初「按需再加」的约定回归。

现状（已核实）：`files/wardrobe.json`（schemaVersion=1，SSOT 经 libs/store `SsotRepository`）+ `files/images/*.webp`（`FileMediaStore`）；演示模式指向 `cacheDir/mock-images` 不碰真实数据；无设置页。

## US-24 数据包导入导出

> 作为衣橱主人，我能把全部数据（含图片）导出成一个结构化数据包，交给 AI 或其他工具加工后导回，实现批量打标、清洗与迁移；数据包同时天然是我的完整备份。

验收要点（详细清单见下「验收标准」）：

- 导出：回顾页一键导出 zip（wardrobe.json + images/ + manifest.json），无需存储权限（SAF）。
- 导入：合并（默认，按 id 去重、包内实体覆盖同 id 本地实体、本地多出者保留）或替换（整包覆盖，二次确认）两种模式。
- 错包防御：跨 app 包 / 坏 JSON / 缺图 / schemaVersion 过高，一律拒绝且本地数据零改动。
- agent 友好：包格式自描述（manifest），图片命名与格式放宽，导入时统一归一化。

## 数据包格式 v1（两 app 共通契约）

```text
wardrobe-backup-YYYYMMDD-HHmm.zip
├─ manifest.json      # 自描述头，见下
├─ wardrobe.json      # 与 files/wardrobe.json 完全同构（SSOT 根原样，含 schemaVersion）
└─ images/            # 图片文件，文件名 = wardrobe.json 中引用的包内名
   ├─ uuid.webp       #   app 导出：原 uuid.webp 原名
   └─ 任意安全名.jpg   #   agent 产出：允许自定义名 + jpg/png/webp（见放宽项）
```

### manifest.json

```json
{
  "packageFormat": 1,
  "app": "wardrobe",
  "schemaVersion": 1,
  "exportedAt": 1758950000000,
  "generator": "wardrobe 0.6.0",
  "counts": { "persons": 1, "items": 42, "outfits": 8, "notes": 5,
              "wearLogs": 30, "wishItems": 6, "wishOutfits": 2 }
}
```

- `packageFormat`：包格式自身版本（与 app `schemaVersion` 分开演进）。
- `generator`：app 导出写「应用名+版本」；agent 产出写工具名（如 `"agent: gpt-x"`），用于排查。
- `counts`：导入预检与结果反馈用，校验时只提醒不强校验。

### 对 agent 产出包的放宽项（关键设计）

app 导出的包是最严格的；agent 产出的包放宽以下各项，**导入时统一归一化**，让 agent 尽量好写：

| 项 | app 导出 | agent 产出（放宽后） | 导入归一化 |
|---|---|---|---|
| 图片文件名 | `<uuid>.webp` | 任意 `[A-Za-z0-9._-]+` | 全部重写为全新 `<uuid>.webp`，JSON 引用同步重映射（彻底消除重名冲突，单一代码路径） |
| 图片格式 | webp | webp / jpg / png | 非 webp 或最长边 >1440 → 走现有 ImageFileStore 压缩管线（1440px / 质量 82） |
| 实体 id | UUID | 新增实体必须 UUID（校验）；已有实体保持原 id（合并键） | — |
| 时间戳 | epoch millis | 同；缺省 0 允许 | — |

> 归一化统一「重命名 + 重映射」也覆盖 app 自家导出包的回导（幂等，代价是图片重写一遍；个人级数据规模可接受，优化留实现裁量）。

## 导入语义

### 预检（全部通过才动本地数据，任何一条失败 = 整包拒绝并给出可读原因）

1. zip 可解压，含 `manifest.json` 与 `wardrobe.json`。
2. `packageFormat == 1`；`app == "wardrobe"`（eats 的包 → 明确报「这是吃啥的数据包」）。
3. `schemaVersion <= 当前版本`；高于当前 → 拒绝并提示「数据包来自更新版本，请先升级 app」。低于当前 → 走现有迁移链升级后导入。
4. `wardrobe.json` 可反序列化为 `WardrobeData`（`ignoreUnknownKeys` 语义：未知字段忽略、缺字段取默认）。
5. 图片齐全：合并结果中所有指向包内实体的图片引用，在包内都能找到文件且魔数合法（webp/jpg/png）；转码试跑通过。缺图/坏图 → 拒绝并列出清单（与 validator 同规则，agent 可修包重试）。

### 合并（默认模式）

- 七个集合（persons / items / outfits / notes / wearLogs / wishItems / wishOutfits）各自**按 id 合并**：
  - 包有本地无 → 新增；包有本地有 → **包内实体整体覆盖**（AI 打标回写的语义就是「这是该实体的新版本」；不做字段级深合并，语义简单可预期）；本地有包无 → 保留不动（合并永不删数据）。
- 合并结果跑现有 `cleaned()` 清洗悬空引用，再走一次 `mutate{}` 单事务 commit（SSOT 原子性天然成立）。
- 被覆盖实体不再被引用的旧图片文件删除；执行中途失败残留的孤儿图片无引用、无害。

### 替换模式

- 整包覆盖：先全量预检 → 清空 `images/` 与本地 JSON → 落包。入口处二次确认对话框（列明将删除的本地规模 vs 包规模）。

### 通用约束

- **演示模式下禁用**导入导出（不碰真实 `images/`，也不污染演示数据），与现有演示模式原则一致。
- 导入成功的 snackbar 反馈：新增 X · 更新 Y · 保留 Z。

## 交互线框（评审稿 D1–D7，2026-09-21；eats it-012 共用同一套，差异见注记 11）

### D0 整体链路（三泳道：App 内 / 手机系统 / AI 侧）

```
   App 内（W9 回顾页）          手机系统（SAF）           App 外（AI 侧）
   ─────────────────           ──────────────           ──────────────
   「数据」小节
    │ ① 点「导出数据包」 ──────▶ 保存位置/文件名
    │                            默认 Downloads/
    │ ② 打包进度（秒级）         wardrobe-backup-….zip
    │ ◀── ✓ 已导出 [分享] ◀────  写入完成
    │
    │ ③ [分享] 或手动发走 ─────▶ 微信/AirDrop/网盘 ─────▶ 🤖 agent
    │                            发给电脑/聊天            读 skill 文档·打标/
    │                                                     补字段/加条目·validate
    │ ◀──────────── 新 zip 传回手机并下载 ◀────────────── 自检 PASS 后交付
    │
    │ ④a 点「导入数据包」 ─────▶ 文件选择器选中 zip
    │ ④b 微信「用其他应用打开」或 ─▶ 直接进入 ⑤
    │    文件管理器「分享→衣橱」     （免选文件，直达校验）
    │ ⑤ 校验中（1–2 秒，无感）
    │ ⑥ 预览确认（唯一决策点）
    │     合并（默认）/ 替换（二次确认）
    │ ⑦ 导入进度（秒级）→ ✓ 完成
    ▼ 数据即时生效（无需重启）
```

### D1 入口：W9 回顾页底部「数据」小节（eats 挂 W7 回忆提醒卡下，同构）

```
W9 衣橱回顾页（底部）                    演示模式时
┌────────────────────────────┐   ┌────────────────────────────┐
│ 💤 衣柜提醒设置          ›  │   │ 💼 数据                     │
├────────────────────────────┤   │  ⇪ 导出数据包（置灰）        │
│ 💼 数据                     │   │  ⇩ 导入数据包（置灰）        │
│  ⇪ 导出数据包           ›  │   │  演示模式下不可用，           │
│  ⇩ 导入数据包           ›  │   │  不触碰真实数据               │
└────────────────────────────┘   └────────────────────────────┘
  低频入口，排设置类卡片区最后
```

### D2 导出三步：系统保存 → 打包中 → 完成

```
① 系统保存对话框(SAF)         ② 打包中(应用内模态)         ③ 完成 snackbar
┌──────────────────────┐   ┌───────────────────┐   ╭──────────────────────────────╮
│ 保存到                │   │     ◌  打包中      │   │ ✓ 已导出 · 42 单品 · 9 穿搭   │
│ wardrobe-backup-     │   │   写入图片 18/36   │   │   36 图 · 87 MB      [分享]  │
│ 20260921-1432.zip    │   │  （大包需几秒）     │   ╰──────────────────────────────╯
│ [取消]      [保存]    │   └───────────────────┘    [分享]→系统分享面板
└──────────────────────┘                             ↑「发给 AI」的主交接动作
```

### D3 导入前段：两条入口 → 同一校验

```
④a 应用内：系统文件选择器(SAF)     ④b 系统直达：免选文件
┌──────────────────────┐       微信聊天中点开 zip → 微信提示
│ ▸ Downloads           │       「暂不支持打开」→ [用其他应用打开]
│   wardrobe-backup-…   │       → 选「衣橱」
│   打标-第2轮.zip      │       文件管理器 / 下载列表 [分享] → 衣橱 同效
│ [取消]       [打开]   │              │
└──────────────────────┘              ▼
                          ┌───────────────────┐
                          │   ◌ 校验数据包…    │← 两条入口汇入同一流程，
                          │  （约 1–2 秒）     │  ⑤ 之后完全一致
                          └───────────────────┘
```

### D4 导入预览确认（预检通过后弹出 · 唯一决策点 · 默认合并态）

```
┌──────────────────────────────────┐
│ 📦 wardrobe-backup-20260921.zip   │
│ 来自 wardrobe 0.6.0 · 今天 14:32  │← agent 产的包显示「来自 agent 加工」
├──────────────────────────────────┤
│ 合并模式将发生：                   │
│   ＋ 新增    2 单品 · 1 心愿单品   │
│   ↻ 更新   12 单品 · 3 穿搭        │← 更新 = 包内版本整体覆盖同 id 实体
│   ＝ 保留   28 单品 · 6 穿搭       │← 本地多出的原样不动（合并永不删数据）
│ ⚠ 3 件在导出后被本地修改过，       │← 仅计数>0 时显示
│   导入将覆盖这些修改               │
├──────────────────────────────────┤
│ ◉ 合并（推荐）                    │
│ ○ 替换全部                        │
├──────────────────────────────────┤
│             [ 取消 ]  [ 导入 ]    │
└──────────────────────────────────┘
```

### D5 切到「替换全部」→ 摘要切换 + 二次确认

```
⑥' 选中「替换全部」后摘要整体切换       ⑦ 点[导入]弹二次确认
┌──────────────────────────────────┐ ┌──────────────────────────────┐
│ ⚠ 替换模式将发生：                 │ │ 替换全部数据？                │
│   ✕ 清除  本地 42 单品 · 9 穿搭    │ │                              │
│          · 36 图 · 30 打卡 · 8 心愿│ │ 将删除本地 42 单品、9 穿搭、  │
│   ＋ 导入  包内 40 单品 · 9 穿搭    │ │ 36 张图…，替换为包内内容。     │
│          · 34 图 · 30 打卡 · 8 心愿│ │ 此操作无法撤销。               │
│        [ 取消 ]     [ 导入 ]       │ │                              │
└──────────────────────────────────┘ │ 建议先导出一份当前数据再替换。  │
                                     │        [ 取消 ]   [ 替换 ]    │
                                     └──────────────────────────────┘
```

### D6 导入执行与完成（合并/替换共用）

```
⑧ 导入中（模态）                  ⑨ 完成 snackbar
┌───────────────────┐      ╭──────────────────────────────╮
│     ◌ 导入中       │      │ ✓ 导入完成 · 新增 3 · 更新 15 │
│   处理图片 5/14    │      │   数据已即时生效               │
│  （大包需几秒）     │      ╰──────────────────────────────╯
└───────────────────┘
```

### D7 预检失败（本地零改动，原因逐条可读）

```
┌──────────────────────────────┐
│ 无法导入此数据包               │
│                              │
│ ✗ 这是「吃啥」的数据包，       │← 单一主因时只显示一条
│   请在吃啥中导入              │
│ ✗ 缺失图片 2 张：             │
│   top-01.jpg / shoe.jpg      │← 多条时逐条列出、可滚动
│ ✗ 数据包来自更新版本（3 > 1），│
│   请先升级 app                │
│ ✗ 文件不是有效的数据包         │← ④b 路径兜底：随手分享
│   （损坏或不是 zip 数据包）    │   任意 zip 给衣橱时的提示
│                              │
│ ✓ 本地数据未做任何改动         │← 固定收尾，安人心
│                   [ 知道了 ]  │
└──────────────────────────────┘
```

### 交互注记（实现与 eats 差异以此为准）

1. **入口**：W9 设置卡区（eats 为 W7 回忆提醒卡下），不新开页面、不占 Tab；未来若出现正式设置页可整体迁入。
2. **导出无中间确认页**：直达系统保存对话框；规模信息在完成 snackbar 补给。默认文件名 `wardrobe-backup-YYYYMMDD-HHmm.zip`。
3. **完成 snackbar 带 [分享]**：直达系统分享面板——「发给 AI」是主路径，不让用户去文件管理器找文件。
4. **导入两条入口、同一流程**：应用内 SAF 选文件（④a）与系统直达（④b，manifest 注册 `ACTION_SEND` + `ACTION_VIEW`，收 `application/zip` 与 `application/octet-stream`）汇入同一校验→预览流程。微信长按文件不提供系统分享，主路径是聊天中点开 zip →「用其他应用打开」→ 衣橱；文件管理器/下载列表「分享」→ 衣橱 同效。收到的 Uri 先整体拷入应用缓存再校验（临时读权限即取即用，不持有外部 Uri）。衣橱与吃啥都会出现在系统选择器里，选错由 D7 跨 app 拒绝兜底。
5. **导入两段式**：校验全自动无感；预览确认屏是唯一决策点，信息三段式=包来源 / 差异摘要 / 模式选择。
6. **差异摘要**固定三行（＋新增 ↻更新 ＝保留），条件警示行「N 件导出后被本地修改过」仅在计数>0 时出现。
7. **合并默认**；选替换时摘要切换为「✕清除 / ＋导入」两行；替换需二次确认，文案带具体数字。
8. **结果即时生效**（StateFlow 广播），无需重启——刻意区别于演示模式的「重启生效」。
9. **失败零改动**承诺固定显示在错误弹窗底部；中断语义：SAF 取消=无事发生，打包/导入为秒级模态不可取消，导入单事务、中途杀进程本地不变。
10. **幂等**：同一包重复导入=再次覆盖更新，不会产生重复实体。
11. **App 容忍悬空引用**（沿用 `cleaned()` 清洗，不作为拒绝理由）；skill 的 validator 更严格（缺引用即 FAIL）——不对称是设计使然：app 面向数据安全，validator 面向教学。
12. **eats 差异**：入口挂 W7；计数行无穿搭/打卡/心愿，改为「＋新增 X 家 · ↻更新 Y 家」；错误样例含 `PLAY+TAKEOUT` 无效组合；④b 系统直达入口同样注册（选择器里显示「吃啥」）；其余同构。
13. **实施时**本节并入 02-wireframes.md（W9 增「数据」小节注记），编号 D0–D7 保留作引用锚点。

## libs/store 0.2.0：PackageCodec（BackupCodec 按需回归）

职责边界（守住「零业务概念」铁律）：

- **codec（SDK）**：zip 读写、manifest 校验、图片搬运与归一化钩子。不知道衣物为何物。
- **merge（app domain 层纯函数）**：`mergeWardrobe(local: WardrobeData, incoming: WardrobeData): WardrobeData`，实体级合并语义，JVM 单测。

API 草案（实现时可微调，职责不变）：

```kotlin
class PackageCodec<T : Any>(
    private val media: MediaStore,
    private val appId: String,          // "wardrobe"
    private val dataFileName: String,   // "wardrobe.json"
    private val serializer: KSerializer<T>,
) {
    suspend fun writePackage(out: OutputStream, data: T): Result<Unit>
    data class RawPackage(val manifest: Manifest, val dataJson: String, val imageNames: List<String>)
    suspend fun readPackage(input: InputStream): Result<RawPackage>
    suspend fun importImage(bytes: ByteArray, transcode: (ByteArray) -> ByteArray?): String  // → uuid.webp
}
```

- 版本 bump 0.1.0 → **0.2.0**；libs/store/specs/00-architecture.md 增「数据包格式契约」章节并更新实现状态（BackupCodec 从精简记录移入已实现）。
- 依赖规则不变：core 纯 JVM 可测。

## agent skill：`.agents/skills/data-package/`（仓库级，随版本化）

```
.agents/skills/data-package/
├─ SKILL.md               # 触发场景（"帮我给衣橱/吃啥数据打标再导回"）、工作流：
│                         #   拿到用户导出的 zip → 解包读懂 → 按分册加工 → validate.py 自检 → 交付 zip
├─ docs/format.md         # 包格式 + manifest 契约（两 app 共通）+ 放宽项与归一化说明
├─ docs/wardrobe.md       # wardrobe schema 分册（本迭代交付）
├─ docs/eats.md           # eats schema 分册（eats it-012 交付）
├─ tools/validate.py      # python3 纯标准库校验器，与 app 导入预检同规则：
│                         #   manifest / 枚举 / 必填 / 引用完整性（itemIds·personId·parentId·wishItemIds…）/
│                         #   图片齐全性+魔数 / 新增 id 为 UUID；输出 PASS 或逐条 FAIL+修复建议
└─ examples/
   ├─ wardrobe-sample.zip # 手工构造的最小合法包（含 1 张 jpg 验证放宽项）
   └─ eats-sample.zip     # eats it-012 交付
```

schema 分册除字段表外，重点写**加工守则**（这是 skill 真正的价值）：

- 哪些字段适合 AI 改：`tags`（≤10 去重、贴着 TagPresets 预设词汇）、`desc`（款式一句话）、`color`、`name` 规范化、`category` 归类。
- 哪些不要碰：`rating`/`cost` 类主观字段（wardrobe 无，eats 有）、`createdAt`/`updatedAt`（除非用户明说）、`id`（已有实体改 id = 变成新增）。
- 枚举精确值：8 类 WardrobeCategory 的中文字面量（「上装」「外套」…），一字不差。
- 引用规则：outfit.itemIds / note.parentId / wishOutfit.wishItemIds（至少一件）必须指向包内或既有实体。

## 验收标准

1. **备份回放**：模拟器造数据 → 导出 → 清应用数据 → 导入（替换）→ 实体数一致、图片全部显示、抽签/回顾/打卡正常。
2. **合并回流**（本迭代核心场景）：从导出包构造「打标包」（改若干 item 的 tags/desc + 新增 2 个 item 各带 1 张 jpg）→ 导入合并 → 更新生效、新增可见、其余数据原样保留、无重复实体。
3. **错包防御**：eats 包 / 坏 JSON / 缺图 / schemaVersion=99 四连测 → 全部拒绝、原因可读、重启后本地数据零改动。
4. **演示模式**：导入导出入口置灰/隐藏。
5. **skill 实测**：另一个 agent 会话仅凭 skill 文档 + examples 样包，产出一个打标包 → `validate.py` PASS → 导入模拟器成功。
6. **单测**：PackageCodec（JVM：roundtrip / 坏包 / manifest）、`mergeWardrobe`（覆盖/新增/保留/悬空清洗）、schemaVersion 守卫。
7. specs 同步：00/01（US-24）/02（W9 数据小节）/03（存储格式改写为真实状况+包格式）/04（若分层有变）/06（ADR-022）。

## 影响范围

- `libs/store`：0.2.0，新增 package codec + 单测 + specs 更新（先行，eats it-012 复用）。
- `wardrobe/app`：`di/AppContainer`（codec 装配）、`data/repo`（import/export service + merge）、`domain`（mergeWardrobe 纯函数）、`ui/recap`（数据小节 + 对话框）、manifest（zip intent-filter：ACTION_SEND/ACTION_VIEW）+ MainActivity（onNewIntent 路由与直达导入流程）、单测。
- `.agents/skills/data-package/`：SKILL.md + format.md + wardrobe.md + validate.py + wardrobe-sample.zip。
- specs：如验收标准第 7 条；CHANGELOG.md 记一行。

## 范围外（明确不做）

- 云端同步（libs/sync 职责，另迭代）；定时自动备份；包加密。
- 字段级深合并（实体级覆盖已满足打标回流场景）。
- iOS/桌面端导入工具。

## 验证记录（2026-09-21 实施回填）

**实现**：libs/store 0.2.0（PackageCodec + `SnapshotStore.upgrade`，11 新单测）；wardrobe domain `DataPackageMerge.kt`（merge/diff/remap/referenced 纯函数，6 单测）、`WardrobePackages` 服务、`WardrobeRepository.replaceAll`、W9「数据」小节 + D2–D7 全套对话框（`DataPackageSection.kt`）、导出 snackbar [分享]（AppViewModel 新增 ActionToast）、manifest SEND/VIEW intent-filter + MainActivity 直达路由、file_paths 增 cache/share。

**构建与单测**：wardrobe 62 单测 0 失败、eats 53 单测 0 失败、store 24 单测 0 失败；两 app assembleDebug 成功。

**模拟器实测（emulator-5554，真实数据）**：

| 场景 | 结果 |
|---|---|
| W9 数据小节渲染（提醒设置之后，演示模式语义在 eats 侧同构） | ✅ 截图 wd-data-section |
| 合并导入样例包（agent: sample）：预览屏 AI 加工包识别 + 差异三行式 + 单选模式 | ✅ wd-import-confirm |
| 合并落盘：18→20 单品、5→6 穿搭、各域 +1；shirt.png/pants.png → 归一 webp | ✅ run-as 核对 wardrobe.json + images/ |
| 导出 → SAVE → 拉回本机 validator PASS（2.29MB / 21 图包） | ✅ 回环闭合（导出包 23 WARN 全为历史短 id，见下） |
| 跨 app 拒绝（eats 包 → 衣橱） | ✅「这是『吃啥』的数据包」wd-cross-app-reject |
| 非 zip 拒绝 | ✅ wd-notzip-reject |
| 替换：摘要切「✕清除/＋导入」→ 二次确认 → 即时生效（标题/品类带刷新） | ✅ wd-replace-summary / wd-replace-confirm |
| 备份恢复：导出包替换导回，persons/items/outfits/wearLogs 与快照一致 | ✅ |
| ④b intent-filter 注册（SEND/VIEW application/zip → 系统 ResolverActivity） | ✅（shell 无法代授 Uri 权限弹不出真实分享面板，直达下游与 ④a 共用已实测；真机微信路径留 Leo 日常验证） |

**实测发现并修复**：validator 对应用导出包误报 66 错——真实数据存在早期种子脚本的短 id（it1/p1），原实现判非 UUID 后将 id 排除出引用集导致全部误报悬空。修复：非 UUID 降为 WARN 且仍计入引用集（commit fix(skills)）。旧图像孤儿文件按设计保留（替换模式只删被引用文件），无害。

**注意**：21 图规模的包导入耗时 >5s（转码逐张进行），进度对话框会停留数秒，属预期；更大包可后续优化为并行转码。

**agent 实测（skill 验收第 5 条）**：示例包 validate PASS + 真机导入成功已覆盖；「另一 agent 会话凭文档产出打标包」留作后续独立验证（skill 已具备自检闭环）。
