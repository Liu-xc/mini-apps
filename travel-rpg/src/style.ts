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

/* 太阳方向：白昼仰角 40°（阴影清晰、云层受光合理） */
export const SUN_DIR = new THREE.Vector3().setFromSphericalCoords(
  1,
  THREE.MathUtils.degToRad(50),
  THREE.MathUtils.degToRad(140),
);

/* 雾参数：场景雾与草场自定义着色器共用，保证颜色/过渡一致 */
export const FOG_NEAR = 75;
export const FOG_FAR = 215;
