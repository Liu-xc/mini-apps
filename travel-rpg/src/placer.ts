import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight } from './terrain';
import type { SceneDef } from './scenes';

/* 场景摆放（it-003）：按 SceneDef 数据实例化。
   任意来源模型统一归一化到 1 米高，再按 placement.scale（目标米数）缩放。 */

interface CatalogEntry { id: string; file: string; [k: string]: unknown; }

let catalogPromise: Promise<Map<string, string>> | null = null;
function loadCatalog(): Promise<Map<string, string>> {
  if (!catalogPromise) {
    catalogPromise = fetch(`${import.meta.env.BASE_URL}assets/catalog.json`)
      .then(r => r.json())
      .then((j: { entries: CatalogEntry[] }) =>
        new Map(j.entries.map(e => [e.id, e.file])));
  }
  return catalogPromise;
}

const loader = new GLTFLoader();
const templateCache = new Map<string, Promise<THREE.Group | null>>();

function loadTemplate(file: string): Promise<THREE.Group | null> {
  let p = templateCache.get(file);
  if (!p) {
    p = new Promise(res =>
      loader.load(`${import.meta.env.BASE_URL}${file}`, g => res(g.scene),
        undefined, () => { console.warn('[placer] 加载失败:', file); res(null); }));
    templateCache.set(file, p);
  }
  return p;
}

export interface PlacedObject {
  asset: string;
  obj: THREE.Object3D;
  yaw: number;
  scale: number;   // 目标高度（米）
}

export interface PlacerResult {
  group: THREE.Group;
  ready: Promise<PlacedObject[]>;
}

export function buildScenePlacements(scene: SceneDef): PlacerResult {
  const group = new THREE.Group();
  group.name = `placements:${scene.id}`;
  const ready = (async () => {
    const catalog = await loadCatalog();
    const placed: PlacedObject[] = [];
    for (const pl of scene.placements) {
      const file = catalog.get(pl.asset);
      if (!file) { console.warn('[placer] 目录无此资产:', pl.asset); continue; }
      const tpl = await loadTemplate(file);
      if (!tpl) continue;
      const obj = tpl.clone(true);
      obj.updateMatrixWorld(true);
      const box0 = new THREE.Box3().setFromObject(obj);
      const h0 = Math.max(box0.max.y - box0.min.y, 0.001);
      obj.scale.setScalar(pl.scale / h0);          // 归一化后按目标高度缩放
      obj.rotation.y = pl.yaw;
      obj.updateMatrixWorld(true);
      const box1 = new THREE.Box3().setFromObject(obj);
      obj.position.set(pl.x, terrainHeight(pl.x, pl.z) - box1.min.y, pl.z);
      obj.traverse(o => {
        if (o instanceof THREE.Mesh) { o.castShadow = true; o.receiveShadow = true; }
      });
      obj.userData.placement = pl;
      group.add(obj);
      placed.push({ asset: pl.asset, obj, yaw: pl.yaw, scale: pl.scale });
    }
    return placed;
  })();
  return { group, ready };
}
