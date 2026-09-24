import * as THREE from 'three';
import { PALETTE } from './style';

/* ---------- 确定性 2D 值噪声 ---------- */
function hash2(x: number, z: number): number {
  const s = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
  return s - Math.floor(s);
}
function smooth(t: number): number {
  return t * t * (3 - 2 * t);
}
function vnoise(x: number, z: number): number {
  const ix = Math.floor(x), iz = Math.floor(z);
  const fx = smooth(x - ix), fz = smooth(z - iz);
  const a = hash2(ix, iz), b = hash2(ix + 1, iz);
  const c = hash2(ix, iz + 1), d = hash2(ix + 1, iz + 1);
  return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
}
function fbm(x: number, z: number, oct = 3): number {
  let v = 0, amp = 0.5, fr = 1;
  for (let i = 0; i < oct; i++) {
    v += vnoise(x * fr + i * 17.3, z * fr - i * 9.1) * amp;
    amp *= 0.5;
    fr *= 2;
  }
  return v;
}

export const WORLD_HALF = 120;   /* 地形半径（±WORLD_HALF） */
export const PLAYER_LIMIT = 88;  /* 角色活动边界（另加 90 半径圆钳制） */
export const UNDERLAY_Y = -6.5;  /* 底板高度：地形边缘沉入此处，地平线无缝 */

/* 达里湖（it-005 AC-1）：湖盆参数与水位。水线 = 湖岸 wobble 半径的 ~0.72 倍，
   滩涂带宽 ~4m，湖心最深水位下 -2.6m（仿真见 it-005 验证记录）。 */
export const LAKE = { x: 54, z: -38, r: 30, level: -1.35 };

/* 湖岸线半径：角向低频噪声扰动（有机岸线的唯一事实源，水面/芦苇/地形共用） */
export function lakeRadiusAt(ang: number): number {
  return LAKE.r * (1 + (vnoise(Math.cos(ang) * 2 + 40, Math.sin(ang) * 2 + 40) - 0.5) * 0.42);
}

/* 任意 (x,z) 的地面高度——玩家贴地、镜头避地、散布物落位共用这一个函数 */
export function terrainHeight(x: number, z: number): number {
  let h = 0;
  h += (vnoise(x * 0.018 + 10, z * 0.018 + 10) - 0.5) * 7.5;  // 大起伏
  h += (vnoise(x * 0.055 + 20, z * 0.055 + 20) - 0.5) * 2.6;  // 中起伏
  h += (vnoise(x * 0.15 + 30, z * 0.15 + 30) - 0.5) * 0.45;   // 细节
  const d = Math.hypot(x, z);
  const t = Math.min(Math.max((d - 7) / 14, 0), 1);            // 出生点压平
  h *= smooth(t);
  /* 达里湖湖盆：岸线内把地形压到水下山盘 */
  const dx = x - LAKE.x, dz = z - LAKE.z;
  const dist = Math.hypot(dx, dz);
  if (dist < LAKE.r * 2.2) {
    const ld = dist / lakeRadiusAt(Math.atan2(dz, dx));
    if (ld < 1.35) {
      const shore = smooth(Math.min(Math.max((1 - ld) / 0.35, 0), 1));
      const bed = LAKE.level - 2.6 * smooth(Math.min(Math.max((0.85 - ld) / 0.85, 0), 1));
      h = h * (1 - shore) + bed * shore;
    }
  }
  const ef = smooth(Math.min(Math.max((d - 96) / 20, 0), 1));  // 世界边缘沉入底板
  return h * (1 - ef) + UNDERLAY_Y * ef;
}

/* 玩家/镜头用的「可行走地面」：湖盆内钳制在膝深水位（涉水，it-005 AC-1）。
   湖外天然洼地不钳制——那是干沟，可以走下去。 */
export function groundHeight(x: number, z: number): number {
  const h = terrainHeight(x, z);
  if (h < LAKE.level - 0.5 && Math.hypot(x - LAKE.x, z - LAKE.z) < LAKE.r * 1.6) {
    return LAKE.level - 0.5;
  }
  return h;
}

function pbr(url: string, srgb: boolean): THREE.Texture {
  const t = new THREE.TextureLoader().load(url);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(20, 20);
  t.anisotropy = 16;
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

/* ---------- 地形网格：PolyHaven PBR 三件套（diff + normal + rough） ---------- */
export function buildTerrain(): THREE.Mesh {
  const size = WORLD_HALF * 2;
  const seg = 192;
  const geo = new THREE.PlaneGeometry(size, size, seg, seg);
  geo.rotateX(-Math.PI / 2);
  const pos = geo.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    pos.setY(i, terrainHeight(pos.getX(i), pos.getZ(i)));
  }
  pos.needsUpdate = true;
  geo.computeVertexNormals();
  const nrm = geo.attributes.normal;

  /* 白昼植被色系（替换旧金色时刻土黄） */
  const cLow = new THREE.Color('#7fb855');
  const cHigh = new THREE.Color('#c9d98a');
  const cRock = new THREE.Color('#a8a196');
  const sandDry = new THREE.Color('#d6cb9c');
  const sandWet = new THREE.Color('#c2ae7f');
  const bedCol = new THREE.Color('#8fa07a');
  const tmp = new THREE.Color();
  const colors = new Float32Array(pos.count * 3);
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), z = pos.getZ(i), y = pos.getY(i);
    const slope = 1 - nrm.getY(i);
    const ht = Math.min(Math.max((y + 4) / 8, 0), 1);
    tmp.copy(cLow).lerp(cHigh, ht);
    const v = vnoise(x * 0.25 + 5, z * 0.25 + 5) - 0.5;
    tmp.offsetHSL(0, 0, v * 0.05);
    const macro = fbm(x * 0.012 + 50, z * 0.012 + 50, 3) - 0.5;
    tmp.offsetHSL(0.02, 0.03, macro * 0.16);
    tmp.lerp(cRock, Math.min(slope * 2.2, 1) * 0.85);
    /* 湖岸湿沙环（it-005 AC-5）：水位带草色 → 干沙 → 湿沙 → 水下苔底 */
    if (y < LAKE.level + 1.1) {
      tmp.lerp(sandDry, Math.min(Math.max((LAKE.level + 1.1 - y) / 0.9, 0), 1) * 0.75);
      tmp.lerp(sandWet, Math.min(Math.max((LAKE.level + 0.25 - y) / 0.5, 0), 1) * 0.8);
      if (y < LAKE.level - 0.2) {
        tmp.lerp(bedCol, Math.min(Math.max((LAKE.level - 0.2 - y) / 1.2, 0), 1) * 0.7);
      }
    }
    colors[i * 3] = tmp.r;
    colors[i * 3 + 1] = tmp.g;
    colors[i * 3 + 2] = tmp.b;
  }
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));

  const base = import.meta.env.BASE_URL;
  const mat = new THREE.MeshStandardMaterial({
    map: pbr(`${base}textures/leafy_grass_diff_2k.jpg`, true),
    normalMap: pbr(`${base}textures/leafy_grass_nor_gl_2k.jpg`, false),
    roughnessMap: pbr(`${base}textures/leafy_grass_rough_2k.jpg`, false),
    normalScale: new THREE.Vector2(1.15, 1.15),
    roughness: 1,
    metalness: 0,
    vertexColors: true,
  });
  /* 反平铺（it-005 AC-5）：albedo 改为世界坐标双尺度采样 + 宏观噪声混合，
     打断 20× 平铺网格；法线/粗糙度维持原 vMapUv 平铺（微观重复不可辨）。 */
  mat.onBeforeCompile = shader => {
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vTerr;')
      .replace('#include <begin_vertex>',
        '#include <begin_vertex>\nvTerr = transformed;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', [
        '#include <common>',
        'varying vec3 vTerr;',
        'float tHash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }',
        'float tNoise(vec2 p){',
        '  vec2 i = floor(p), f = fract(p);',
        '  f = f * f * (3.0 - 2.0 * f);',
        '  return mix(mix(tHash(i), tHash(i + vec2(1.0, 0.0)), f.x),',
        '             mix(tHash(i + vec2(0.0, 1.0)), tHash(i + vec2(1.0, 1.0)), f.x), f.y);',
        '}',
      ].join('\n'))
      .replace('#include <map_fragment>', [
        '#ifdef USE_MAP',
        '  float tB = tNoise(vTerr.xz * 0.045) * 0.62 + tNoise(vTerr.xz * 0.012) * 0.38;',
        '  float tM = smoothstep(0.32, 0.68, tB);',
        '  vec2 tUvA = vTerr.xz * 0.085;',
        '  vec2 tUvB = vec2(-vTerr.z, vTerr.x) * 0.028 + 17.3;',
        '  diffuseColor *= mix(texture2D(map, tUvA), texture2D(map, tUvB), tM);',
        '#endif',
      ].join('\n'));
  };
  mat.customProgramCacheKey = () => 'terrainAntitile';

  const mesh = new THREE.Mesh(geo, mat);
  mesh.name = 'terrain';
  mesh.receiveShadow = true;
  return mesh;
}

/* 地形外的素色平原：延伸进雾里，封掉地平线缺口 */
export function buildUnderlay(): THREE.Mesh {
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(1400, 1400).rotateX(-Math.PI / 2),
    new THREE.MeshStandardMaterial({ color: PALETTE.underlay, roughness: 1, metalness: 0 }),
  );
  mesh.position.y = UNDERLAY_Y;
  mesh.name = 'underlay';
  mesh.material.userData.outlineParameters = { visible: false };
  return mesh;
}
