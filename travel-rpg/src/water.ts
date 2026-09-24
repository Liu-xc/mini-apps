import * as THREE from 'three';
import { LAKE, terrainHeight } from './terrain';
import { PALETTE, SUN_DIR, FOG_NEAR, FOG_FAR } from './style';

/* 达里湖水面（it-005 AC-2）：单 Mesh 自定义着色器。
   顶点属性 aDepth = 水位 - 地形（CPU 预烤），驱动深浅混色/岸沫/波浪幅度；
   法线由三层滚动值噪声差分得到，太阳波光走 Bloom 阈值上的高光；
   岸线 alpha 羽化 + 场景雾对齐（与远圈草卡同式）。 */

export function buildWater(): { mesh: THREE.Mesh; update: (t: number) => void } {
  const R = LAKE.r * 1.35;
  const seg = 110;
  const geo = new THREE.PlaneGeometry(R * 2, R * 2, seg, seg).rotateX(-Math.PI / 2);
  geo.translate(LAKE.x, LAKE.level, LAKE.z);
  const pos = geo.attributes.position;
  const depth = new Float32Array(pos.count);
  for (let i = 0; i < pos.count; i++) {
    depth[i] = Math.max(0, LAKE.level - terrainHeight(pos.getX(i), pos.getZ(i)));
  }
  geo.setAttribute('aDepth', new THREE.BufferAttribute(depth, 1));

  const uTime = { value: 0 };
  const mat = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    uniforms: {
      uTime,
      uSun: { value: SUN_DIR.clone() },
      uSunCol: { value: new THREE.Color('#ffedc9').multiplyScalar(1.15) },
      uSkyCol: { value: new THREE.Color('#7faede') },
      uShallow: { value: new THREE.Color('#4f9a80') },
      uDeep: { value: new THREE.Color('#1d4a60') },
      uFogC: { value: PALETTE.fog },
      uFogN: { value: FOG_NEAR },
      uFogF: { value: FOG_FAR },
    },
    vertexShader: /* glsl */ `
      uniform float uTime;
      attribute float aDepth;
      varying vec3 vW;
      varying float vDepth;
      void main() {
        vDepth = aDepth;
        vec3 p = position;
        float dk = min(aDepth, 1.2);
        p.y += (sin(uTime * 1.4 + position.x * 0.55 + position.z * 0.35)
              + 0.5 * sin(uTime * 2.3 + position.x * 1.3 - position.z * 0.9)) * 0.035 * dk;
        vec4 wp = modelMatrix * vec4(p, 1.0);
        vW = wp.xyz;
        gl_Position = projectionMatrix * viewMatrix * wp;
      }
    `,
    fragmentShader: /* glsl */ `
      uniform float uTime;
      uniform vec3 uSun, uSunCol, uSkyCol, uShallow, uDeep, uFogC;
      uniform float uFogN, uFogF;
      varying vec3 vW;
      varying float vDepth;
      float wHash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
      float wNoise(vec2 p){
        vec2 i = floor(p), f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(wHash(i), wHash(i + vec2(1.0, 0.0)), f.x),
                   mix(wHash(i + vec2(0.0, 1.0)), wHash(i + vec2(1.0, 1.0)), f.x), f.y);
      }
      float wHeight(vec2 p){
        float t = uTime;
        return wNoise(p * 0.55 + vec2(t * 0.32, t * 0.21)) * 0.6
             + wNoise(p * 1.40 - vec2(t * 0.23, t * 0.31)) * 0.3
             + wNoise(p * 3.10 + vec2(t * 0.50, -t * 0.40)) * 0.1;
      }
      void main() {
        vec2 p = vW.xz;
        float e = 0.22;
        float hC = wHeight(p);
        vec3 N = normalize(vec3(hC - wHeight(p + vec2(e, 0.0)), 0.28,
                                hC - wHeight(p + vec2(0.0, e))));
        vec3 V = normalize(cameraPosition - vW);
        float fres = pow(1.0 - max(dot(V, N), 0.0), 3.0);
        vec3 col = mix(uShallow, uDeep, smoothstep(0.08, 2.4, vDepth));
        col = mix(col, uSkyCol, fres * 0.42);
        /* 太阳波光：收紧成细碎闪光（过宽会成皂渍斑块） */
        vec3 H = normalize(V + normalize(uSun));
        col += uSunCol * pow(max(dot(N, H), 0.0), 320.0) * 1.0;
        /* 岸沫：浅水带 + 噪声破碎 */
        float band = 1.0 - smoothstep(0.03, 0.42, vDepth);
        float fn = wNoise(p * 2.6 + vec2(uTime * 0.4, -uTime * 0.3));
        float foam = smoothstep(0.52, 0.8, band * 0.75 + fn * 0.4);
        col = mix(col, vec3(0.96, 0.98, 0.97), foam);
        float alpha = mix(0.42, 0.88, smoothstep(0.0, 1.4, vDepth));
        alpha = max(alpha, foam * 0.9);
        alpha *= smoothstep(-0.02, 0.14, vDepth);   /* 岸线羽化 */
        float fogF = smoothstep(uFogN, uFogF, distance(cameraPosition, vW));
        col = mix(col, uFogC, fogF);
        gl_FragColor = vec4(col, alpha);
      }
    `,
  });
  mat.userData.outlineParameters = { visible: false };

  const mesh = new THREE.Mesh(geo, mat);
  mesh.name = 'water';
  mesh.renderOrder = 2;
  mesh.frustumCulled = false;
  mesh.userData.outlineParameters = { visible: false };
  return { mesh, update: t => { uTime.value = t; } };
}
