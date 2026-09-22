# 00 — 业务背景与核心价值流

## 业务背景

Leo 订阅了 GLM Coding Plan（个人版），控制台展示三档用量，每档带剩余百分比与重置时间：

1. **5 小时窗口**（滚动窗口，重置时刻为当天某点）
2. **每周**（周配额，重置日为未来某天）
3. **ZCode MCP**（工具专项额度）

痛点：每次确认「还能不能继续猛 coding」都要开浏览器登录控制台，打断心流。

## 核心价值流

```
官方用量接口（monitor/usage/quota/limit）
  → UsageProvider 拉取 + 宽松解析（Keychain 存 Key）
  → UsageStore 快照（内存 + 磁盘缓存）
  → 刘海胶囊两态展示（紧凑三迷你条 / 展开三行明细）
  → 定时轮询 + 手动刷新 + 失败退避
```

## 非目标（M1）

- 不代理/不参与任何对话调用（只做只读监控；官方声明套餐外直调 API 按量计费）
- 不做多套餐/多账号管理（M1 单 Key）
- 不做 model-usage / tool-usage 明细页（M2）

## 环境

Leo 的 MacBook Air M2 13"（1470×956 逻辑点，**有刘海**，safeAreaInsets.top=32，
刘海右缘 `auxiliaryTopRightArea.minX=825`），macOS 15.7，只有 Command Line Tools（无完整 Xcode）。
