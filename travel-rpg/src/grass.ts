import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight, LAKE, lakeRadiusAt } from './terrain';
import { PALETTE, SUN_DIR, FOG_NEAR, FOG_FAR } from './style';
import { uCloudT, CLOUD_GLSL } from './cloud';

/* 风动草场（it-002 AC-4 / it-005 扩展）：
   近圈 = Quaternius 真模型 ×3200（Standard 材质 + onBeforeCompile 注入风，全套 PBR/受影）
   远圈 = 自建刀片卡片 ×10600（4 面，取 Grass.png 色列，自定义着色器与雾/分级管线对齐）
   芦苇 = 岸线水位带 Wispy/Common_Tall（it-005 AC-3，共用风场）
   玩家避让（it-005 AC-8）：1.5m 内草叶向玩家反方向推开的 uniform 注入。 */

const NEAR_COUNT = 3200;
const FAR_COUNT = 10600;
const uTime: { value: number } = { value: 0 };   // 近/远共用时间
const uPlayer = { value: new THREE.Vector3(0, 0, 0) };  // 玩家避让（it-005）

function mulberry32(seed: number): () => number {
  let s = seed | 0;
  return () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

interface Spot { x: number; z: number; yaw: number; s: number; u: number; }

function makeSpots(
  rand: () => number, count: number, rMin: number, rMax: number,
  hMin: number, hMax: number, block: Array<[number, number]>,
): Spot[] {
  const spots: Spot[] = [];
  let guard = count * 6;
  while (spots.length < count && guard-- > 0) {
    const ang = rand() * Math.PI * 2;
    const r = rMin + Math.pow(rand(), 0.82) * (rMax - rMin);
    const x = Math.cos(ang) * r, z = Math.sin(ang) * r;
    if (terrainHeight(x, z) < LAKE.level + 0.22) continue;   // 水下不长草（it-005）
    let blocked = false;
    for (const [bx, bz] of block) {
      if ((x - bx) ** 2 + (z - bz) ** 2 < 5.3) { blocked = true; break; }
    }
    if (blocked) continue;
    spots.push({
      x, z,
      yaw: rand() * Math.PI * 2,
      s: hMin + rand() * (hMax - hMin),
      u: 0.06 + rand() * 0.88,   // Grass.png 色列（黄绿~橙秋变化）
    });
  }
  return spots;
}

/* 风注入（近圈/芦苇共用）：sway + gust 阵风波 + 玩家避让 + 云影（it-006） */
function windify(mat: THREE.Material, amp: number, maxH: number, key: string): void {
  const sm = mat as THREE.MeshStandardMaterial;
  sm.side = THREE.DoubleSide;
  sm.userData.outlineParameters = { visible: false };
  sm.onBeforeCompile = shader => {
    shader.uniforms.uTime = uTime;
    shader.uniforms.uPlayer = uPlayer;
    shader.uniforms.uCloudT = uCloudT;
    shader.uniforms.uAmp = { value: amp };
    shader.uniforms.uMaxH = { value: maxH };
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>',
        '#include <common>\nuniform float uTime; uniform float uAmp; uniform float uMaxH;\nuniform vec3 uPlayer;\nvarying vec2 vCW;')
      .replace('#include <begin_vertex>', [
        '#include <begin_vertex>',
        '#ifdef USE_INSTANCING',
        '  vec3 iOrigin = vec3(instanceMatrix[3][0], instanceMatrix[3][1], instanceMatrix[3][2]);',
        '#else',
        '  vec3 iOrigin = vec3(0.0);',
        '#endif',
        '  vCW = iOrigin.xz;',
        '  float gPhase = iOrigin.x * 0.33 + iOrigin.z * 0.27;',
        '  float gSway = sin(uTime * 2.0 + gPhase) + 0.4 * sin(uTime * 4.2 + gPhase * 1.6);',
        // 阵风波：沿 (1,0.6) 方向移动的涌浪带，过境时摆幅加倍（BotW 手感）
        '  float gWave = fract((iOrigin.x + iOrigin.z * 0.6) * 0.006 - uTime * 0.05);',
        '  float gGust = smoothstep(0.88, 1.0, gWave) + smoothstep(0.12, 0.0, gWave);',
        '  gSway *= 1.0 + 1.5 * gGust;',
        '  float gH = clamp(transformed.y / max(uMaxH, 0.001), 0.0, 1.0);',
        '  transformed.x += gSway * uAmp * gH * gH;',
        '  transformed.z += gSway * uAmp * 0.5 * gH * gH;',
        // 玩家避让：1.5m 内草叶推开（it-005 AC-8）
        '  vec2 gToP = iOrigin.xz - uPlayer.xz;',
        '  float gPd = length(gToP);',
        '  float gPush = (1.0 - smoothstep(0.18, 1.5, gPd)) * 0.5;',
        '  transformed.xz += (gPd > 0.001 ? gToP / gPd : vec2(1.0, 0.0)) * gPush * gH * gH;',
      ].join('\n'));
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>',
        '#include <common>\nuniform float uCloudT;\nvarying vec2 vCW;\n' + CLOUD_GLSL)
      .replace('#include <map_fragment>',
        '#include <map_fragment>\n  diffuseColor.rgb = cloudShade(diffuseColor.rgb, vCW, uCloudT);');
  };
  sm.customProgramCacheKey = () => key;
}

export interface GrassField {
  group: THREE.Group;
  update: (t: number, playerPos: THREE.Vector3) => void;
  reedsDone: boolean;
}

export function buildGrassField(block: Array<[number, number]>): GrassField {
  const group = new THREE.Group();
  group.name = 'grassField';
  const rand = mulberry32(90210);
  const nearSpots = makeSpots(rand, NEAR_COUNT, 1.6, 26, 0.36, 0.68, block);
  const farSpots = makeSpots(rand, FAR_COUNT, 3, 66, 0.26, 0.5, block);
  const dummy = new THREE.Object3D();
  const api: GrassField = { group, update: () => {}, reedsDone: false };

  /* ---------- 近圈：Quaternius 真模型 + 风注入 ---------- */
  const base = import.meta.env.BASE_URL;
  new GLTFLoader().load(`${base}assets/quaternius/Grass_Common_Short.gltf`, gltf => {
    gltf.scene.updateMatrixWorld(true);
    const found: Array<{ geo: THREE.BufferGeometry; mat: THREE.Material }> = [];
    gltf.scene.traverse(o => {
      if (o instanceof THREE.Mesh && found.length === 0) {
        found.push({
          geo: o.geometry,
          mat: Array.isArray(o.material) ? o.material[0] : o.material,
        });
      }
    });
    if (found.length === 0) return;
    const geo = found[0].geo;
    const bb = new THREE.Box3().setFromBufferAttribute(
      geo.getAttribute('position') as THREE.BufferAttribute,
    );
    const maxH = Math.max(bb.max.y - bb.min.y, 0.01);
    const windMat = found[0].mat.clone();
    windify(windMat, 0.16, maxH, 'windgrass');

    const inst = new THREE.InstancedMesh(geo, windMat, nearSpots.length);
    nearSpots.forEach((sp, i) => {
      dummy.position.set(sp.x, terrainHeight(sp.x, sp.z), sp.z);
      dummy.rotation.set(0, sp.yaw, 0);
      dummy.scale.setScalar(sp.s);
      dummy.updateMatrix();
      inst.setMatrixAt(i, dummy.matrix);
    });
    inst.instanceMatrix.needsUpdate = true;
    inst.castShadow = false;
    inst.receiveShadow = true;
    inst.computeBoundingSphere();
    group.add(inst);
  }, undefined, () => console.warn('[grass] 近圈模型加载失败'));

  /* ---------- 芦苇带（it-005 AC-3）：岸线水位带 Wispy/Common_Tall ---------- */
  const reedSpots: Spot[] = [];
  for (let a = 0; a < Math.PI * 2; a += 0.035) {
    const wl = lakeRadiusAt(a);
    for (let k = 0; k < 3; k++) {
      const r = wl * (0.72 + rand() * 0.34);
      const x = LAKE.x + Math.cos(a) * r, z = LAKE.z + Math.sin(a) * r;
      const h = terrainHeight(x, z);
      if (h > LAKE.level + 0.02 && h < LAKE.level + 0.6) {
        reedSpots.push({
          x, z, yaw: rand() * Math.PI * 2,
          s: 0.5 + rand() * 0.45,
          u: 0.06 + rand() * 0.88,
        });
      }
    }
  }
  const REEDS = ['Grass_Wispy_Tall', 'Grass_Common_Tall'];
  for (const name of REEDS) {
    new GLTFLoader().load(`${base}assets/quaternius/${name}.gltf`, gltf => {
      gltf.scene.updateMatrixWorld(true);
      let done = false;
      gltf.scene.traverse(o => {
        if (o instanceof THREE.Mesh && !done) {
          done = true;
          const bb = new THREE.Box3().setFromBufferAttribute(
            o.geometry.getAttribute('position') as THREE.BufferAttribute,
          );
          const maxH = Math.max(bb.max.y - bb.min.y, 0.01);
          const reedMat = (Array.isArray(o.material) ? o.material[0] : o.material).clone();
          windify(reedMat, 0.24, maxH, 'windreed' + name);
          const slice = reedSpots.filter((_, i) => i % REEDS.length === REEDS.indexOf(name));
          const inst = new THREE.InstancedMesh(o.geometry, reedMat, slice.length);
          slice.forEach((sp, i) => {
            dummy.position.set(sp.x, terrainHeight(sp.x, sp.z) - 0.02, sp.z);
            dummy.rotation.set(0, sp.yaw, 0);
            dummy.scale.setScalar(sp.s);
            dummy.updateMatrix();
            inst.setMatrixAt(i, dummy.matrix);
          });
          inst.instanceMatrix.needsUpdate = true;
          inst.castShadow = false;
          inst.receiveShadow = true;
          inst.computeBoundingSphere();
          group.add(inst);
          api.reedsDone = true;
        }
      });
    }, undefined, () => console.warn('[grass] 芦苇加载失败:', name));
  }

  /* ---------- 远圈：刀片卡片（自建几何 + 包内色列贴图，自定义着色器） ---------- */
  const blade = new THREE.BufferGeometry();
  const bw = 0.055;
  blade.setAttribute('position', new THREE.Float32BufferAttribute([
    -bw, 0, 0, bw, 0, 0, -bw * 0.2, 1, 0, bw * 0.2, 1, 0,
  ], 3));
  blade.setAttribute('uv', new THREE.Float32BufferAttribute([0, 0, 1, 0, 0, 1, 1, 1], 2));
  blade.setIndex([0, 1, 2, 2, 1, 3]);
  const grassTex = new THREE.TextureLoader().load(`${base}assets/quaternius/Grass.png`);
  grassTex.colorSpace = THREE.SRGBColorSpace;
  grassTex.wrapS = grassTex.wrapT = THREE.ClampToEdgeWrapping;

  const farMat = new THREE.ShaderMaterial({
    side: THREE.DoubleSide,
    uniforms: {
      uTime,
      uPlayer,
      uCloudT,
      uMap: { value: grassTex },
      uSun: { value: SUN_DIR },
      uSunCol: { value: new THREE.Color('#fff0d6').multiplyScalar(1.9) },
      uSky: { value: PALETTE.hemiSky },
      uGnd: { value: PALETTE.hemiGround },
      uFogC: { value: PALETTE.fog },
      uFogN: { value: FOG_NEAR },
      uFogF: { value: FOG_FAR },
    },
    vertexShader: /* glsl */ `
      uniform float uTime;
      uniform vec3 uPlayer;
      attribute float aU;
      varying vec2 vUv;
      varying vec3 vW;
      varying float vDepth;
      void main() {
        vUv = vec2(aU + uv.x * 0.05, uv.y);
        vec3 p = position;
        #ifdef USE_INSTANCING
          mat4 imat = instanceMatrix;
        #else
          mat4 imat = mat4(1.0);
        #endif
        vec3 origin = imat[3].xyz;
        float phase = origin.x * 0.31 + origin.z * 0.23;
        float sway = sin(uTime * 2.0 + phase) + 0.4 * sin(uTime * 4.1 + phase * 1.6);
        float wave = fract((origin.x + origin.z * 0.6) * 0.006 - uTime * 0.05);
        float gust = smoothstep(0.88, 1.0, wave) + smoothstep(0.12, 0.0, wave);
        sway *= 1.0 + 1.5 * gust;
        float h = clamp(p.y, 0.0, 1.0);
        p.x += sway * 0.22 * h * h;
        p.z += sway * 0.11 * h * h;
        vec2 toP = origin.xz - uPlayer.xz;
        float pd = length(toP);
        float push = (1.0 - smoothstep(0.18, 1.5, pd)) * 0.5;
        p.xz += (pd > 0.001 ? toP / pd : vec2(1.0, 0.0)) * push * h * h;
        vec4 wp = modelMatrix * imat * vec4(p, 1.0);
        vW = wp.xyz;
        vec4 mv = viewMatrix * wp;
        vDepth = -mv.z;
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: /* glsl */ `
      uniform sampler2D uMap;
      uniform float uTime;
      uniform float uCloudT;
      uniform vec3 uSun, uSunCol, uSky, uGnd, uFogC;
      uniform float uFogN, uFogF;
      varying vec2 vUv;
      varying vec3 vW;
      varying float vDepth;
      ${CLOUD_GLSL}
      void main() {
        vec4 tex = texture2D(uMap, vUv);
        if (tex.a < 0.45) discard;
        vec3 albedo = tex.rgb;
        float ndl = max(dot(vec3(0.0, 1.0, 0.0), normalize(uSun)), 0.0);
        vec3 amb = mix(uGnd, uSky, 0.7);
        vec3 lit = albedo * (uSunCol * (0.42 + 0.58 * ndl) + amb * 0.95);
        lit *= 1.0 + 0.05 * sin(uTime * 3.0 + vW.x * 0.4 + vW.z * 0.31);
        lit = cloudShade(lit, vW.xz, uCloudT);
        float fogF = smoothstep(uFogN, uFogF, vDepth);
        gl_FragColor = vec4(mix(lit, uFogC, fogF), 1.0);
      }
    `,
  });
  farMat.userData.outlineParameters = { visible: false };

  const farInst = new THREE.InstancedMesh(blade, farMat, farSpots.length);
  const aU = new Float32Array(farSpots.length);
  farSpots.forEach((sp, i) => {
    dummy.position.set(sp.x, terrainHeight(sp.x, sp.z), sp.z);
    dummy.rotation.set(0, sp.yaw, 0);
    dummy.scale.set(sp.s * 0.55, sp.s * 0.5, sp.s * 0.55);
    dummy.updateMatrix();
    farInst.setMatrixAt(i, dummy.matrix);
    aU[i] = sp.u;
  });
  blade.setAttribute('aU', new THREE.InstancedBufferAttribute(aU, 1));
  farInst.instanceMatrix.needsUpdate = true;
  farInst.castShadow = false;
  farInst.receiveShadow = false;
  farInst.computeBoundingSphere();
  group.add(farInst);

  api.update = (t, playerPos) => {
    uTime.value = t;
    uPlayer.value.copy(playerPos);
  };
  return api;
}
