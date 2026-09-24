import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight } from './terrain';
import { makeToon } from './style';

function mulberry32(seed: number): () => number {
  let s = seed | 0;
  return () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* Kenney Nature Kit 子集（CC0，tools/fetch-assets.sh 可重下）——全部现成开源素材 */
const TREE_TYPES = [
  'tree_oak_fall', 'tree_default_fall', 'tree_simple_fall', 'tree_fat_fall',
  'tree_tall_fall', 'tree_blocks_fall', 'tree_pineRoundC', 'tree_pineRoundE',
  'tree_blocks', 'tree_default', 'tree_oak', 'tree_pineTallA',
  'tree_thin_fall', 'tree_small_fall',
];
const ROCK_TYPES = [
  'rock_largeA', 'rock_largeC', 'rock_smallA', 'rock_smallFlatA', 'rock_tallA',
  'rock_largeB', 'rock_largeD', 'rock_smallC', 'rock_tallB',
];
const BUSH_TYPES = ['plant_bush', 'plant_bushLarge', 'plant_bushDetailed', 'plant_bushSmall'];
const GRASS_TYPES = [
  'grass', 'grass_large', 'grass_leafs', 'grass_leafsLarge',
  'plant_flatShort', 'plant_flatTall',
];
const FLOWER_TYPES = [
  'flower_yellowA', 'flower_redA', 'flower_purpleA',
  'flower_yellowB', 'flower_redB', 'flower_purpleB',
  'flower_yellowC', 'flower_yellowC',
];

interface Spot { x: number; z: number; yaw: number; targetH: number; j: number; }

/* 全部点位在同步阶段一次算完（含目标高度/抖动）——异步加载只读不掷随机，
   布局每次刷新完全一致 */
function makeSpots(
  rand: () => number, count: number, spread: number, minR: number,
  hMin: number, hMax: number,
): Spot[] {
  const spots: Spot[] = [];
  for (let i = 0; i < count; i++) {
    let x = 0, z = 0, tries = 0;
    do {
      x = (rand() - 0.5) * spread;
      z = (rand() - 0.5) * spread;
      tries++;
    } while (Math.hypot(x, z) < minR && tries < 24);
    spots.push({
      x, z,
      yaw: rand() * Math.PI * 2,
      targetH: hMin + rand() * (hMax - hMin),
      j: 0.88 + rand() * 0.3,
    });
  }
  return spots;
}

/* 散布物：加载 Kenney GLB → 按类型实例化（共享矩阵，低 draw call），渐进入场 */
export function buildScatter(): THREE.Group {
  const group = new THREE.Group();
  group.name = 'scatter';
  const rand = mulberry32(20260924);
  const loader = new GLTFLoader();
  const base = `${import.meta.env.BASE_URL}assets/nature/`;
  const dummy = new THREE.Object3D();

  const treeSpots = makeSpots(rand, 130, 184, 14, 3.6, 6.8);
  const rockSpots = makeSpots(rand, 42, 176, 10, 0.5, 1.9);
  const bushSpots = makeSpots(rand, 34, 150, 10, 0.7, 1.5);
  const grassSpots = makeSpots(rand, 340, 62, 2.5, 0.35, 0.75);
  const flowerSpots = makeSpots(rand, 170, 62, 3.5, 0.35, 0.62);

  const loadSpec = (
    names: string[], spots: Spot[], sink: number, jitter: number,
    opts: { cast: boolean; lift?: number; tint?: string } = { cast: true },
  ): void => {
    const share = Math.ceil(spots.length / names.length);
    for (const name of names) {
      const slice = spots.splice(0, share);
      if (slice.length === 0) return;
      loader.load(
        base + name + '.glb',
        gltf => {
          gltf.scene.updateMatrixWorld(true);
          const box = new THREE.Box3().setFromObject(gltf.scene);
          const boxH = Math.max(box.max.y - box.min.y, 0.001);
          const meshes: THREE.Mesh[] = [];
          gltf.scene.traverse(o => { if (o instanceof THREE.Mesh) meshes.push(o); });
          for (const m of meshes) {
            m.updateMatrixWorld(true);
            const local = m.matrixWorld.clone();
            /* 草：贴图染绿（原色偏灰白）+ 微 emissive；花：只微提亮保原色 */
            const mats = Array.isArray(m.material) ? m.material : [m.material];
            for (const mat of mats) {
              if (opts.tint && 'color' in mat) {
                (mat as THREE.MeshStandardMaterial).color = new THREE.Color(opts.tint);
              }
              if (opts.lift && 'emissive' in mat) {
                (mat as THREE.MeshStandardMaterial).emissive = new THREE.Color('#5a7040');
                (mat as THREE.MeshStandardMaterial).emissiveIntensity = opts.lift;
              }
              if (opts.lift) mat.userData.outlineParameters = { visible: false };
            }
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

  loadSpec(TREE_TYPES, treeSpots, 0.12, 1.5);
  loadSpec(ROCK_TYPES, rockSpots, 0.08, 1.2);
  loadSpec(BUSH_TYPES, bushSpots, 0.05, 1.0);
  loadSpec(GRASS_TYPES, grassSpots, 0.0, 0.8, { cast: false, lift: 0.15, tint: '#6f9448' });
  loadSpec(FLOWER_TYPES, flowerSpots, 0.0, 0.8, { cast: false, lift: 0.18 });

  /* 远景林带剪影：雾中出层次（简单锥体环，r 100~115） */
  const tlGeo = new THREE.ConeGeometry(1, 3, 6);
  tlGeo.translate(0, 1.5, 0);
  const tlColors = ['#6a5340', '#4f5a44'];
  for (let v = 0; v < 2; v++) {
    const tl = new THREE.InstancedMesh(tlGeo, makeToon(tlColors[v]), 55);
    tl.castShadow = false;
    tl.receiveShadow = false;
    for (let i = 0; i < 55; i++) {
      const ang = rand() * Math.PI * 2;
      const r = 100 + rand() * 15;
      const x = Math.cos(ang) * r, z = Math.sin(ang) * r;
      dummy.position.set(x, terrainHeight(x, z) - 2.1, z);
      dummy.rotation.set(0, rand() * Math.PI * 2, 0);
      const h = 3.5 + rand() * 3.2;
      dummy.scale.set(h * 0.45, h, h * 0.45);
      dummy.updateMatrix();
      tl.setMatrixAt(i, dummy.matrix);
    }
    tl.instanceMatrix.needsUpdate = true;
    tl.computeBoundingSphere();
    group.add(tl);
  }

  /* 出生点营地 + 框景：全部 Kenney 现成道具（评审轮 P1：前景空） */
  interface Hero { n: string; x: number; z: number; h: number; yaw?: number; }
  const HERO: Hero[] = [
    { n: 'tree_oak_fall', x: -14, z: -10, h: 5.6 },
    { n: 'tree_pineRoundC', x: 12.5, z: -17, h: 6.4 },
    { n: 'rock_largeA', x: -6.5, z: -13, h: 1.5 },
    { n: 'rock_largeC', x: 7, z: -8, h: 1.1 },
    { n: 'rock_smallA', x: -17, z: -4, h: 0.8 },
    { n: 'campfire_logs', x: -4.5, z: -7.5, h: 0.75, yaw: 0.6 },
    { n: 'tent_smallOpen', x: 6.2, z: -10.5, h: 1.7, yaw: -2.4 },
    { n: 'log_stack', x: -8.2, z: -4.5, h: 0.85, yaw: 0.3 },
    { n: 'sign', x: 2.6, z: -13.5, h: 1.35, yaw: 0.15 },
    { n: 'stump_round', x: 9.5, z: -5.5, h: 0.7 },
    { n: 'fence_simple', x: -13, z: -16, h: 1.15, yaw: -0.35 },
    { n: 'fence_simple', x: -10.6, z: -16.9, h: 1.15, yaw: -0.35 },
    { n: 'fence_simpleLow', x: -8.2, z: -17.8, h: 1.0, yaw: -0.35 },
    { n: 'log', x: 4.2, z: -4.6, h: 0.45, yaw: 1.2 },
  ];
  for (const heroDef of HERO) {
    loader.load(
      base + heroDef.n + '.glb',
      gltf => {
        gltf.scene.updateMatrixWorld(true);
        const b = new THREE.Box3().setFromObject(gltf.scene);
        const bh = Math.max(b.max.y - b.min.y, 0.001);
        gltf.scene.scale.setScalar(heroDef.h / bh);
        if (heroDef.yaw) gltf.scene.rotation.y = heroDef.yaw;
        gltf.scene.updateMatrixWorld(true);
        const b2 = new THREE.Box3().setFromObject(gltf.scene);
        const ground = terrainHeight(heroDef.x, heroDef.z);
        gltf.scene.position.set(heroDef.x, ground - b2.min.y, heroDef.z);
        gltf.scene.traverse(o => {
          if (o instanceof THREE.Mesh) { o.castShadow = true; o.receiveShadow = true; }
        });
        group.add(gltf.scene);
      },
      undefined,
      () => console.warn('[scatter] 道具加载失败:', heroDef.n),
    );
  }

  return group;
}
