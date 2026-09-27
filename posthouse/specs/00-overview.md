# 00-overview — 驿站（posthouse）

> macOS 菜单栏常驻工具：本地 git 仓库的**烽火台**（实时报警）与**邸报**（每日战报）。
> 立项依据见 `specs/iterations/it-001-beacon-and-gazette.md`。

## 业务背景

两个真实痛点合成一个产品：

1. **推送屡屡受挫**——代理出口挂、直连被墙、workflow scope 被拒，常出现「本地积压几十个提交、推送悄悄失败、过几天才发现」。
2. **并行会话状态散乱**——多个 AI 会话满天飞，某天到底干了什么没有统一视图。

## 核心价值流

```
扫描仓库 → 探测状态（本地脏/ahead/behind/远端连通）
  ├─ 烽火台：三态图标 + 明细菜单 + 手动推送（带网络参数）
  ├─ 自动哨兵：白名单仓库断网恢复后自动补推（安全边界硬规则）
  └─ 邸报：每晚扫当日 git log → 战报 Markdown + 成就发放
```

## 三态语义（BeaconState）

| 态 | 图标 | 判定 |
|---|---|---|
| ok 全绿 | circlebadge | ahead=0 且远端通（或无远端） |
| backlog 狼烟 | flame | ahead>0 或 behind>0 |
| unreachable 熄火 | bolt.slash | 配了远端且探测不通 |

聚合：任一熄火 > 任一狼烟 > 全绿。

## 部署形态

- SwiftPM 可执行 + `tools/build-app.sh` 组装 `.app`（`LSUIElement=true` 纯菜单栏，无 Dock 图标）。
- 无 Xcode 工程；命令行工具链（CLT）即可构建。测试用 swift-testing（CLT 自带）。
- 数据落 `~/Library/Application Support/Posthouse/`（config/status/log/成就档案）；邸报默认出 `~/Documents/daily-gazette/`（不入公开仓库）。
- 首次 `open` 启动需 macOS「文稿」文件夹访问授权（TCC 弹窗）；引擎扫描有 15s 超时兜底，授权前不挂死。
