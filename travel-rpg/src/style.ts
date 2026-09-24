import * as THREE from 'three';

/* 全场景共享的 3 阶 cel-shading gradientMap（ADR-002） */
let gradient: THREE.DataTexture | null = null;
export function toonGradient(): THREE.DataTexture {
  if (!gradient) {
    const data = new Uint8Array([105, 190, 255]);
    gradient = new THREE.DataTexture(data, 3, 1, THREE.RedFormat);
    gradient.minFilter = THREE.NearestFilter;
    gradient.magFilter = THREE.NearestFilter;
    gradient.needsUpdate = true;
  }
  return gradient;
}

export function makeToon(
  color: THREE.ColorRepresentation,
  opts: THREE.MeshToonMaterialParameters = {},
): THREE.MeshToonMaterial {
  return new THREE.MeshToonMaterial({ color, gradientMap: toonGradient(), ...opts });
}

/* 黄金时刻色板——迁自平面气氛稿（reports/2026-09-24-travel-rpg-scenes，乌兰布统站） */
export const PALETTE = {
  skyTop: new THREE.Color('#2b4a7c'),
  skyHorizon: new THREE.Color('#f2d6a4'),
  skyHaze: new THREE.Color('#e6d2a8'),
  sunColor: new THREE.Color('#fff3d0'),
  fog: new THREE.Color('#f2d6a4'),   // 与 skyHorizon 同色：地平线雾与天空无缝相接
  sunLight: new THREE.Color('#ffe2b0'),
  hemiSky: new THREE.Color('#a8c6e8'),
  hemiGround: new THREE.Color('#c8a068'),
};
