# it-002 — 多内容源架构 + 小米 MiMo TOKEN Plan 接入

状态：**实施中（Leo 直接下单「你来实现这个功能」，视为确认）**
日期：2026-09-23

## 背景与动机

Leo 同时持有 GLM Coding Plan 与小米 MiMo TOKEN Plan 两份套餐，希望灵岛支持**多内容源**并可
**切换查看**。这同时验证 it-001 预留的 `UsageProviding` 协议缝与 ADR-008 的多厂商定位。

## MiMo 数据源调研（spike 2026-09-23 实测）

- 端点：`GET https://platform.xiaomimimo.com/api/v1/tokenPlan/usage`
- 认证：**仅浏览器 Cookie**（`api-platform_serviceToken` / `userId` / `api-platform_slh` / `api-platform_ph`）；
  tp- 开头的 plan key 以 Bearer/裸/x-api-key/query 等形态均 401 跳小米 SSO（该 key 是调模型 API 用，
  控制台接口不认）→ **MiMo 凭证 = Cookie 字符串，会过期需在设置里更新**（UI 需明示）
- 返回结构（HTTP 200）：

```json
{ "code": 0, "message": "",
  "data": {
    "usage":   { "percent": 0.01, "items": [
        { "name": "plan_total_token", "used": 5387634858, "limit": 456000000000, "percent": 0.01 },
        { "name": "compensation_total_token", "used": 0, "limit": 0, "percent": 0 } ] },
    "monthUsage": { "percent": 0.0118, "items": [ { "name": "month_total_token", ... } ] } } }
```

- `percent` 为**小数比例**（0.0118 = 1.18% 已用），剩余 = (1 − percent) × 100
- `usage` 为套餐当期窗口，`monthUsage` 为当月统计（当前两值相同）；`limit=0` 的条目（补偿包）跳过
- **无重置时间字段**（行不展示重置时间）

## 用户故事

- **US-7 多内容源切换**：展开卡片顶部提供内容源切换 chips（GLM / MiMo），点击即切换；
  菜单栏提供同款单选；每个源独立快照缓存，切换即时呈现缓存并后台刷新。
- **US-8 MiMo 接入**：设置页新增 MiMo 区（粘贴 Cookie 字符串，存钥匙串 account `mimo-cookie`，
  UI 注明 Cookie 会过期）；卡片展示 MiMo 档位剩余%（恒定身份色，无重置时间行）；
  Cookie 失效时页脚明示「MiMo Cookie 已过期，请更新」。

## 验收标准

- AC1 切换 chips 点击即换源，快照独立、互不覆盖；菜单栏单选与 chips 状态一致。
- AC2 MiMo 行数值与平台控制台一致（剩余% 向下取整）。
- AC3 GLM 源行为与 it-001 完全一致（回归）。
- AC4 MiMo Cookie 失效/缺失有明确文案引导，不崩溃。
- AC5 凭证不入仓库/日志；单测含 MiMo 真实结构 fixture。

## 影响范围

ProviderKind 枚举 + 按源快照缓存（snapshot-<kind>.json）+ 按源钥匙串 account
（glm-key / mimo-cookie；迁移旧 api-key → glm-key）+ 卡片 chips 行（高度公式 +26）+
设置页 MiMo 区 + 菜单栏内容源单选。

## 验证记录

2026-09-23 实施完成：

- **架构**：`ProviderKind`（glm/mimo）+ `ProviderUsageFetcher` 按源分发（GLM=API Key 双端点，
  MiMo=Cookie 单端点）；快照按源独立缓存（snapshot-<kind>.json）；钥匙串按源账户
  （glm-key / mimo-cookie，it-001 旧 api-key 自动迁移）；切换即时呈现缓存并后台刷新。
- **UI**：卡片顶部内容源 chips（GLM/MiMo，点击切换、激活高亮）；菜单栏「内容源」单选同步；
  设置页新增 MiMo Cookie 区（含过期说明）。
- **实机验证**：CGEvent 点 MiMo chip 切换成功——GLM 源 97%/5%（滚动窗口真实波动），
  MiMo 源 99%（Cookie 拉取平台真实数据，剩余=(1−percent)×100）；两源快照独立、互不覆盖。
- **测试**：swift-testing **17/17**（新增 MiMo 解析 fixture：percent 小数比例、补偿包 limit=0 跳过、
  envelope 401/缺 data 报错）。
- **体验增补 4（Leo 反馈「老弹钥匙串密码」「MiMo 要展示已用 token（B）」）**：
  凭证迁出钥匙串改 0600 本地文件（ad-hoc 重签名导致 ACL 每版失效的弹窗根治，首启自动迁移）；
  MiMo 图例新增「已用 5.58B / 456B」（billion，≥100B 无小数）；环心对齐、小百分比最小可见弧、
  底轨描边降噪、图例层级微调。
- **展示形态增补 3（Leo 反馈「并排放两个环形图，做精致点」）**：并排双源面板——
  GLM 双环面板 + MiMo 单环面板，白 4.5% 圆角底+描边、环心标识、面板内图例、定高对称；
  未配置源显示面板内引导。
- **展示形态增补 2（Leo 反馈）**：去掉切换 chips 全部同屏展示；三环（5小时/每周/MiMo）；
  **颜色=健康度**（≥50% 绿 / 20–50% 橙 / <20% 红，环与图例同色）——修正身份色橙在 99% 时像不健康的违和。
- **展示形态增补（Leo 反馈）**：明细改 Apple 健康式同心环（环填充=剩余量，环心=内容源名，
  图例行右侧），线性进度条废弃；GLM 双环（蓝外/绿内）与 MiMo 单橙环均实机截图验证。
- **凭证安全**：Cookie/Key 只入钥匙串；tp- plan key 经 Bearer/裸/x-api-key/query 实测均 401
  （它是模型调用 key，控制台接口只认登录态），故 MiMo 采用 Cookie 方案并在 UI 注明过期需更新。
