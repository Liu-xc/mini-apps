# it-082 Kolors 默认 seed=-1 被 SiliconFlow 拒（生图必失败 hotfix）

- 状态：已实施
- 日期：2026-10-03
- 类型：bugfix（AGENTS 纯 bugfix 流程，回填问题与修法）
- 关联：it-077（生图链路/参数编码）、it-081/A-13（编码回退规则的前序修订）

## 问题

Leo 真机（小米 14 Pro，it-080 体验包，真实模式）点「生成穿搭效果图」直接失败。App 无 HTTP 日志，经 Mac 侧 curl 复现（内置 Key）实锤：

```
POST /v1/images/generations {"model":"Kwai-Kolors/Kolors","seed":-1,...}
→ 400 {"code":20015,"message":"seed: Must be greater than or equal to 0"}
```

- SiliconFlow 生图默认模型 Kolors（目录第一个 IMAGE_GEN），目录里 seed `default=-1`（UI「-1=随机」惯例）
- 编码层原样透传 -1 → 服务端 400 → `AgentError.fromHttp` → UI 失败态
- 同 Key 的 Z-Image-Turbo（无 seed 参数）与省略 seed 的 Kolors 均请求成功，排除 Key/网络问题

## 修法

两层，语义分层清晰：

1. `libs/agent/ModelCatalog.kt`：Kolors seed 补声明 `min = 0.0`（如实反映服务端约束；default 保持 -1=UI 随机语义）
2. `wardrobe .../ImageParamEncoder.kt`：**INT 值低于声明 min → 省略字段**交服务端自选（SiliconFlow 省略 seed=随机）；未声明 min 的模型（DashScope 系 -1=官方随机语义）不受影响、原样透传

## 验证

- [x] curl 三发实锤：Kolors+seed(-1)→400 code 20015；Kolors 省略 seed→出图；Z-Image-Turbo→出图
- [x] 回归测试 `int below declared min is omitted for server-side default`：-1+min=0 省略 / 5 正常发 / 无 min 的 -1 透传
- [x] `./gradlew test` 全绿
- [x] it082 包装机（覆盖装）真实模式重启
- [ ] Leo 真机重试生成出图（待反馈）

## 教训（候选 LESSONS）

App 不打 HTTP 错误日志时，用桌面 curl + 同构请求体复现是定位厂商 400 的最短路径——UI 失败态文案（AgentError.fromHttp 的 message）在锁屏下拿不到，别死磕 logcat。
