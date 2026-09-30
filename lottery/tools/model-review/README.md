# 模型导入与物理检查

这是it-004的资产审查工具，不是成品摇奖机或Android剧场。没有生成可视机器网格，不向APP打包任何测试模型。

固定Godot **4.5.2 stable**，官方[发行包](https://github.com/godotengine/godot-builds/releases/tag/4.5.2-stable)，本次macOS包SHA-256：`2a3f35cf5813b0d26e3f4c15dabc5e7c58407fceec7bae5291740772f72d141a`。下载到本地工具缓存，解压后使用其`Godot.app/Contents/MacOS/Godot`，无需全局安装。Android AAR接入尚未实施。

在本目录运行（将`GODOT`设为本地可执行文件的绝对路径）：

```bash
"$GODOT" --headless --path . --script inspect.gd -- /absolute/path/model.glb
"$GODOT" --headless --fixed-fps 120 --path . --script physics-smoke.gd
```

`inspect.gd`通过真实GLTFDocument导入文件，输出SHA-256、大小、节点、局部包围盒（glTF米单位）、全局变换、面索引/顶点、UV/法线、材质与PBR贴图信息。要判断整体尺寸需同时考虑变换。缺失文件/导入失败/无网格返回非零。Godot本身有脚本解析失败但进程返回0的情况，检查运行日志必须确认有完整JSON且没有`SCRIPT ERROR`/`ERROR`。

导入成功不代表质量或许可合格。人工必须检查授权范围、三视图/近景、腔壳和管壁厚度、法线、连接件、独立部件、可换材质槽、机械比例、碰撞结构及移动端实机效果。GLB导入损失的高级材质也需逐项确认。

`physics-smoke.gd`仅用不可视碰撞形状验证Jolt重力、球球/球地接触、堆积和停稳：960个物理帧内不穿地，最终球心约0.5/1.5米、速度接近0。虽然开启CCD，本例不是高速穿透验证；不能推断气流、导管、目标球捕获、Android性能或生命周期合格。

验证证据与候选拒绝原因见[审查报告](../../reports/2026-09-30-it004/README.md)。
