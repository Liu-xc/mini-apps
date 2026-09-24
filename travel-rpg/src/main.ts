import * as THREE from 'three';
import { buildTerrain, buildUnderlay } from './terrain';
import { buildScatter } from './scatter';
import { buildClouds } from './clouds';
import { buildSky, SUN_DIR } from './sky';
import { Player } from './player';
import { CameraRig } from './cameraRig';
import { Input, isTouchMode } from './controls';
import { PALETTE } from './style';

/* ---------- 错误收集（验证钩子 + 页面角标） ---------- */
const errors: string[] = [];
window.addEventListener('error', e => errors.push(String(e.message)));
window.addEventListener('unhandledrejection', e =>
  errors.push(String((e.reason as Error | undefined)?.message ?? e.reason)),
);
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
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.setSize(innerWidth, innerHeight);
app.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.fog = new THREE.Fog(PALETTE.fog, 60, 165);

const camera = new THREE.PerspectiveCamera(55, innerWidth / innerHeight, 0.1, 900);
camera.position.set(0, 3, 8);

scene.add(buildSky());
const sun = new THREE.DirectionalLight(PALETTE.sunLight, 2.6);
sun.position.copy(SUN_DIR).multiplyScalar(120);
scene.add(sun);
/* 背光补光：来自镜头侧的弱暖光，避免角色背对太阳时黑成剪影 */
const fill = new THREE.DirectionalLight('#ffe8c8', 0.9);
fill.position.set(30, 45, 90);
scene.add(fill);
scene.add(new THREE.HemisphereLight(PALETTE.hemiSky, PALETTE.hemiGround, 1.45));
scene.add(buildUnderlay());
scene.add(buildTerrain());
scene.add(buildScatter());
const clouds = buildClouds();
scene.add(clouds.group);

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
  renderer.render(scene, camera);
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
    };
  }
}
