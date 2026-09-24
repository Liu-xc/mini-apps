import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight, LAKE } from './terrain';

/* 划船（it-008 AC-2）：canoe 载具——上船后 W/S 推进、A/D 转向、水中拖拽减速，
   浅水/岸线阻挡（碰岸回弹），波浪起伏+艏摇。下船落在船侧，衔接游泳/涉水。 */

const THRUST_ACC = 5.5;
const MAX_FWD = 4.4;
const MAX_REV = 1.6;
const TURN_RATE = 1.9;

export interface Boat {
  group: THREE.Group;
  pos: THREE.Vector3;
  yaw: number;
  isRiding: () => boolean;
  update: (dt: number, elapsed: number, thrust: number, turn: number) => void;
  tryMount: (playerPos: THREE.Vector3) => boolean;
  dismount: () => THREE.Vector3;
  speed: () => number;
}

export function buildBoat(x: number, z: number): Boat {
  const group = new THREE.Group();
  group.name = 'boat';
  const pos = new THREE.Vector3(x, 0, z);
  let yaw = 2.18;   // 艏朝湖心
  let speed = 0;
  let riding = false;
  const api: Boat = {
    group,
    pos,
    yaw,
    isRiding: () => riding,
    update: () => {},
    tryMount: () => false,
    dismount: () => pos.clone(),
    speed: () => speed,
  };

  new GLTFLoader().load(`${import.meta.env.BASE_URL}assets/nature/canoe.glb`, gltf => {
    const model = gltf.scene;
    model.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(model);
    const size = box.getSize(new THREE.Vector3());
    /* 船按长度归一化到 2.6m（高度归一化对扁平船体会爆比例） */
    const len = Math.max(size.x, size.z, 0.001);
    model.scale.setScalar(2.6 / len);
    if (size.x > size.z) model.rotation.y = Math.PI / 2;   // 长轴对齐艏向(+z)
    model.position.y -= box.min.y * (2.6 / len);
    group.add(model);
    settle();
  }, undefined, () => console.warn('[boat] canoe.glb 加载失败'));

  function settle(): void {
    const raw = terrainHeight(pos.x, pos.z);
    const floating = raw < LAKE.level - 0.15;
    pos.y = floating ? LAKE.level - 0.04 : raw + 0.02;
    group.position.copy(pos);
    group.rotation.set(0, yaw, 0);
  }
  settle();

  api.update = (dt: number, elapsed: number, thrust: number, turn: number) => {
    if (!riding) return;
    /* 推进 + 拖拽（松桨多衰减，倒桨限低速） */
    speed += thrust * THRUST_ACC * dt;
    const drag = Math.abs(thrust) < 0.1 ? 2.2 : 0.9;
    speed *= Math.exp(-drag * dt);
    speed = Math.max(-MAX_REV, Math.min(MAX_FWD, speed));
    /* 转向效率随航速 */
    yaw += turn * TURN_RATE * dt * Math.max(0.9, 0.35 + Math.min(Math.abs(speed) / 3, 1));
    const fwd = new THREE.Vector3(Math.sin(yaw), 0, Math.cos(yaw));
    const nx = pos.x + fwd.x * speed * dt;
    const nz = pos.z + fwd.z * speed * dt;
    /* 水域边界：漂浮时岸线阻挡；搁浅时只准向更深方向推离 */
    const cur = terrainHeight(pos.x, pos.z);
    if (cur < LAKE.level - 0.15) {
      if (terrainHeight(nx, nz) < LAKE.level - 0.15) {
        pos.x = nx;
        pos.z = nz;
      } else {
        speed *= -0.15;   // 碰岸
      }
    } else if (terrainHeight(nx, nz) <= cur + 0.05) {
      pos.x = nx;
      pos.z = nz;
      speed *= Math.exp(-2.2 * dt);   // 滩上推涉/沿岸挪（按 dt 衰减）
    } else {
      speed *= -0.15;
    }
    /* 浮态：水位 + 波浪起伏 + 艏摇侧倾（搁浅时坐在滩上） */
    const bob = Math.sin(elapsed * 1.9) * 0.045;
    pos.y = Math.max(LAKE.level - 0.04, terrainHeight(pos.x, pos.z) + 0.02) + bob;
    group.position.copy(pos);
    group.rotation.set(
      Math.min(Math.abs(speed) * 0.03, 0.08) * (thrust >= 0 ? 1 : -1) + Math.sin(elapsed * 1.3) * 0.012,
      yaw,
      turn * 0.06 * Math.min(Math.abs(speed), 1.5) + Math.sin(elapsed * 1.7) * 0.015,
    );
  };

  api.tryMount = playerPos => {
    if (riding) return false;
    if (playerPos.distanceTo(pos) > 2.6) return false;
    riding = true;
    speed = 0;
    return true;
  };

  api.dismount = () => {
    riding = false;
    speed = 0;
    /* 下船点：船侧 1.1m（落水即游泳/涉水状态接管） */
    const side = new THREE.Vector3(Math.cos(yaw), 0, -Math.sin(yaw));
    return pos.clone().addScaledVector(side, 1.1);
  };

  return api;
}
