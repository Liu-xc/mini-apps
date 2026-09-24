# it-003 · 资产库沉淀 + 屏内场景编排器

- **状态**：实施中（Leo 口头授权：「继续大搞特搞！尽量多沉淀资产，后面可以比较快速编排我的场景」，
  2026-09-24；范围即其原话的工程化拆解，提案与实施同轮）
- **里程碑**：M2 前置（三站场景编排的工具与数据层）
- **涉及用户故事**：US-4（资产质量延续）；新增 US-5（见 01-user-stories）

## 背景与动机

it-002 打通了画面管线，Leo 认可（「效果还不错」）。下一阶段目标是让他
**快速编排自己的场景**（赤峰三站：石林/达里湖/乌兰布统），这需要两件事：
① 足够厚、有目录可查的**资产库**（而不是每次临下临用）；
② 数据驱动的**场景定义 + 屏内编辑器**（点地放置/拖拽/导出，而不是改代码摆放）。

## 验收标准

- **AC-1 资产沉淀**：Quaternius MegaKit **全量 68 款模型 + 21 张贴图**入库
  （本地 zip 直接补拷，贴图压 1024）；PolyHaven 草原模型精选入库
  （逐文件探真实体积 bin+贴图，单模型超预算弃用，总量 ≤20MB）；
  新增 **2 张天空 HDRI**（经 API 实证的 dawn/sunset 类，供三站不同时段光照）。
- **AC-2 资产目录**：`public/assets/catalog.json` 全量登记
  （id/名称/类别/来源/许可/文件路径/标签），`tools/gen_catalog.py` 可再生成；
  fetch-assets.sh 同步扩展到全量清单。
- **AC-3 场景数据化**：`src/scenes.ts` 定义 `SceneDef/Placement` schema（03-data-model.md 同步新建）；
  现营地 hero 摆放迁移为 `camp` 场景数据；`src/placer.ts` 统一按数据实例化，
  CAMP_BLOCK 避让区从同一份数据派生（单一事实源）。画面回归与 it-002 一致。
- **AC-4 屏内编辑器（`?edit=1`）**：
  资产面板（按 catalog 分类浏览）→ 点击地形放置（射线贴地）→ 拖拽移动 →
  点选选中 → Delete 删除 → R/Shift+R 旋转 → +/− 缩放；
  **localStorage 即时持久化（刷新保留编排结果）**；导出按钮 = JSON 复制到剪贴板 + 下载 .json
  （可粘回 src/scenes.ts 入库）。编辑模式隐藏玩家、保留镜头环视缩放。
- **AC-5 回归**：非编辑模式画面与 it-002 一致（同营地构图）；US-1 行为断言全绿、
  errs=[]、构建零错；编辑器冒烟断言（放置/移动/删除/导出 JSON 结构）经 `window.__editor` 钩子。
- **AC-6 文档**：03-data-model 新建、ADR-004、US-5、README（编辑器用法）、CHANGELOG、本文件验证记录。

## 影响范围

- 新增：`src/scenes.ts`、`src/placer.ts`、`src/editor.ts`、`tools/gen_catalog.py`、
  `public/assets/catalog.json`、`specs/03-data-model.md`。
- 修改：`scatter.ts`（hero 放置移交 placer，仅保留氛围散布与 CAMP_BLOCK 派生）、
  `main.ts`（编辑模式接线 + `__editor` 钩子）、`index.html`（编辑器 UI 容器）、
  `06-decisions.md`（ADR-004）、`01-user-stories.md`（US-5）、README、CHANGELOG、fetch 脚本。
- 素材体积：MegaKit 全量 +~7MB、PH 精选 ≤20MB、HDRI×2 ~12MB——提交时列清单核账。

## 验证记录

**2026-09-24 实施完成，AC-1 ~ AC-6 全部通过。**

### AC 逐条

| AC | 结果 |
|---|---|
| AC-1 资产沉淀 | ✅ Quaternius MegaKit **全量 68 款模型 + 21 贴图**（本地 zip 直接补拷 +32 款，贴图压 1024，目录 156 文件 25MB）；PolyHaven 草原模型**精选 7 款 17.2MB**（grass_medium_01/grass_bermuda/boulder/rock_07/fern/dandelion/moss——按 API 权威 `include` 清单下载，18MB 预算内砍掉 shrub_01、tree_stump_01 记录在案）；**三时段天空 HDRI**：day(48d)/dawn(qwantani)/sunset(belfast) 各 2K puresky |
| AC-2 资产目录 | ✅ `catalog.json` **96 条**（foliage16/flower11/tree20/mushroom2/rock26/prop14/character1/texture3/hdri3），`tools/gen_catalog.py` 可再生成；fetch-assets.sh 扩展为全量拷贝 + PH API 预算下载 + 末尾自动跑 gen_catalog（bash -n 语法过） |
| AC-3 场景数据化 | ✅ `scenes.ts`（SceneDef/Placement schema 同步 03-data-model.md 新建）+ 营地 14 件迁移；`placer.ts` 归一化到 1 米再按目标高度缩放；`deriveBlock` 从 placements 派生避让区（scatter/grass 改签名吃 avoid，单一事实源）；**普通模式 probe placementChildren=14，构图与 it-002 一致（截图核对）** |
| AC-4 屏内编辑器 | ✅ `?edit=1` 实拍确认：面板（分类 树/岩石/草木/花/蘑菇/道具 + 资产列表 + 导出/重置 + 操作提示）、底部编辑提示条、玩家隐藏。冒烟断言：`place(Pine_3)` 14→15 ✓、`exportJson()` 结构合法且 length 匹配 ✓、**localStorage 持久化 15（刷新保留）** ✓、拖拽暂停环视（orbitEnabled 开关接入 controls） ✓ |
| AC-5 回归 | ✅ 普通模式：行为断言全绿（W 前进/跳跃 1.27→落地/errs=[]）、placement=14、构图一致；编辑器冒烟 errs=[]；构建零错误；**「重置默认」实测**：hadOverride=true→清除→回落代码场景 |
| AC-6 文档 | ✅ 03-data-model 新建、ADR-004、US-5、本记录、README/CHANGELOG 同步 |

### 过程坑（复用）

- PolyHaven 模型 gltf 的贴图**不在 gltf 目录旁**：真实路径按 `api.polyhaven.com/files/<id>`
  的 `include` 清单（贴图在 `jpg/` 树、bin 在 `8k/` 路径），照单下载即可；体积预算用
  API 返回的 `size` 前置计算，不必 HEAD 探测。
- 模型 gltf 自带的相对 `textures/` 引用对 CDN **必然 404**（服务器布局与打包布局不同），
  本地按 gltf 期望的相对结构落盘即可让 GLTFLoader 正常解析。
- dl.polyhaven 短时并发会 SSL EOF，curl --retry 3 稳；python urllib 更抖，别用。
- IAB 截图对「同 tab 新导航」会持续给旧表面：**reload 一次再拍**即恢复（元素级截图不支持）。
- 记忆串台：游戏 index.html 从来没有 station tabs/progress（那是平面气氛稿的 DOM）——
  改 HTML 前先 grep 锚点，别凭印象写 old_string。

### 遗留（进 M1/M2）

- 编辑器增强：幽灵预览（放置前半透明跟手）、撤销栈、散布种子固化、多场景（三站）切换。
- PolyHaven shrub_01/tree_stump_01 两款因预算未入库；KayKit Forest 包（无 glTF 格式）放弃。
- 三时段 HDRI 已入库但场景光照仍固定白昼——M2 做三站时按站切换 dawn/day/sunset。
- 编辑模式下 fps 采样受 IAB 干扰（28~60 浮动），真机结论仍待移动端实测。
