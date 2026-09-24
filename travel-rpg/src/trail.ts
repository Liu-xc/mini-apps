import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight, LAKE } from './terrain';

/* 营地→湖湾石径（it-005 AC-4）：二次贝塞尔沿线摆 RockPath 石板，
   10 款变体全激活（入库未上场资产），弧长等距 + 横向抖动，落到滩涂前为止。
   塞尔达式「路引」：给玩家一个可读的前进方向。 */

const VARIANTS = [
  'RockPath_Round_Wide', 'RockPath_Round_Thin',
  'RockPath_Round_Small_1', 'RockPath_Round_Small_2', 'RockPath_Round_Small_3',
  'RockPath_Square_Wide', 'RockPath_Square_Thin',
  'RockPath_Square_Small_1', 'RockPath_Square_Small_2', 'RockPath_Square_Small_3',
];

interface Stone { x: number; z: number; yaw: number; h: number; }

function mulberry32(seed: number): () => number {
  let s = seed | 0;
  return () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function buildTrail(): { group: THREE.Group; done: { value: boolean } } {
  const group = new THREE.Group();
  group.name = 'trail';
  const done = { value: false };
  const rand = mulberry32(778899);

  const P0 = new THREE.Vector2(13, -8.5);   // 营地东缘
  const P1 = new THREE.Vector2(26, -10);
  const P2 = new THREE.Vector2(31.5, -21.5);

  /* 弧长等距采石位，遇滩涂（水位上方 0.8m 内）停步 */
  const stones: Stone[] = [];
  const prev = P0.clone();
  let sinceLast = 999;
  for (let i = 1; i <= 80 && stones.length < 34; i++) {
    const t = i / 80;
    const q = P0.clone().multiplyScalar((1 - t) * (1 - t))
      .add(P1.clone().multiplyScalar(2 * (1 - t) * t))
      .add(P2.clone().multiplyScalar(t * t));
    sinceLast += q.distanceTo(prev);
    prev.copy(q);
    if (terrainHeight(q.x, q.y) < LAKE.level + 0.8) break;
    if (sinceLast >= 1.35 + rand() * 0.4) {
      sinceLast = 0;
      const tangent = P1.clone().multiplyScalar(2 * (1 - t))
        .add(P2.clone().multiplyScalar(2 * t)).sub(P0.clone().multiplyScalar(2 * (1 - t)));
      const perp = new THREE.Vector2(-tangent.y, tangent.x).normalize();
      const off = (rand() - 0.5) * 1.6;
      stones.push({
        x: q.x + perp.x * off,
        z: q.y + perp.y * off,
        yaw: Math.atan2(tangent.y, tangent.x) + (rand() - 0.5) * 0.9,
        h: 0.9 + rand() * 0.5,   // 目标「宽」度（石板按水平最大边归一化）
      });
    }
  }

  /* 按变体分组实例化：石板扁平，按水平最大边归一化到目标宽度（高度归一化会爆宽） */
  const buckets = new Map<string, Stone[]>();
  for (const st of stones) {
    const v = VARIANTS[Math.floor(rand() * VARIANTS.length)];
    if (!buckets.has(v)) buckets.set(v, []);
    buckets.get(v)!.push(st);
  }
  const base = import.meta.env.BASE_URL;
  let pending = buckets.size;
  if (pending === 0) { done.value = true; return { group, done }; }
  for (const [name, list] of buckets) {
    new GLTFLoader().load(`${base}assets/quaternius/${name}.gltf`, gltf => {
      gltf.scene.updateMatrixWorld(true);
      gltf.scene.traverse(o => {
        if (o instanceof THREE.Mesh) {
          const box = new THREE.Box3().setFromObject(gltf.scene);
          const size = box.getSize(new THREE.Vector3());
          const w0 = Math.max(size.x, size.z, 0.001);
          const dummy = new THREE.Object3D();
          const inst = new THREE.InstancedMesh(o.geometry, o.material, list.length);
          list.forEach((st, i) => {
            const s = st.h / w0;
            dummy.position.set(
              st.x, terrainHeight(st.x, st.z) - box.min.y * s - 0.03, st.z);
            dummy.rotation.set(0, st.yaw, 0);
            dummy.scale.setScalar(s);
            dummy.updateMatrix();
            inst.setMatrixAt(i, dummy.matrix);
          });
          inst.instanceMatrix.needsUpdate = true;
          inst.castShadow = false;
          inst.receiveShadow = true;
          inst.computeBoundingSphere();
          group.add(inst);
        }
      });
      if (--pending === 0) done.value = true;
    }, undefined, () => {
      console.warn('[trail] 加载失败:', name);
      if (--pending === 0) done.value = true;
    });
  }
  return { group, done };
}
