---
name: data-package
description: 为 mini-apps 的「衣橱 wardrobe」「吃啥 eats」两个 Android 应用产出可导入的结构化数据包（zip = manifest.json + 应用 JSON + images/）：批量打标/补字段/清洗/新增条目（含图片），或从用户导出的备份包加工后再导回。当用户说「帮我给衣橱数据打标再导回」「整理一批餐厅数据导入吃啥」「AI 加工数据包」「批量录入衣物/食堂」时使用。
---

# Data Package · 给衣橱/吃啥产出可导入的数据包

把「收集一批原始数据 → AI 加工 → 整包导回 app」做成可靠流水线。**出口只有一个：
一个能通过自检并成功导入 app 的 zip。**

```
0 问清目标应用 → 1 拿到导出包 → 2 解包读懂 → 3 按分册加工
→ 4 validate.py 自检至 PASS → 5 交付 zip + 导入指引
```

## 阶段 0 · 目标应用与任务

两应用数据结构完全不同，先确定给谁产包：**wardrobe（衣橱：角色/单品/穿搭/打卡/心愿）**
或 **eats（吃啥：食堂去处/吃饭记录）**。不确定就问；用户可能两个都要（分两个包交付）。

常见任务与产出形态：

| 任务 | 做法 |
|---|---|
| 批量打标（用户给一批照片+描述） | 新增实体：每件生成 UUID，图片改名后放 `images/`，字段按分册填 |
| AI 加工已有数据（用户给导出包） | 改 tags/desc/color/cuisine 等允许字段，**不改 id 与时间戳** |
| 外部收藏夹/订单截图整理 | 逐条转成实体；图片可省略（用占位由 app 处理），有图放 `images/` |
| 清洗（去重/归一品类/规范命名） | 只改内容字段；删除实体需用户明示 |

## 阶段 1 · 拿数据

- 用户已给 zip → 直接用。
- 没有 → 指导用户在 app 内导出：**回顾页（📊）→ 底部「数据」→ 导出数据包**，
  然后把 zip 发来（微信/隔空投送均可）。导出包是最可靠的字段范本。

## 阶段 2 · 解包读懂

```bash
unzip -o package.zip -d work/     # manifest.json + wardrobe.json|eats.json + images/
cat work/manifest.json
```

**必读分册**（加工规则、字段表、枚举字面量都在里面，不要凭记忆写）：

- 通用包格式与放宽项：`docs/format.md`
- 衣橱字段与加工守则：`docs/wardrobe.md`
- 吃啥字段与加工守则：`docs/eats.md`

`examples/` 有两个最小合法包，结构照它们抄。

## 阶段 3 · 加工

铁律（详细守则见分册）：

1. **已有实体不改 id**——id 是合并键，改了 = 旧实体保留 + 新实体重复。
2. **新增实体必须生成 UUID v4**（小写十六进制）。
3. **主观字段不要碰**：评分、花费、种草/安排状态、打卡记录——这些是用户自己的事实。
4. **枚举一字不差**：衣橱品类用 `TOP/OUTERWEAR/...`（英文），不是中文标签。
5. **图片**：放 `images/`，文件名 `[A-Za-z0-9._-]+`，jpg/png/webp 都行；
   JSON 里引用**包内文件名**。导入时 app 会转码归一，无需自行压缩到很小。
6. **时间戳**：epoch 毫秒。新增实体的 createdAt/updatedAt 用生成时刻；
   既有实体的时间戳原样保留。
7. manifest.json：`packageFormat:1`、`app` 对、`generator` 写明工具名
   （如 `"agent: gpt-5"`）、`counts` 如实填。

## 阶段 4 · 自检（不过不交付）

```bash
python3 .agents/skills/data-package/tools/validate.py <包.zip> --app wardrobe   # 或 eats
```

规则与 app 导入预检**同源**（缺图/坏枚举/断引用/坏 UUID 都会 FAIL）。修到 PASS 为止；
WARN 可以接受但要看一眼。改包后重跑。

## 阶段 5 · 交付

1. 给用户 zip（命名建议 `<app>-ai-<任务>-<日期>.zip`，如 `wardrobe-ai-打标-20260921.zip`）。
2. 附导入指引（一句话）：**把 zip 发到手机 → 微信里点开选「用其他应用打开 → 衣橱/吃啥」**，
   或 app 内 回顾页 → 数据 → 导入数据包；选**合并**（默认）即可，重复导入同一包是安全的。
3. 提醒：合并模式下包内版本会覆盖 app 内同 id 实体；如果用户导出后在 app 里改过数据，
   预览屏会有警示行，让 TA 自己确认。

## 脚本

- `tools/validate.py`：唯一校验器（python3 标准库，无依赖）。
- `tools/make_examples.py`：重新生成 `examples/` 两个样例包（改了 schema 后跑它刷新）。
