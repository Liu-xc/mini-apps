import * as THREE from 'three';
import { terrainHeight } from './terrain';

/* 环境生命（it-005 AC-7）：蝴蝶×8 绕花丛 Lissajous 巡游（双翼扑扇），
   远景鸟群×6 高空盘旋（剪影 M 形 + 缩放扑翼）。全程序化零资产。 */

interface Fly {
  g: THREE.Group;
  wl: THREE.Mesh;
  wr: THREE.Mesh;
  ax: number; az: number;         // 巡游锚点
  r: number; sa: number; sb: number; ph: number;
}

const BIRD_CTR = { x: 5, z: -5 };

export function buildAmbient(): { group: THREE.Group; update: (t: number) => void } {
  const group = new THREE.Group();
  group.name = 'ambient';

  /* ---------- 蝴蝶 ---------- */
  const anchors: Array<[number, number]> = [
    [8, 2], [-6, 4], [14, -3], [-12, -2], [4, 8], [18, 4],
  ];
  const wingGeo = new THREE.PlaneGeometry(0.16, 0.12).translate(0.08, 0, 0);
  const flyColors = ['#f5f0e6', '#e8a13c', '#d9d3f2', '#f5f0e6'];
  const flies: Fly[] = [];
  for (let i = 0; i < 8; i++) {
    const [ax, az] = anchors[i % anchors.length];
    const mat = new THREE.MeshBasicMaterial({
      color: flyColors[i % flyColors.length], side: THREE.DoubleSide,
    });
    mat.userData.outlineParameters = { visible: false };
    const g = new THREE.Group();
    const wl = new THREE.Mesh(wingGeo, mat);
    const wr = new THREE.Mesh(wingGeo, mat);
    wr.scale.x = -1;
    g.add(wl, wr);
    group.add(g);
    flies.push({
      g, wl, wr,
      ax: ax + (i % 3) * 1.7, az: az + (i % 2) * 1.4,
      r: 1.6 + (i % 4) * 0.8,
      sa: 0.23 + (i % 5) * 0.04, sb: 0.19 + (i % 3) * 0.05,
      ph: i * 1.37,
    });
  }

  /* ---------- 鸟群：M 形剪影 ---------- */
  const birdGeo = new THREE.BufferGeometry();
  birdGeo.setAttribute('position', new THREE.Float32BufferAttribute([
    0, 0, 0.26, -0.55, 0.14, -0.2, 0, 0, -0.06, 0.55, 0.14, -0.2,
  ], 3));
  birdGeo.setIndex([0, 1, 2, 0, 2, 3]);
  birdGeo.computeVertexNormals();
  const birdMat = new THREE.MeshBasicMaterial({
    color: '#23262d', side: THREE.DoubleSide,
  });
  birdMat.userData.outlineParameters = { visible: false };
  const birds: Array<{ m: THREE.Mesh; r: number; h: number; w: number; ph: number }> = [];
  for (let i = 0; i < 6; i++) {
    const m = new THREE.Mesh(birdGeo, birdMat);
    m.scale.setScalar(1.6 + (i % 3) * 0.5);
    group.add(m);
    birds.push({
      m,
      r: 34 + (i % 4) * 7,
      h: 26 + (i % 5) * 2.2,
      w: (i % 2 ? 1 : -1) * (0.05 + (i % 3) * 0.012),
      ph: i * 1.05,
    });
  }

  return {
    group,
    update(t: number) {
      for (const f of flies) {
        const px = f.ax + Math.sin(t * f.sa + f.ph) * f.r;
        const pz = f.az + Math.cos(t * f.sb + f.ph * 1.3) * f.r;
        const py = terrainHeight(px, pz) + 0.95 + Math.sin(t * 1.3 + f.ph) * 0.35;
        /* 朝向速度前方（0.6s 后的采样点） */
        const qx = f.ax + Math.sin((t + 0.6) * f.sa + f.ph) * f.r;
        const qz = f.az + Math.cos((t + 0.6) * f.sb + f.ph * 1.3) * f.r;
        f.g.position.set(px, py, pz);
        f.g.rotation.y = Math.atan2(qx - px, qz - pz);
        const flap = Math.sin(t * 16 + f.ph) * 0.78;
        f.wl.rotation.y = 0.35 + flap;
        f.wr.rotation.y = -0.35 - flap;
      }
      for (const b of birds) {
        const ang = t * b.w + b.ph;
        b.m.position.set(
          BIRD_CTR.x + Math.cos(ang) * b.r,
          b.h + Math.sin(t * 0.5 + b.ph) * 2,
          BIRD_CTR.z + Math.sin(ang) * b.r,
        );
        /* 精确切线朝向（机头 +z） */
        b.m.rotation.y = Math.atan2(-Math.sin(ang) * b.w, Math.cos(ang) * b.w);
        b.m.scale.y = 0.55 + 0.5 * Math.abs(Math.sin(t * 6.5 + b.ph * 3));
      }
    },
  };
}
