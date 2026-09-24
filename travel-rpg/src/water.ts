import * as THREE from 'three';
import { Reflector } from 'three/examples/jsm/objects/Reflector.js';
import { LAKE, terrainHeight } from './terrain';
import { PALETTE, SUN_DIR, FOG_NEAR, FOG_FAR, type TimePreset } from './style';

/* 达里湖水面（it-005 AC-2 / it-012 AC-2 反射重构）：
   Reflector 平面反射（512 RT，倒映远山/树/天空）+ 原水深混色/岸沫/太阳波光
   着色器融合——菲涅尔权重混倒影，波纹法线扰动反射 UV；
   顶点属性 aDepth（CPU 预烤水深）继续驱动深浅混色/岸沫/波幅。 */

const waterShader = {
  name: 'DaliWaterMirror',
  uniforms: {
    uTime: { value: 0 },
    color: { value: null },
    tDiffuse: { value: null },
    textureMatrix: { value: null },
    uSun: { value: SUN_DIR.clone() },
    uSunCol: { value: new THREE.Color('#ffedc9').multiplyScalar(1.15) },
    uSkyCol: { value: new THREE.Color('#7faede') },
    uShallow: { value: new THREE.Color('#4f9a80') },
    uDeep: { value: new THREE.Color('#1d4a60') },
    uFogC: { value: PALETTE.fog },
    uFogN: { value: FOG_NEAR },
    uFogF: { value: FOG_FAR },
    uFresK: { value: 0.42 },
    uReflStr: { value: 0.78 },
  },
  vertexShader: /* glsl */ `
    uniform float uTime;
    uniform mat4 textureMatrix;
    attribute float aDepth;
    varying vec3 vW;
    varying float vDepth;
    varying vec4 vReflUv;
    void main() {
      vDepth = aDepth;
      vec3 p = position;
      float dk = min(aDepth, 1.2);
      p.y += (sin(uTime * 1.4 + position.x * 0.55 + position.z * 0.35)
            + 0.5 * sin(uTime * 2.3 + position.x * 1.3 - position.z * 0.9)) * 0.035 * dk;
      vec4 wp = modelMatrix * vec4(p, 1.0);
      vW = wp.xyz;
      vReflUv = textureMatrix * wp;
      gl_Position = projectionMatrix * viewMatrix * wp;
    }
  `,
  fragmentShader: /* glsl */ `
    uniform float uTime;
    uniform sampler2D tDiffuse;
    uniform vec3 uSun, uSunCol, uSkyCol, uShallow, uDeep, uFogC;
    uniform float uFogN, uFogF, uFresK, uReflStr;
    varying vec3 vW;
    varying float vDepth;
    varying vec4 vReflUv;
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
      /* 倒影：波纹扰动反射 UV，浅水弱深水强 */
      vec4 reflUv = vReflUv;
      reflUv.xy += N.xz * 0.35 * min(vDepth, 1.0);
      vec3 refl = texture2DProj(tDiffuse, reflUv).rgb;
      float fres = pow(1.0 - max(dot(V, N), 0.0), 3.0);
      vec3 col = mix(uShallow, uDeep, smoothstep(0.08, 2.4, vDepth));
      col = mix(col, refl, clamp(uReflStr * (0.35 + 0.65 * fres) * smoothstep(0.04, 0.5, vDepth), 0.0, 1.0));
      col += uSkyCol * fres * uFresK * 0.5;
      /* 太阳波光：收紧成细碎闪光（过宽会成皂渍斑块） */
      vec3 H = normalize(V + normalize(uSun));
      col += uSunCol * pow(max(dot(N, H), 0.0), 320.0) * 1.0;
      /* 岸沫：浅水带 + 噪声破碎 */
      float band = 1.0 - smoothstep(0.03, 0.42, vDepth);
      float fn = wNoise(p * 2.6 + vec2(uTime * 0.4, -uTime * 0.3));
      float foam = smoothstep(0.52, 0.8, band * 0.75 + fn * 0.4);
      col = mix(col, vec3(0.96, 0.98, 0.97), foam);
      float alpha = mix(0.42, 0.92, smoothstep(0.0, 1.4, vDepth));
      alpha = max(alpha, foam * 0.9);
      alpha *= smoothstep(-0.02, 0.14, vDepth);   /* 岸线羽化 */
      float fogF = smoothstep(uFogN, uFogF, distance(cameraPosition, vW));
      col = mix(col, uFogC, fogF);
      gl_FragColor = vec4(col, alpha);
    }
  `,
};

export function buildWater(): { mesh: THREE.Mesh; update: (t: number) => void; setTime: (p: TimePreset) => void } {
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

  const uTime = waterShader.uniforms.uTime;
  const mirror = new Reflector(geo, {
    clipBias: 0.003,
    textureWidth: 512,
    textureHeight: 512,
    color: 0x7f9db0,
    shader: waterShader,
  });
  const mat = mirror.material as THREE.ShaderMaterial;
  mat.transparent = true;
  mat.depthWrite = false;
  mat.userData.outlineParameters = { visible: false };
  mirror.name = 'water';
  mirror.renderOrder = 2;
  mirror.frustumCulled = false;
  mirror.userData.outlineParameters = { visible: false };

  return {
    mesh: mirror as unknown as THREE.Mesh,
    update: t => { uTime.value = t; },
    setTime: p => {
      mat.uniforms.uShallow.value.set(p.waterShallow);
      mat.uniforms.uDeep.value.set(p.waterDeep);
      (mat.uniforms.uSunCol.value as THREE.Color).set('#ffedc9').multiplyScalar(p.waterSunK);
      mat.uniforms.uFresK.value = p.waterFresK;
      mat.uniforms.uFogN.value = p.fogNear;
      mat.uniforms.uFogF.value = p.fogFar;
    },
  };
}
