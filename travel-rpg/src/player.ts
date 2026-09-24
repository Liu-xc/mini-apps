import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { PLAYER_LIMIT, terrainHeight, groundHeight, LAKE } from './terrain';

const GRAVITY = 22;
const JUMP_V = 8.2;
const WALK_SPEED = 4.2;
const RUN_SPEED = 7.6;
const SWIM_SPEED = 2.6;
const ACCEL = 10;

/* 状态 → KayKit 动画剪辑候选（名字按优先级回退，剪辑缺失时保持当前动作） */
const STATE_ANIMS: Record<string, readonly string[]> = {
  idle: ['idle'],
  walk: ['walking_a', 'walking_b', 'walking', 'walk'],
  run: ['running_a', 'running_b', 'running', 'run'],
  air: ['jump_idle', 'jump'],
  swim: ['swimming', 'swim', 'idle'],   // KayKit 无游泳剪辑：直身 idle 供程序化压平+划臂
};

function blobTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas');
  cv.width = cv.height = 128;
  const c = cv.getContext('2d')!;
  const g = c.createRadialGradient(64, 64, 6, 64, 64, 62);
  g.addColorStop(0, 'rgba(0,0,0,.42)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  c.fillStyle = g;
  c.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(cv);
}

/* 兜底旅人材质（PBR，与 it-002 世界光照一致） */
const std = (color: string) =>
  new THREE.MeshStandardMaterial({ color, roughness: 0.85, metalness: 0 });

/* 角色：运动学跑跳 + 地形贴合 + 动画状态机（M0） */
export class Player {
  readonly group = new THREE.Group();
  readonly pos = new THREE.Vector3(0, 0, 0);
  readonly vel = new THREE.Vector3();
  onGround = true;
  modelSource: 'fallback' | 'knight' = 'fallback';
  yaw = Math.PI;
  wading = false;    // 涉水中（it-005 AC-1）
  swimming = false;  // 游泳中（it-008 AC-1）
  /* 足迹反馈钩子（it-005 AC-8）：dust 层在 main 接线 */
  onStep?: (p: THREE.Vector3, running: boolean) => void;
  onLand?: (p: THREE.Vector3, impact: number) => void;
  private stepAcc = 0;
  private bones: THREE.Bone[] = [];
  private swimPivot: THREE.Group | null = null;   // 髋部枢轴（俯卧用）

  get clipNames(): string[] { return [...this.actions.keys()]; }
  get boneNames(): string[] { return this.bones.map(b => b.name); }

  private visual = new THREE.Group();
  private blob: THREE.Mesh;
  private mixer?: THREE.AnimationMixer;
  private actions = new Map<string, THREE.AnimationAction>();
  private current?: THREE.AnimationAction;
  private state = 'idle';

  constructor(scene: THREE.Scene) {
    this.group.add(this.visual);
    this.blob = new THREE.Mesh(
      new THREE.CircleGeometry(0.55, 24).rotateX(-Math.PI / 2),
      new THREE.MeshBasicMaterial({ map: blobTexture(), transparent: true, depthWrite: false }),
    );
    this.blob.renderOrder = 1;
    this.blob.visible = false;   // 实时阴影接管脚下投影（评审轮 P0 修复）
    (this.blob.material as THREE.MeshBasicMaterial).userData.outlineParameters = { visible: false };
    this.group.add(this.blob);
    this.buildFallback();
    this.group.position.copy(this.pos);
    scene.add(this.group);
    this.loadGltf();
  }

/* 程序化斗笠旅人（glTF 到位前的占位，也是加载失败的兜底） */
private buildFallback(): void {
    const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.26, 0.66, 4, 10), std('#37414f'));
    body.position.y = 0.59;
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.2, 12, 10), std('#e8c9a0'));
    head.position.y = 1.3;
    const hat = new THREE.Mesh(new THREE.ConeGeometry(0.44, 0.24, 10), std('#c9a45f'));
    hat.position.y = 1.5;
    const nose = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.05, 0.16), std('#1c2129'));
    nose.position.set(0, 1.28, 0.2);
    this.visual.add(body, head, hat, nose);
  }

  /* KayKit Adventurers 骑士（CC0，76 段动画）：归一化 + Idle/Walking/Running/Jump 映射 */
  private loadGltf(): void {
    const url = `${import.meta.env.BASE_URL}assets/Knight.glb`;
    new GLTFLoader().load(
      url,
      gltf => {
        const model = gltf.scene;
        const box = new THREE.Box3().setFromObject(model);
        const size = box.getSize(new THREE.Vector3());
        model.scale.setScalar(1.8 / (size.y || 1));
        const box2 = new THREE.Box3().setFromObject(model);
        model.position.y -= box2.min.y;   // 脚底落地
        model.traverse(o => {
          if (o instanceof THREE.Mesh) o.castShadow = true;
        });
        this.visual.clear();
        this.visual.add(model);
        this.modelSource = 'knight';
        this.mixer = new THREE.AnimationMixer(model);
        for (const clip of gltf.animations) {
          this.actions.set(clip.name.toLowerCase(), this.mixer.clipAction(clip));
        }
        this.visual.traverse(o => {
          if (o instanceof THREE.SkinnedMesh) this.bones = o.skeleton.bones;
        });
        /* 髋部枢轴（it-009 游泳俯卧）：模型下移使枢轴位于髋高 */
        const HIP = 0.95;
        const pivot = new THREE.Group();
        pivot.position.y = HIP;
        pivot.add(model);
        model.position.y -= HIP;
        this.visual.add(pivot);
        this.swimPivot = pivot;
        this.playAny(STATE_ANIMS.idle);
      },
      undefined,
      () => {
        // 下载/解析失败 → 保留程序化角色（结果记录在 it-001 验证记录）
        console.warn('[player] Knight.glb 加载失败，使用程序化角色');
      },
    );
  }

  private playAny(names: readonly string[]): boolean {
    for (const name of names) {
      const next = this.actions.get(name);
      if (next && next !== this.current) {
        this.current?.fadeOut(0.22);
        next.reset().fadeIn(0.22).play();
        this.current = next;
        return true;
      }
      if (next) return true;   // 已是当前动作
    }
    return false;
  }

  tryJump(): void {
    if (this.onGround && !this.swimming) {
      this.vel.y = JUMP_V;
      this.onGround = false;
    }
  }

  /* dir：相机相对的水平移动方向（已归一化或为零向量） */
  update(dt: number, dir: THREE.Vector3, run: boolean, elapsed: number): void {
    const wasGround = this.onGround;
    const fallV = this.vel.y;
    const base = this.swimming ? SWIM_SPEED : (run ? RUN_SPEED : WALK_SPEED);
    const speed = base * (this.wading ? 0.55 : 1);
    const moving = dir.lengthSq() > 1e-4;
    const a = 1 - Math.exp(-ACCEL * dt);
    this.vel.x += ((moving ? dir.x * speed : 0) - this.vel.x) * a;
    this.vel.z += ((moving ? dir.z * speed : 0) - this.vel.z) * a;
    if (!this.swimming) this.vel.y -= GRAVITY * dt;

    this.pos.addScaledVector(this.vel, dt);
    this.pos.x = Math.max(-PLAYER_LIMIT, Math.min(PLAYER_LIMIT, this.pos.x));
    this.pos.z = Math.max(-PLAYER_LIMIT, Math.min(PLAYER_LIMIT, this.pos.z));
    const rr = Math.hypot(this.pos.x, this.pos.z);              // 圆形世界边界
    if (rr > 90) {
      this.pos.x *= 90 / rr;
      this.pos.z *= 90 / rr;
    }

    const raw = terrainHeight(this.pos.x, this.pos.z);
    const ground = groundHeight(this.pos.x, this.pos.z);
    this.wading = !this.swimming && ground - raw > 0.15;   // 被水位钳抬起 = 涉水
    const waterDepth = LAKE.level - raw;
    /* 游泳切换（it-008 AC-1）：深水入水（滞回防抖），浅水自动回岸 */
    if (!this.swimming && waterDepth > 1.0) this.swimming = true;
    else if (this.swimming && waterDepth < 0.85) this.swimming = false;

    if (this.swimming) {
      /* 浮在水面：重力停用，位置向水面收敛 */
      this.vel.y = 0;
      const surf = LAKE.level - 0.22;
      this.pos.y += (surf - this.pos.y) * (1 - Math.exp(-6 * dt));
      this.onGround = false;
    } else if (this.pos.y <= ground) {
      this.pos.y = ground;
      if (this.vel.y < 0) this.vel.y = 0;
      this.onGround = true;
    } else {
      this.onGround = false;
    }

    if (moving) {
      const target = Math.atan2(dir.x, dir.z);
      let d = target - this.yaw;
      d = Math.atan2(Math.sin(d), Math.cos(d));   // 最短弧
      this.yaw += d * (1 - Math.exp(-12 * dt));
    }
    this.group.position.copy(this.pos);
    this.group.rotation.y = this.yaw;

    // 脚下软影：跳跃时收缩变淡
    const h = this.pos.y - ground;
    this.blob.position.y = -h + 0.03;
    this.blob.scale.setScalar(Math.max(0.45, 1 - h * 0.09));
    (this.blob.material as THREE.MeshBasicMaterial).opacity =
      Math.max(0.25, 0.9 - h * 0.12);

    const sp = Math.hypot(this.vel.x, this.vel.z);

    /* 足迹事件（it-005 AC-8）：步距累计 + 落地冲击 */
    if (this.onGround) {
      if (!wasGround && fallV < -4.5) this.onLand?.(this.pos, -fallV);
      this.stepAcc += sp * dt;
      if (sp > 0.6 && this.stepAcc > (run ? 2.1 : 1.55)) {
        this.stepAcc = 0;
        this.onStep?.(this.pos, run);
      }
    } else {
      this.stepAcc = 1.2;   // 落地即触达半步
    }

    const st = !this.onGround ? 'air' : sp > 6 ? 'run' : sp > 0.4 ? 'walk' : 'idle';
    if (st !== this.state) {
      this.state = st;
      if (!this.playAny(STATE_ANIMS[st])) this.playAny(STATE_ANIMS.air);   // 无游泳剪辑时优雅回退
    }
    if (this.swimming) {
      this.visual.position.y = -0.55 + Math.sin(elapsed * 2.2) * 0.05;   // 半沉+起伏
      if (this.swimPivot) this.swimPivot.rotation.x = 1.35;              // 俯卧（绕髋，近水平）
    } else {
      this.visual.position.y =
        this.modelSource === 'fallback' && sp > 0.4 && this.onGround
          ? Math.abs(Math.sin(elapsed * 10)) * 0.05 : 0;
      if (this.swimPivot) this.swimPivot.rotation.x = 0;
    }
    this.mixer?.update(dt);
    /* 程序化蛙泳：mixer 写完姿态后覆写臂/腿（it-009 AC-1） */
    if (this.swimming && this.bones.length > 0) {
      const ph = elapsed * 3.6;
      const sweep = Math.sin(ph);
      const byName = (n: string) => this.bones.find(b => b.name === n);
      const armL = byName('upperarml'), armR = byName('upperarmr');
      const legL = byName('upperlegl'), legR = byName('upperlegr');
      if (armL) { armL.rotation.x = -0.6 + sweep * 1.1; armL.rotation.z = 0.55 + sweep * 0.25; }
      if (armR) { armR.rotation.x = -0.6 + sweep * 1.1; armR.rotation.z = -0.55 - sweep * 0.25; }
      const kick = Math.sin(elapsed * 5.2) * 0.4;
      if (legL) legL.rotation.x = kick;
      if (legR) legR.rotation.x = -kick;
    }
  }
}
