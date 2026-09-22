# glm-island — GLM Coding Plan 灵动岛

macOS 菜单栏刘海胶囊，常驻监控 GLM Coding Plan 三档用量（5 小时窗口 / 每周 / ZCode MCP）：
紧凑态三根迷你进度条，hover 展开与控制台一致的三行明细。规格文档见 [specs/](specs/)，
当前进度见 [specs/iterations/it-001-notch-capsule-mvp.md](specs/iterations/it-001-notch-capsule-mvp.md)。

## 构建 / 测试 / 打包

只需 Command Line Tools（无需完整 Xcode），macOS 14+：

```bash
swift build              # 构建
swift test               # 单测（swift-testing，CLT 无 XCTest）
./tools/make-app.sh      # 打包 → 仓库根 dist/glm-island.app（ad-hoc 签名，不入 git）
```

## 运行

```bash
open /path/to/dist/glm-island.app    # 常规启动（菜单栏出现三彩条图标，刘海右缘出现胶囊）
```

- 首次启动自动展开引导 → 点「去设置」粘贴 API Key（只存本机钥匙串）。
- 调试/走查：`GLM_ISLAND_DEMO=1 <可执行文件>` 演示模式（假数据不请求接口）；
  `GLM_ISLAND_EXPAND=1` 启动即固定展开。

## 接入真实数据（M0 spike）

```bash
GLM_API_KEY=你的Key ./tools/spike-usage.sh          # 国内 bigmodel.cn
GLM_API_KEY=你的Key ./tools/spike-usage.sh zai      # 国际 z.ai
```

把真实响应对照 `Sources/GlmIsland/Core/QuotaResponseParser.swift` 的候选键收紧解析，
并从响应里拷一段（脱敏）进单测 fixture。接口鉴权：`Authorization: <key>`，不带 Bearer 前缀。

## 已知限制

- `limits[]` 字段无公开文档，解析为宽松匹配（spike 前显示可能为 -- 或顺序兜底）；
- 胶囊落位为**智能避让**（右缘空隙 → 左缘空隙 → 刘海正下方悬浮，30s 重算），
  位置会随菜单栏排布漂移；手动钉住位置待 M2；
- 系统级全屏截图不合成该层窗口，走查请用 `screencapture -l <windowID>`；
- 开机自启（SMAppService）需 .app 形态，裸 `swift run` 下开关会报错并回显原因。
