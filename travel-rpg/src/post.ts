import * as THREE from 'three';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { Pass } from 'three/examples/jsm/postprocessing/Pass.js';
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { FXAAShader } from 'three/examples/jsm/shaders/FXAAShader.js';

/* 用 OutlineEffect 替代 RenderPass 渲染场景（描边 + 主场景一次完成），
   输出进 composer 的线性 HDR 管线：Bloom → OutputPass(ACES+sRGB)
   → 调色(暗角/饱和) → FXAA。 */
class OutlineRenderPass extends Pass {
  constructor(
    private scene: THREE.Scene,
    private camera: THREE.Camera,
    readonly effect: { render: (s: THREE.Scene, c: THREE.Camera) => void },
    private useOutlineRef: { on: boolean },
  ) {
    super();
    this.needsSwap = false;
    this.clear = true;
  }
  render(
    renderer: THREE.WebGLRenderer,
    _writeBuffer: THREE.WebGLRenderTarget,
    readBuffer: THREE.WebGLRenderTarget,
  ): void {
    renderer.setRenderTarget(this.renderToScreen ? null : readBuffer);
    if (this.clear) renderer.clear();
    if (this.useOutlineRef.on) this.effect.render(this.scene, this.camera);
    else renderer.render(this.scene, this.camera);
  }
}

const GradeShader = {
  uniforms: {
    tDiffuse: { value: null as THREE.Texture | null },
    uVig: { value: 0.26 },
    uSat: { value: 1.12 },
  },
  vertexShader: /* glsl */ `
    varying vec2 vUv;
    void main() {
      vUv = uv;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
  `,
  fragmentShader: /* glsl */ `
    uniform sampler2D tDiffuse;
    uniform float uVig;
    uniform float uSat;
    varying vec2 vUv;
    void main() {
      vec4 c = texture2D(tDiffuse, vUv);
      float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
      c.rgb = mix(vec3(l), c.rgb, uSat);
      float d = distance(vUv, vec2(0.5));
      c.rgb *= 1.0 - uVig * smoothstep(0.42, 0.92, d);
      gl_FragColor = c;
    }
  `,
};

export interface Post {
  composer: EffectComposer;
  outlineState: { on: boolean };
  resize: (w: number, h: number, pr: number) => void;
  render: (dt: number) => void;
}

export function createPost(
  renderer: THREE.WebGLRenderer,
  scene: THREE.Scene,
  camera: THREE.Camera,
  effect: { render: (s: THREE.Scene, c: THREE.Camera) => void },
): Post {
  const size = new THREE.Vector2();
  renderer.getDrawingBufferSize(size);
  const rt = new THREE.WebGLRenderTarget(size.x, size.y, {
    type: THREE.HalfFloatType,
    samples: 4,
  });
  const composer = new EffectComposer(renderer, rt);
  const outlineState = { on: true };
  composer.addPass(new OutlineRenderPass(scene, camera, effect, outlineState));
  const bloom = new UnrealBloomPass(new THREE.Vector2(size.x, size.y), 0.32, 0.55, 0.82);
  composer.addPass(bloom);
  composer.addPass(new OutputPass());
  const grade = new ShaderPass(GradeShader);
  composer.addPass(grade);
  const fxaa = new ShaderPass(FXAAShader);
  composer.addPass(fxaa);

  const resize = (w: number, h: number, pr: number) => {
    composer.setSize(w, h);
    bloom.setSize(w, h);
    (fxaa.uniforms.resolution.value as THREE.Vector2).set(1 / (w * pr), 1 / (h * pr));
  };
  resize(window.innerWidth, window.innerHeight, renderer.getPixelRatio());

  return { composer, outlineState, resize, render: dt => composer.render(dt) };
}
