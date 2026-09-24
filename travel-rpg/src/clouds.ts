import * as THREE from 'three';

/* 柔和积云：画布贴图 Sprite，缓慢漂移（迁自平面气氛稿的云语言） */
function cloudTexture(seed: number): THREE.CanvasTexture {
  const cv = document.createElement('canvas');
  cv.width = 256;
  cv.height = 128;
  const c = cv.getContext('2d')!;
  let s = seed | 0;
  const rnd = () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  c.clearRect(0, 0, 256, 128);
  const blobs = 7 + Math.floor(rnd() * 4);
  for (let i = 0; i < blobs; i++) {
    const x = 40 + (i / blobs) * 176 + (rnd() - 0.5) * 30;
    const y = 74 + (rnd() - 0.5) * 26;
    const r = 22 + rnd() * 30;
    const g = c.createRadialGradient(x, y, r * 0.2, x, y, r);
    g.addColorStop(0, 'rgba(255,252,244,.9)');
    g.addColorStop(0.65, 'rgba(255,248,236,.45)');
    g.addColorStop(1, 'rgba(255,246,232,0)');
    c.fillStyle = g;
    c.beginPath();
    c.arc(x, y, r, 0, Math.PI * 2);
    c.fill();
  }
  const tex = new THREE.CanvasTexture(cv);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export interface CloudLayer {
  group: THREE.Group;
  update: (dt: number) => void;
}

export function buildClouds(count = 9): CloudLayer {
  const group = new THREE.Group();
  group.name = 'clouds';
  const sprites: THREE.Sprite[] = [];
  let s = 777;
  const rnd = () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const textures = [cloudTexture(11), cloudTexture(23), cloudTexture(42)];
  for (let i = 0; i < count; i++) {
    const mat = new THREE.SpriteMaterial({
      map: textures[i % textures.length],
      transparent: true,
      depthWrite: false,
      fog: false,
      opacity: 0.6 + rnd() * 0.3,
    });
    mat.userData.outlineParameters = { visible: false };
    const sp = new THREE.Sprite(mat);
    const w = 60 + rnd() * 80;
    sp.scale.set(w, w * 0.42, 1);
    sp.position.set((rnd() - 0.5) * 560, 26 + rnd() * 34, (rnd() - 0.5) * 560);
    sp.renderOrder = -1;
    sprites.push(sp);
    group.add(sp);
  }
  return {
    group,
    update: (dt: number) => {
      for (const sp of sprites) {
        sp.position.x += dt * (1.2 + sp.scale.y * 0.05);
        if (sp.position.x > 380) sp.position.x = -380;
      }
    },
  };
}
