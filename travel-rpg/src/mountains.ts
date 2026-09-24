import * as THREE from 'three';
import { UNDERLAY_Y } from './terrain';

/* 远山剪影环（it-006 AC-1）：两层程序化山脊带（角向 fbm 峰高），
   Basic 材质 vertexColors 纵向渐变 + 吃雾——世界边缘外读作「群山远景」。 */

interface Layer { r: number; hBase: number; hVar: number; col: string; seed: number; }

const LAYERS: Layer[] = [
  { r: 188, hBase: 15, hVar: 30, col: '#8ba4bb', seed: 31 },   // 远层：高、淡
  { r: 142, hBase: 10, hVar: 20, col: '#6f8ba0', seed: 77 },   // 近层：矮、深
];

/* 1D 角向 fbm（确定性，4 频段叠加弱化折线感） */
function ridge(seeda: number, ang: number): number {
  const s = Math.sin((ang * 3 + seeda) * 12.9898) * 43758.5453;
  const n1 = s - Math.floor(s);
  const s2 = Math.sin((ang * 7 - seeda) * 78.233) * 12345.6789;
  const n2 = s2 - Math.floor(s2);
  const s3 = Math.sin((ang * 13 + seeda * 2) * 39.425) * 9876.543;
  const n3 = s3 - Math.floor(s3);
  const s4 = Math.sin((ang * 23 + seeda * 3) * 17.709) * 3456.789;
  const n4 = s4 - Math.floor(s4);
  return n1 * 0.42 + n2 * 0.26 + n3 * 0.19 + n4 * 0.13;
}

function buildLayer(L: Layer): THREE.Mesh {
  const seg = 160;
  const baseY = UNDERLAY_Y - 2.5;
  const pos = new Float32Array((seg + 1) * 2 * 3);
  const col = new Float32Array((seg + 1) * 2 * 3);
  const idx: number[] = [];
  const base = new THREE.Color(L.col);
  const c = new THREE.Color();
  for (let i = 0; i <= seg; i++) {
    const ang = (i / seg) * Math.PI * 2;
    const x = Math.cos(ang) * L.r, z = Math.sin(ang) * L.r;
    const hn = ridge(L.seed, ang * 4);
    const h = L.hBase + hn * L.hVar;
    pos[i * 6] = x;      pos[i * 6 + 1] = baseY; pos[i * 6 + 2] = z;
    pos[i * 6 + 3] = x;  pos[i * 6 + 4] = h;     pos[i * 6 + 5] = z;
    c.copy(base).multiplyScalar(0.72 + hn * 0.4);
    col[i * 6] = c.r;     col[i * 6 + 1] = c.g;  col[i * 6 + 2] = c.b;
    col[i * 6 + 3] = c.r; col[i * 6 + 4] = c.g * 1.06; col[i * 6 + 5] = c.b * 1.1;
    if (i < seg) {
      const a = i * 2, b = i * 2 + 1, c2 = i * 2 + 2, d = i * 2 + 3;
      idx.push(a, b, c2, c2, b, d);
    }
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
  geo.setIndex(idx);
  const mat = new THREE.MeshBasicMaterial({
    vertexColors: true, side: THREE.DoubleSide, fog: true,
  });
  mat.userData.outlineParameters = { visible: false };
  const mesh = new THREE.Mesh(geo, mat);
  mesh.name = 'mountain' + L.r;
  return mesh;
}

export function buildMountains(): THREE.Group {
  const group = new THREE.Group();
  group.name = 'mountains';
  for (const L of LAYERS) group.add(buildLayer(L));
  return group;
}
