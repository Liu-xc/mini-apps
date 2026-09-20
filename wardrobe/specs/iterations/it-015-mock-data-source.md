# it-015 · 应用内 Mock 数据源（演示模式）

- **状态**：已确认（/goal 指令，2026-09-20）
- **提案日期**：2026-09-20
- **范围**：新增应用内可切换的 Mock 数据源（演示模式），供测试体验与 AI 走查

## 背景与动机

`libs/store` 接入已完成（f1ccd13）。现测试/走查数据靠 `tools/demo-data.sh` 外部灌入——直接覆盖真实
wardrobe.json 与 images/，体验完还得手动清理，且截图走查期间有任何写操作都会污染真数据。

需要**应用内的演示模式**：一键切到内存 Mock 仓库（人物/衣物/组合/笔记全链路丰富数据），一键退回，
真实数据零接触；衣物照片由打包进 APK 的 assets 驱动（thiings 素材，与 demo-data.sh 同源），离线可用、走查可复现。
eats 侧同迭代落地同一模式（eats it-006），两应用体验一致。

## 方案

- `data/mock/MockWardrobeData.kt`：确定性种子——2 个角色、12+ 件衣物（六品类覆盖，照片=assets/mock 内置图）、
  2-3 套组合、3 条笔记；时间戳相对生成（恒「最近」）。
- `data/mock/MockWardrobeRepository.kt`：实现 `WardrobeRepository` 全接口，内存 StateFlow，写操作只改内存不落盘；
  图片文件管理指向 `cacheDir/mock-images`（首次进入演示模式时从 assets 解包）。
- 开关：`DemoMode`（SharedPreferences 布尔位），AppContainer 构造时决定装配真实/Mock 仓库。
- 切换即重启进程；演示中页面顶部显示「演示数据」横幅，点横幅退出。
- 入口：衣橱页顶部工具区加「演示」按钮，**仅 BuildConfig.DEBUG 可见**。
- `tools/demo-data.sh` 保留，README 注明两者区别。

## 涉及用户故事

不新增用户故事（开发/测试基础设施）。US-03（衣橱页新增 debug 入口，release 不可见）。

## 验收标准

- 演示模式：三 Tab（搭配/记录/衣橱）全部呈现丰富假数据，衣物卡带真实感照片；组合/换装/笔记可正常操作
- 新增/删除只影响内存；退出后真实衣橱数据与图片原样；非 DEBUG 构建无入口
- `assembleDebug` / `test` 全绿；模拟器实测演示模式开关往返

## 影响范围

新增 `data/mock/*`、`assets/mock/*`；`di/AppContainer.kt`、`ui/wardrobe/WardrobeScreen.kt`（debug 入口+横幅）、
`MainActivity.kt`/`WardrobeApp.kt`（重启工具）、specs（02/04/06）、CHANGELOG。

## 验证记录

- 构建：`assembleDebug` ✅ / `testDebugUnitTest` ✅（新增 MockWardrobeRepositoryTest 5 例全绿）
- 模拟器实测（emulator-5554）：
  - 真实数据「衣橱 · Leo / 共 17 件」→ 衣橱页标题行演示按钮（DEBUG）→ 确认对话框 → 进入演示：重启后横幅「演示数据中」出现，衣橱显示 mock 角色「我 / 共 12 件」，衣物卡照片为 assets/mock 内置 thiings 素材（assets 解包路径实测渲染 ✅），搭配页卡组/记录页均跑 mock 数据
  - 点横幅退出：重启回真实「Leo / 共 17 件」，无横幅，真实数据零污染 ✅
  - 开发中修复一题：演示对话框误插在 `pendingDelete?.let { }` 块内导致永不渲染（点击无效的假象）→ 移到函数层；DemoBanner 与 eats 同批修状态栏遮挡 / commit 落盘。
- APK 增量：assets/mock 17 张 webp ≈ 2.2MB ✅
- 走查后修复：AI 走查（reports/2026-09-20-demo-mode-audit）抓出种子 15 处「名称/颜色与配图矛盾」
  （白T恤配绿衣、藏青球衣配橙红等，会原样印进导出长图）——逐张对齐实际素材修正并重验，
  导出预览标签已与图一致；「连衣裙·连衣裙」标签自我重复顺带消除（改名碎花连衣裙）。
