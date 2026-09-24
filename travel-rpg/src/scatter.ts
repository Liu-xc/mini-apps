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

/* 共享风时钟（与 grass.ts 的 uTime 各自独立但同频，视觉无碍） */
const swayTime = { value: 0 };
export function tickScatterWind(t: number): void { swayTime.value = t; }

/* 氛围散布（it-002/003）：植被/岩石 = Quaternius MegaKit；
   策展式摆放走 scenes.ts + placer.ts，此处只做种子随机的背景填充。 */
const Q = 'assets/quaternius/';

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

interface Spot { x: number; z: number; yaw: number; targetH: number; j: number; }

function makeSpots(
  rand: () => number, count: number, spread: number, minR: number,
  hMin: number, hMax: number, avoid: Array<[number, number]>, avoidR2 = 7,
): Spot[] {
  const spots: Spot[] = [];
  let guard = count * 6;
  while (spots.length < count && guard-- > 0) {
    const x = (rand() - 0.5) * spread;
    const z = (rand() - 0.5) * spread;
    if (Math.hypot(x, z) < minR) continue;
    let ok = true;
    for (const [bx, bz] of avoid) {
      if ((x - bx) ** 2 + (z - bz) ** 2 < avoidR2) { ok = false; break; }
    }
    if (!ok) continue;
    spots.push({
      x, z,
      yaw: rand() * Math.PI * 2,
      targetH: hMin + rand() * (hMax - hMin),
      j: 0.88 + rand() * 0.3,
    });
  }
  return spots;
}

/* avoid = 场景 placements 派生的避让区（scenes.ts deriveBlock，营地场景数据为唯一事实源） */
export function buildScatter(avoid: Array<[number, number]>): THREE.Group {
  const group = new THREE.Group();
  group.name = 'scatter';
  const rand = mulberry32(20260924);
  const loader = new GLTFLoader();
  const dummy = new THREE.Object3D();

  const treeSpots = makeSpots(rand, 130, 184, 14, 4.0, 7.2, avoid);
  const rockSpots = makeSpots(rand, 40, 176, 9, 0.5, 1.9, avoid);
  const bushSpots = makeSpots(rand, 36, 150, 8, 0.7, 1.6, avoid);
  const flowerSpots = makeSpots(rand, 70, 88, 4, 0.3, 0.6, avoid);
  const mushSpots = makeSpots(rand, 18, 70, 5, 0.22, 0.45, avoid);
  const cloverSpots = makeSpots(rand, 24, 76, 4, 0.2, 0.4, avoid);

  const loadSpec = (
    names: string[], spots: Spot[], sink: number, jitter: number,
    opts: { cast: boolean; sway?: number; leafGlow?: boolean } = { cast: true },
  ): void => {
    const share = Math.ceil(spots.length / names.length);
    for (const name of names) {
      const slice = spots.splice(0, share);
      if (slice.length === 0) return;
      loader.load(
        Q + name + '.gltf',
        gltf => {
          gltf.scene.updateMatrixWorld(true);
          const box = new THREE.Box3().setFromObject(gltf.scene);
          const boxH = Math.max(box.max.y - box.min.y, 0.001);
          const meshes: THREE.Mesh[] = [];
          gltf.scene.traverse(o => { if (o instanceof THREE.Mesh) meshes.push(o); });
          for (const m of meshes) {
            m.updateMatrixWorld(true);
            const local = m.matrixWorld.clone();
            const mat = m.material as THREE.Material;
            /* 树叶透光：叶片材质加微量暖绿 emissive，背光侧不发死黑（假次表面） */
            if (opts.leafGlow && /leaf|leave|_c|foliage/i.test(mat.name) &&
                'emissive' in mat) {
              const sm = mat as THREE.MeshStandardMaterial;
              sm.emissive = new THREE.Color('#3d4422');
              sm.emissiveIntensity = 0.4;
            }
            /* 植被随风：与草场同族的轻量摆动（花 > 草木 > 灌木） */
            if (opts.sway && 'onBeforeCompile' in mat) {
              const sm = mat as THREE.MeshStandardMaterial;
              const amp = opts.sway;
              sm.onBeforeCompile = shader => {
                shader.uniforms.uTime = swayTime;
                shader.uniforms.uAmp = { value: amp };
                shader.uniforms.uMaxH = { value: boxH };
                shader.vertexShader = shader.vertexShader
                  .replace('#include <common>',
                    '#include <common>\nuniform float uTime; uniform float uAmp; uniform float uMaxH;')
                  .replace('#include <begin_vertex>', [
                    '#include <begin_vertex>',
                    '#ifdef USE_INSTANCING',
                    '  vec3 sOrigin = vec3(instanceMatrix[3][0], instanceMatrix[3][1], instanceMatrix[3][2]);',
                    '#else',
                    '  vec3 sOrigin = vec3(0.0);',
                    '#endif',
                    '  float sPhase = sOrigin.x * 0.41 + sOrigin.z * 0.33;',
                    '  float sSway = sin(uTime * 1.7 + sPhase) + 0.5 * sin(uTime * 3.3 + sPhase * 1.9);',
                    '  float sWave = fract((sOrigin.x + sOrigin.z * 0.6) * 0.006 - uTime * 0.05);',
                    '  sSway *= 1.0 + 1.2 * (smoothstep(0.88, 1.0, sWave) + smoothstep(0.12, 0.0, sWave));',
                    '  float sH = clamp(transformed.y / max(uMaxH, 0.001), 0.0, 1.0);',
                    '  transformed.x += sSway * uAmp * sH * sH;',
                    '  transformed.z += sSway * uAmp * 0.6 * sH * sH;',
                  ].join('\n'));
              };
              sm.customProgramCacheKey = () => 'sway' + amp;
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

  loadSpec(TREE_TYPES, treeSpots, 0.1, 1.5, { cast: true, leafGlow: true });
  loadSpec(ROCK_TYPES, rockSpots, 0.06, 1.0, { cast: true });
  loadSpec(BUSH_TYPES, bushSpots, 0.04, 1.0, { cast: true, sway: 0.05, leafGlow: true });
  loadSpec(FLOWER_TYPES, flowerSpots, 0.0, 0.6, { cast: false, sway: 0.09 });
  loadSpec(MUSH_TYPES, mushSpots, 0.0, 0.5, { cast: false });
  loadSpec(CLOVER_TYPES, cloverSpots, 0.0, 0.5, { cast: false, sway: 0.06 });

  /* 微观地表（it-004 AC-6）：落瓣 + 小碎石——启用入库未上场的资产 */
  const petalSpots = makeSpots(rand, 46, 70, 1.5, 0.1, 0.2, []);
  loadSpec(['Petal_1', 'Petal_2', 'Petal_3', 'Petal_4', 'Petal_5'],
    petalSpots, 0.0, 0.4, { cast: false });
  const microPebbleSpots = makeSpots(rand, 44, 76, 1.5, 0.12, 0.3, []);
  loadSpec(['Pebble_Round_1', 'Pebble_Round_2'],
    microPebbleSpots, 0.02, 0.6, { cast: false });

  /* 远景林带剪影：真树（Pine_2），雾中出层次 */
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
  loadSpec(['Pine_2'], ringSpots, 2.0, 0.6, { cast: false });

  return group;
}
