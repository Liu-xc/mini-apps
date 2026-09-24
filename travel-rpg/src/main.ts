import * as THREE from 'three';
import { OutlineEffect } from 'three/examples/jsm/effects/OutlineEffect.js';
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader.js';
import { buildTerrain, buildUnderlay } from './terrain';
import { buildScatter } from './scatter';
import { buildClouds } from './clouds';
import { buildSky, buildSunGlow, SUN_DIR } from './sky';
import { Player } from './player';
import { CameraRig } from './cameraRig';
import { Input, isTouchMode } from './controls';
import { PALETTE } from './style';

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

/* ---------- 渲染器 / 场景 / 相机 ---------- */
const app = document.getElementById('app')!;
const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
/* 高清策略：高 DPI 用原生 2x；dpr=1 的屏幕超采样 1.5 倍渲染再缩合，边缘更锐 */
const rawDpr = window.devicePixelRatio || 1;
renderer.setPixelRatio(rawDpr >= 1.5 ? Math.min(rawDpr, 2) : 1.5);
renderer.setSize(innerWidth, innerHeight);
renderer.toneMapping = THREE.ACESFilmicToneMapping;   // 电影感分级（天空 shader 同步走此管线）
renderer.toneMappingExposure = 1.2;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
app.appendChild(renderer.domElement);
/* 全场卡通描边（sky/底板/影子/草花显式关闭，见各材质 userData） */
const effect = new OutlineEffect(renderer, {
  defaultThickness: 0.0034,
  defaultColor: [0.1, 0.075, 0.055],
  defaultAlpha: 0.92,
  defaultKeepAlive: true,
});
let useOutline = true;   // 评审探针可切换（__game.setOutline）

const scene = new THREE.Scene();
scene.fog = new THREE.Fog(PALETTE.fog, 70, 195);

const camera = new THREE.PerspectiveCamera(55, innerWidth / innerHeight, 0.1, 900);
camera.position.set(0, 3, 8);

scene.add(buildSky());
scene.add(buildSunGlow());
/* 主光：太阳（投射跟随玩家的局部阴影） */
const sun = new THREE.DirectionalLight(PALETTE.sunLight, 3.2);
sun.castShadow = true;
sun.shadow.mapSize.set(4096, 4096);
sun.shadow.camera.left = -38;
sun.shadow.camera.right = 38;
sun.shadow.camera.top = 38;
sun.shadow.camera.bottom = -38;
sun.shadow.camera.near = 1;
sun.shadow.camera.far = 220;
sun.shadow.bias = -0.0003;
sun.shadow.normalBias = 0.03;
sun.shadow.camera.updateProjectionMatrix();
scene.add(sun);
scene.add(sun.target);
/* 背光补光：来自镜头侧的弱暖光，避免角色背对太阳时黑成剪影 */
const fill = new THREE.DirectionalLight('#ffe8c8', 0.48);
fill.position.set(30, 45, 90);
scene.add(fill);
scene.add(new THREE.HemisphereLight(PALETTE.hemiSky, PALETTE.hemiGround, 0.92));
scene.add(buildUnderlay());
scene.add(buildTerrain());
const scatterGroup = buildScatter();
scene.add(scatterGroup);
const clouds = buildClouds();
scene.add(clouds.group);

/* Poly Haven spruit_sunrise HDRI（CC0）→ PMREM 环境光，给 GLB 材质真实天光 */
new RGBELoader().load(`${import.meta.env.BASE_URL}textures/spruit_sunrise_2k.hdr`, hdr => {
  hdr.mapping = THREE.EquirectangularReflectionMapping;
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromEquirectangular(hdr).texture;
  scene.environmentIntensity = 0.55;
  pmrem.dispose();
  hdr.dispose();
}, undefined, () => console.warn('[env] HDRI 加载失败，沿用布光'));

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

window.addEventListener('resize', () => {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
});

/* ---------- 验证钩子（it-001 浏览器断言用） ---------- */
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
  /* 评审探针：阴影贴图/描边开关/散布加载数 */
  probe: () => ({
    shadowEnabled: renderer.shadowMap.enabled,
    sunMapReady: !!sun.shadow.map,
    sunPos: sun.position.toArray().map(v => +v.toFixed(1)),
    scatterChildren: scatterGroup.children.length,
    outline: useOutline,
    envReady: !!scene.environment,
  }),
  setOutline: (v: boolean) => { useOutline = v; },
};

/* ---------- 主循环 ---------- */
const _dir = new THREE.Vector3();
const _right = new THREE.Vector3();
const _moveDir = new THREE.Vector3();
let last = performance.now();
let elapsed = 0;

function tick(dt: number): void {
  elapsed += dt;
  clouds.update(dt);
  /* 太阳与阴影相机跟随玩家（局部高分辨率阴影） */
  sun.position.copy(player.pos).addScaledVector(SUN_DIR, 90);
  sun.target.position.copy(player.pos);
  sun.target.updateMatrixWorld();
  input.sample();
  const look = input.consumeLook();
  rig.addLook(look.dx, look.dy);
  rig.zoomBy(input.consumeZoom());
  if (input.consumeJump()) player.tryJump();

  rig.getForwardXZ(_dir);                       // 前（水平）
  _right.set(-_dir.z, 0, _dir.x);               // 右 = 前 × 上
  _moveDir
    .set(0, 0, 0)
    .addScaledVector(_right, input.move.x)
    .addScaledVector(_dir, input.move.y);
  if (_moveDir.lengthSq() > 1) _moveDir.normalize();

  player.update(dt, _moveDir, input.run, elapsed);
  rig.update(dt, player.pos);
  if (useOutline) effect.render(scene, camera);
  else renderer.render(scene, camera);
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
        sunPos: number[];
        scatterChildren: number;
        outline: boolean;
        envReady: boolean;
      };
      setOutline: (v: boolean) => void;
    };
  }
}
