# it-002 模拟器分治——每应用固定 AVD + 设备绑定脚本（跨应用 / hotfix）

- 日期：2026-09-29
- 类型：纯 bugfix（按 AGENTS.md 跳过①提案，回填问题与修法）
- 范围：wardrobe、darkroom 的开发工具链（`tools/emu.sh`），不涉及应用代码

## 问题与动机

同时开发衣橱与显影时，两会话反复争抢同一台模拟器：

- 前台被对方 `am start` 抢走，截图混入对方画面，走查被迫「重聚焦→快截图→失败重试」（原 [LESSONS 兜底规则](../../LESSONS.md)，来自 wardrobe it-055）；
- it-064 曾因此放弃模拟器端到端验证（「AVD 被 darkroom 并行会话持续占用前台，force-stop 即被拉回」）；
- 根因：① 两会话默认共用一台模拟器（文档里惯用 `emulator-5554`，历史 AVD 无应用归属）；② 序列号硬编码端口号（`emulator-5554/5556`），随模拟器启停漂移，上一会话写死的号码会被下一会话接盘到别的设备；③ 裸 `adb` / `./gradlew installDebug` 不带设备选择，多设备在线时打到**所有**设备，互相串包。

## 修法

**设备分治：每个应用独占自己的 AVD，命令一律绑定解析出的序列号。**

1. **AVD 归属约定**：`wardrobe_*`（主用 `wardrobe_test`）只归衣橱；`darkroom_*`（主用 `darkroom_qa`）只归显影。两应用可以同时各开一台模拟器，前台之争自然消失。
2. **`应用名/tools/emu.sh`**（两份同构，仅常量不同）：`up`（启动/复用本应用 AVD，优先固定端口 5554/5564，被占则自动换口并提示）、`serial`、`install`（assembleDebug + `adb -s` 安装）、`launch` / `restart` / `stop`、`cap`（截图）、`uadump`（无障碍树）。
3. **序列号按 AVD 名解析**（`getprop ro.boot.qemu.avd_name`），不硬编码 `emulator-端口`：端口只是显示名，漂移不影响绑定；解析时优先主 AVD，其次任意本前缀实例。
4. `uadump` 先删旧文件再 dump，失败缓拍重试——避免转场中 dump 失败却 pull 回过期的无障碍树（验证阶段实际踩到）。
5. 文档同步：wardrobe/darkroom README 增「模拟器」小节；LESSONS 旧兜底规则（多会话共用一台 AVD 的重聚焦重试术）按其淘汰条款删除，换成新不变量。

## 影响范围

- 新增：`wardrobe/tools/emu.sh`、`darkroom/tools/emu.sh`（可执行脚本）。
- 修改：`wardrobe/README.md`、`darkroom/README.md`、根 `LESSONS.md`、根 `CHANGELOG.md`。
- 不触碰应用源码与构建配置；`./gradlew installDebug` 仍可用但多设备在线时不安全（README 已注明）。

## 验证记录（2026-09-29，3 台模拟器同时在线场景）

| 项 | 结果 |
|---|---|
| `wardrobe/tools/emu.sh serial` | `emulator-5558`（wardrobe_test，双 wardrobe_* 在跑时正确优先主 AVD） |
| `darkroom/tools/emu.sh serial` | `emulator-5560`（darkroom_qa），各归各的 |
| 两边 `cap` 截图 + `uadump` 前台包名核对 | 衣橱设备曾残留系统相机 `com.android.camera2`（旧争抢残局的现形），显影设备为 `com.leo.darkroom`——绑定互不串台 |
| `wardrobe/tools/emu.sh install + launch` | 装包 Success，`am start` 拉起后无障碍树前台为 `com.leo.wardrobe`（112 节点） |
| `up` 复用在跑实例路径 | 正常（本次未触发冷启动；冷启动走 `emulator -avd <主AVD> -port <端口>`，端口被占自动换口） |

遗留：冷启动与端口冲突回退路径未实跑（当前 3 台 AVD 均已在跑）；下次重启模拟器时顺手 `tools/emu.sh up` 验证即可。
