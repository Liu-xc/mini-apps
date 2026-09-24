import * as THREE from 'three';

/* it-002 色彩纪律（白昼体系）：
   蓝调天空打底（IBL/半球光同源）、暖光只给日光本体，杜绝全屏土黄泥色。
   雾色需与天空 HDRI 地平线一致——像素取样校准（见 it-002 验证记录）；
   it-006 按地平线重校压深一档（原 #cfe0ec 比天空亮，读作发光白带/远海）。 */
export const PALETTE = {
  fog: new THREE.Color('#c3d5e2'),
  sunLight: new THREE.Color('#fff3de'),
  hemiSky: new THREE.Color('#a9cdf2'),
  hemiGround: new THREE.Color('#8fae68'),
  fill: new THREE.Color('#e7f0fa'),
  underlay: new THREE.Color('#b9c9a0'),
};

/* 太阳方向：由时段预设驱动（it-007），SUN_DIR 原地更新——
   水面/草场 uniform 持同一引用，改角度即全场生效 */
export const SUN_DIR = new THREE.Vector3().setFromSphericalCoords(
  1,
  THREE.MathUtils.degToRad(50),
  THREE.MathUtils.degToRad(140),
);
export function setSunAngle(polarDeg: number, azimuthDeg: number): void {
  SUN_DIR.setFromSphericalCoords(
    1, THREE.MathUtils.degToRad(polarDeg), THREE.MathUtils.degToRad(azimuthDeg));
}

/* 雾参数：场景雾与草场自定义着色器共用，保证颜色/过渡一致 */
export const FOG_NEAR = 75;
export const FOG_FAR = 215;

/* ---------- 时段预设（it-007）：day = it-002~006 现行白昼纪律 ---------- */
export type TimeId = 'day' | 'dawn' | 'sunset';
export interface TimePreset {
  hdr: string;            // textures/ 下的 HDRI 文件
  hdriYaw: number;        // HDRI 旋转（把暖色地平线转向常见视角）
  bgIntensity: number;
  envIntensity: number;
  fog: string;
  fogNear: number;
  fogFar: number;
  sat: number;            // 分级饱和度
  sun: string;            // 日光色
  sunIntensity: number;
  sunPolarDeg: number;    // 仰角（小=低垂长影）
  sunAzimuthDeg: number;
  hemiSky: string;
  hemiGround: string;
  fill: string;
  grassSunCol: string;    // 远圈草直射色基色（× grassSunK）
  grassSunK: number;
  waterSky: string;       // 水面菲涅尔天空色 / 浅深水色 / 波光强度
  waterShallow: string;
  waterDeep: string;
  waterSunK: number;
  waterFresK: number;     // 菲涅尔混天空系数（黄昏压低防浑浊镜面）
}

export const TIME_PRESETS: Record<TimeId, TimePreset> = {
  day: {
    hdr: 'puresky_2k.hdr', hdriYaw: 0, bgIntensity: 0.94, envIntensity: 0.62,
    fog: '#c3d5e2', fogNear: 75, fogFar: 215, sat: 1.12, sun: '#fff3de', sunIntensity: 4.1, sunPolarDeg: 50, sunAzimuthDeg: 140,
    hemiSky: '#a9cdf2', hemiGround: '#8fae68', fill: '#e7f0fa',
    grassSunCol: '#fff0d6', grassSunK: 1.9,
    waterSky: '#7faede', waterShallow: '#4f9a80', waterDeep: '#1d4a60', waterSunK: 1.15,
    waterFresK: 0.42,
  },
  dawn: {
    hdr: 'dawn_puresky_2k.hdr', hdriYaw: 0, bgIntensity: 1.0, envIntensity: 0.55,
    fog: '#e3cfcf', fogNear: 65, fogFar: 205, sat: 1.08, sun: '#ffd9b0', sunIntensity: 3.6, sunPolarDeg: 72, sunAzimuthDeg: 115,
    hemiSky: '#d8b9c4', hemiGround: '#97977a', fill: '#f2e2da',
    grassSunCol: '#ffd9b0', grassSunK: 1.8,
    waterSky: '#e3bfc6', waterShallow: '#6f9488', waterDeep: '#2c4455', waterSunK: 1.0,
    waterFresK: 0.48,
  },
  sunset: {
    hdr: 'sunset_puresky_2k.hdr', hdriYaw: 1.6, bgIntensity: 1.02, envIntensity: 0.6,
    fog: '#e2c3a4', fogNear: 90, fogFar: 230, sat: 1.24, sun: '#ffae60', sunIntensity: 4.5, sunPolarDeg: 65, sunAzimuthDeg: 250,
    hemiSky: '#e2b48c', hemiGround: '#8f7a5e', fill: '#f4d9b8',
    grassSunCol: '#ffc080', grassSunK: 2.0,
    waterSky: '#e8b98a', waterShallow: '#5f9a80', waterDeep: '#24455a', waterSunK: 1.5,
    waterFresK: 0.3,
  },
};

/* 就地改写 PALETTE（草场/水面 uniform 持 PALETTE 引用，随之联动） */
export function applyPaletteToPalette(p: TimePreset): void {
  PALETTE.fog.set(p.fog);
  PALETTE.sunLight.set(p.sun);
  PALETTE.hemiSky.set(p.hemiSky);
  PALETTE.hemiGround.set(p.hemiGround);
  PALETTE.fill.set(p.fill);
  setSunAngle(p.sunPolarDeg, p.sunAzimuthDeg);
}
