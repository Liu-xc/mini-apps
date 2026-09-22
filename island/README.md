# 灵岛（island）

Mac 刘海处的**功能入口容器**：默认完全隐形，鼠标移到刘海即动画展开内容卡片。
当前接入的内容源是 **GLM Coding Plan TOKEN 用量**（5 小时窗口 / 每周；每档：剩余% · 重置时间 · 进度条），
数据源为可插拔协议（`UsageProviding`），未来可扩展其它 TOKEN 厂商与能力卡片。

规格文档见 [specs/](specs/)，当前进度见 [specs/iterations/it-001-notch-capsule-mvp.md](specs/iterations/it-001-notch-capsule-mvp.md)。

## 构建 / 测试 / 打包

只需 Command Line Tools（无需完整 Xcode），macOS 14+：

```bash
swift build              # 构建
swift test               # 单测（swift-testing，CLT 无 XCTest）
./tools/make-app.sh      # 打包 → 仓库根 dist/island.app（ad-hoc 签名，不入 git）
```

## 运行

```bash
open /path/to/dist/island.app
```

- 默认无任何常驻 UI；鼠标移到刘海触发展开，点击可固定，移开/点外部收起。
- 首次使用：菜单栏图标 → 设置 → 粘贴 API Key（只存本机钥匙串）。
- 调试钩子：`GLM_ISLAND_DEMO=1` 演示数据；`GLM_ISLAND_EXPAND=1` 启动即展开；
  `GLM_ISLAND_SEED_KEY=xx` 首启注入钥匙串（App 自写 ACL，避免弹窗）。

## 接入真实数据

```bash
GLM_API_KEY=你的Key ./tools/spike-usage.sh          # 国内 bigmodel.cn
GLM_API_KEY=你的Key ./tools/spike-usage.sh zai      # 国际 z.ai
```

真实接口契约（spike 已校准）见 [specs/03-data-model.md](specs/03-data-model.md)。

## 已知限制

- 展开卡片顶边贴屏幕顶沿，会临时盖住该跨度内的状态图标，移开或点击外部即让位；
- 系统级全屏截图不合成该层窗口，走查请用 `screencapture -l <windowID>`；
- 开机自启（SMAppService）需 .app 形态，裸 `swift run` 下开关会报错并回显原因。
