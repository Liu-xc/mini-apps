import * as THREE from 'three';
import { OutlineEffect } from 'three/examples/jsm/effects/OutlineEffect.js';
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader.js';
import { Lensflare, LensflareElement } from 'three/examples/jsm/objects/Lensflare.js';
import { buildTerrain, buildUnderlay, groundHeight } from './terrain';
import { buildScatter, tickScatterWind } from './scatter';
import { buildGrassField } from './grass';
import { buildScenePlacements } from './placer';
import { buildWater } from './water';
import { buildTrail } from './trail';
import { buildCampfire } from './campfire';
import { buildAmbient } from './ambient';
import { buildDust } from './dust';
import { buildMountains } from './mountains';
import { uCloudT } from './cloud';
import { buildBoat } from './canoe';
import { getWorldScene, deriveBlock, STATIONS } from './scenes';
import { initEditor, type EditorApi } from './editor';
import { Player } from './player';
import { CameraRig } from './cameraRig';
import { Input, isTouchMode } from './controls';
import { createPost } from './post';
import { PALETTE, SUN_DIR, FOG_NEAR, FOG_FAR, TIME_PRESETS, applyPaletteToPalette, type TimeId } from './style';

/* 三站选择（it-008）：?scene= 决定出生站，默认达里湖 */
const station = STATIONS.find(st => st.id === new URLSearchParams(location.search).get('scene'))
  ?? STATIONS.find(st => st.id === 'dali')!;

/* ---------- 错误收集（验证钩子 + 页面角标；console.error 一并捕获） ---------- */
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

/* 编辑模式（?edit=1）：it-003 屏内场景编排器 */
const editMode = new URLSearchParams(location.search).has('edit');
const activeScene = getWorldScene();              // 合并世界（it-009）：localStorage world 覆盖 > 三站代码场景
const avoid = deriveBlock(activeScene.placements); // 营地避让区 = 数据派生（单一事实源）

/* ---------- 渲染器 ---------- */
const app = document.getElementById('app')!;
const renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance' });
const rawDpr = window.devicePixelRatio || 1;
const PR = rawDpr >= 1.5 ? Math.min(rawDpr, 2) : 1.5;
renderer.setPixelRatio(PR);
renderer.setSize(innerWidth, innerHeight);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.15;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
app.appendChild(renderer.domElement);
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

/* ---------- 天空与环境光：时段系统（it-007）——day/dawn/sunset 预设切换 ---------- */
let sunBase = 4.1;   // 太阳呼吸的基准强度（随预设变）
const envLoader = new RGBELoader();
let timeId: TimeId = station.time;
{
  const t = new URLSearchParams(location.search).get('time');
  if (t && t in TIME_PRESETS) timeId = t as TimeId;
}

function applyTime(id: TimeId): void {
  timeId = id;
  const p = TIME_PRESETS[id];
  applyPaletteToPalette(p);
  if (scene.fog instanceof THREE.Fog) {
    scene.fog.color.copy(PALETTE.fog);
    scene.fog.near = p.fogNear;
    scene.fog.far = p.fogFar;
  }
  sun.color.copy(PALETTE.sunLight);
  sunBase = p.sunIntensity;
  fill.color.copy(PALETTE.fill);
  hemi.color.set(p.hemiSky);
  hemi.groundColor.set(p.hemiGround);
  scene.environmentIntensity = p.envIntensity;
  scene.backgroundIntensity = p.bgIntensity;
  scene.backgroundRotation.set(0, p.hdriYaw, 0);
  scene.environmentRotation.set(0, p.hdriYaw, 0);
  waterRes.setTime(p);
  grassField.setTime(p);
  post.setTime(p.sat);
  envLoader.load(`${import.meta.env.BASE_URL}textures/${p.hdr}`, hdr => {
    hdr.mapping = THREE.EquirectangularReflectionMapping;
    const oldBg = scene.background as THREE.Texture | null;
    const oldEnv = scene.environment as THREE.Texture | null;
    scene.background = hdr;
    const pmrem = new THREE.PMREMGenerator(renderer);
    scene.environment = pmrem.fromEquirectangular(hdr).texture;
    pmrem.dispose();
    oldBg?.dispose();
    oldEnv?.dispose();
  }, undefined, () => console.warn('[env] HDRI 加载失败: ' + p.hdr));
}
const TIME_ORDER: TimeId[] = ['day', 'dawn', 'sunset'];
window.addEventListener('keydown', e => {
  if (e.code === 'KeyT' && !e.repeat) {
    applyTime(TIME_ORDER[(TIME_ORDER.indexOf(timeId) + 1) % TIME_ORDER.length]);
  }
});

/* ---------- 布光：白昼（蓝影纪律） ---------- */
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
const hemi = new THREE.HemisphereLight(PALETTE.hemiSky, PALETTE.hemiGround, 0.85);
scene.add(hemi);

/* ---------- 世界 ---------- */
scene.add(buildUnderlay());
const mountainGroup = buildMountains();            // 远山剪影环（it-006）
scene.add(mountainGroup);
const terrain = buildTerrain();
scene.add(terrain);
const waterRes = buildWater();                     // 达里湖（it-005）
scene.add(waterRes.mesh);
const scatterGroup = buildScatter(avoid);
scene.add(scatterGroup);
const grassField = buildGrassField(avoid);
scene.add(grassField.group);
const placerRes = buildScenePlacements(activeScene);   // 合并世界实例化（it-009）
scene.add(placerRes.group);
const trailRes = buildTrail();                     // 营地→湖石径（it-005）
scene.add(trailRes.group);
const ambientRes = buildAmbient();                 // 蝴蝶/鸟群（it-005）
scene.add(ambientRes.group);
const dustRes = buildDust();                       // 尘土/涟漪（it-005）
scene.add(dustRes.group);
/* 篝火特效：锚定场景数据里的柴堆位置（编辑器挪柴堆特效跟走） */
const campfirePl = activeScene.placements.find(p => p.asset === 'kenney:campfire_logs');
const campfire = buildCampfire(campfirePl?.x ?? -4.5, campfirePl?.z ?? -7.5);
scene.add(campfire.group);
/* 划船载具（it-008 AC-2）：达里湖站离岸浮位 */
const boat = buildBoat(37.5, -26.5);
scene.add(boat.group);

/* ---------- 光斑 ---------- */
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

/* ---------- 花粉/光尘（it-004 AC-7）：随玩家视野域漂移的暖色微粒 ---------- */
const motes = (() => {
  const N = 70;
  const base = new Float32Array(N * 3);
  const phase = new Float32Array(N);
  for (let i = 0; i < N; i++) {
    const a = (i * 2.399963) % (Math.PI * 2);
    const r = 4 + ((i * 7919) % 100) / 100 * 26;
    base[i * 3] = Math.cos(a) * r;
    base[i * 3 + 1] = 0.4 + ((i * 104729) % 100) / 100 * 6.5;
    base[i * 3 + 2] = Math.sin(a) * r;
    phase[i] = (i * 1.7) % 6.28;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(base.slice(), 3));
  const mat = new THREE.PointsMaterial({
    color: '#fff3cf', size: 0.085, sizeAttenuation: true,
    transparent: true, opacity: 0.5, depthWrite: false,
  });
  mat.userData.outlineParameters = { visible: false };
  const points = new THREE.Points(geo, mat);
  points.frustumCulled = false;
  points.name = 'motes';
  scene.add(points);
  return {
    update(t: number, focus: THREE.Vector3) {
      points.position.set(focus.x, 0, focus.z);
      const attr = geo.getAttribute('position') as THREE.BufferAttribute;
      for (let i = 0; i < N; i++) {
        const b = i * 3;
        attr.setX(i, base[b] + Math.sin(t * 0.32 + phase[i]) * 1.6);
        attr.setY(i, base[b + 1] + Math.sin(t * 0.55 + phase[i] * 1.7) * 0.7);
        attr.setZ(i, base[b + 2] + Math.cos(t * 0.27 + phase[i]) * 1.6);
      }
      attr.needsUpdate = true;
    },
  };
})();

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

/* 站点出生点（it-008 AC-3） */
player.pos.set(station.spawn[0], groundHeight(station.spawn[0], station.spawn[1]) + 0.05, station.spawn[1]);

/* 足迹反馈接线（it-005 AC-8）：尘土 / 涟漪；游泳划水也出涟漪 */
player.onStep = (p, running) => {
  if (player.swimming || player.wading) dustRes.ripple(p.x, p.z, player.swimming ? 1.1 : (running ? 1.35 : 1));
  else dustRes.puff(p.x, p.y, p.z, running ? 1.25 : 1);
};
player.onLand = (p, impact) => {
  if (player.wading) {
    dustRes.ripple(p.x, p.z, 1.7);
  } else {
    dustRes.puff(p.x, p.y, p.z, 1.5);
    dustRes.puff(p.x + 0.3, p.y, p.z - 0.2, 1.1);
  }
  void impact;
};

/* ---------- 后期链 ---------- */
/* 划船上下船（it-008 AC-2）+ 站点循环（AC-3） */
const promptEl = document.getElementById('prompt')!;
function toggleBoat(): void {
  if (!boat || editMode) return;
  if (boat.isRiding()) {
    const out = boat.dismount();
    player.pos.copy(out);
    player.vel.set(0, 0, 0);
    player.group.visible = true;
  } else if (boat.tryMount(player.pos)) {
    player.group.visible = false;
  }
}
window.addEventListener('keydown', e => {
  if (e.repeat) return;
  if (e.code === 'KeyE') toggleBoat();
  if (e.code === 'KeyG' && !editMode) {
    const cur = STATIONS.find(st => st.id === probeStation().id) ?? STATIONS[1];
    const i = STATIONS.indexOf(cur);
    const next = STATIONS[(i + 1) % STATIONS.length];
    if (boat?.isRiding()) toggleBoat();
    player.pos.set(next.spawn[0], groundHeight(next.spawn[0], next.spawn[1]) + 0.05, next.spawn[1]);
    player.vel.set(0, 0, 0);
  }
});
promptEl.addEventListener('pointerdown', e => {
  e.preventDefault();
  toggleBoat();
});
/* 区域徽标：站域 20m 内显示站名，荒野显示荒野（it-009 AC-2） */
const hintEl = document.getElementById('hint')!;
let hintBase = '';
let regionName = station.name;
function nearestStation(): { st: (typeof STATIONS)[number]; dist: number } {
  let best = STATIONS[0], bd = 1e9;
  for (const st of STATIONS) {
    const d = Math.hypot(player.pos.x - st.spawn[0], player.pos.z - st.spawn[1]);
    if (d < bd) { bd = d; best = st; }
  }
  return { st: best, dist: bd };
}
function probeStation() { return nearestStation().st; }
if (!editMode) {
  hintBase = hintEl.textContent!.replace(/^【.*?】/, '');
  hintEl.textContent = `【${regionName}】` + hintBase;
}

const post = createPost(renderer, scene, camera, effect);
applyTime(timeId);   // 首次应用时段预设（须在世界/灯/水体/post 之后）

window.addEventListener('resize', () => {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
  post.resize(innerWidth, innerHeight, renderer.getPixelRatio());
});

/* ---------- 编辑模式接线（?edit=1） ---------- */
let editorApi: EditorApi | null = null;
if (editMode) {
  document.body.classList.add('edit');
  player.group.visible = false;                     // 藏玩家，保留移动=镜头漫游
  document.getElementById('hint')!.textContent =
    '编辑模式 · 点地面放置 · 拖动移动 · R 旋转 +/− 缩放 · Del 删除';
  placerRes.ready.then(placed => {
    initEditor({
      sceneId: 'world',
      scene: activeScene,
      placed,
      group: placerRes.group,
      terrain,
      canvas: renderer.domElement,
      setOrbit: on => { input.orbitEnabled = on; },
    }).then(api => {
      api.setCamera(camera);
      editorApi = api;
      window.__editor = api;
    }).catch(e => errors.push('[editor] init: ' + String(e)));
  });
}

/* ---------- 验证钩子 ---------- */
window.__game = {
  errors,
  scene,   // 评审/调参钩子（时段 HDRI 旋转等现场调试）
  state: () => ({
    pos: [player.pos.x, player.pos.y, player.pos.z].map(v => +v.toFixed(2)),
    onGround: player.onGround,
    model: player.modelSource,
    camYaw: +rig.yaw.toFixed(3),
    camDist: +rig.dist.toFixed(2),
    calls: renderer.info.render.calls,
    tris: renderer.info.render.triangles,
  }),
  tick: (frames: number, dtMs = 16.7) => {
    for (let i = 0; i < frames; i++) tick(dtMs / 1000);
  },
  probe: () => ({
    shadowEnabled: renderer.shadowMap.enabled,
    sunMapReady: !!sun.shadow.map,
    scatterChildren: scatterGroup.children.length,
    grassChildren: grassField.group.children.length,
    placementChildren: placerRes.group.children.length,
    waterReady: !!waterRes.mesh,
    trailDone: trailRes.done.value,
    reedsDone: grassField.reedsDone,
    campfireReady: campfire.group.parent === scene,
    mountainLayers: mountainGroup.children.length,
    time: timeId,
    station: station.id,
    region: regionName,
    swimming: player.swimming,
    boating: boat ? boat.isRiding() : false,
    hasSwimClip: player.clipNames.some(n => /swim|float/i.test(n)),
    clipNames: player.clipNames.slice(0, 80),
    bones: player.boneNames.slice(0, 60),
    outline: post.outlineState.on,
    envReady: !!scene.environment,
    background: !!scene.background,
    fps: lastFps,
    editMode,
    editorReady: editorApi !== null,
  }),
  setOutline: (v: boolean) => { post.outlineState.on = v; },
  /* 评审/截图用：瞬移 + 视角（it-005） */
  tp: (x: number, z: number, yawDeg = 0, pitchDeg = 14) => {
    player.pos.set(x, groundHeight(x, z), z);
    player.vel.set(0, 0, 0);
    rig.yaw = (yawDeg * Math.PI) / 180;
    rig.pitch = (pitchDeg * Math.PI) / 180;
  },
};

/* ---------- 主循环 ---------- */
const _dir = new THREE.Vector3();
const _right = new THREE.Vector3();
const _moveDir = new THREE.Vector3();
let boatRippleT = 0;
let promptState = '';
let last = performance.now();
let elapsed = 0;
let fpsFrames = 0, fpsClock = 0, lastFps = 0;

function tick(dt: number): void {
  elapsed += dt;
  fpsFrames++; fpsClock += dt;
  if (fpsClock >= 0.5) { lastFps = Math.round(fpsFrames / fpsClock); fpsFrames = 0; fpsClock = 0; }
  grassField.update(elapsed, player.pos);
  tickScatterWind(elapsed);
  waterRes.update(elapsed);
  campfire.update(elapsed);
  ambientRes.update(elapsed);
  dustRes.update(dt);
  uCloudT.value = elapsed;                          // 云影时钟（it-006）
  /* 太阳呼吸：慢噪声轻起伏（it-006 AC-4），基准随时段预设（it-007） */
  sun.intensity = sunBase * (0.9 + 0.1 *
    (0.5 + 0.5 * Math.sin(elapsed * 0.11)) * (0.6 + 0.4 * Math.sin(elapsed * 0.043 + 2)));
  motes.update(elapsed, player.pos);
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

  /* 区域徽标刷新 */
  if (!editMode && hintBase) {
    const { st, dist } = nearestStation();
    const rn = dist < 20 ? st.name : '环线荒野';
    if (rn !== regionName) {
      regionName = rn;
      hintEl.textContent = `【${rn}】` + hintBase;
    }
  }

  /* 交互提示条（近船/骑乘） */
  const nearBoat = !!boat && !editMode && player.pos.distanceTo(boat.pos) < 2.6;
  const label = boat?.isRiding() ? 'E 下船' : nearBoat ? 'E 上船' : '';
  if (label !== promptState) {
    promptState = label;
    promptEl.textContent = label;
    promptEl.style.display = label ? 'block' : 'none';
  }

  /* 划船：玩家物理挂起，镜头跟船（it-008 AC-2） */
  if (boat?.isRiding()) {
    boat.update(dt, elapsed, input.move.y, input.move.x);
    boatRippleT -= dt;
    if (Math.abs(boat.speed()) > 0.8 && boatRippleT <= 0) {
      dustRes.ripple(boat.pos.x, boat.pos.z, 1.6);
      boatRippleT = 0.28;
    }
    rig.update(dt, boat.pos, 1.5);   // 骑乘镜头拉远（it-009 AC-3）
    post.render(dt);
    return;
  }

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
      scene: THREE.Scene;
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
        placementChildren: number;
        waterReady: boolean;
        trailDone: boolean;
        reedsDone: boolean;
        campfireReady: boolean;
        mountainLayers: number;
        time: string;
        station: string;
        region: string;
        swimming: boolean;
        boating: boolean;
        hasSwimClip: boolean;
        clipNames: string[];
        bones: string[];
        outline: boolean;
        envReady: boolean;
        background: boolean;
        fps: number;
        editMode: boolean;
        editorReady: boolean;
      };
      setOutline: (v: boolean) => void;
      tp: (x: number, z: number, yawDeg?: number, pitchDeg?: number) => void;
    };
    __editor?: EditorApi;
  }
}
