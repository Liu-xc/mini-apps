# 03-data-model · 数据模型

> it-003 新建。当前仅覆盖**场景编排**；旅程内容/道具/里程等 M3 数据待 US-3 落地时增补。

## 场景定义（SceneDef）

代码位置：`src/scenes.ts`（TS 模块，编译期类型检查；编辑器导出的 JSON 与之同构）。

```
SceneDef {
  id: string            // 场景标识，如 "camp" / "shilin-viewpoint"
  name: string          // 展示名（未来地名揭示可复用）
  placements: Placement[]
}

Placement {
  asset: string         // catalog.json 的资产 id（如 "quaternius:CommonTree_3"）
  x: number             // 世界坐标（米），y 由地形高度推得，落库不存 y
  z: number
  yaw: number           // 弧度
  scale: number         // 相对 catalog 记录的标准高度缩放
}
```

**约束与派生**

- `y` 不入库：放置时 `y = terrainHeight(x, z)`（含世界边缘沉降），保证地形变更后可重算。
- 营地避让区 `CAMP_BLOCK` **从 placements 派生**（半径阈值 2.6m），不再手写坐标——
  场景数据是唯一事实源。
- 资产 id 前缀约定 `<source>:<name>`：`quaternius:` / `kenney:` / `kaykit:` / `polyhaven:`。
- 环境类散布（随机树/岩石/花草/远景林带）**不属于 Placement**：它们由种子随机生成
  （`scatter.ts`），编排器只管策展式摆放。

## 资产目录（catalog.json）

代码位置：`public/assets/catalog.json`，由 `tools/gen_catalog.py` 扫描素材目录 +
内置元数据表生成（fetch-assets.sh 重下素材后需重跑）。

```
CatalogEntry {
  id: string            // 同 Placement.asset
  name: string          // 展示名
  category: string      // tree | rock | grass | flower | mushroom | prop | character | texture | hdri
  source: string        // Quaternius / Kenney / KayKit / Poly Haven / three.js
  sourcePage: string    // 出处页面（口碑/许可溯源）
  license: string       // CC0 / MIT …
  file: string          // 相对 public/ 的路径（gltf/glb/hdr/jpg）
  stdHeight?: number    // 标准高度（米），首次入库时测量，编辑器按此缩放
  tags: string[]
}
```

## 编辑器本地态（localStorage）

- key：`travel-rpg:scene:<sceneId>` → `Placement[]` JSON（覆盖同 id 的代码场景，
  即「屏幕里编排的即所见」）；导出 JSON 供粘回 `src/scenes.ts` 永久入库。
- 属于运行时状态，不进 git。
