# 06-decisions — 驿站（posthouse）ADR

## ADR-001 macOS 菜单栏原生 app，数据源走本地 git CLI

- **背景**：要监控的是「本地 git 状态 + 本机凭证/代理环境」。
- **决定**：SwiftUI `MenuBarExtra`（macOS 14+）+ `Process` 调 `/usr/bin/git`。
- **后果**：状态即真实环境（凭证、代理、HTTP/1.1 参数全部天然生效）；
  不引 libgit2，不依赖 GitHub API（离线/断连也可观测）。
- **被否**：Android/网页端（只能看远端 API 的降级视图，放 M4）。

## ADR-002 测试栈用 swift-testing 而非 XCTest

- **背景**：本机只有 Command Line Tools，**没有 Xcode、没有 XCTest**；CLT 自带
  `Testing.framework`（Swift 6 官方测试框架）。
- **决定**：`tools-version:6.0` + `swiftLanguageModes: [.v5]`，测试用 `@Test`/`#expect`。
- **后果**：`swift test` 全链路可用；IDE 断言 UI 等 XCTest 特有能力用不到（本项目不需要）。

## ADR-003 自动推送安全边界（不可逆规则）

- **决定**：双闸门默认全关；永不 force；永不自动 pull/rebase/merge（behind 只报警）；
  远端不通/退避冷却内不推；non-FF 只通知。全部落为 `PushDecision` 纯函数 + 单测。
- **理由**：自动推送是本产品唯一有破坏性的能力，规则必须可审计、可回放、默认关闭。
- **后果**：分叉场景需要人工介入（设计如此，非缺陷）。

## ADR-004 扫描识别仓库用 `.git` 存在性，而非 rev-parse

- **背景**：`git rev-parse --is-inside-work-tree` 对仓库内**子目录**也返回 true，
  实测把 `dist/`、`gradle/`、`wardrobe/` 全误判成独立仓库。
- **决定**：目录自身存在 `.git`（目录或 worktree 文件）才算仓库；扫描含根自身。
- **后果**：monorepo 只报一条（正确行为）；submodule/worktree 因 `.git` 文件同样成立。

## ADR-005 零 Xcode 工程：SwiftPM + build-app.sh 手工组装 .app

- **决定**：SwiftPM 出可执行，`tools/build-app.sh` 写 Info.plist（`LSUIElement=true`）
  + ad-hoc codesign 组装 bundle。
- **理由**：CLT 环境无法生成 xcodeproj；菜单栏 app 无需沙壳/公证（本机自用）。
- **后果**：ad-hoc 签名变化会重置 TCC 授权（重建后首次启动重新授权「文稿」）；
  若要分发需补 Developer ID + 公证（未计划）。

## ADR-006 目录扫描 15s 超时兜底（TCC 不陪葬）

- **背景**：GUI 首启访问 `~/Documents` 会挂起等 TCC 授权弹窗，实测 `contentsOfDirectory`
  长时间阻塞，引擎轮询被整体卡死。
- **决定**：扫描在独立队列执行，15s 未返回则放弃本轮并记 WARN；git 调用全部带超时。
- **后果**：授权前 app 显示「未发现仓库」而非挂死；授权后下一轮自动恢复。

## ADR-007 DispatchSource timer 懒创建

- **背景**：CLI 模式（--probe/--push/--gazette）不启动轮询；inline 创建的 timer
  从未 resume，deinit 释放 suspended source 触发 libdispatch SIGTRAP（实测 exit=133）。
- **决定**：timer 可选、`start()` 时创建并 resume，`deinit` 兜底 cancel。
- **后果**：GUI 常驻与 CLI 一次性共存于同一二进制。
