import * as THREE from 'three';
import { create as createJoystick } from 'nipplejs';

/* 触屏模式：粗指针 / 有触摸点 / ?touch=1 强制（供桌面浏览器自测移动 UI） */
export function isTouchMode(): boolean {
  return (
    matchMedia('(pointer: coarse)').matches ||
    navigator.maxTouchPoints > 0 ||
    new URLSearchParams(location.search).has('touch')
  );
}

/* 输入聚合：键鼠 + 触屏摇杆 → 统一状态 */
export class Input {
  readonly move = new THREE.Vector2();  // 输入空间：x 右 / y 前，幅值 ≤ 1
  run = false;
  jumpQueued = false;

  private lookDX = 0;
  private lookDY = 0;
  private zoomAcc = 0;
  private keys = new Set<string>();
  private dragging = false;
  orbitEnabled = true;   // it-003 编辑器拖动对象时暂停环视
  private lastX = 0;
  private lastY = 0;
  private joyActive = false;

  constructor(canvas: HTMLCanvasElement, private touch: boolean) {
    window.addEventListener('keydown', e => {
      if (e.code === 'Space') {
        if (!e.repeat) this.jumpQueued = true;
        e.preventDefault();
      }
      this.keys.add(e.code);
    });
    window.addEventListener('keyup', e => this.keys.delete(e.code));
    window.addEventListener('blur', () => this.keys.clear());

    canvas.addEventListener('pointerdown', e => {
      // 触屏模式下摇杆区（左下）的触摸不参与环视（保险过滤，正常会被 #stick 拦截）
      if (e.pointerType === 'touch' && this.touch &&
          e.clientX < innerWidth * 0.48 && e.clientY > innerHeight * 0.52) return;
      this.dragging = true;
      this.lastX = e.clientX;
      this.lastY = e.clientY;
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', e => {
      if (!this.dragging) return;
      const dx = e.clientX - this.lastX;
      const dy = e.clientY - this.lastY;
      this.lastX = e.clientX;
      this.lastY = e.clientY;
      if (!this.orbitEnabled) return;
      this.lookDX += dx;
      this.lookDY += dy;
    });
    const end = (e: PointerEvent) => {
      if (!this.dragging) return;
      this.dragging = false;
      try { canvas.releasePointerCapture(e.pointerId); } catch { /* 已释放 */ }
    };
    canvas.addEventListener('pointerup', end);
    canvas.addEventListener('pointercancel', end);
    canvas.addEventListener('wheel', e => {
      e.preventDefault();
      this.zoomAcc += e.deltaY;
    }, { passive: false });
  }

  /* 触屏虚拟摇杆（静态，左下角区域内） */
  attachJoystick(zone: HTMLElement): void {
    const joy = createJoystick({
      zone,
      mode: 'static',
      position: { left: '50%', top: '50%' },
      size: 132,
      color: { front: 'rgba(245,234,210,.85)', back: 'rgba(18,15,12,.35)' },
      restOpacity: 0.5,
      threshold: 0.08,
    });
    joy.on('start', () => { this.joyActive = true; });
    joy.on('move', evt => {
      this.joyActive = true;
      this.move.set(evt.data.vector.x, evt.data.vector.y);
      this.run = evt.data.force > 0.92;   // 推满 = 跑
    });
    joy.on('end', () => {
      this.joyActive = false;
      this.move.set(0, 0);
      this.run = false;
    });
  }

  /* 每帧采样键盘（摇杆激活时以摇杆为准） */
  sample(): void {
    if (this.joyActive) return;
    const k = this.keys;
    const x = (k.has('KeyD') || k.has('ArrowRight') ? 1 : 0) -
              (k.has('KeyA') || k.has('ArrowLeft') ? 1 : 0);
    const y = (k.has('KeyW') || k.has('ArrowUp') ? 1 : 0) -
              (k.has('KeyS') || k.has('ArrowDown') ? 1 : 0);
    this.move.set(x, y);
    if (this.move.lengthSq() > 1) this.move.normalize();
    this.run = k.has('ShiftLeft') || k.has('ShiftRight');
  }

  consumeLook(): { dx: number; dy: number } {
    const r = { dx: this.lookDX, dy: this.lookDY };
    this.lookDX = 0;
    this.lookDY = 0;
    return r;
  }

  consumeZoom(): number {
    const z = this.zoomAcc;
    this.zoomAcc = 0;
    return z;
  }

  consumeJump(): boolean {
    const j = this.jumpQueued;
    this.jumpQueued = false;
    return j;
  }
}
