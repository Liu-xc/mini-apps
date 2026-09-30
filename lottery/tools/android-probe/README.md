# Android 引擎兼容原型（非新版APP）

独立Gradle工具包`com.leo.lottery.engineprobe`，不在正式`lottery/app`依赖图中。用于资产下载待登录期间验证Godot4.5.2嵌入、真实GLB/PBR、Jolt接触与原生宿主生命周期。测试资产为署名的DamagedHelmet，不是摇奖机；不会生成替代机械模型。

## 构建与运行

在本目录运行。`GODOT`指向官方4.5.2可执行文件；测试GLB来源/哈希/署名见[THIRD-PARTY.txt](scene/THIRD-PARTY.txt)。测试文件与引擎二进制不进git。

```bash
"$GODOT" --headless --editor --path scene --quit
"$GODOT" --headless --path scene --script pack.gd -- /tmp/probe.pck /absolute/DamagedHelmet.glb
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/leo/Library/Android/sdk \
../../gradlew assembleDebug -PprobePack=/tmp/probe.pck
```

默认从Maven Central解析`org.godotengine:godot:4.5.2.stable`。若已经从同一官方仓库下载AAR，可加`-PgodotAar=/absolute/godot-4.5.2.stable.aar`避免重复下载；构建校验官方SHA-256 `e920f3b514907931621f3639b30069fd124ca008d68b366d43a36f41388be8c7`，拒绝其他文件。[官方AAR](https://repo.maven.apache.org/maven2/org/godotengine/godot/4.5.2.stable/godot-4.5.2.stable.aar)、[官方校验值](https://repo.maven.apache.org/maven2/org/godotengine/godot/4.5.2.stable/godot-4.5.2.stable.aar.sha256)。AndroidX Fragment固定1.8.6（与官方POM一致）；工具目前只打包arm64-v8a。

`pack.gd`先检查场景脚本可以编译，再打包二进制ProjectSettings与脚本类缓存，避免Android缺失配置/缓存。输入GLB哈希必须匹配已署名的测试文件，不能偷偷替换成未署名资产。

安装与测试（始终解析本应用设备，不硬编码序列号）：

```bash
probe_serial=$(../emu.sh serial)
"$ANDROID_HOME/platform-tools/adb" -s "$probe_serial" install -r build/outputs/apk/debug/lottery-engine-probe-debug.apk
python3 verify-device.py          # Vulkan/Mobile；本机模拟器测试失败，保留证据
python3 verify-device.py --gles   # 明确选择OpenGL；本机模拟器10次进退通过
```

手动进入OpenGL验证路径：

```bash
"$ANDROID_HOME/platform-tools/adb" -s "$probe_serial" shell am start \
  -n com.leo.lottery.engineprobe/.LauncherActivity \
  --ez probe_launch true --ez probe_gles true --ez probe_auto_close false
```

这些命令参数仅属于工具；正式APP没有这些入口。模型查看器能拖动相机并按“退出验证场景”离开。脚本设置竖屏，不能只依靠Android Manifest，因为引擎会设置屏幕方向。

## 生命周期与实测边界

- 原生Launcher与引擎`:stage`分进程，Godot插件回报内部事件；结果通过Activity result返回。
- 引擎场景退出先返回结果、后销毁进程。新场景启动前查询自己的旧场景进程是否已释放，以免旧Activity的退出杀掉刚启动的下一场。5秒未释放则显示错误，不盲目复用已终止的native单例。
- 每轮真实导入GLB、运行360物理帧的不可视碰撞检查、回传结果并释放场景进程；验证器要求主PID恒定、十个不同场景PID、十次成功导入/接触/返回以及最终无残留场景进程。
- 本机Apple M2、硬件GPU Android模拟器：Vulkan/Mobile出现`QueuePresentKHR failed with error: 5`并停止推进，不能接受；OpenGL路径实际渲染且上述10轮通过。不能据此称Vulkan可用，不能自动将生产渲染降级，也没有证明玻璃、导管、气流、主题、后台恢复、真机FPS或视觉质量。
- 当前debug验证APK约80.4MiB，存在未剥离native符号；这不是新正式APP包体或性能数据。

截图、失败记录和通过记录见[it-004阶段报告](../../reports/2026-09-30-it004/README.md)。
