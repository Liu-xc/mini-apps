# it-002 · 飞书多维表格云同步（调研与提案）

- **状态**：调研完成 → SDK 已实现（2026-09-20：libs/store 落地并接入 wardrobe；libs/sync 契约 + bitable 适配器就绪、按计划未接入 App）。App 接入（US-15a/b/c）与真机实测清单留待后续迭代
- **提案日期**：2026-09-19
- **范围**：多端数据同步 —— 以飞书多维表格（Bitable）作为云端同步后端

## 背景与动机

it-001 是纯本机存储（JSON + WebP，无账号无服务端），「云端同步」当时列为范围外。现在出现真实诉求：

1. **多端录入**：本人与家人希望在自己的手机上各自录入/浏览，数据互通（当前多角色是同机隔离的）。
2. **换机/备份**：本地 zip 导出是手动动作，希望有一个常驻的云端副本。
3. 候选方案：直接用**飞书多维表格**当数据库 —— 免费国内直连、自带移动端可查数据、结构化 CRUD。

核心疑问（本次调研要回答）：**是否必须依赖飞书登录？API 能力与限额是否够用？同步引擎要自己做到什么程度？**

## 调研结论（2026-09-19）

### 1. 鉴权：App 内不需要飞书登录 ✅

| 方式 | 说明 | 适用 |
|---|---|---|
| `tenant_access_token`（应用身份） | 在[飞书开放平台](https://open.feishu.cn)创建**企业自建应用** → 拿 App ID/App Secret → 开通多维表格权限 → 在多维表格网页版通过「··· 更多 → 添加文档应用」把应用**加为协作者**。之后 App 用凭证直换 token 读写表格 | **本场景推荐**：单用户自用，无登录 UI |
| `user_access_token`（OAuth 登录） | 用户扫码/授权登录飞书 | 多用户产品才需要，本场景不用 |

- 个人手机号注册飞书即视为创建了一个企业，**自己就是管理员**：可自由创建自建应用、自行审批权限，免费，无需企业认证。
- 代价：App 内需要保管 `app_secret`。自用 App 可接受；建议做成**设置页让用户手填四元组**（app_id / app_secret / app_token / table 前缀），存 DataStore，不硬编码进 APK 分发。
- 新 US 草案（US-15）：设置页「云同步」—— 填入飞书凭证 → 测试连接 → 开关同步。

### 2. API 能力：覆盖本项目全部实体 ✅

- **完整 CRUD**：记录 列出/检索/新增/批量新增/更新/批量更新/删除；批量单次上限 1000 条（整批成败）。
- **检索**：单次最多返回 500 行，支持 filter 条件 → 可按「修改时间」做**增量拉取**（每张表加一个系统「最后更新时间」字段即可）。
- **图片附件**：两步流程 —— `POST /drive/v1/medias/upload_all`（multipart）拿 `file_token` → 写入记录的附件字段。下载接口可取回（临时 URL 有时效，App 端以本地 webp 缓存为准）。
- **无官方增量推送给客户端**：事件订阅/长连接主要面向服务器场景；手机端用「启动时 + 手动刷新」轮询增量即可，本场景数据量完全够。
- 官方明确建议**对单个多维表格同时只发一次写操作** → 同步层必须串行写队列。

### 3. 限额：个人衣柜规模远够用 ✅

| 维度 | 免费版限额 | 本项目预估 |
|---|---|---|
| 单表行数 | 约 2 万行（随套餐不同，表格 UI 内可查确切值） | 单表最多数千行 |
| 每表字段数 | ≤ 300 | ≤ 15 |
| 云空间存储 | 15 GB | 数千张 WebP（每张约 100–300KB）< 1GB |
| API 频控 | 每 API×每应用×每租户；批量写约 10 QPS；返回 429/`99991400`，响应头带重试秒数；**多维表格不支持申请提频** | 个人使用量远低于阈值，做好指数退避即可 |
| API 费用 | 免费 | — |

### 4. 数据模型映射草案（4 张数据表）

| 表 | 字段（Bitable 类型） | 备注 |
|---|---|---|
| Persons | id(文本) name emoji createdAt(日期/数字) | 行数极少 |
| Items | id personId category name color desc tags(**多选**) image(附件) createdAt updatedAt | 附件即单品图 |
| Outfits | id personId itemIds(**文本，存 JSON 数组**) tags(多选) effectImages(文本 JSON) createdAt updatedAt | 不用关联字段（API 读写关联字段繁琐），id 数组字符串更简单 |
| Notes | id parentType parentId text createdAt | |

- 软删 `deletedAt` 字段（tombstone）代替物理删除，用于多端删除传播。
- `schemaVersion`、组合记忆（DataStore）等纯本地状态**不参与同步**。

### 5. 同步架构：轻同步方案（2026-09-20 按用户定调定稿）

前提（用户确认）：**实时性要求低、几乎不存在并行编辑、核心诉求是多人分发**。据此砍掉重型同步引擎——无字段级合并、无 tombstone 表、无事件订阅/长连接，改为「云端为正本 + 本地缓存 + 记录级覆盖」：

```
云端（多维表格）= 正本          本地 JSON/WebP = 缓存 + 离线兜底（it-001 机制原样保留）

上行（写路径）：本地改动 → 快照更新/落盘（现状不变）→ 待推队列(upsert/delete)
              联网时批量推送（batch ≤1000，串行，429 指数退避）
下行（读路径）：启动时 + 下拉刷新 → 按「最后更新时间」增量拉（首装全量）
对账：以云端 id 集合为准，删除本地多余记录 ← 代替 tombstone（数据仅数千行，对账廉价）
冲突：记录级 LWW，整行覆盖（并行概率≈0，用户已接受）
图片：附件为正本；上行串行排队（5QPS 限额），下行按需下载 + LRU 缓存
```

工程量重估：相比原草案（字段合并 + tombstone + dirty 语义），轻同步约砍掉一半同步层代码；剩余主要工作 = Retrofit 封装 ~8 个端点、实体↔记录映射、对账循环、待推队列持久化。

分层落位：`data/sync/`（SyncEngine + FeishuBitableApi + DTO 映射），domain 的 `WardrobeRepository` 接口不动，UI 加设置页入口（连接配置 / 同步状态 / 手动同步）。**遵守 04-architecture 依赖规则。**

### 6. 风险与代价（必须向用户明示）

1. **同步引擎是本项目迄今最大的一块新增代码**（dirty 队列、tombstone、LWW 合并、退避重试、图片排队）——近似自研一个 mini 同步协议，预计工作量 ≥ it-001 的数据层+UI 层之和。
2. **平台依赖**：数据活在飞书云上；API 语义、频控、附件行为可能变化（且不支持提频）；飞书故障则同步暂停（本地不受影响）。本地 zip 备份通道保留，作为数据主权兜底。
3. **附件吞吐**：每张图一次 multipart 上传，频控下批量导入需排队限速（首次全量上传几百张约需数分钟，可接受）。
4. **app_secret 保管**：仅自用 + 设置页录入，不进版本库、不打进公开 APK。

### 7. 替代方案对比（为何仍推荐 Bitable）

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **飞书多维表格** | 免费国内直连、结构化 CRUD、飞书 App 内可直接浏览/筛选衣物表、零运维 | 频控不可提额、附件上传慢、需自建同步层 | **推荐** |
| WebDAV（坚果云） | 实现最简单（同步 zip/JSON 文件） | 整文件覆盖无冲突处理，多端并发写会丢数据；图片包越来越大 | 备选（可作为"懒人方案"先做） |
| Syncthing | P2P 无云端、数据全在自己设备 | 安卓后台常驻受限，每台设备都要装要配 | 不推荐 |
| Supabase / Firebase | 真正的数据库语义、实时订阅 | 海外网络不可靠/延迟；要管账号与账单 | 不推荐 |

### 8. 补充调研（2026-09-20）：凭证模型、分享协议与多人协作

前提澄清：使用者均为**飞书个人用户**（无企业版）。个人账号注册即拥有自己的个人租户，可在开放平台建自建应用。

**连接一张表需要 3 个参数**（不止 AppID/Secret 两个）：

| 参数 | 说明 |
|---|---|
| app_id + app_secret | 换取 `tenant_access_token`（应用身份） |
| app_token（目标表格） | 表格 URL 中可直接解析，用户粘贴链接即可自动提取 |
| table_id | 无需用户填：首次连接按表名自动发现，不存在则自动建表 |

无官方 Android SDK；官方 oapi-sdk-java 未针对安卓优化，建议 Retrofit + OkHttp 直接封装 ~7 个 REST 端点（token、list tables、search/list records、batch create/update/delete、media upload）。

**分享协议（"发个 URL/二维码，对方秒连这张表"）：机制上完全可行。**
二维码内嵌 `{app_id, app_secret, app_token}`，对方 App 扫码即连——**对方连飞书账号都不需要**，因为凭证本身就是身份，飞书的租户/用户体系被整体绕开。但必须清楚它的安全语义：

- 等价于分发**主密钥**：持凭证者可读写删这张表的全量数据；
- 无法按人吊销：唯一的"踢人"手段是重置 app_secret（全体下线）；
- 飞书侧无法区分是谁在改（审计盲区）。

缓解设计：**只读分享用"二号应用"**——分享者另建一个只开通 `bitable:app:readonly`（查看/评论/导出）权限的应用，把只读凭证外发，读写凭证自留。

「自己的数据 + 别人分享来的数据」两源并存：App 挂载多个数据源（每源一套凭证 + app_token），本地记录打 origin 标记，别人分享的源只读浏览。可行，但 UI 与同步层复杂度明显上升 → **列为 it-003+ 候选，it-002 先做单源**。

**多人协作的四种模型（按个人用户场景排序）：**

| 模型 | 做法 | 身份/权限 | 适用 |
|---|---|---|---|
| M1 共享凭证 | 全家 App 填同一套 owner 凭证 | 飞书视角所有变更来自"应用"；可在记录自报 personId/device（软身份） | 家人都要用我们的 App |
| **M2 飞书侧文档协作（推荐）** | 表分享给家人的个人飞书账号（可编辑），家人用飞书 App 内的多维表格界面增删改/传图，我们的 App 统一同步 | 飞书文档权限承担：按人授权、按人吊销 | 家人轻度参与，零凭证外发 |
| M2.5 各自凭证挂同一张表 | 家人拿到表的「可管理」权限后，尝试把**自己的**自建应用加为文档协作者 | 同 M1，但各用各的凭证 | ⚠️ 社区信息称可行、未官方确认，需实测 |
| M3 OAuth 登录 | 家人各自登录飞书授权 | 真实个人身份 | ❌ **修正前轮说法**：自建应用的 OAuth 只能授权本租户成员，各自独立的个人账号之间走不通；除非全家加入同一组织 + 登录流，成本最高，不建议 |

**it-002 待实测清单**（深度调研后仅剩 5 项，预计合计 1 小时）：

1. ✅ ~~个人能否建应用~~ → 官方确认：个人可免费创建自己的租户成为"企业用户"，自己即管理员（[企业自建应用开发流程](https://open.feishu.cn/document/home/develop-a-self-built-app/develop-process)）
2. ✅ ~~权限生效机制~~ → 官方确认：需「创建版本 → 发布 → 管理员审批」，个人场景自己批自己
3. ✅ ~~增量拉取~~ → 官方确认：`records/search` + filter 系统字段「最后更新时间」(LastModifiedTime) `>=` 上次同步毫秒时间戳 + 分页（[筛选参数说明](https://open.feishu.cn/document/server-docs/docs/bitable-v1/filter-parameter-description)）
4. ✅ ~~附件上传~~ → 官方确认：`medias/upload_all` 单文件 ≤20MB（本项目 WebP 单图几百 KB），`parent_type=bitable_image`，频控 5 QPS / 10000 次/天（[素材上传文档](https://open.feishu.cn/document/server-docs/docs/drive-v1/media/upload_all)）
5. ⬜ 30 分钟真机走通全链路：注册 → 建应用 → 发版 → 挂协作者 → 一条记录读写（注意[个人账号权益](https://www.feishu.cn/hc/zh-CN/articles/900832687008)，行数上限以表格 UI 内显示为准）
6. ⬜ M2.5：家人以「可管理」权限尝试挂**自己的**应用到我的表（仅有[社区线索](https://www.yingdao.com)，无官方文档）
7. ⬜ 只读二号应用（`bitable:app:readonly`）+ 外发凭证实测
8. ⬜ 独立版多维表格（base.feishu.cn）协作实测：家人无飞书账号经链接参与编辑的行为与权限边界

### 11. 国内「客户端直连」在线存储方案横向对比（2026-09-20 补充）

先澄清一个关键误解：**本地安卓 App 完全可以直接访问飞书多维表格**——调用链就是 `App --HTTPS--> open.feishu.cn/open-apis/...`，全程无自建服务器。"没有官方安卓 SDK"仅指需用 Retrofit 自行封装 ~8 个 REST 端点，不是访问障碍。

| 方案 | 客户端直连 | 免费层 | 结构化查询 | 图片存储 | 用户身份 | 备注 |
|---|---|---|---|---|---|---|
| **飞书多维表格** | ✅ REST | ✅ 限额内全免（单表约 2 万行 / 云空间 15GB / API 免费） | filter 检索（500 行/次） | ✅ 附件字段（两步上传） | ❌ 仅应用身份 | 附带 M2 家人协作通道（飞书 App 直接编辑）；批量写需串行 |
| LeanCloud | ✅ 官方安卓 SDK | 开发版免费（API 约 3 万次/天） | ✅ 真查询语言 | ✅ 文件存储 | ✅ 内置用户系统（可选） | 国内最老牌 BaaS；SDK 6.1.0+ 支持仅 AppID 安全初始化；公司体量小，政策有过调整 |
| 腾讯云开发 CloudBase | ✅ 官方安卓 SDK | ❌ 2025-09 起基础套餐+按量，个人版约 19.9 元/月起（免费环境仅新用户体验） | ✅ 文档数据库 | ✅ 存储 | ✅ | 大厂背书最稳；要实名+绑腾讯云 |
| 维格表 Vika | ✅ REST | 免费档 API 仅 2 QPS | 检索 | ✅ 附件 | ❌ | 与飞书同类且更弱；开源版 APITable 可自部署 |
| 坚果云 WebDAV | ✅ WebDAV | 免费账户流量约 上传 1GB/下载 3GB 每月 | ❌ 整文件 | 文件 | ❌ | 实现最简单，多端并发覆盖会丢数据 |
| Supabase / Firebase | SDK | 有 | ✅ | ✅ | ✅ | ❌ 海外：Firebase 国内不可达；Supabase 延迟/账号风险，不适合家庭多端 |

结论：无论哪家，**「离线可用 + 多端合并」的同步引擎都要自己写**——BaaS 只标准化了存储与查询，冲突合并仍留给我们。选型差异在于：

- 追求**免费 + 附带飞书侧协作通道** → 维持飞书多维表格（it-002 现方案）；
- 追求**真数据库语义 + 官方 SDK + 查询无 500 行/次限制**，接受花钱/供应商绑定 → CloudBase（稳）或 LeanCloud（免费档）；
- LeanCloud/CloudBase 相对 Bitable 的真实收益：对象级 updatedAt、批量保存、指针关联让同步层略薄；代价是失去「家人在飞书 App 里直接编辑」这条免费协作线（BaaS 的用户系统要做 OAuth/登录 UI，又回到登录问题）。

### 12. 端到端联通链路（个人账号，2026-09-20 深度验证）

```
① 手机号注册飞书（自动获得个人租户；也可免费创建一个自己的企业租户）
② open.feishu.cn 开发者后台 → 创建企业自建应用 → 凭证页拿 App ID / App Secret
③ 权限管理 → 开通「查看、评论、编辑和管理多维表格」(bitable:app)
④ 版本管理 → 创建版本 → 发布 → 自己（管理员）审批通过 ← 权限此时才生效
⑤ 飞书网页版新建多维表格《衣橱》→「···更多 → 添加文档应用」→ 选自己的应用 → 可编辑
⑥ App 设置页：粘贴表格链接（自动解析 app_token）+ 填 App ID/Secret → 测试连接
⑦ 首次同步：按表名自动发现/创建 Persons/Items/Outfits/Notes 四张表 → 全量上传
```

关键机制全部有官方文档背书：个人可[免费创建自己的租户](https://open.feishu.cn/document/home/develop-a-self-built-app/develop-process)成为企业用户（无企业认证要求）；权限变更必须走发版+审批才生效；应用挂协作者要求对表格有管理权限（自己的表天然满足）；token 用 `POST /open-apis/auth/v3/tenant_access_token/internal`。

**利好（2025-08/09 起）**：多维表格已独立成产品（base.feishu.cn，可脱离飞书套件使用），家人协作**无需安装飞书 App、无飞书账号可经链接参与**（具体权限边界见待实测第 8 项）——M2 协作通道的参与门槛大幅降低。

错误码预案（App 需优雅处理）：`99991400`/HTTP 429 = 限流（按响应头 reset 秒数退避）；`1061073 no scope auth` = 权限未生效（提示去发版）；`1061061` = 个人云空间超限。

**上手链接清单**（按 §12 顺序）：

1. 注册/登录飞书（手机号，自动获得个人租户）：https://www.feishu.cn/
2. 开发者后台 → 创建企业自建应用 → 凭证页拿 App ID/Secret：https://open.feishu.cn/app
3. 应用内「权限管理」→ 搜索"多维表格" → 开通读写权限（传附件如报 `1061073`，按提示补开云文档素材权限）
4. 「版本管理与发布」→ 创建版本 → 发布 → 自己审批通过（参考[发版审核指南](https://www.feishu.cn/hc/zh-CN/articles/374230668270)）
5. 新建多维表格《衣橱》：https://base.feishu.cn/ （表格 URL 中 `/base/` 后面那段就是 app_token）
6. 表格网页版「··· → 更多 → 添加文档应用」→ 选自己的应用 → 权限可编辑
7. 终端验证两连：换 token → 列表（返回 200 即全链路通）：
   `curl -X POST https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal -H 'Content-Type: application/json' -d '{"app_id":"cli_xxx","app_secret":"xxx"}'`
   `curl -H "Authorization: Bearer t-xxx" https://open.feishu.cn/open-apis/bitable/v1/apps/{app_token}/tables`

### 13. 协作与分享的推荐组合（结论）

| 通道 | 谁 | 怎么连 | 吊销 |
|---|---|---|---|
| **M1 主通道** | 你 + 愿装 App 的家人 | 扫二维码（内嵌 app_id/secret/app_token），无需任何飞书账号 | 只能整体轮换 secret |
| **M2 辅通道** | 不装 App 的家人 | 表格「分享」→ 添加协作者/链接分享（可阅读/可编辑），在多维表格独立版或飞书 App 里直接增删改传图 | 飞书文档权限按人移除 |
| 只读外发 | 朋友 | 二号 readonly 应用的凭证二维码 | 轮换二号应用 secret |

两通道写同一张表：家人在飞书 UI 的手改会更新行的「最后更新时间」，我们的增量拉取自然汇合（M2 手改带其飞书身份，M1/App 写入显示为"应用"）。归属细化：App 记录内自报 personId/device 软身份。

**2026-09-20 用户定调：分发是第一优先级** → 二维码快速接入从"待确认"升级为 it-002 范围内交付（US-15c）；M1/M2 双通道并行支持。只读二号应用（朋友外发）列为本迭代可选项。

### 14. 全云端存储形态（回答「未来数据都在云端」）

采用 **云端 SSOT + 本地可丢弃缓存**（cloud-first with local cache），而非 cloud-only：

- **云端（飞书表格）是唯一可信正本**：元数据 + 图片附件全量在云。任何一台设备丢失/损坏/换新，装 App 填凭证即全量恢复。
- **本地 JSON/WebP 从「主存储」降级为「缓存 + 离线兜底」**：离线照常全部功能可用（写入进本地队列），联网后补推；图片按需下载 + LRU 清理。
- 不选 cloud-only 的理由：纯云每次操作走网络，电梯/地铁/弱网体验崩坏；个人工具不值得。缓存一致性由同步引擎（§5）维护，复杂度与 cloud-only 相同。
- **容量测算**：个人云空间 15GB ≈ 单图 300KB 可存 5 万张，本项目峰值用不到 10%。行数风险点在 Notes（按每天 10 条评论 ×10 年 ≈ 3.65 万行，可能触顶免费版约 1–2 万行/表的口径）→ 缓解：Notes 按年分表或定期归档导出。
- **数据主权兜底**：现有 zip 导出保留，另加「从云端全量导出」——数据本来就是我们自己的结构，随时可整体拉走迁移到任何底座（含未来换 LeanCloud/CloudBase）。

### 15. SDK 化路径（2026-09-20 记录：预判此能力为 mini-apps 公共底座）

用户判断：飞书多维表格接入可能成为本仓库多个 app 的公共能力（潜在消费方：wardrobe 现在需要；eats、clips 未来；clips 的 Mac SwiftUI 端则按契约独立实现）。

**分层边界（it-002 起即按此实现，暂不物理拆库）**：

```
┌ 通用层（未来 SDK 内容，零业务概念）────────────────┐
│ FeishuAuth      token 获取/缓存/2h 自动续期          │
│ BitableClient   ~8 个 REST 端点（表/记录/附件上传）  │
│ RateLimiter     串行写队列 + 429 指数退避            │
│ BitableError    错误分类（403 / 1061073 / 1061061 / 429）│
│ CredentialStore 加密存储 + 连接配置模型（三参数）    │
├ 半通用层（SDK 给骨架，App 填策略）─────────────────┤
│ SyncEngine      待推队列 + 增量拉取 + 对账循环        │
│                 （钩子：实体↔记录映射、冲突策略）     │
├ 应用层（各 App 自写，永不进 SDK）──────────────────┤
│ wardrobe：Person/Item/Outfit/Note ↔ 4 张表映射      │
│ UI（设置页/同步状态）、业务语义                      │
└───────────────────────────────────────────────────┘
```

**节奏：先边界、后拆库。** it-002 在 wardrobe 内按上述边界实现（包结构 `data/sync/` 内部再分 `client/` 与 `engine/`），约束只有一条：**通用层不得 import 任何 wardrobe 业务类型**。第二个消费方出现时（eats/clips 接入）再物理拆出 `libs/feishu-bitable-sdk`，用 Gradle composite build（`includeBuild`）接入——免发版、源码同仓、各 app 独立构建不受影响。提前拆库的风险：没有第二消费者时抽象必然切错层。

**契约先行**：SDK 落地时同步写一份语言无关的契约文档（REST 端点清单 + 字段类型映射规范 + 轻同步语义 + 错误码处理约定），clips 的 Mac SwiftUI 端按同一契约独立实现——与 clips it-001「契约 = specs + 双端单测对齐」的做法一致。

**2026-09-20 更新（数据架构三次演进）：**①「SDK 先行设计」；②「后端可低成本切换」→ 重构为中立契约 + 适配器（[libs/sync/specs/00-architecture.md](../../../libs/sync/specs/00-architecture.md)：contract 层 SyncSource + 后端无关引擎，feishu-bitable 只是首个适配器，record_id/串行写等细节不外泄；换后端 = 换适配器，后端间迁移是契约层工具）；③「本地存储也是一套 SDK」→ [libs/store/specs/00-architecture.md](../../../libs/store/specs/00-architecture.md)（it-001 数据层的通用化抽取：原子快照/bak/迁移链/SSOT/媒体/zip，备份格式契约不变）。两 SDK 互不依赖，app 数据层组合：**store=本地正本（mutate.writeHook 接 sync 待推队列），sync=云端镜像**。it-002 实现路径：先以 store SDK 替换 wardrobe 数据层（纯重构，测试护栏），再按 sync 设计 §9 接入；过渡期源码可先落在 wardrobe 内，但包结构按 `store/`、`sync/contract`、`sync/backend/bitable` 分明，拆库纯搬移。

## 待用户确认的问题

**已确认（2026-09-20 用户拍板）**：

- ✅ Q1 形态：数据存飞书云 + App 内填凭证，无登录页
- ✅ Q4 家人参与：M1（装 App 扫码）+ M2（飞书/独立版直接编辑）双通道并行
- ✅ Q5 分发协议：二维码快速接入**纳入 it-002**（第一优先级）；多源只读浏览推 it-003+
- ✅ Q6 底座：飞书多维表格
- ✅ 同步策略：轻同步——以表格数据为准，启动/手动刷新拉取，记录级覆盖，无实时推送

**仍待确认（带默认值，无异议即按默认执行）**：

1. 同步范围：**默认「全家所有 Person 同步同一张表」**（分发诉求本身指向共享衣橱）。若要按人隔离（每人只见自己的），需按 Person 分视图/过滤，复杂度上升，暂不建议。
2. 图片：**默认「全量上行附件」**——分发若缺图片等于半残；容量测算余量 10 倍以上。若在意首次全量上传耗时（数百张 × 串行 5QPS），可加"仅 Wi-Fi 上传图片"开关。

## 影响范围（确认后实现时同步更新）

- `00-overview.md`：从「范围外」移出云同步，关键用户与设备段改写（单机 → 多端）
- `01-user-stories.md`：新增 US-15a（云连接配置）/ US-15b（同步与对账）/ US-15c（二维码分发接入）及验收标准
- `03-data-model.md`：新增 Bitable 表结构映射小节、软删字段
- `04-architecture.md`：新增 `data/sync/` 分层说明
- `06-decisions.md`：ADR-011 飞书多维表格作为同步后端（含替代方案取舍）；ADR-012 SDK 边界与拆库时机（§15：先边界后拆库、composite build、契约文档）
- 仓库布局（远期）：代码落地时新增 `libs/sync/`（contract + backends + android，composite build），届时修订根 AGENTS.md「不在应用目录外放代码」条款（SDK 属基础设施例外）

## 验证记录

**2026-09-20 · SDK 阶段（it-002 前置工程）**

| 项 | 结果 |
|---|---|
| libs/store 单测 | 20/20 通过（原子写、三级恢复、迁移链、SSOT 回滚、writeHook、孤儿清理、zip 往返） |
| wardrobe 单测 | `:app:testDebugUnitTest` 全绿（原 JsonFileStore 测试随职责移入 SDK 退役） |
| wardrobe APK | `assembleDebug` 通过（composite build 依赖替换正常，APK 24.7MB） |
| libs/sync 单测 | 49/49 通过（contract 24：队列合并/对账/冲突裁决/状态机/附件隔离；bitable 25：建表/补列/收编/分页/退避/错误折叠/codec） |
| 依赖边界审计 | grep 全通过：store 不依赖 Android/app；contract 不依赖 bitable/Android/app/store；bitable 不依赖 app/store；wardrobe 不依赖 sync |
| review 修复 | ① push/pull 成功不落 Done 状态 ② 传输级失败不落 Failed ③ 附件上传失败会中断整批 → 均已修复并补 5 个状态机单测 |
| 已知边界 | FakeTransport 仅验证我方请求构造与响应解析，真实飞书链路（token/附件 extra 参数/临时下载 URL 形态）仍需按实测清单真机验证 |

**App 接入（US-15a/b/c：设置页、扫二维码、同步织入）未开始**——按用户目标「多维表 SDK 先实现、不接入」执行。

## 主要参考

- [多维表格概述与使用限制](https://open.feishu.cn/document/server-docs/docs/bitable-v1/bitable-overview?lang=zh-CN)
- [开放平台频控策略](https://open.feishu.cn/document/server-docs/api-call-guide/frequency-control?lang=zh-CN)
- [批量新增记录（10 QPS/1000条）](https://open.feishu.cn/document/server-docs/docs/bitable-v1/app-table-record/batch_create?lang=zh-CN)
- [通过 WebSocket 接收事件](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/event-subscription-guide/receive-events-through-websocket)
- [多维表格付费权益说明](https://www.feishu.cn/hc/zh-CN/articles/487931070605)、[行数扩容](https://www.feishu.cn/hc/zh-CN/articles/389918352455)
