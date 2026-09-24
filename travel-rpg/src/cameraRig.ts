import * as THREE from 'three';
import { terrainHeight } from './terrain';

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));
const _target = new THREE.Vector3();
const _offset = new THREE.Vector3();
const _desired = new THREE.Vector3();

/* 第三人称弹性跟随镜头：拖拽环视 + 滚轮缩放 + 地形避让 */
export class CameraRig {
  yaw = 0;
  pitch = 0.37;
  dist = 7.5;
  private lookTarget = new THREE.Vector3(0, 1.5, 0);

  constructor(private camera: THREE.PerspectiveCamera) {}

  addLook(dx: number, dy: number): void {
    if (dx === 0 && dy === 0) return;
    this.yaw -= dx * 0.005;
    this.pitch = clamp(this.pitch + dy * 0.005, -0.18, 1.15);
  }

  zoomBy(delta: number): void {
    if (delta !== 0) this.dist = clamp(this.dist + delta * 0.012, 3.5, 14);
  }

  update(dt: number, playerPos: THREE.Vector3): void {
    _target.set(playerPos.x, playerPos.y + 1.5, playerPos.z);
    this.lookTarget.lerp(_target, 1 - Math.exp(-12 * dt));

    const cp = Math.cos(this.pitch);
    _offset
      .set(Math.sin(this.yaw) * cp, Math.sin(this.pitch), Math.cos(this.yaw) * cp)
      .multiplyScalar(this.dist);
    _desired.copy(this.lookTarget).add(_offset);
    this.camera.position.lerp(_desired, 1 - Math.exp(-9 * dt));

    const minY = terrainHeight(this.camera.position.x, this.camera.position.z) + 0.8;
    if (this.camera.position.y < minY) this.camera.position.y = minY;
    this.camera.lookAt(this.lookTarget);
  }

  /* 水平视线方向（移动方向的「前」） */
  getForwardXZ(out: THREE.Vector3): THREE.Vector3 {
    return out.set(-Math.sin(this.yaw), 0, -Math.cos(this.yaw));
  }
}
