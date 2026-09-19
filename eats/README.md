# 吃啥 (eats)

记录堂食 / 外卖 / 自做三类吃饭选项：地图标记、最近一次追踪、转盘快速决策。

- **状态**：it-001 已完成
- 规格文档：[specs/](specs/)，业务背景见 [specs/00-overview.md](specs/00-overview.md)
- 架构决策：[specs/06-decisions.md](specs/06-decisions.md)（含 ADR-002 高德瓦片修订、ADR-009 链接方案）

## 构建

与 `wardrobe/` 同基线（ADR-001）：JDK 17 + Android SDK（platform 35 / build-tools 34+）。

```bash
./gradlew assembleDebug   # APK → app/build/outputs/apk/debug/
./gradlew test            # JVM 单测（domain/data，无需模拟器）
```

## 演示数据

```bash
tools/demo-data.sh [device-serial]   # 生成 8 家食堂/11 条记录并推入设备（debug 包，经 run-as）
```
