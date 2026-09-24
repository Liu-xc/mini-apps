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

/* 任意 (x,z) 的地面高度——玩家贴地、镜头避地、散布物落位共用这一个函数 */
export function terrainHeight(x: number, z: number): number {
  let h = 0;
  h += (vnoise(x * 0.018 + 10, z * 0.018 + 10) - 0.5) * 7.5;  // 大起伏
  h += (vnoise(x * 0.055 + 20, z * 0.055 + 20) - 0.5) * 2.6;  // 中起伏
  h += (vnoise(x * 0.15 + 30, z * 0.15 + 30) - 0.5) * 0.45;   // 细节
  const d = Math.hypot(x, z);
  const t = Math.min(Math.max((d - 7) / 14, 0), 1);            // 出生点压平
  h *= smooth(t);
  const ef = smooth(Math.min(Math.max((d - 96) / 20, 0), 1));  // 世界边缘沉入底板
  return h * (1 - ef) + UNDERLAY_Y * ef;
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
    colors[i * 3] = tmp.r;
    colors[i * 3 + 1] = tmp.g;
    colors[i * 3 + 2] = tmp.b;
  }
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));

  const base = import.meta.env.BASE_URL;
  const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({
    map: pbr(`${base}textures/leafy_grass_diff_2k.jpg`, true),
    normalMap: pbr(`${base}textures/leafy_grass_nor_gl_2k.jpg`, false),
    roughnessMap: pbr(`${base}textures/leafy_grass_rough_2k.jpg`, false),
    normalScale: new THREE.Vector2(1.15, 1.15),
    roughness: 1,
    metalness: 0,
    vertexColors: true,
  }));
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
