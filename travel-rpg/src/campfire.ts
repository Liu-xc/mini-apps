import * as THREE from 'three';
import { terrainHeight } from './terrain';

/* 营地篝火（it-005 AC-6）：全程序化——噪声上升流火焰（交叉双面片、加法混合）、
   GPU 循环烟羽（Points，顶点算生命周期）、火星、随机闪烁点光（不投影保性能）。 */

export interface Campfire { group: THREE.Group; update: (t: number) => void; }

const flameFrag = /* glsl */ `
  uniform float uTime;
  varying vec2 vUv;
  float fHash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float fNoise(vec2 p){
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(fHash(i), fHash(i + vec2(1.0, 0.0)), f.x),
               mix(fHash(i + vec2(0.0, 1.0)), fHash(i + vec2(1.0, 1.0)), f.x), f.y);
  }
  void main() {
    vec2 uv = vUv;                     // 0..1，底边贴柴堆
    float t = uTime;
    float wind = (fNoise(vec2(uv.x * 2.5, uv.y * 2.0 - t * 2.6)) - 0.5)
               * 0.55 * (0.4 + uv.y);
    float cx = 0.5 + wind;
    float w = mix(0.36, 0.04, smoothstep(0.0, 1.0, uv.y));
    float body = smoothstep(w, w * 0.3, abs(uv.x - cx)) * smoothstep(1.02, 0.7, uv.y);
    float core = smoothstep(w * 0.55, w * 0.1, abs(uv.x - cx))
               * smoothstep(0.85, 0.3, uv.y);
    /* 白昼火焰：NormalBlending 高不透明度 + 高饱和，避免加法混合被亮背景洗白 */
    vec3 col = mix(vec3(0.82, 0.22, 0.02), vec3(0.92, 0.46, 0.05), body);
    col = mix(col, vec3(1.0, 0.82, 0.36), core);
    float a = body * (0.88 + 0.12 * sin(t * 17.0 + uv.y * 9.0));
    gl_FragColor = vec4(col * 0.88, a);
  }
`;

/* 烟羽/火星共用：顶点里 fract(uTime*速度+种子) 算生命周期，零 CPU 粒子 */
function risePoints(opts: {
  n: number; speed: number; rise: number; size: number;
  drift: number; color: THREE.Color; blending: THREE.Blending;
  fragAlpha: string;
}): THREE.Points {
  const seeds = new Float32Array(opts.n);
  for (let i = 0; i < opts.n; i++) seeds[i] = (i * 0.618 + 0.11) % 1;
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(opts.n * 3), 3));
  geo.setAttribute('aSeed', new THREE.BufferAttribute(seeds, 1));
  const mat = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    blending: opts.blending,
    uniforms: {
      uTime: { value: 0 },
      uColor: { value: opts.color },
      uPx: { value: 620 },   // 点尺寸换算（≈ 0.5*height/tan(fov/2)*dpr，resize 误差可接受）
    },
    vertexShader: /* glsl */ `
      uniform float uTime;
      uniform float uPx;
      attribute float aSeed;
      varying float vLife;
      void main() {
        float life = fract(uTime * ${opts.speed.toFixed(3)} + aSeed);
        vLife = life;
        vec2 sway = vec2(sin(aSeed * 40.3), cos(aSeed * 57.1)) * ${opts.drift.toFixed(3)} * life;
        vec3 p = vec3(
          sway.x + sin(uTime * 0.9 + aSeed * 31.0) * 0.1 * life,
          0.35 + life * ${opts.rise.toFixed(3)},
          sway.y + cos(uTime * 0.8 + aSeed * 23.0) * 0.08 * life);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        float size = mix(${(opts.size * 0.4).toFixed(3)}, ${opts.size.toFixed(3)}, life);
        gl_PointSize = size * uPx / max(-mv.z, 0.6);
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: /* glsl */ `
      uniform vec3 uColor;
      varying float vLife;
      void main() {
        float d = length(gl_PointCoord - 0.5);
        ${opts.fragAlpha}
      }
    `,
  });
  mat.userData.outlineParameters = { visible: false };
  const pts = new THREE.Points(geo, mat);
  pts.frustumCulled = false;
  pts.renderOrder = 3;
  return pts;
}

export function buildCampfire(x: number, z: number): Campfire {
  const group = new THREE.Group();
  group.name = 'campfireFx';
  group.position.set(x, terrainHeight(x, z) + 0.04, z);

  /* 火焰：交叉双面片（不透明混合，白昼可读） */
  const flameGeo = new THREE.PlaneGeometry(1.1, 1.5);
  flameGeo.translate(0, 0.7, 0);
  const flameMat = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    side: THREE.DoubleSide,
    uniforms: { uTime: { value: 0 } },
    vertexShader: `varying vec2 vUv;
      void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: flameFrag,
  });
  flameMat.userData.outlineParameters = { visible: false };
  const flame = new THREE.Group();
  const f1 = new THREE.Mesh(flameGeo, flameMat);
  const f2 = new THREE.Mesh(flameGeo, flameMat);
  f2.rotation.y = Math.PI / 2;
  flame.add(f1, f2);
  flame.position.y = 0.16;
  group.add(flame);

  /* 烟羽：灰白软粒子，正弦包络淡入淡出 */
  const smoke = risePoints({
    n: 18, speed: 0.14, rise: 3.4, size: 2.7, drift: 0.34,
    color: new THREE.Color('#8d8478'), blending: THREE.NormalBlending,
    fragAlpha: [
      'float a = smoothstep(0.5, 0.12, d) * sin(vLife * 3.14159) * 0.27;',
      'gl_FragColor = vec4(uColor, a);',
    ].join('\n'),
  });
  smoke.position.y = 0.9;
  group.add(smoke);

  /* 火星：亮橙小点快速上升，加法混合 + 闪烁 */
  const embers = risePoints({
    n: 22, speed: 0.5, rise: 2.4, size: 0.09, drift: 0.45,
    color: new THREE.Color('#ffb066'), blending: THREE.AdditiveBlending,
    fragAlpha: [
      'float tw = 0.6 + 0.4 * sin(vLife * 47.0);',
      'float a = smoothstep(0.5, 0.05, d) * (1.0 - vLife) * tw;',
      'gl_FragColor = vec4(uColor, a);',
    ].join('\n'),
  });
  group.add(embers);

  /* 闪烁点光 */
  const light = new THREE.PointLight("#ff9d4d", 21, 14, 2);
  light.position.y = 0.9;
  light.castShadow = false;
  group.add(light);

  return {
    group,
    update(t: number) {
      flameMat.uniforms.uTime.value = t;
      (smoke.material as THREE.ShaderMaterial).uniforms.uTime.value = t;
      (embers.material as THREE.ShaderMaterial).uniforms.uTime.value = t;
      flame.scale.setScalar(1 + 0.06 * Math.sin(t * 13) + 0.04 * Math.sin(t * 29 + 2));
      light.intensity = 21 + Math.sin(t * 11.3) * 4 + Math.sin(t * 23.7 + 1.7) * 3;
    },
  };
}
