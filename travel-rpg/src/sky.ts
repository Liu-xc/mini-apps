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
      uExposure: { value: 1.2 },   // 与 main.ts renderer.toneMappingExposure 保持一致
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
      uniform float uExposure;
      // three ACES Filmic 同款曲线（手写，避免 ShaderMaterial 的 tonemapping include
      // 编译失败导致整片黑天——评审轮实测踩过）
      vec3 acesFilmic(vec3 color) {
        color *= uExposure;
        const mat3 mIn = mat3(
          0.59719, 0.07600, 0.02840,
          0.35458, 0.90834, 0.13383,
          0.04823, 0.01566, 0.83777);
        const mat3 mOut = mat3(
           1.60475, -0.10208, -0.00327,
          -0.53108,  1.10813, -0.07276,
          -0.07367, -0.00605,  1.07602);
        vec3 v = mIn * color;
        vec3 a = v * (v + 0.0245786) - 0.000090537;
        vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
        return clamp(mOut * (a / b), 0.0, 1.0);
      }
      void main() {
        vec3 d = normalize(vDir);
        vec3 col = mix(uHorizon, uTop, smoothstep(0.05, 0.55, max(d.y, 0.0)));
        float sd = max(dot(d, normalize(uSunDir)), 0.0);
        col += uSunColor * pow(sd, 500.0) * 1.6;   // 日轮
        col += uSunColor * pow(sd, 24.0) * 0.10;   // 光晕（收窄，消掉地平线白带）
        // 低空暖抬升：让天空低区与地面雾色 (238,215,170) 对齐，消掉亮带（评审轮实测值）
        col += vec3(0.10, 0.065, 0.030) * smoothstep(0.14, 0.0, max(d.y, 0.0));
        col = mix(uHaze, col, smoothstep(-0.08, 0.02, d.y));  // 地平线下雾霭
        col = acesFilmic(col);
        // 线性 → sRGB（与主管线 colorspace_fragment 同款精确曲线）
        col = mix(col * 12.92,
                  1.055 * pow(max(col, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055,
                  step(vec3(0.0031308), col));
        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.frustumCulled = false;
  mesh.name = 'sky';
  mat.userData.outlineParameters = { visible: false };
  return mesh;
}

/* 太阳光斑：大颗 additive 辉光，撑起「黄金时刻」的主角 */
export function buildSunGlow(): THREE.Sprite {
  const cv = document.createElement('canvas');
  cv.width = cv.height = 256;
  const c = cv.getContext('2d')!;
  const g = c.createRadialGradient(128, 128, 8, 128, 128, 126);
  g.addColorStop(0, 'rgba(255,248,224,.95)');
  g.addColorStop(0.22, 'rgba(255,232,178,.55)');
  g.addColorStop(0.55, 'rgba(255,208,140,.18)');
  g.addColorStop(1, 'rgba(255,200,130,0)');
  c.fillStyle = g;
  c.fillRect(0, 0, 256, 256);
  const tex = new THREE.CanvasTexture(cv);
  tex.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({
    map: tex,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    fog: false,
    opacity: 0.9,
  }));
  sp.scale.setScalar(170);
  sp.position.copy(SUN_DIR).multiplyScalar(430);
  sp.material.userData.outlineParameters = { visible: false };
  sp.name = 'sunGlow';
  return sp;
}
