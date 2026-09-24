import * as THREE from 'three';
import { OutlineEffect } from 'three/examples/jsm/effects/OutlineEffect.js';
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader.js';
import { Lensflare, LensflareElement } from 'three/examples/jsm/objects/Lensflare.js';
import { buildTerrain, buildUnderlay } from './terrain';
import { buildScatter, CAMP_BLOCK } from './scatter';
import { buildGrassField } from './grass';
import { Player } from './player';
import { CameraRig } from './cameraRig';
import { Input, isTouchMode } from './controls';
import { createPost } from './post';
import { PALETTE, SUN_DIR, FOG_NEAR, FOG_FAR } from './style';

/* ---------- 错误收集（验证钩子 + 页面角标；console.error 一并捕获，shader 编译失败只走 console） ---------- */
const errors: string[] = [];
window.addEventListener('error', e => errors.push(String(e.message)));
window.addEventListener('unhandledrejection', e =>
  errors.push(String((e.reason as Error | undefined)?.message ?? e.reason)),
);
const _origError = console.error.bind(console);
console.error = (...args: unknown[]) => {
  errors.push('[console] ' + args.map(String).join(' ').slice(0, 300));
  _origError(...args);
};
const errEl = document.getElementById('err')!;
setInterval(() => {
  if (errors.length) {
    errEl.style.display = 'block';
    errEl.textContent = errors.join(' | ');
  }
}, 1000);

/* ---------- 渲染器 ---------- */
const app = document.getElementById('app')!;
const renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance' });
/* 高清策略：高 DPI 用原生 2x；dpr=1 的屏幕超采样 1.5 倍渲染（FXAA 兜底锯齿） */
const rawDpr = window.devicePixelRatio || 1;
const PR = rawDpr >= 1.5 ? Math.min(rawDpr, 2) : 1.5;
renderer.setPixelRatio(PR);
renderer.setSize(innerWidth, innerHeight);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.15;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
app.appendChild(renderer.domElement);
/* 全场卡通描边（sky背景/底板/草花 显式关闭，见各材质 userData） */
const effect = new OutlineEffect(renderer, {
  defaultThickness: 0.0035,
  defaultColor: [0.1, 0.075, 0.055],
  defaultAlpha: 0.92,
  defaultKeepAlive: true,
});

/* ---------- 场景 / 相机 / 雾 ---------- */
const scene = new THREE.Scene();
scene.fog = new THREE.Fog(PALETTE.fog, FOG_NEAR, FOG_FAR);
const camera = new THREE.PerspectiveCamera(55, innerWidth / innerHeight, 0.1, 1200);
camera.position.set(0, 3, 8);

/* ---------- 天空与环境光：PolyHaven 纯天空 HDRI（真云 + IBL 同源） ---------- */
new RGBELoader().load(`${import.meta.env.BASE_URL}textures/puresky_2k.hdr`, hdr => {
  hdr.mapping = THREE.EquirectangularReflectionMapping;
  scene.background = hdr;               // 现成资源做天空（含真云），替代手绘天
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromEquirectangular(hdr).texture;
  scene.environmentIntensity = 0.62;
  pmrem.dispose();
  // hdr 留给 background 使用，不 dispose
}, undefined, () => console.warn('[env] 天空 HDRI 加载失败'));

/* ---------- 布光：白昼（太阳 40° + 蓝调半球 + 冷补光 = 蓝影纪律） ---------- */
const sun = new THREE.DirectionalLight(PALETTE.sunLight, 4.1);
sun.castShadow = true;
sun.shadow.mapSize.set(4096, 4096);
sun.shadow.camera.left = -38;
sun.shadow.camera.right = 38;
sun.shadow.camera.top = 38;
sun.shadow.camera.bottom = -38;
sun.shadow.camera.near = 1;
sun.shadow.camera.far = 240;
sun.shadow.bias = -0.0003;
sun.shadow.normalBias = 0.03;
sun.shadow.camera.updateProjectionMatrix();
scene.add(sun);
scene.add(sun.target);
const fill = new THREE.DirectionalLight(PALETTE.fill, 0.3);
fill.position.set(30, 45, 90);
scene.add(fill);
scene.add(new THREE.HemisphereLight(PALETTE.hemiSky, PALETTE.hemiGround, 0.85));

/* ---------- 世界 ---------- */
scene.add(buildUnderlay());
scene.add(buildTerrain());
const scatterGroup = buildScatter();
scene.add(scatterGroup);
const grassField = buildGrassField(CAMP_BLOCK);
scene.add(grassField.group);

/* ---------- 光斑：three 官方 Lensflare（现成纹理，替代手绘辉光） ---------- */
const lensflare = new Lensflare();
new THREE.TextureLoader().load(
  `${import.meta.env.BASE_URL}textures/lensflare0.png`,
  t => lensflare.addElement(new LensflareElement(t, 520, 0, new THREE.Color('#fff6e0'))),
);
new THREE.TextureLoader().load(
  `${import.meta.env.BASE_URL}textures/lensflare3.png`,
  t => lensflare.addElement(new LensflareElement(t, 80, 0.45)),
);
scene.add(lensflare);

/* ---------- 角色 / 镜头 / 输入 ---------- */
const player = new Player(scene);
const rig = new CameraRig(camera);
const touchMode = isTouchMode();
const input = new Input(renderer.domElement, touchMode);

if (touchMode) {
  document.body.classList.add('touch');
  document.getElementById('hint')!.textContent = '左摇杆移动 · 右侧拖拽环视 · 跳 起跳';
  input.attachJoystick(document.getElementById('stick')!);
}
document.getElementById('jump')!.addEventListener('pointerdown', e => {
  e.preventDefault();
  input.jumpQueued = true;
});

/* ---------- 后期链：描边渲染 → Bloom → ACES输出 → 暗角/饱和 → FXAA ---------- */
const post = createPost(renderer, scene, camera, effect);

window.addEventListener('resize', () => {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
  post.resize(innerWidth, innerHeight, renderer.getPixelRatio());
});

/* ---------- 验证钩子（it-001/002 浏览器断言用） ---------- */
window.__game = {
  errors,
  state: () => ({
    pos: [player.pos.x, player.pos.y, player.pos.z].map(v => +v.toFixed(2)),
    onGround: player.onGround,
    model: player.modelSource,
    camYaw: +rig.yaw.toFixed(3),
    camDist: +rig.dist.toFixed(2),
    calls: renderer.info.render.calls,
    tris: renderer.info.render.triangles,
  }),
  /* 同步步进：内嵌浏览器 RAF 不常跑，测试/断言用它驱动游戏循环 */
  tick: (frames: number, dtMs = 16.7) => {
    for (let i = 0; i < frames; i++) tick(dtMs / 1000);
  },
  probe: () => ({
    shadowEnabled: renderer.shadowMap.enabled,
    sunMapReady: !!sun.shadow.map,
    scatterChildren: scatterGroup.children.length,
    grassChildren: grassField.group.children.length,
    outline: post.outlineState.on,
    envReady: !!scene.environment,
    background: !!scene.background,
    fps: lastFps,
  }),
  setOutline: (v: boolean) => { post.outlineState.on = v; },
};

/* ---------- 主循环 ---------- */
const _dir = new THREE.Vector3();
const _right = new THREE.Vector3();
const _moveDir = new THREE.Vector3();
let last = performance.now();
let elapsed = 0;
let fpsFrames = 0, fpsClock = 0, lastFps = 0;

function tick(dt: number): void {
  elapsed += dt;
  fpsFrames++; fpsClock += dt;
  if (fpsClock >= 0.5) { lastFps = Math.round(fpsFrames / fpsClock); fpsFrames = 0; fpsClock = 0; }
  grassField.update(elapsed);
  /* 太阳与阴影相机跟随玩家（局部高分辨率阴影） */
  sun.position.copy(player.pos).addScaledVector(SUN_DIR, 95);
  sun.target.position.copy(player.pos);
  sun.target.updateMatrixWorld();
  lensflare.position.copy(sun.position);
  input.sample();
  const look = input.consumeLook();
  rig.addLook(look.dx, look.dy);
  rig.zoomBy(input.consumeZoom());
  if (input.consumeJump()) player.tryJump();

  rig.getForwardXZ(_dir);
  _right.set(-_dir.z, 0, _dir.x);
  _moveDir.set(0, 0, 0)
    .addScaledVector(_right, input.move.x)
    .addScaledVector(_dir, input.move.y);
  if (_moveDir.lengthSq() > 1) _moveDir.normalize();

  player.update(dt, _moveDir, input.run, elapsed);
  rig.update(dt, player.pos);
  post.render(dt);
}

function frame(now: number): void {
  const dt = Math.min((now - last) / 1000, 0.05);
  last = now;
  tick(dt);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);

declare global {
  interface Window {
    __game?: {
      errors: string[];
      state: () => {
        pos: number[];
        onGround: boolean;
        model: string;
        camYaw: number;
        camDist: number;
        calls: number;
        tris: number;
      };
      tick: (frames: number, dtMs?: number) => void;
      probe: () => {
        shadowEnabled: boolean;
        sunMapReady: boolean;
        scatterChildren: number;
        grassChildren: number;
        outline: boolean;
        envReady: boolean;
        background: boolean;
        fps: number;
      };
      setOutline: (v: boolean) => void;
    };
  }
}
