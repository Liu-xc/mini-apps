# it-002 · 飞书多维表格云同步（调研与提案）

- **状态**：调研完成，提案待用户确认（未开始写代码）
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

### 5. 同步架构草案（本迭代真正的工程量所在）

本地 JSON 仍是 SSOT（离线可用、快照广播机制不变），Bitable 是云端镜像：

```
本地写 → 快照更新+落盘（现状不变）→ 记入 dirty 队列(表,id,op)
联网上行：串行队列 → batch_create/update/delete（≤1000/批）→ 失败退避重试
下行拉取：按 updatedAt/修改时间 filter 增量 → 与本地合并
冲突策略：字段级 LWW（比 updatedAt）；删除 vs 修改 → tombstone 优先
图片：上传走附件两步流程，串行排队；下载按需，本地 webp 缓存为唯一显示来源
```

分层落位：`data/sync/`（SyncEngine + FeishuBitableApi + DTO 映射），domain 的 `WardrobeRepository` 接口不动，UI 只加设置页入口。**遵守 04-architecture 依赖规则。**

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

## 待用户确认的问题

1. 是否接受「数据存飞书云 + App 内填凭证（无飞书登录页）」这个形态？
2. 同步范围：全家（所有 Person）一起同步，还是按 Person 开关？（多端各自录入是否 = 跨设备共享同一衣橱）
3. 图片是否全部上行（附件），还是首版只同步元数据、图片仅本地 + zip 导出？

## 影响范围（确认后实现时同步更新）

- `00-overview.md`：从「范围外」移出云同步，关键用户与设备段改写（单机 → 多端）
- `01-user-stories.md`：新增 US-15（云同步配置与执行）及验收标准
- `03-data-model.md`：新增 Bitable 表结构映射小节、软删字段
- `04-architecture.md`：新增 `data/sync/` 分层说明
- `06-decisions.md`：ADR-011 飞书多维表格作为同步后端（含替代方案取舍）

## 验证记录

（实现后回填）

## 主要参考

- [多维表格概述与使用限制](https://open.feishu.cn/document/server-docs/docs/bitable-v1/bitable-overview?lang=zh-CN)
- [开放平台频控策略](https://open.feishu.cn/document/server-docs/api-call-guide/frequency-control?lang=zh-CN)
- [批量新增记录（10 QPS/1000条）](https://open.feishu.cn/document/server-docs/docs/bitable-v1/app-table-record/batch_create?lang=zh-CN)
- [通过 WebSocket 接收事件](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/event-subscription-guide/receive-events-through-websocket)
- [多维表格付费权益说明](https://www.feishu.cn/hc/zh-CN/articles/487931070605)、[行数扩容](https://www.feishu.cn/hc/zh-CN/articles/389918352455)
