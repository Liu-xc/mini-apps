# 04-architecture — 驿站（posthouse）

## 分层与模块

```
UI（SwiftUI MenuBarExtra）        CLI（--probe/--push/--gazette）
        │ AppModel / SharedModel 单例              │ CLI.runOnce
        └──────────────┬───────────────────────────┘
                 WatchEngine（编排层，后台串行队列）
        ┌──────────────┼──────────────────┐
   StatusProbe      PushDecision/Backoff   GazetteEngine/GazetteStore
  （只读探测）       （纯决策·单测核心）      （纯构建+落盘·可回放）
        │                │
    GitRunner（协议）→ GitShell（Process 调 /usr/bin/git）
        │
   RepoScanner / ConfigStore / Notifier(osascript) / PLog
```

依赖方向：UI/CLI → WatchEngine → 领域模块 → GitRunner 协议。领域模块（PushDecision、
GazetteEngine、BackoffState、GitFailureClassifier）零 UI/IO 依赖，纯函数可单测。

## 线程模型

- `posthouse.engine`（串行）：轮询编排、自动推送决策、邸报到点检查。
- `posthouse.probe`（并发）：多仓并行探测，`group.wait()` 聚合。
  **坑**：探测绝不能派回 engine 串行队列自身再 wait（自死锁，it-001 实测踩中）。
- 主队列：`onStatuses`/`onPushResult` 回调（UI 绑定）。
- 定时：`DispatchSourceTimer`，**start() 时懒创建并 resume**；
  CLI 模式不 start，若 inline 创建又不 resume，deinit 释放 suspended source 会触发
  libdispatch SIGTRAP（实测踩中）。

## git 交互

- 只读探测：`rev-parse` / `status --porcelain` / `rev-list --count @{u}..HEAD` / `ls-remote`（带超时）。
- ahead/behind 相对**本地 remote-tracking ref**（不 fetch，避免探测有副作用）；
  分叉在 push 被拒时由 non-FF 分类兜住。
- 推送：`[pushArgs…] push <remote> <branch>`，永不 force；stderr 按
  `GitFailureClassifier` 分类 network/auth/nonFastForward/other。
- 仓库判定：**`.git` 存在于目录自身**才视为仓库
  （`rev-parse --is-inside-work-tree` 对子目录也 true，会把 dist/gradle 等误判成仓，实测踩中）。

## 安全边界（M2，硬规则）

1. 双闸门（总开关 ∧ 白名单），默认全关；
2. behind>0 → 决策层直接 skip 并发「分叉」通知（每仓去重），永不 pull/rebase/merge；
3. 远端不通 → skip，指数退避 30s→600s 封顶；
4. push 被拒 non-FF → 只通知不动作；
5. 连续网络失败 ≥3 → 疑似代理故障提醒（network-rescue 联动留 M4）。

## 失败韧性

- 所有 git 调用带超时（GitShell SIGTERM 兜底）。
- 目录扫描带 15s 超时（TCC 授权弹窗可能挂起 `contentsOfDirectory`，引擎不陪葬）。
- 日志：`PLog` 文件日志同步落盘（验收与排障证据链）。

## 构建与部署

- SwiftPM（tools 6.0 / 语言模式 v5）+ `tools/build-app.sh` 手工组装 `.app`
  （Info.plist `LSUIElement=true`，ad-hoc 签名），无 Xcode 工程。
- 测试：swift-testing（CLT 自带 Testing.framework；CLT **无 XCTest**）。
- 首次 GUI 启动受 TCC「文稿」授权约束；shell 启动继承终端授权（验收用后者）。
