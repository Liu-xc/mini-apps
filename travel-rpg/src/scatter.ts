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

/* Kenney Nature Kit 子集（CC0，tools/fetch-assets.sh 可重下） */
const TREE_TYPES = [
  'tree_oak_fall', 'tree_default_fall', 'tree_simple_fall', 'tree_fat_fall',
  'tree_tall_fall', 'tree_blocks_fall', 'tree_pineRoundC', 'tree_pineRoundE',
];
const ROCK_TYPES = ['rock_largeA', 'rock_largeC', 'rock_smallA', 'rock_smallFlatA', 'rock_tallA'];
const BUSH_TYPES = ['plant_bush', 'plant_bushLarge'];
const GRASS_TYPES = ['grass', 'grass_large'];
const FLOWER_TYPES = ['flower_yellowA', 'flower_redA', 'flower_purpleA'];

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
  const grassSpots = makeSpots(rand, 260, 70, 3, 0.35, 0.7);
  const flowerSpots = makeSpots(rand, 120, 70, 4, 0.35, 0.6);

  const loadSpec = (names: string[], spots: Spot[], sink: number, jitter: number): void => {
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
            const inst = new THREE.InstancedMesh(m.geometry, m.material, slice.length);
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
  loadSpec(GRASS_TYPES, grassSpots, 0.0, 0.8);
  loadSpec(FLOWER_TYPES, flowerSpots, 0.0, 0.8);

  return group;
}
