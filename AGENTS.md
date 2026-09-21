# AGENTS.md — 仓库协作铁律

本仓库面向「人 + AI 协作者」，采用 **spec-driven** 工作流。任何协作者（包括未来任何 AI 会话）在本仓库工作前必须先读完本文。

## 迭代流程（功能开发的标准循环）

```
新需求
  → ① 在 应用名/specs/iterations/ 新建 it-XXX-slug.md
      写清楚：背景与动机、涉及的用户故事（US 编号）、验收标准、影响范围
  → ② 用户确认提案后才开始写代码
  → ③ 实现：代码 + 同步更新受影响的常青 spec（见下）
  → ④ 验证：构建/测试/截图，结果回填 it-XXX 文件的「验证记录」小节
  → ⑤ CHANGELOG.md 记一行；git 提交（规范见下）
```

**纯 bugfix 可跳过①**，但必须在 it-XXX（或新建 it-XXX-hotfix）回填问题与修法。

## 常青 spec（代码合入时必须保持一致）

以 `wardrobe/` 为例（其他应用同理）：

| 文件 | 内容 | 何时更新 |
|---|---|---|
| `specs/00-overview.md` | 业务背景、核心价值流 | 业务方向变化时 |
| `specs/01-user-stories.md` | US/UC 编号 + 验收标准 | 新增/修改用户故事 |
| `specs/02-wireframes.md` | 线框 W1–W8 + 交互路径 | 界面/交互变更 |
| `specs/03-data-model.md` | ERD、字段、枚举、存储格式 | 实体/字段/存储变更 |
| `specs/04-architecture.md` | 分层、模块、设计模式、依赖规则 | 结构性变更 |
| `specs/05-design-system.md` | 视觉 token、动效清单 | 视觉/动效变更 |
| `specs/06-decisions.md` | ADR 架构决策记录 | 每个不可逆技术决策 |

**spec 与代码不一致视为迭代未完成。**

## 提交规范

```
feat|fix|docs|spec|chore(scope): 一句话描述 (#it-XXX)
```

- scope = 应用名（如 `wardrobe`）
- 纯文档改动用 `docs` 或 `spec` 前缀
- 一次提交尽量对应一个迭代内的完整步骤

## 上下文恢复（接手任意工作前）

1. 读 `README.md`（仓库总览）→ 目标应用 `README.md`（构建方式）
2. UI/交互相关任务先读根目录 [`DESIGN.md`](DESIGN.md)（跨应用质量基准：硬 token/动效规则/触感基线/反例清单/评审 query）
3. 读该应用 `specs/00-overview.md` → `01-user-stories.md` → 与任务相关的常青 spec
4. 读 `specs/iterations/` 里最近一次迭代，了解当前进度与遗留问题
5. 再读代码

## 其他

- 不要在应用目录外放应用代码；不要在根目录放业务逻辑。唯一例外：跨应用公共 SDK 放 `libs/`（如 `libs/store`、`libs/sync`），经 composite build（includeBuild）接入各应用，同样 specs 先行（`libs/<名>/specs/`），依赖方向只能「应用 → libs」「libs → libs 的 contract」，不得反向。
- 依赖变更、版本升级必须记录 ADR（06-decisions.md）。
- 演示/测试数据的生成脚本放 `应用名/tools/`，并可在 it-XXX 中引用。
