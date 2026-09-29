# CHANGELOG

> AGENTS.md 迭代流程第⑥步要求的变更流水；本文件于 it-001 建立（此前仓库未落地该约定）。

## Unreleased

- **feat(wardrobe)**: it-072 品类清单直选可发现性升级——W1 槽位清单入口从 n/m 单点扩为三通道（名称条整条可点 + 长按照片区 + n/m 原位），名称条加 `⊞ n/m` 图标计数组；长按读全名 toast 废止（US-39 修订：清单名称两行完整可见为超集）；items==1 不挂假入口。详 [it-072](wardrobe/specs/iterations/it-072-slot-picker-discoverability.md)。

- **feat(wardrobe)**: it-071 全应用滚动性能优化——P0 W3 直修（imageFileOf 组合期 `File.exists()` 归零、TrimAlpha 不透明快速短路、网格 sorted hoist + contentType）；P1 结构（记录页单容器 LazyGrid 真懒化、CountUp/ScrollFade 读点入 draw、OutfitScreen 屏级派生 remember、心愿行删行级阴影）；P2 组合期清理；P3 release 开 R8+资源收缩 + debug-keystore 签名 + 基线 profile 链路（ADR-028，25,098 条规则）。同协议对照：Slow UI thread 68→29（−57%）、p95 150→93~125、UI 线程相位 8.2→0.8ms；模拟器 janky% 仍 ~95%（软件渲染 GPU 顶上限），真机复验为最终判据。91 测绿 + R8 冒烟全页通过。详 [it-071](wardrobe/specs/iterations/it-071-scroll-performance.md)。

- **feat(wardrobe)**: it-070 穿搭视图切换 · 主题设置 · 右滑归入——W7「成品图 / 单品布局」两段切换（有成品图才显示、默认成品图、会话内记住，US-48 编辑交互不变）；W11 新增「外观」卡三选一（跟随系统/亮色/暗色，`theme_mode` 持久化即点即生效，`LocalAppDarkTheme` 收敛 recap/长图局部取色）；carddeck 右滑改两段式归入（拖拽 1:1 单调跟手 + smoothstep 归入、上一张盖过顶卡，废止旧 crossfade 倒车）并修冷启动首滑不提交（三重门读进 snapshotFlow 计算块，防观察者饿死）。91 测绿 + eats 构建绿。详 [it-070](wardrobe/specs/iterations/it-070-card-view-toggle-theme-swipe.md)。

- **fix(darkroom)**: it-014 拍立得卡面外缘 hairline 立边——浅纸卡与纸底仅差 ~7/255 边界不可见，全模式卡外缘统一 1px hairline（`palette.hairline`、cardWidth×0.0018，预览/导出两端同参数；深色卡既有外框不变），亮/深主题与查看器实测到位。版本 0.5.13。详 [it-014](darkroom/specs/iterations/it-014-polaroid-card-hairline.md)。

- **fix(wardrobe)**: it-069 P2 机械细项打包——W1 空组合按钮禁用/随机单件轻提示/顶栏触控 48dp；W3/W8 筛选 rememberSaveable 跨 Tab 保持；W7 顶栏日期并入 updatedAt；W5 卡下重复文案废止、W8 随机按钮并入 W1 形制、W10 折叠符号与「→/↗」残留清理、ChatList 时间戳全站格式、W2 emoji 行 FlowRow；W6 重拼进度角标+手改重置提示、W13 流式可预输入。91 测绿。详 [it-069](wardrobe/specs/iterations/it-069-p2-mechanical-package.md)。

- **feat(wardrobe)**: it-068 穿搭详情放开调整单品 + 成品图标注——有成品图也可换季改一件（it-049 限制解除，不再逼用户创建副本丢打卡/评论）；单品变更后成品图区标注「成品图为调整前组合」（`Outfit.effectStale` 持久、旧快照兼容），录入新成品图即解除。91 测绿（+2 兼容/解除守门测）。详 [it-068](wardrobe/specs/iterations/it-068-outfit-edit-with-effect-annotation.md)。

- **fix(darkroom)**: it-013 画册干净底面 + 显影末态原片保真——移除画册底衬米色 slab（背景回归全应用纸底，与 W2/W3 一致）；显影末态「相纸感」残留（柔焦 2px/黑位抬 13/颗粒/暗角/高光压缩）全部精确归零，成片=原片（像素对照：夜景 mean 误差 <1/255、黑位 p1 20→5、高频图锐度 5.3→90.6），显影中段动画不变；05 动效表立「末态保真铁律」。详 [it-013](darkroom/specs/iterations/it-013-clean-pager-and-end-fidelity.md)。

- **fix(wardrobe)**: it-065 穿搭卡组边界与全应用质感收口——W8 卡组静止层叠落进 20dp 页面栅格（end 22dp 吸收顶卡单侧 28dp 层叠偏移、停驻卡恒屏外，it-064 start 内缩口径废止）；W3 网格底缘渐隐废止（米白带盖照片/标签）；横滑渐隐起点策略 `fadeAtStart`（W8/W10 起点提示、W3 因筛选钮维持干净）；W4 吸底可达（imePadding + 48dp 余量）；「颜色」重复标题源码/实机均不复现。详 [it-065](wardrobe/specs/iterations/it-065-ui-polish-and-card-bounds.md)。

- **chore(wardrobe)**: it-067 构建版本号自动生成——`versionName = 0.5.0.<wardrobe 提交数>`（git rev-list 限定衣橱范围，未提交改动加 `-dirty`）、`versionCode = 提交数`，设置页/回顾页「装的哪版」可辨识，销记「版本号停更」二次复发；ADR-027。详 [it-067](wardrobe/specs/iterations/it-067-auto-versioning.md)。

- **chore(dev)**: 模拟器分治——每应用固定 AVD（wardrobe_test / darkroom_qa）+ `tools/emu.sh` 设备绑定脚本（up/install/launch/cap/uadump，序列号按 AVD 名解析不硬编码端口），并行会话不再互抢模拟器/前台；LESSONS 旧「共用 AVD 重聚焦重试」兜底规则替换为新不变量。详 [it-002](specs/iterations/it-002-emu-device-split.md)。

- **fix(darkroom)**: it-012 收尾 10——v0.5.10 两处发版回归热修：画册底衬误用文字色 inkFaint 铺成深灰 slab（改固定浅暖灰 0xE2DCCC+去 8dp 卡阴影）、PhotoViewer 半透明透底致顶栏深字隐形（改不透明 0xFF151515，实测顶栏区 std=0）；中段灰雾收敛（contrast/brightness 起手抬高、模糊提前收清）；自此恢复发版前模拟器全量自测（独立 darkroom_qa AVD，序列/查看器/浮层子代理验收全过），v0.5.11 出包。

## 2026-09-29

- **feat(wardrobe)**: it-066 导出回程锚点、长图放大核对与空态行动按钮——W1 回程提示条（复制长图后引导录回成品图、未收藏自动建穿搭、愿望组合护栏、会话级状态）；W6 长图预览原位全幅展开（Prompt 可读、自滚、角标收起，销账 it-017/it-042 C5 挂账）；W8/W3 空态补「去搭配一套 / 清除筛选」（DESIGN.md §5.8）。89 测绿 + 演示包构建过，W6 双态 AVD 实证。详 [it-066](wardrobe/specs/iterations/it-066-export-loop-closure.md)。

- **fix(darkroom)**: it-012 收尾 9——显影色彩改原位分层加深（染料通道增益与冷暖摆幅收回克制档，消除全图绿→紫色相摆动）；卡纸分层改底衬色调（画册底比相纸深一档），弃投影与描边硬线。版本 0.5.9。

- **fix(darkroom)**: it-012 收尾 8（视觉自查轮）——画册卡补 1dp hairline 描边（无阴影后与纸底糊边的 P1）；查看器存图/编辑按钮统一 44dp；编辑分组卡分隔线加深、区标题 SemiBold、副题改「点完成返回成片」。版本 0.5.8。

- **fix(darkroom)**: it-012 收尾 7——配置浮层改深色相机面板（暗底+亮色胶囊，对表手机相机参考图）；画册卡阴影彻底移除（纸上贴纸不需投影）；拍立得显影改分层浮现（先灰调结构→色彩分层迟到（染料增益翻倍）→对比与锐度最后到位，模糊保持到后半程才清）。版本 0.5.7。

- **fix(darkroom)**: it-012 收尾 6——配置面板改相机式浮层（半透明蒙层盖照片上、点蒙层收起，新增相纸选项）；画册卡阴影 8dp→2dp 去突兀；大图查看器按钮暗底配亮字（编辑钮原黑墨色不可读）。版本 0.5.6。

- **fix(darkroom)**: it-012 收尾 5——编辑表单收成一张分组卡（去碎片感）；画册卡撑满屏宽（去左右预留 peek）；默认相纸改真实拍立得暖白纸感（paper/ink/accent 全套暖化，弃冷白死黑）。版本 0.5.5。

- **fix(darkroom)**: it-012 收尾 4——末态「相纸感」叠加压至极轻（grain 0.20→0.06、vignette 0.20→0.12、高光压缩 0.94→0.98、色温收敛）：显影完成的画面观感对齐原图质量（源已是 1440 原图解码，非缩略图）；过程动画不变。版本 0.5.4。详 [it-012](darkroom/specs/iterations/it-012-album-first-ia.md)。

- **fix(darkroom)**: it-012 收尾 3——末态柔焦归零（滚动中卡片全锐）；大图查看改为整张成品卡全屏放大（1440 宽渲染相纸/日期/脚注全套，不再是裸照片）；编辑表单收纳列入下轮。版本 0.5.3。详 [it-012](darkroom/specs/iterations/it-012-album-first-ia.md)。

- **fix(darkroom)**: it-012 收尾 2——画册画质修复：页图弃用 loadThumbnail（系统低质 512px 缩略，上屏即糊），改 ImageDecoder 原图降采样 1440 长边（软件位图，导出端可画）；网格小图仍走 256 缩略；缓存分两层控内存。版本 0.5.2。详 [it-012](darkroom/specs/iterations/it-012-album-first-ia.md)。

- **fix(darkroom)**: it-012 收尾——画册卡改原地亮相（去掉画册语境下读成「先跳下去」的出纸位移，位移动画只属显影台）；修 weight 挂在组件内容里对 Column 无效的布局 bug（翻页器吃满高度把动画模式/速度配置胶囊挤出屏外，首页网格同型错误一并修）。版本 0.5.1。详 [it-012](darkroom/specs/iterations/it-012-album-first-ia.md)。

- **feat(darkroom)**: it-012 相册优先 IA 重构——首页改为纯照片墙（小图网格 + 拍照/设置两枚图标，表单按钮全清），点缩略图进「画册模式」独立屏（翻页显影、动画完停留、快捷面板+重播）；返回栈理顺（画册↔网格、成片按来源回）；未授权/空相册为安静引导态；拍照保留经典显影台路径；ThumbCache 双尺寸键。67 测绿。版本 0.5.0。详 [it-012](darkroom/specs/iterations/it-012-album-first-ia.md)。

- **feat(darkroom)**: it-011 收尾 2——沉浸相册底栏改专业相机式快捷面板：摘要胶囊（模式·速度一览）点开向上面板，快速换显影模式与显影速度（速度首次移出设置页，重播即生效）；SelectChip 抽共享。版本 0.4.2。详 [it-011](darkroom/specs/iterations/it-011-immersive-album.md)。

- **fix(darkroom)**: it-011 收尾——W3 拆「成片查看/编辑」双态：显影完成默认落成片查看（仅存图/存视频/分享/再洗等关键操作 + 编辑按钮），编辑是显式动作（完成/返回键退出）；沉浸相册点按成品=全屏大图查看器（带存图片/编辑动作），不再直跳编辑；沉浸模式顶行新增退出按钮（会话级，回退态可一键再进）。版本 0.4.1。详 [it-011](darkroom/specs/iterations/it-011-immersive-album.md) 收尾修正。

- **fix(wardrobe)**: it-064 四处布局修正——W1 垂直预算压缩（鞋槽初始一屏完整可见）；W8 撤销 hero 共享元素修复切换不丝滑（it-058 引入的回归）+ 卡组与筛选行缓冲；W3 网格底内衬 96→76dp 消除 FAB 下空白带；筛选行渐隐默认（起点）不再渲染，详 [it-064](wardrobe/specs/iterations/it-064-w1-w3-w8-layout-fixes.md)。

- **feat(darkroom)**: it-011 沉浸式相册显影——授权后首页变成可左右滑动的相纸墙：首次（本次启动内）翻到的照片自动跑一遍当前模式的出纸+显影动画，已播过的翻回直显成品；显影中点按跳过、成品点按直达成片页（日期章取拍摄日期、创作选项沿用）、重播按钮手动重跑；未授权/拒绝回退原选图布局（引导卡点击才发起权限，ADR-008 推翻零相册权限）；MediaStore 分页+缩略图 LRU。67 测绿（+GalleryPlaybackTest）。详 [it-011](darkroom/specs/iterations/it-011-immersive-album.md)。

- **fix(darkroom)**: it-010 收尾——三档显影时长整体缩短三分之一（慢洗 12s→8s、标准 8s→5.4s、快显 4s→2.7s，Leo 反馈），刻度条/导出时长随 durationMs 派生自动跟随，单测锁值。版本 0.3.1。详 [it-010](darkroom/specs/iterations/it-010-craft-options-and-tidy.md) 验证记录。

- **feat(darkroom)**: it-010 小而美布局收口与创作选项系统——拍立得可选四款相纸（经典白/暖米/墨框/灰板，配色真源 CardPalette.forFrame、打印对比单测锁）、标题字体（衬线/黑体）×字号（小/标准/大）、卡脚脚注自定义（留空不印，替代水印开关并迁移旧偏好）；W3 section 统一「标题左+当前值右」+ pressScale 全选择项、W1 头部一行化、W4 去水印行；选项与显影模式同语义跨会话保留。64 测绿 + 视觉走查 8/10（P0=0，P1 已修）。详 [it-010](darkroom/specs/iterations/it-010-craft-options-and-tidy.md)。

- **feat(wardrobe)**: it-063 W3 衣橱页 chrome 减负——新增入口全宽按钮改右下角 56dp 墨色 FAB（网格落到底部导航，静止位 96dp 净空）；品类行渐隐带宽 28→44dp 提前封满（被裁图标彻底消隐，不再贴「筛选」）；卡右上菜单钮 40% 黑底圆改 26dp 纸底 hairline 描边印刷点（MoreHoriz）；底部死区白块消除。亮暗两模式像素实测 + 独占 AVD 交互验证全过，it-037「CTA 不覆盖卡片」验收由本迭代替代。详 [it-063](wardrobe/specs/iterations/it-063-wardrobe-chrome-slim.md)。

- **feat(wardrobe)**: it-061 鞋槽显示修复与导出表单减负——透明素材先裁 alpha 边再 Fit（鞋/帽槽内容充满格，鞋槽 2.6→2.2）；槽位 n/m 点开品类清单 BottomSheet 直选（替代循环翻页）；W6 画面设定默认折叠、五维默认全空（去记忆与对话预选），详 [it-061](wardrobe/specs/iterations/it-061-slot-display-and-export-slim.md)。

- **fix(wardrobe)**: it-062 顾问两页顶栏同色 + 会话列表预览 Markdown 扁平化——W12/W13 `TopAppBar` 默认纯白 surface 落在纸面背景上形成割裂白条，改显式 `ec.paper` 同底；列表预览此前直接显示 `##`/`-`/`**` 原始标记，新增 `markdownPreviewText()`（与正文渲染同口径的扁平化，旧索引残留句中 ` - ` 折叠为 ` · `，data 层不动）。`MarkdownPreviewTextTest` 6 例 + 全量单测绿，AVD 演示模式种旧格式数据实测两页同色、预览干净。详 [it-062](wardrobe/specs/iterations/it-062-chat-topbar-and-list-preview.md)。

- **feat(darkroom)**: it-009 显影早期提速与成片页主视觉——三模式曲线去前期死区（8% 即有可察觉淡影，单测锁死），拍立得出纸后 1 秒内影调即开始浮现；W3 预览宽度优先放大（4:5 照片区约 75% 屏宽，46% 屏高限幅），点按预览进全屏大图（暗底 Fit、点按/返回关闭、OpenInFull 角标）。60 测绿，AVD 实测早帧影调展开翻倍。详 [it-009](darkroom/specs/iterations/it-009-pacing-and-result-preview.md)。

- **fix(darkroom)**: it-008 三次修正——拍立得显影对表真实相纸：暗角改为成片特征（起手近无、定影段落 0.20，旧版起手 0.45 的角部压暗使均匀浮现读成「中心晕开」）；柔焦 0.05→0.016（影像全程锐利，糊的只是白浊层）；影调次序显影（highlightGain 0.62→0.94，暗部先现、高光最后到位）；白浊层早期微偏冷回中性（修掉 or 0xFFFFFF 淹没冷调的死代码）。按住药水条 seek 采 23–97% 六帧像素级复核：径向坡降 −40→−9 luma、暗/亮密度比单调收敛、末态 ESF 2px 锐利。版本 0.2.3。详 [it-008](darkroom/specs/iterations/it-008-store-polish-motion.md) 验证记录。

- **feat(wardrobe)**: it-060 演示 mock 数据扩容——内置衣橱从 36 件单品/9 套穿搭扩至 68 件/15 套，补齐八品类与季节/场合组合，新增 32 张 RGBA 透明底 PNG；素材包 validator PASS（0 警告），详 [it-060](wardrobe/specs/iterations/it-060-mock-corpus-expansion.md)。
- **feat(wardrobe)**: it-059 录入表单点选化 + 演示人物数据——W4 主区零打字（颜色 13 预设 chips/常用标签 10 预设 chips，自由值附加 chip 回显），名称留空自动命名「颜色+品类」，描述/自定义输入折叠进「补充细节」二次交互；保存条件只剩照片必填；mock Leo 预置人台风格形象参考照（导出面板「附形象参考照」演示模式直接可体验），详 [it-059](wardrobe/specs/iterations/it-059-quick-entry-form.md)。

- **fix(darkroom)**: it-008 二次修正——拍立得按真实过程重做：「左下入口偏心推进的药膜前沿 + 堆积暗边」（it-007 语义，实机即胶水扩散层）重写为**白浊阻光层均匀消散**（整张从白里逐步浮现，无方向、无暗边，仅 ≤5.5% 纸面噪点），reveal 曲线摊满全程不再 70% 全透；**药液气泡整体删除**（真实显影在夹层内看不见泡）。RevealField 真源一次改齐三渲染端；59 测绿；模拟器全程序列像素级复核（方向性亮度差较旧版缩小 28 倍、气泡检测全零）。版本 0.2.2。详 [it-008](darkroom/specs/iterations/it-008-store-polish-motion.md) 验证记录。

- **fix(darkroom)**: it-008 收尾修正——移除拍立得的前沿湿光带：对角近似层与真实偏心前沿不重合，实机观感像「一层胶水扩散」，与「逐步浮现」的显影语言冲突；胶片湿边（与 SWEEP 前沿精确对位、属前沿本身）与药液气泡保留。单测锁死拍立得/数码湿光恒零；版本 0.2.1。详 [it-008](darkroom/specs/iterations/it-008-store-polish-motion.md) 验证记录。

- **feat(darkroom)**: it-008 显影过程动效精修与上架级质感收口——显影中段新增 progress 确定性的药液气泡（暗晕+白芯、种子驱动、倒放可逆）与前沿湿光带（胶片与 RevealField 前沿公式精确对位），定影落定并发 600ms 对角光泽扫，阶段文案 fade+rise 过场、百分比 tabular figures；上架收口：应用图标重绘为石墨底灰阶「显影中」构图（告别 it-006 前的墨绿+橙旧身份）、窗口底色对齐 paper token 消灭冷启动闪变、W1 首次冷入场五组 stagger 编排（导航壳持 flag 二次进入不重放）、W3 成片亮相入场、模式 chip/样片卡/snackbar 全部消灭瞬跳；新增 `DevelopFx` 纯函数真源 +7 单测（63 绿）。详 [it-008](darkroom/specs/iterations/it-008-store-polish-motion.md)。

- **feat(wardrobe)**: it-058 交互体感上线专项——共享元素转场首次真正接线（W1 槽位/W3 网格→W5 详情、W8 hero→W7 轮播，此前 spec 声称但来源端从未挂 key）；全站照片灰阶占位消除白块闪现（Coil placeholder + crossfade）；新增 `pressScale` 按压反馈（照片卡/主 CTA，减弱动态不缩放）；随机一套忙碌态（Casino 旋转+防连点）；对话页 typing 三点呼吸与气泡入场；W1 四分区与导出面板设定行 stagger 入场；Tab 切换升 fade+微缩放层次；冷启动 splash 背景对齐 paper（含深色）；05 动效清单三处纸面承诺勘误（#3 接线/#8 空态自绘/#10 sheet stagger）并新增 #14–#18。动效走查方法：5 段录屏 709 帧 filmstrip + 视觉评审 + 源码钉死，详 [it-058](wardrobe/specs/iterations/it-058-motion-polish.md)。

- **feat(darkroom)**: it-007 显影模式体系与拍立得还原——W1 新增「拍立得/数码相机/胶片」三模式选择（持久化），三模式各有独立卡面（白框相纸 / 深灰回放屏 + OSD 带 / 35mm 齿孔片条）、显现前沿（化学偏心 / 网格块 / 横向冲洗）与出纸动画（槽口升纸 / 开机扫描线 / 片盒卷出），曲线与印字配色按模式分派且三渲染端共用真源；拍立得按实物重修（滚轴入口偏心推进、分染料上色时序与窄动态、出纸分段顿挫 + 槽口下压、纸纹与成像区细线），并修掉定影末尾模糊硬跳变、模式偏好旧快照回冲、Activity 重建重放出纸三个缺陷；W2 的 Material Slider 换成自绘药水刻度条（5% 短刻 / 25% 长刻 / 阶段分界、阶段名上轨、拖动倒放语义不变）。56 单测全绿，AVD 走查三模式 W1–W3 与胶片原生导出，详 [it-007](darkroom/specs/iterations/it-007-develop-modes-and-fidelity.md)。

- **feat(wardrobe)**: it-057 导出表单随推荐走——对话入口改净初值（不再叠加历史记忆，修复实测「推荐休闲装、表单残留办公室」的语境冒充）；场景短语未命中预设时回填原文为自由值（≤8 字），氛围按前缀放宽（休闲→休闲随性），季节/光线/构图仍走枚举；场景行新增「自定义」输入入口，自由值 chip 可一键清除；81 测全绿 + 污染-验证两段式 AVD 走查（记忆「夜晚霓虹」不再带入对话面板），详 [it-057](wardrobe/specs/iterations/it-057-export-scene-freeform.md)。

- **feat(wardrobe)**: it-056 导出面板携带顾问场景——对话推荐卡「复制长图」打开 W6 面板时，标题/说明命中的五维预设选项（场景·办公室、季节·早秋 等）自动预选、一键可改，不再要求用户把 Agent 刚说过的场景重选一遍；提取走 `extractRecommendationSelections` 纯函数（长词优先包含匹配，半截词不命中），`ExportSheet` 新增 `presetSelections` 参数以记忆为底覆盖同 key，四处既有调用零变化；81 测全绿 + AVD 端到端走查，详 [it-056](wardrobe/specs/iterations/it-056-chat-export-scene-preset.md)。

- **feat(wardrobe)**: it-055 顾问卡片可点可导——W13 推荐卡已匹配单品 tile 点击进 W5 单品详情（未匹配不可点、中性提示保留），卡片底部「复制长图」与 W5 顶下新导出入口均复用 W6 导出面板（`existingOutfit=null`，搭配页/心愿同先例），整套与单件都能出「照片拼版+五维提示词」长图拿去外部生图；匹配逻辑抽 `matchRecommendationItems` 纯函数 +2 JVM 单测，77 测全绿；浅/深色走查 P0/P1=0，详 [it-055](wardrobe/specs/iterations/it-055-chat-card-detail-and-export.md)。

- **docs(repo)**: 建立 Agent 经验自进化闭环（仓库级元迭代 it-001）——新增根 `LESSONS.md` 经验库（头部读写/晋级/淘汰/容量规则 + 回填 8 条种子教训）；AGENTS.md 迭代流程新增第⑤步「经验沉淀」（原 CHANGELOG 步顺延为⑥）、上下文恢复新增必读 `LESSONS.md`；仓库级元迭代提案落档 `specs/iterations/it-001-agent-lessons-loop.md`，README 仓库约定补经验库一行。

## 2026-09-28

- **feat(wardrobe)**: it-054 顾问回复改为 Markdown 渲染，并将模型引用的当前衣橱单品解析为真实照片穿搭卡片；未匹配建议显式标注，复制保留原始 Markdown；新增解析器单测与 W13 浅/深色走查。

- **feat(wardrobe)**: it-052 全局黑白灰高级视觉与排版系统落地——主题 token、Material 色阶、全局 Typography、心愿占位、年度回顾、数据包状态符号、复制纸屑与导出长图均收口为中性灰阶；照片/成品图保留原色；同步更新 DESIGN.md 与 wardrobe 常青 spec。
- **feat(darkroom)**: it-006 黑白灰编辑式视觉——全局纸面/Surface/交互强调与 Snackbar 逆色改中性灰阶，重订 CJK 字级并限制字距用于短拉丁标签；作品标题改常规衬线最多两行、日期改 Sans 等宽数字灰阶排印；分享装饰同步去暖色，照片影调选项保留。`testDebugUnitTest assembleDebug installDebug` 通过，Android 14 AVD 浅/深色 W1–W4 走查，详 [it-006](darkroom/specs/iterations/it-006-monochrome-editorial-identity.md)。
- **feat(wardrobe)**: it-050 顾问升为第四个底部导航 Tab，新增多会话列表与详情；旧单会话自动进入历史索引，未配置 Key 时历史可读但不可新建/发送。Mock 环境启用独立的连接偏好、Keystore、会话、用量与 7 天/100 条/50 MB 响应缓存；整轮完成后才写缓存，失败/取消丢弃，W11 自检不走缓存。`:app:testDebugUnitTest` 70 项全绿、`:app:assembleDebug` 成功；模拟器确认四 Tab 与历史会话列表。Mock 真 Key 首发/缓存命中未做联网验收，避免调用未由用户在测试命名空间配置的凭证；详 [it-050](wardrobe/specs/iterations/it-050-chat-top-level-and-mock-cache.md)。
- **feat(darkroom)**: it-005 可选成片风格——W3 加入原色/暖片/银影/青幕/柔光横向缩略预览，效果即时应用到分享预览、静图和视频逐帧；柔光按亮部阈值生成确定性晕光并缓存，风格切换会使旧导出失效；压缩 W3 预览与编辑区，保持导出按钮首屏可见。Debug 构建及 Android 14 AVD W3 浅/深色检查通过，详 [it-005](darkroom/specs/iterations/it-005-selectable-photo-looks.md)。
- **feat(darkroom)**: it-004 暗房仪式与社交成片——W1 换成本地照片样片，W2 改为照片种子驱动的确定性药膜扩散并加入阶段短句与有重量的出纸回正，W3 将卡片预览提升为主视觉并新增 1:1/4:5/9:16 图片与视频统一构图（含故事安全区）；增加 `ShareLayout` 与 ADR-006，不引入依赖。Android 14 AVD 验证三画幅图片/视频均落库为 1080×1080、1080×1350、1080×1920，系统分享面板可打开；浅色/深色 W3 检查通过；`testDebugUnitTest` 38 项全绿，详 [it-004](darkroom/specs/iterations/it-004-emotional-darkroom-share.md)。

## 2026-09-27

- **fix(darkroom)**: it-003 功能收口与成片页信息架构——速度档即选即生效（原 `clock` 固定 STANDARD，慢洗/快显仅改标签：startSession 按所选档重建，斜率实测 8.0/12.5/27 %·s⁻¹ 三档对表；SLOW 导出 MP4 `mvhd`=13.999s 与 ExportPlan 精确一致）；成片页进度条上贴 CTA、无滚动 dump 到「冲洗中」、存图/存视频 y2148–2197 入首屏（追加成片卡高上限 360dp + 编辑卡/段间距压缩，对齐改版线框）、分享×2 与再洗一张收为一行；W1 移除重复「设置」chip（dump 实测入口 1 处）、footer 常驻视口底；33 单测 0 失败，验证记录回填 it-003（详 specs/iterations/it-003-result-ia-speed.md）。

- **fix(darkroom)**: it-002 稳定性与卡面几何——新增 `ui/PageInsets.kt` 四页统一让出状态栏+手势条（顶行控件中心 y111 死区→239 可点，⚙/← 中心 tap 实测双向通过）；`CardLayout.solve(width, maxH)` 按可用高度反解卡宽 + 签名域互斥 0.52/0.54w + 日期章超域缩字（Compose/native 双端），像素断言 W2/W3 签名区溢出 0px（原 +32/+100px）、长标题间距 45px 无重叠（原撞 ~140px）；`ManifestGuardTest` 锁 VIBRATE 权限防 P0 回归；CardLayoutTest 9 测试（含 maxH 反解/无界等价/任意宽度互斥）、`testDebugUnitTest` 33 绿；01/02/04 常青 spec 同步，验证记录回填 it-002。

- **fix(darkroom)**: it-001 首轮 UI 审查 hotfix——P0 定影落定必崩（manifest 缺 `VIBRATE`，`Haptics.confirm` 抛 SecurityException，logcat 复现 3 次）补 normal 权限即修、全流程回归通过；同轮完成 4 页走查 + 11 项功能自测（药水条 seek/甩一甩注入/双导出落盘/三入口全通），遗留 5×P1（速度档只改标签、页头无 insets 触摸死区、卡面溢出与重叠、首屏 CTA 裁切）与 5×P2，含 4 张改版线框与证据链，详 `reports/darkroom-ui-audit/` 与 it-001 验证记录。

- **feat(posthouse)**: it-001 驿站首次落地——macOS 菜单栏「烽火台 + 自动哨兵 + 邸报」全量实施（M1/M2/M3）：三态图标（全绿/狼烟 flame+计数/熄火）逐仓明细与对账级探测（status.json 与 `git status`/`rev-list` 逐值相等，实拍菜单栏火焰+1）；手动/CLI 推送注入 `http.version=HTTP/1.1` + `postBuffer=512MB` 并落日志；自动补推双闸门**默认全关**，硬规则「永不 force、永不自动 pull/rebase/merge、远端不通不推」全部落 `PushDecision` 纯函数单测——沙盒 e2e 断网→恢复 **4 秒自动补推**、白名单外仓库零触碰、non-FF 被拒只通知不动手、分叉每仓一次提醒、网络失败指数退避 30s→600s + 疑似代理故障提醒；22:30 邸报定时（改钟实测 16:06:05 自动产出）Markdown 战报与 git 真值逐项对账（6 提交/fix2 feat1 docs3/积压 1 仓/连续活跃 9 天）+ 成就规则引擎 6 枚纯函数可回放（首日点亮/连修三坑/深夜修罗/大部队/清仓大吉/千军一发，档案去重）；SwiftPM+build-app.sh 组装 .app 零 Xcode 工程，swift-testing **39 测试全绿**；实测踩坑四连入库档：子目录 `.git` 判定（rev-parse 会把 dist/gradle 误判成仓）、engine 串行队列自死锁、未 resume timer 释放 SIGTRAP（CLI exit=133）、TCC 文稿授权挂起 + 15s 扫描超时兜底；附 CLI 三旗标 `--probe/--push/--gazette` 脚本化入口与 network-rescue 一键诊断入口、M3 可选 LLM 润色开关（默认关，llm.key 0600，失败回退规则版；详 posthouse/specs/iterations/it-001 验证记录）。

- **fix(wardrobe+libs/carddeck+eats)**: it-048 卡组 hotfix——W8 甩卡被容器 `clipToBounds` 截断（it-031 旧库时代遗留，it-047 真实飞行后暴露）wardrobe/eats 两处已删并留防回归注释；连续快滑「滑不动」根因 = it-047 提交管线以 settledValue 翻转为信号（同向二次落锚不翻转即吞提交 + 排队 snapTo 抢锁拉回手中卡片），改**到达帧提交**（offset 精确到锚 + pointerDown/isAnimationRunning/flingInProgress 三重落定门——fling 的 spring animate 不在官方动画跟踪内，缺独立门实测一次甩卡连环推进 4 张）+ 按下快进结算 `onDeckDown`；实测单甩 +1、250ms 间隔连滑 +2、回卷/抽取/eats 连甩全过，双端单测绿（详 it-048-hotfix-deck-clip-continuity.md）。
- **docs(readme×5)**: 全仓应用说明文档配图——各 README 新增「界面速览」图集（图片入各应用 `docs/img/`，共 20 张 JPEG/PNG ≈4.6MB）：wardrobe 7 屏 + eats 6 屏（模拟器演示模式逐屏实拍，W 编号与线框对应）、travel-rpg 三站 4 场景（dev5199 + `__game.tp` 摆机位，乌兰布统用 sunset 时段）、xiangqi 终局对局全景（含着法表/胜率曲线）、island DebugShot 卡片（演示数据，GLM 橙环展示健康度配色）+ 设置窗；顺带修正 eats README 过期状态行与 wardrobe 构建注记；clips 无 UI 不配图。

- **docs(readme)**: 赤峰环线/象棋竞技场标记**已归档**——做游戏的预期下调，经确认选「代码原地保留」方案：两目录与 specs 完整保留可随时复启，仅 README 状态列收口（travel-rpg 过期的「it-001 开发中」修至实况 it-012，xiangqi 修至 it-001~003 MVP 完成）；不拆仓不打 tag 不删代码。

- **fix(island)**: it-003 loading 图标永转+抖——埋探针日志证明状态机健康（fetch 0.3s/轮 准时停）→ 根因在动画层：`.animation(nil)` 与 `withTransaction(disablesAnimations)` **离散写入均打不断 in-flight `repeatForever`**（模板匹配 48 角度×对照实验 SAD=0 实证：修复前停转后 67.5°/255°/105°/300° 持续变）；终修 = **0.01s 有限动画覆盖同 keypath**（视觉=原地归零不倒转）；「不中心对称/抖」= 字形 ink 74×90 非方质心画 0.63pt 偏心圆（慢转被读成抖，停转即消）；验证在途 82.5° 在转、停后 6s/7.5s/9s 恒 0°；锚点实测正确（0°/180° bbox 重合，偏差 0.06pt 亚像素）；05 动效表同步禁用 nil 停转；swift test 49/49 绿。

## 2026-09-26

- **feat(wardrobe+libs/carddeck+eats)**: it-047 卡组换官方 API 自研内核（AnchoredDraggable，foundation 1.8.3 零 experimental）——甩卡/回看全路径动画+速度判定（自研 `flingTarget`：速度 ≥125dp/s 或 100dp 位置阈值，修官方 computeTarget v=0 丢甩出）、单一弹簧源 `spring(0.9,500)` 全路径收口 + `committedTarget` 幂等提交管线、drawRandom 首达截停保 420–560ms 步距、抽中落定 Confirm 震接线；删 `compose-swipeable-cards` 三方依赖与三处 JitPack 仓库声明；W1/W7 pager 参数收敛（snap/fling spec 显式注入 + `beyondViewportPageCount=1` + `animateScrollToPage` 传 spec）+ SlotGrid 落定轻弹 1→1.03→1；减弱动态单一入口降级（snap 即时落位、拖拽保 1:1）；eats SpinScreen 迁移连带修复（首次获得可注入动画）；双端 assembleDebug+test 绿、模拟器全路径实测；对抗评审（1 blocker/5 major/18 minor）全处置——W1/W7 减弱动态改 `EditorialMotion.pagerFling` 瞬时降级（官方 SnapFlingBehavior 吃不到系统缩放，字节码实证）、提交守卫三重条件修连按›吞步、n/m 下沉 `DeckCounter`、空牌堆 Rest 锚防越界、SDK 侧抽取中翻页 no-op、`runSettlePulse` 收口 Motion.kt；真机 60fps 与 Layout Inspector 计数器量化遗留。
- **fix(wardrobe)**: it-046 槽位卡撑满去黑边距——深色下旧衬纸（surfaceVariant@55%）+Fit 留边即 Leo 所见黑边距；改分档呈现：常规格（0.6/0.78/0.85）Crop 满格无留边，帽(1.0)/鞋(2.6) 极端比例保 Fit + 固定浅纸 #F2F3F5（主题无关；裁切伤害以内容包围盒实测证否）；W1 序号 n/m 末页回卷改即时落位。
- **fix(wardrobe+libs/carddeck)**: it-046 穿搭卡组动画丝滑化——库 1.1.4 反编译实证飞卡默认 `spring(0.6,100)`（落定≈1s、9% 过冲晃尾）且 drawRandom 55ms 连发远短于飞行时长（多张叠飞/moveNext 半空摘除）+ 末张纯瞬移；`CardDeck` 增 `animations` 参数透传（wardrobe 注入 `spring(0.9,500)`≈0.32s 落定），drawRandom 改步距 420→560ms、步数 4+rand(n)、末张「甩出+复位」组合步；specs 01(US-46)/05（槽位呈现+动效表#12+衬纸表）/it-046/carddeck 00 同步；模拟器像素探针+连拍+回卷实测全过，eats 编译兼容。
- **fix(island)**: it-003 对抗审计 5 CONFIRMED + 11 P2 全修——**P0-A hitTest 坐标系错配**（point 窗口基坐标 vs rect 屏幕全局恒不相交 → 展开态点击全死+穿透下层，`screenPoint` 换算修复）；**P0-B** 绝对量/aux/重置数字补 numericText（§5.9 残留）；**P1-C** 环 stroke 外溢 4pt（路径=外径−环宽，墨迹精确 66.0pt）；**P1-D** 0% 绘最小健康色弧、无数据整圈白 25%（与色点/菜单同值）；**P1-E** DESIGN §2.5 登记指针例外 ≥24pt + 空态按钮实测 25pt；P2 批：判定矩形随遮罩 0.45s 插值、圆帽交叠补偿（99% 留缝/100% 闭合）、环居中 W2 对齐、明细行 firstTextBaseline、`·`/`--%` 统一 55% 白、US-2 双源错误措辞自洽；swift test 48/48 绿（+7 纯函数回归 +4 渲染级 ImageRenderer 回归），三态截图像素断言全过，独立对抗核实 5/5 CONFIRMED（10/10 verdicts）。
- **fix(island)**: it-003 用户反馈两连——①「环内两种绿」：主环弧线去沿弧渐变+辉光改**实色**（一环一色一义，像素级验证弧上仅 1 种色值）；②「两环没对齐」：实为用户运行的 `dist/island.app` 是 it-003 前旧包（25 日 23:37 打包，双环 60/68 错位即 it-003 已修的 P1-7），新单环 UI 未达屏幕；dist 已重建，待退出旧实例重开生效。
- **feat(island)**: it-003 卡片改版 + 可扩展架构——UI 走查 P0/P1/P2 全修：**单环主锚+行式明细**（面板头=源名+主档重置/失败红字、环心=剩余%+档名、环下副档/绝对量行、空态虚线环+去设置），**ProviderRegistry 单表驱动**（凭证/面板/设置/菜单遍历注册表，新增源=case+一条+解析器），**按源状态 `SourceState`**（逐源成败不吞，修 it-002 AC4；清凭证删缓存），**`IslandLayout` 布局单一真源**（遮罩/hitTest/窗口帧同源、面板高度=渲染分支），页脚 TimelineView 真实时间，健康度配色收编 `IslandTheme`（US-4 修正、菜单图标同语义），numericText 数字过渡（§5.9），设置清除二次确认+逐源诊断+620 高度，菜单分组摘要；ADR-009/010/011 补录，specs 00–06 + README + DESIGN §1 全同步；swift test 37/37 绿，DebugShot 五状态截图走查回填验证记录。

## 2026-09-25

- **fix(wardrobe)**: it-045 顶栏↔内容间距全站统一 20dp——盘点实证 0/4/6/8/10/20dp 六种取值并存（W4/W5/W7/W11 内容贴死顶栏 0dp、W9 4dp、W10 6dp、W12 8dp、三 Tab 页 6/10dp 且标题行 top 8/12 不一），12 处逐屏改齐（W12 reverseLayout 下 contentPadding 属滚动内衬不可见，改容器级 padding 恒定生效），spec 05 补「顶栏↔内容节奏」条目、01 补 US-45；模拟器 dump 逐屏量化 19.8–20.2dp 全过、单测绿。
- **feat(wardrobe)**: it-045 演示体验包构建开关 `-PdemoDefault=true` → `BuildConfig.DEMO_DEFAULT` 注入 DemoMode 缺省值（显式偏好仍优先、常规包恒 false），出 mock 数据默认开的体验包，退出通道（设置/5 连点）不变。
- **fix(wardrobe)**: it-042 第五轮走查修复 C1–C8（P1×2/P2×6，C5 挂账）——W2「使用中」补 8dp 分读、W8 卡组 hero 成品图改衬纸 Fit 修裁头（05「成品图一律 Crop」条款作废）、OutfitThumb 日期并入 yyyy/MM/dd、W1 槽位长按 toast 读全名、W9 打卡 0 隐藏闲置冗余卡、W10 域名字距归零、W1/W3 滚动底缘 28dp 渐隐（共享 `fadingBottomEdge`）；specs 01/02/05 同步，编译+单测绿，模拟器复验回填验证记录。来源=2026-09-25 r5 走查（P0×0/P1×2/P2×6）。

- **docs(xiangqi)**: it-003 过夜双局结果——Flash 档 mimo-v2.6-flash 将死胜 glm-5.3-flash（76手/105分），Pro 档 glm-5.3 将死胜 mimo-v2.6-pro（53手/109分），四模型 1:1；报告+双局快照/棋谱入库 `reports/2026-09-24-xiangqi-showdown/`，it-003 验证记录回填（含无限等待 Promise.race 假判负坑）。

## 2026-09-24

- **feat(xiangqi)**: it-002 Jev 胜率评估与曲线——每手落子后异步问 TypeSafe Jev（Choice: 红胜/和/黑胜概率）回填 `Move.eval` 并广播，侧栏纯 SVG「胜率曲线」（红实黑虚双线+50%基线+置信图例），导出棋谱附评估值；key 入 `.env.local`（gitignore），`JEV_DISABLED` 关停冒烟；specs 01/02/03/04/05 同步，smoke 回归 PASS。网络坑：mac LibreSSL 访问 api.typesafe.ai 被掐、Node OpenSSL 正常。**修订**：评估按局可选（勾选框+`eval-toggle` 运行中切换，三态实测 PASS）、发给 Jev 的 prompt 全英文（state/历史走 ICCS）。

- **feat(xiangqi)**: it-001 真模型验证收口——GLM+MiMo 两把 key 入 pi `auth.json`（免 env、跳过会阻塞的钥匙串注入）；超时语义由总时长改**静默超时**（真局实证长思考被误杀后修复，ADR-004/US-03 同步）；Flash 对决第三局 60 手零判负达 AC 并最终**第 102 手困毙自然终局（黑方 mimo 胜，记分板 1:0）**、6 次非法重试自愈、上下文隔离实测；思考流同类增量合并成段；前端默认对阵 `glm-5.3-flash vs mimo-v2.6-flash`。

## 2026-09-23

- **feat(xiangqi)**: it-001 AI 象棋竞技场 MVP——仓库首个 web 应用：`server.mjs`（零依赖 http+SSE+POST）spawn 两个 `pi --mode rpc` 隔离 session 子进程，FEN+合法走法白名单+ICCS 协议驱动对弈（非法重试 ≤3、超时重试、将死/困毙/60 回合无吃子/150 回合上限裁定），前端 xiangqiboardjs+xiangqi.js 观战 UI（思考流/中文记谱/记分板/手动代走/棋谱导出），specs 全套 + 假模型全链路冒烟（`tools/smoke.mjs` 双阶段 PASS）。
