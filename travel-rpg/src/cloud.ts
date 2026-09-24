/* 云影（it-006 AC-3）：共享时间 uniform + 可注入的片元 GLSL 片段。
   大尺度值噪声斑沿风向漂移，材质在光照前乘暗——
   覆盖地形与草场（近圈/远圈），散布树/道具不参与（风格化取舍）。 */

export const uCloudT = { value: 0 };

export const CLOUD_GLSL = /* glsl */ `
  float cHash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float cNoise(vec2 p){
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(cHash(i), cHash(i + vec2(1.0, 0.0)), f.x),
               mix(cHash(i + vec2(0.0, 1.0)), cHash(i + vec2(1.0, 1.0)), f.x), f.y);
  }
  float cloudField(vec2 p, float t){
    float n = cNoise(p * 0.013 + vec2(t * 0.020, t * 0.007)) * 0.65
            + cNoise(p * 0.047 - vec2(t * 0.011, t * 0.005)) * 0.35;
    return smoothstep(0.56, 0.80, n);
  }
  vec3 cloudShade(vec3 col, vec2 w, float t){
    return col * (1.0 - cloudField(w, t) * 0.34);
  }
`;
