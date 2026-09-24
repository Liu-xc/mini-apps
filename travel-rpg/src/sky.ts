import * as THREE from 'three';
import { PALETTE } from './style';

/* 太阳方向：低仰角黄金时刻，位于初始镜头前方偏左 */
export const SUN_DIR = new THREE.Vector3().setFromSphericalCoords(
  1,
  THREE.MathUtils.degToRad(78),   // 天顶角 78° → 仰角 12°
  THREE.MathUtils.degToRad(135),
);

/* 渐变天空穹顶：顶色 → 地平线 → 地平线下雾霭，含日轮与光晕 */
export function buildSky(): THREE.Mesh {
  const geo = new THREE.SphereGeometry(500, 32, 16);
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide,
    depthWrite: false,
    fog: false,
    uniforms: {
      uTop: { value: PALETTE.skyTop },
      uHorizon: { value: PALETTE.skyHorizon },
      uHaze: { value: PALETTE.skyHaze },
      uSunColor: { value: PALETTE.sunColor },
      uSunDir: { value: SUN_DIR },
    },
    vertexShader: /* glsl */ `
      varying vec3 vDir;
      void main() {
        vDir = normalize(position);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: /* glsl */ `
      varying vec3 vDir;
      uniform vec3 uTop, uHorizon, uHaze, uSunColor, uSunDir;
      void main() {
        vec3 d = normalize(vDir);
        vec3 col = mix(uHorizon, uTop, smoothstep(0.02, 0.5, max(d.y, 0.0)));
        float sd = max(dot(d, normalize(uSunDir)), 0.0);
        col += uSunColor * pow(sd, 500.0) * 1.6;   // 日轮
        col += uSunColor * pow(sd, 24.0) * 0.10;   // 光晕（收窄，消掉地平线白带）
        col = mix(uHaze, col, smoothstep(-0.08, 0.02, d.y));  // 地平线下雾霭
        // 自定义 ShaderMaterial 不经过 three 的输出色彩空间转换，手动线性→sRGB，
        // 否则天空比走正规管线的雾/地形暗一半，地平线必出接缝带
        col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2));
        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.frustumCulled = false;
  mesh.name = 'sky';
  return mesh;
}
