import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight } from './terrain';

function mulberry32(seed: number): () => number {
  let s = seed | 0;
  return () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* it-002 素材换血：植被/岩石 = Quaternius Stylized Nature MegaKit（CC0）；
   营地道具保留 Kenney（CC0）。下载与选型清单见 tools/fetch-assets.sh。 */
const Q = 'assets/quaternius/';
const K = 'assets/nature/';

const TREE_TYPES = [
  'CommonTree_1', 'CommonTree_2', 'CommonTree_3', 'CommonTree_4', 'CommonTree_5',
  'Pine_1', 'Pine_3', 'TwistedTree_2', 'TwistedTree_4', 'DeadTree_2',
];
const ROCK_TYPES = [
  'Rock_Medium_1', 'Rock_Medium_2', 'Rock_Medium_3',
  'Pebble_Square_1', 'Pebble_Square_2', 'Pebble_Square_3',
  'Pebble_Round_1', 'Pebble_Round_2',
];
const BUSH_TYPES = ['Bush_Common', 'Bush_Common_Flowers', 'Plant_1_Big', 'Plant_7_Big', 'Fern_1'];
const FLOWER_TYPES = ['Flower_3_Group', 'Flower_4_Group', 'Flower_3_Single', 'Flower_4_Single'];
const MUSH_TYPES = ['Mushroom_Common', 'Mushroom_Laetiporus'];
const CLOVER_TYPES = ['Clover_1', 'Clover_2'];

/* 出生点营地（道具坐标即草场/散布的避让区，改动需同步） */
export const CAMP_BLOCK: Array<[number, number]> = [
  [-4.5, -7.5], [6.2, -10.5], [-8.2, -4.5], [2.6, -13.5], [9.5, -5.5],
  [-13, -16], [-10.6, -16.9], [-8.2, -17.8], [4.2, -4.6],
  [-14, -10], [12.5, -17], [-6.5, -13], [7, -8], [-17, -4],
];

interface Spot { x: number; z: number; yaw: number; targetH: number; j: number; }

function makeSpots(
  rand: () => number, count: number, spread: number, minR: number,
  hMin: number, hMax: number, avoidCamp = true,
): Spot[] {
  const spots: Spot[] = [];
  let guard = count * 6;
  while (spots.length < count && guard-- > 0) {
    const x = (rand() - 0.5) * spread;
    const z = (rand() - 0.5) * spread;
    if (Math.hypot(x, z) < minR) continue;
    if (avoidCamp) {
      let ok = true;
      for (const [bx, bz] of CAMP_BLOCK) {
        if ((x - bx) ** 2 + (z - bz) ** 2 < 7) { ok = false; break; }
      }
      if (!ok) continue;
    }
    spots.push({
      x, z,
      yaw: rand() * Math.PI * 2,
      targetH: hMin + rand() * (hMax - hMin),
      j: 0.88 + rand() * 0.3,
    });
  }
  return spots;
}

interface HeroDef { n: string; x: number; z: number; h: number; yaw?: number; src: 'q' | 'k'; }

/* 出生点营地 + 框景：树/岩石用 Quaternius，道具用 Kenney */
const HERO: HeroDef[] = [
  { n: 'CommonTree_3', x: -14, z: -10, h: 6.2, yaw: 0.7, src: 'q' },
  { n: 'Pine_1', x: 12.5, z: -17, h: 7.0, yaw: -0.4, src: 'q' },
  { n: 'Rock_Medium_1', x: -6.5, z: -13, h: 1.5, yaw: 1.1, src: 'q' },
  { n: 'Rock_Medium_2', x: 7, z: -8, h: 1.1, src: 'q' },
  { n: 'Pebble_Square_1', x: -17, z: -4, h: 0.8, yaw: 2.1, src: 'q' },
  { n: 'campfire_logs', x: -4.5, z: -7.5, h: 0.75, yaw: 0.6, src: 'k' },
  { n: 'tent_smallOpen', x: 6.2, z: -10.5, h: 1.7, yaw: -2.4, src: 'k' },
  { n: 'log_stack', x: -8.2, z: -4.5, h: 0.85, yaw: 0.3, src: 'k' },
  { n: 'sign', x: 2.6, z: -13.5, h: 1.35, yaw: 0.15, src: 'k' },
  { n: 'stump_round', x: 9.5, z: -5.5, h: 0.7, src: 'k' },
  { n: 'fence_simple', x: -13, z: -16, h: 1.15, yaw: -0.35, src: 'k' },
  { n: 'fence_simple', x: -10.6, z: -16.9, h: 1.15, yaw: -0.35, src: 'k' },
  { n: 'fence_simpleLow', x: -8.2, z: -17.8, h: 1.0, yaw: -0.35, src: 'k' },
  { n: 'log', x: 4.2, z: -4.6, h: 0.45, yaw: 1.2, src: 'k' },
];

export function buildScatter(): THREE.Group {
  const group = new THREE.Group();
  group.name = 'scatter';
  const rand = mulberry32(20260924);
  const loader = new GLTFLoader();
  const dummy = new THREE.Object3D();

  const treeSpots = makeSpots(rand, 130, 184, 14, 4.0, 7.2);
  const rockSpots = makeSpots(rand, 40, 176, 9, 0.5, 1.9);
  const bushSpots = makeSpots(rand, 36, 150, 8, 0.7, 1.6);
  const flowerSpots = makeSpots(rand, 70, 88, 4, 0.3, 0.6);
  const mushSpots = makeSpots(rand, 18, 70, 5, 0.22, 0.45);
  const cloverSpots = makeSpots(rand, 24, 76, 4, 0.2, 0.4);

  const loadSpec = (
    names: string[], spots: Spot[], sink: number, jitter: number, base: string,
    opts: { cast: boolean } = { cast: true },
  ): void => {
    const share = Math.ceil(spots.length / names.length);
    for (const name of names) {
      const slice = spots.splice(0, share);
      if (slice.length === 0) return;
      loader.load(
        base + name + '.gltf',
        gltf => {
          gltf.scene.updateMatrixWorld(true);
          const box = new THREE.Box3().setFromObject(gltf.scene);
          const boxH = Math.max(box.max.y - box.min.y, 0.001);
          const meshes: THREE.Mesh[] = [];
          gltf.scene.traverse(o => { if (o instanceof THREE.Mesh) meshes.push(o); });
          for (const m of meshes) {
            m.updateMatrixWorld(true);
            const local = m.matrixWorld.clone();
            const inst = new THREE.InstancedMesh(m.geometry, m.material, slice.length);
            inst.castShadow = opts.cast;
            inst.receiveShadow = true;
            slice.forEach((sp, i) => {
              dummy.position.set(
                sp.x + Math.sin(sp.yaw * 7) * jitter,
                terrainHeight(sp.x, sp.z) - sink,
                sp.z + Math.cos(sp.yaw * 7) * jitter,
              );
              dummy.rotation.set(0, sp.yaw, 0);
              dummy.scale.setScalar((sp.targetH / boxH) * sp.j);
              dummy.updateMatrix();
              dummy.matrix.multiply(local);
              inst.setMatrixAt(i, dummy.matrix);
            });
            inst.instanceMatrix.needsUpdate = true;
            inst.computeBoundingSphere();
            group.add(inst);
          }
        },
        undefined,
        () => console.warn('[scatter] 加载失败:', name),
      );
    }
  };

  loadSpec(TREE_TYPES, treeSpots, 0.1, 1.5, Q);
  loadSpec(ROCK_TYPES, rockSpots, 0.06, 1.0, Q);
  loadSpec(BUSH_TYPES, bushSpots, 0.04, 1.0, Q);
  loadSpec(FLOWER_TYPES, flowerSpots, 0.0, 0.6, Q, { cast: false });
  loadSpec(MUSH_TYPES, mushSpots, 0.0, 0.5, Q, { cast: false });
  loadSpec(CLOVER_TYPES, cloverSpots, 0.0, 0.5, Q, { cast: false });

  /* 远景林带剪影：真树（Pine_2）替代旧锥体，雾中出层次 */
  const ringSpots: Spot[] = [];
  for (let i = 0; i < 55; i++) {
    const ang = rand() * Math.PI * 2;
    const r = 100 + rand() * 15;
    ringSpots.push({
      x: Math.cos(ang) * r, z: Math.sin(ang) * r,
      yaw: rand() * Math.PI * 2,
      targetH: 6 + rand() * 5, j: 0.9 + rand() * 0.25,
    });
  }
  loadSpec(['Pine_2'], ringSpots, 2.0, 0.6, Q, { cast: false });

  /* 营地 hero：按 src 分基址 */
  const heroesByBase: Record<'q' | 'k', HeroDef[]> = { q: [], k: [] };
  for (const h of HERO) heroesByBase[h.src].push(h);
  for (const src of ['q', 'k'] as const) {
    const base = src === 'q' ? Q : K;
    const ext = src === 'q' ? '.gltf' : '.glb';
    for (const h of heroesByBase[src]) {
      loader.load(
        base + h.n + ext,
        gltf => {
          gltf.scene.updateMatrixWorld(true);
          const b = new THREE.Box3().setFromObject(gltf.scene);
          const bh = Math.max(b.max.y - b.min.y, 0.001);
          gltf.scene.scale.setScalar(h.h / bh);
          if (h.yaw) gltf.scene.rotation.y = h.yaw;
          gltf.scene.updateMatrixWorld(true);
          const b2 = new THREE.Box3().setFromObject(gltf.scene);
          const ground = terrainHeight(h.x, h.z);
          gltf.scene.position.set(h.x, ground - b2.min.y, h.z);
          gltf.scene.traverse(o => {
            if (o instanceof THREE.Mesh) { o.castShadow = true; o.receiveShadow = true; }
          });
          group.add(gltf.scene);
        },
        undefined,
        () => console.warn('[scatter] 道具加载失败:', h.n),
      );
    }
  }

  return group;
}
