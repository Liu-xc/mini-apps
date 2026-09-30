# it-004 资产筛选与工具链验证（阶段记录）

2026-09-30。**尚未完成新版APP；当前Android仍是it-003/0.2.0。** 用户已授权实施；模型资产条件未满足。未购买模型、未联系作者、未引入新的APP依赖。

## 已完成

- 固定官方Godot4.5.2：运行输出`4.5.2.stable.official.6ce3de25a`。
- 真实GLB导入工具检查节点、尺寸、UV、法线、PBR纹理和文件哈希；测试缺失文件返回2。
- Jolt接触检查960物理帧通过；两个球心最终高度0.499866/1.499866米，速度0、休眠，无穿地。开启CCD但未验证高速CCD。见[jolt-smoke.txt](jolt-smoke.txt)。
- 使用Khronos示例[DamagedHelmet](https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/DamagedHelmet)验证真实文件导入：3,773,916字节、14,556顶点、46,356面索引，UV/法线/颜色/法线/金属/粗糙度贴图读取通过。见[import-smoke.txt](import-smoke.txt)。此模型只在`/tmp/lottery-it004-tools/`测试，未进入仓库或APP；其README列出ctxwing的CC-BY4和早期theblueturtle_的CC-BY-NC4，不能笼统称所有示例为CC0。
- 阅读官方[Android嵌入示例](https://github.com/m4gr3d/Godot-Android-Samples/blob/master/apps/gltf_viewer/src/main/java/fhuyakou/godot/app/android/gltfviewer/MainActivity.kt)，确认GodotHost/GodotFragment插件宿主路径；尚未运行Android嵌入原型。

## 候选与缺口

| 实际候选 | 事实 | 处理 |
|---|---|---|
| [Lottery Machine / Ankush Gupta](https://sketchfab.com/3d-models/lottery-machine-14cd7c97b7dc4fa5a5d7ecf4c8c7beb1) | 在浏览器实际打开动画预览，约47.9k三角面/24.1k顶点，作者说明Maya/Substance Painter；球腔、连续出口管、接头与底座完整。当前页面无下载/购买入口，搜索索引显示过Get it on Fab，但没有核实有效Fab商品和价格 | 最接近目标的视觉候选；未获取文件，授权、玻璃厚度/UV、拆件和移动端效果均待验，不能直接当作成品素材 |
| [Lottery Machine / MarcMestre](https://sketchfab.com/3d-models/lottery-machine-e9fc8422d1384748a4a5abd4f049753a) | Meshy AI生成、约634k面、不可下载，许可不明 | 拒绝；不能回应用户对AI味和精细模型的要求 |
| [Gumball Machine / pizzaguyty](https://sketchfab.com/3d-models/gumball-machine-free-download-12be48147ad94473994dbb9801247ec6) | 元数据CC Attribution、可下载，约91.9k面；下载接口401要求账户 | 糖果机与气吹式摇奖机机构不同；未取得或验收，不用来替代目标 |
| [Portable Manual Lottery Machine](https://www.turbosquid.com/3d-models/portable-manual-lottery-machine-3d-2049512) | 搜索结果显示$49、手摇Bingo机 | 机构不同，不推荐直接购买 |

[asset-search.json](asset-search.json)保留匿名Sketchfab API查询结果，含无关、NC、无许可等条目，仅为筛选证据，**不是授权清单或打包资产清单**。不从网页查看器抽取受保护模型，也不将作者预览用于APP。

## 下一步所需条件

已向用户询问模型是否允许采用授权专业模型，或模型也必须严格限制开放许可；尚未收到回答。不自动把“实施”解释为购买授权。

取得合法且达标的模型文件后，按it-004继续：文件授权/哈希清单 → 三视图/近景审查 → Godot Android嵌入原型 → Jolt机械碰撞/气流 → 产品布光与两款主题 → UI交互 → Android完整旅程验收。未满足资产关，不提交新的基础体占位APK冒充视觉交付。
