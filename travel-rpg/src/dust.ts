import * as THREE from 'three';
import { LAKE } from './terrain';

/* 足迹反馈（it-005 AC-8）：脚步尘土 puff 池 + 涉水涟漪环池。
   全部预建池化，update(dt) 只推进生命周期，零分配。 */

function puffTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas');
  cv.width = cv.height = 64;
  const c = cv.getContext('2d')!;
  const g = c.createRadialGradient(32, 32, 3, 32, 32, 30);
  g.addColorStop(0, 'rgba(206,192,163,0.85)');
  g.addColorStop(1, 'rgba(206,192,163,0)');
  c.fillStyle = g;
  c.fillRect(0, 0, 64, 64);
  return new THREE.CanvasTexture(cv);
}

interface Puff { sp: THREE.Sprite; life: number; big: number; }
interface Ripple { m: THREE.Mesh; life: number; }

export function buildDust(): {
  group: THREE.Group;
  puff: (x: number, y: number, z: number, big?: number) => void;
  ripple: (x: number, z: number, big?: number) => void;
  update: (dt: number) => void;
} {
  const group = new THREE.Group();
  group.name = 'dust';
  const tex = puffTexture();

  const puffs: Puff[] = [];
  for (let i = 0; i < 14; i++) {
    const mat = new THREE.SpriteMaterial({
      map: tex, transparent: true, opacity: 0, depthWrite: false,
    });
    mat.userData.outlineParameters = { visible: false };
    const sp = new THREE.Sprite(mat);
    sp.visible = false;
    sp.renderOrder = 3;
    group.add(sp);
    puffs.push({ sp, life: 0, big: 1 });
  }
  let puffCursor = 0;

  const ripples: Ripple[] = [];
  const ringGeo = new THREE.RingGeometry(0.3, 0.38, 28).rotateX(-Math.PI / 2);
  for (let i = 0; i < 8; i++) {
    const mat = new THREE.MeshBasicMaterial({
      color: '#e8f4f0', transparent: true, opacity: 0,
      depthWrite: false, side: THREE.DoubleSide,
    });
    mat.userData.outlineParameters = { visible: false };
    const m = new THREE.Mesh(ringGeo, mat);
    m.visible = false;
    m.renderOrder = 4;
    group.add(m);
    ripples.push({ m, life: 0 });
  }
  let rippleCursor = 0;

  return {
    group,
    puff(x, y, z, big = 1) {
      const p = puffs[puffCursor++ % puffs.length];
      p.life = 1;
      p.big = big;
      p.sp.visible = true;
      p.sp.position.set(x + (Math.random() - 0.5) * 0.3, y + 0.12, z + (Math.random() - 0.5) * 0.3);
    },
    ripple(x, z, big = 1) {
      const r = ripples[rippleCursor++ % ripples.length];
      r.life = 1;
      r.m.visible = true;
      r.m.position.set(x, LAKE.level + 0.03, z);
      r.m.scale.setScalar(big);
      r.m.userData.big = big;
    },
    update(dt: number) {
      for (const p of puffs) {
        if (p.life <= 0) continue;
        p.life -= dt * 2.1;
        if (p.life <= 0) { p.sp.visible = false; continue; }
        const k = 1 - p.life;
        p.sp.scale.setScalar((0.25 + k * 0.55) * p.big);
        (p.sp.material as THREE.SpriteMaterial).opacity = p.life * 0.5;
        p.sp.position.y += dt * 0.25;
      }
      for (const r of ripples) {
        if (r.life <= 0) continue;
        r.life -= dt * 1.15;
        if (r.life <= 0) { r.m.visible = false; continue; }
        const k = 1 - r.life;
        const big = (r.m.userData.big as number) || 1;
        r.m.scale.setScalar((0.6 + k * 2.2) * big);
        (r.m.material as THREE.MeshBasicMaterial).opacity = r.life * 0.42;
      }
    },
  };
}
