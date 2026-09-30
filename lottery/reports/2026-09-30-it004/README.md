# it-004 资产筛选与工具链验证（阶段记录）

2026-09-30。**尚未完成新版APP；正式Android仍是it-003/0.2.0。** 用户已授权实施；模型资产条件未满足。未购买模型、未联系作者、未引入新的正式APP依赖；新增的是独立Android引擎兼容工具。

## 已完成

- 固定官方Godot4.5.2：运行输出`4.5.2.stable.official.6ce3de25a`。
- 真实GLB导入工具检查节点、尺寸、UV、法线、PBR纹理和文件哈希；测试缺失文件返回2。
- Jolt接触检查960物理帧通过；两个球心最终高度0.499866/1.499866米，速度0、休眠，无穿地。开启CCD但未验证高速CCD。见[jolt-smoke.txt](jolt-smoke.txt)。
- 使用Khronos示例[DamagedHelmet](https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/DamagedHelmet)验证真实文件导入：3,773,916字节、14,556顶点、46,356面索引，UV/法线/颜色/法线/金属/粗糙度贴图读取通过。见[import-smoke.txt](import-smoke.txt)。源文件位于`/tmp/lottery-it004-tools/`，打入独立验证工具而非正式APP；模型二进制不进仓库。其README列出ctxwing的CC-BY4和早期theblueturtle_的CC-BY-NC4，不能笼统称所有示例为CC0。
- 阅读官方[Android嵌入示例](https://github.com/m4gr3d/Godot-Android-Samples/blob/master/apps/gltf_viewer/src/main/java/fhuyakou/godot/app/android/gltfviewer/MainActivity.kt)，已据固定版GodotActivity/GodotPlugin实现独立Android兼容原型，详细结果见下。

## Android兼容原型实测

`tools/android-probe/`为独立构建与独立包名；测试GLB署名和第三方许可在场景内/包内保留。构建成功：[android-probe-build.txt](android-probe-build.txt)。桌面场景包导入/接触通过：[android-probe-desktop.txt](android-probe-desktop.txt)。打包时增加脚本编译检查，拒绝把解析失败的GDScript打包；Android二进制设置与脚本类缓存也已正确打包。

| 测试 | 实测结果 | 限制 |
|---|---|---|
| Vulkan/Mobile | 未通过。宿主及GLB初始化成功，但`QueuePresentKHR failed with error: 5`，物理帧不再持续推进；冷启动本应用AVD后仍复现 | [首轮日志](android-probe-vulkan-failure.txt)、[超时记录](android-probe-vulkan-cycles.json)；不能称GPU渲染合格 |
| OpenGL/GLES | 实际PBR模型可见，Jolt360物理帧接触检查通过 | 测试模型不是摇奖机；非生产方案自动降级 |
| 退出后快速重进 | 最初第二次进入复用了将要终止的进程，被旧Activity退出连带关闭，验证失败 | [失败证据](android-probe-gles-race.txt)、[失败记录](android-probe-gles-race.json) |
| 修复后10轮 | 查询旧`:stage`进程并等待释放后再进入。十轮GLB导入、Jolt接触、内部事件、Activity结果与场景进程释放均通过；原生主PID5442恒定，10个场景PID不同 | [通过JSON](android-probe-gles-cycles.json)、[完整日志](android-probe-gles-cycles.txt)。不能推断后台恢复、帧率或正式UI验收 |

测试环境：Apple M2，`lottery_qa`、host GPU；仅冷启动该应用AVD，其他应用设备未操作。正式APP仍采用原Filament舞台。独立debug验证APK84,312,102字节（约80.4MiB），未剥离native符号，不当作正式APP包体承诺。

实际截图（均为测试工具，不是新版摇奖机）：[OpenGL模型](android-probe-gles.png)、[10次返回后的原生宿主](android-probe-host-10.png)、[Vulkan失败黑屏](android-probe-first.png)。

## 候选与缺口

| 实际候选 | 事实 | 处理 |
|---|---|---|
| [Loto/Bingo / ian57](https://blendswap.com/blend/11602) | 用户明确个人自用后纳入筛选；实际浏览了预览及下载页。CC-BY-NC-SA，Blender2.6x/Cycles，页面显示7.83MB；金属笼、支架、号码球、接球槽；作者声明引用Sunuba/19seanak19作品 | 手摇机构，可评估部件或复古主题；不直接替换气吹机器。下载要求登录，尚未获取文件或核查引用资产许可/内部结构 |
| [Lottery Machine / Ankush Gupta](https://sketchfab.com/3d-models/lottery-machine-14cd7c97b7dc4fa5a5d7ecf4c8c7beb1) | 在浏览器实际打开动画预览，约47.9k三角面/24.1k顶点，作者说明Maya/Substance Painter；球腔、连续出口管、接头与底座完整。当前页面无下载/购买入口，搜索索引显示过Get it on Fab，但没有核实有效Fab商品和价格 | 最接近目标的视觉候选；未获取文件，授权、玻璃厚度/UV、拆件和移动端效果均待验，不能直接当作成品素材 |
| [Lottery Machine / MarcMestre](https://sketchfab.com/3d-models/lottery-machine-e9fc8422d1384748a4a5abd4f049753a) | Meshy AI生成、约634k面、不可下载，许可不明 | 拒绝；不能回应用户对AI味和精细模型的要求 |
| [Gumball Machine / pizzaguyty](https://sketchfab.com/3d-models/gumball-machine-free-download-12be48147ad94473994dbb9801247ec6) | 元数据CC Attribution、可下载，约91.9k面；下载接口401要求账户 | 糖果机与气吹式摇奖机机构不同；未取得或验收，不用来替代目标 |
| [Portable Manual Lottery Machine](https://www.turbosquid.com/3d-models/portable-manual-lottery-machine-3d-2049512) | 搜索结果显示$49、手摇Bingo机 | 机构不同，不推荐直接购买 |

[asset-search.json](asset-search.json)保留匿名Sketchfab API查询结果，含无关、NC、无许可等条目，仅为筛选证据，**不是授权清单或打包资产清单**。不从网页查看器抽取受保护模型，也不将作者预览用于APP。

## 下一步所需条件

用户已明确：个人自用、不商用，采用免费且允许个人使用的模型即可。按此继续筛选，包含允许非商业改造的模型；不以商用授权为门槛，不走付费采购。模型获取和实际质量仍须验证，不把无需商用授权等同任意网页模型都可下载。

已实际打开BlendSwap模型下载页并进入登录表单。页面明确要求“Sign in to download”，尚未登录，文件尚未取得。已请求用户在应用内浏览器自行登录，无需将密码发送给助手；没有代为创建账号或接受服务条款。登录后才能按站点正常流程获取该候选并核验内部结构/引用素材，不能在此之前称资产关通过。

取得合法且达标的模型文件后，按it-004继续：文件授权/哈希清单 → 三视图/近景审查 → Godot Android嵌入原型 → Jolt机械碰撞/气流 → 产品布光与两款主题 → UI交互 → Android完整旅程验收。未满足资产关，不提交新的基础体占位APK冒充视觉交付。
