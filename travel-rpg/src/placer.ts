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

/* 路牌文字（it-011 AC-1）：找出 bbox 体积最大的网格（板面），在板面正/背
   各叠一块 Canvas 文字面片（木纹底+中文衬线站名），绕过原材质/UV 不确定性。 */
function applySignLabel(sign: THREE.Object3D, text: string): void {
  let board: THREE.Mesh | null = null;
  let boardVol = 0;
  sign.traverse(o => {
    if (o instanceof THREE.Mesh) {
      const b = new THREE.Box3().setFromObject(o);
      const sz = b.getSize(new THREE.Vector3());
      const vol = sz.x * sz.y * sz.z;
      if (vol > boardVol) { boardVol = vol; board = o; }
    }
  });
  if (!board) return;
  const mesh = board as THREE.Mesh;
  const bb = mesh.geometry.boundingBox;
  if (!bb) return;
  const size = bb.getSize(new THREE.Vector3());
  const center = bb.getCenter(new THREE.Vector3());

  const cv = document.createElement('canvas');
  cv.width = 256; cv.height = 128;
  const c = cv.getContext('2d')!;
  c.fillStyle = '#b08558';
  c.fillRect(0, 0, 256, 128);
  c.fillStyle = 'rgba(70, 48, 26, 0.22)';           // 木纹条
  for (let y = 16; y < 128; y += 26) c.fillRect(0, y, 256, 3);
  c.strokeStyle = '#5d3f22'; c.lineWidth = 7;
  c.strokeRect(4, 4, 248, 120);
  c.fillStyle = '#33230f';
  c.font = 'bold 50px "Songti SC", "STSong", "Noto Serif SC", serif';
  c.textAlign = 'center'; c.textBaseline = 'middle';
  c.fillText(text, 128, 62);
  const tex = new THREE.CanvasTexture(cv);
  tex.colorSpace = THREE.SRGBColorSpace;

  const mat = new THREE.MeshStandardMaterial({
    map: tex, roughness: 0.85, metalness: 0,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2,
  });
  mat.userData.outlineParameters = { visible: false };
  for (const flip of [1, -1]) {                      // 正/背双面可读
    const plane = new THREE.Mesh(
      new THREE.PlaneGeometry(size.x * 0.94, size.y * 0.94),
      flip === 1 ? mat : mat.clone(),
    );
    plane.position.copy(center);
    plane.translateZ((size.z / 2 + 0.012) * flip);
    if (flip === -1) plane.rotation.y = Math.PI;
    mesh.add(plane);
  }
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
      if (pl.label) applySignLabel(obj, pl.label);
      obj.userData.placement = pl;
      group.add(obj);
      placed.push({ asset: pl.asset, obj, yaw: pl.yaw, scale: pl.scale });
    }
    return placed;
  })();
  return { group, ready };
}
