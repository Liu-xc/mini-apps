import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { terrainHeight } from './terrain';
import { saveScene, clearSceneOverride, type Placement, type SceneDef } from './scenes';
import type { PlacedObject } from './placer';

/* 屏内场景编辑器（it-003 AC-4，入口 ?edit=1）
   能力：分类资产面板 → 点地放置 → 拖拽移动 → 点选 → R旋转 / +-缩放 / Del删除
        → localStorage 即时持久化 → 导出 SceneDef JSON（复制+下载） */

interface CatEntry { id: string; name: string; category: string; file: string; }

export interface EditorApi {
  place: (asset: string, x: number, z: number, yaw?: number, scale?: number) => boolean;
  count: () => number;
  selected: () => string | null;
  exportJson: () => string;
  setCamera: (c: THREE.PerspectiveCamera) => void;
  state: () => { count: number; asset: string | null; selected: string | null };
}

const CAT_LABEL: Record<string, string> = {
  tree: '树', rock: '岩石', foliage: '草木', flower: '花',
  mushroom: '蘑菇', prop: '道具', rockpath: '石径',
};
const MODEL_CATS = ['tree', 'rock', 'foliage', 'flower', 'mushroom', 'prop'];

export async function initEditor(opts: {
  sceneId: string;
  scene: SceneDef;
  placed: PlacedObject[];
  group: THREE.Group;
  terrain: THREE.Mesh;
  canvas: HTMLCanvasElement;
  setOrbit: (on: boolean) => void;
}): Promise<EditorApi> {
  const { sceneId, scene, group, terrain, canvas } = opts;
  const entries: CatEntry[] = await fetch(`${import.meta.env.BASE_URL}assets/catalog.json`)
    .then(r => r.json())
    .then(j => (j.entries as CatEntry[]).filter(e =>
      MODEL_CATS.includes(e.category) &&
      (e.id.startsWith('quaternius:') || e.id.startsWith('kenney:') || e.id.startsWith('polyhaven:')) &&
      e.id !== 'kaykit:Knight'));

  const placed = opts.placed;
  const loader = new GLTFLoader();
  const tplCache = new Map<string, Promise<THREE.Group | null>>();
  const loadTpl = (file: string): Promise<THREE.Group | null> => {
    let p = tplCache.get(file);
    if (!p) {
      p = new Promise(res => loader.load(`${import.meta.env.BASE_URL}${file}`,
        g => res(g.scene), undefined, () => { console.warn('[editor] 加载失败', file); res(null); }));
      tplCache.set(file, p);
    }
    return p;
  };

  let activeAsset: string | null = null;
  let selected: PlacedObject | null = null;

  /* ---------- 持久化 & 导出 ---------- */
  const serialize = (): Placement[] => placed.map(p => ({
    asset: p.asset, x: +p.obj.position.x.toFixed(2), z: +p.obj.position.z.toFixed(2),
    yaw: +p.yaw.toFixed(3), scale: +p.scale.toFixed(2),
  }));
  const persist = () => saveScene(sceneId, serialize());
  const exportJson = (): string =>
    JSON.stringify({ id: sceneId, name: scene.name, placements: serialize() }, null, 2);

  /* ---------- 放置 ---------- */
  const spawn = async (asset: string, x: number, z: number, yaw: number, scale: number): Promise<boolean> => {
    const entry = entries.find(e => e.id === asset);
    if (!entry) return false;
    const tpl = await loadTpl(entry.file);
    if (!tpl) return false;
    const obj = tpl.clone(true);
    obj.updateMatrixWorld(true);
    const b0 = new THREE.Box3().setFromObject(obj);
    const h0 = Math.max(b0.max.y - b0.min.y, 0.001);
    obj.scale.setScalar(scale / h0);
    obj.rotation.y = yaw;
    obj.updateMatrixWorld(true);
    const b1 = new THREE.Box3().setFromObject(obj);
    const yOff = -b1.min.y;                       // 相对地面的固定抬升（随 scale 缩放）
    obj.position.set(x, terrainHeight(x, z) + yOff, z);
    obj.traverse(o => { if (o instanceof THREE.Mesh) { o.castShadow = true; o.receiveShadow = true; } });
    group.add(obj);
    const po: PlacedObject = { asset, obj, yaw, scale };
    placed.push(po);
    persist();
    updateHud();
    return true;
  };

  /* ---------- 面板 UI ---------- */
  const rootEl = document.getElementById('editor')!;
  rootEl.style.display = 'block';
  const byCat = new Map<string, CatEntry[]>();
  for (const e of entries) {
    if (!byCat.has(e.category)) byCat.set(e.category, []);
    byCat.get(e.category)!.push(e);
  }
  let curCat = 'tree';
  rootEl.innerHTML = `
    <div class="ed-head">场景编辑器 <span class="ed-count"></span></div>
    <div class="ed-cats"></div>
    <div class="ed-list"></div>
    <div class="ed-bar">
      <button class="ed-btn" data-act="export">导出 JSON</button>
      <button class="ed-btn" data-act="reset">重置默认</button>
    </div>
    <div class="ed-hint">点地面放置 · 拖动已放物体移动 · 点选后 R 旋转 / +− 缩放 / Del 删除 · 空白处拖动环视</div>
    <div class="ed-toast"></div>`;
  const catsEl = rootEl.querySelector('.ed-cats') as HTMLElement;
  const listEl = rootEl.querySelector('.ed-list') as HTMLElement;
  const countEl = rootEl.querySelector('.ed-count') as HTMLElement;
  const toastEl = rootEl.querySelector('.ed-toast') as HTMLElement;
  const renderCats = () => {
    catsEl.innerHTML = '';
    for (const cat of MODEL_CATS) {
      if (!byCat.has(cat)) continue;
      const b = document.createElement('button');
      b.className = 'ed-cat' + (cat === curCat ? ' on' : '');
      b.textContent = CAT_LABEL[cat] ?? cat;
      b.onclick = () => { curCat = cat; renderCats(); renderList(); };
      catsEl.appendChild(b);
    }
  };
  const renderList = () => {
    listEl.innerHTML = '';
    for (const e of byCat.get(curCat) ?? []) {
      const b = document.createElement('button');
      b.className = 'ed-item' + (activeAsset === e.id ? ' on' : '');
      b.textContent = e.name.replace(/_/g, ' ');
      b.title = e.id;
      b.onclick = () => {
        activeAsset = activeAsset === e.id ? null : e.id;
        selected = null;
        renderList(); updateHud();
        toast(activeAsset ? '点击地面放置：' + e.name : '已取消放置');
      };
      listEl.appendChild(b);
    }
  };
  const toast = (msg: string) => {
    toastEl.textContent = msg;
    toastEl.classList.add('show');
    setTimeout(() => toastEl.classList.remove('show'), 1600);
  };
  const updateHud = () => {
    countEl.textContent = `· ${placed.length} 件`;
    renderList();
  };
  rootEl.querySelector('[data-act=export]')!.addEventListener('click', async () => {
    const json = exportJson();
    try { await navigator.clipboard.writeText(json); toast('JSON 已复制剪贴板'); }
    catch { toast('复制失败，改用下载'); }
    const blob = new Blob([json], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${sceneId}.scene.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
  rootEl.querySelector('[data-act=reset]')!.addEventListener('click', () => {
    clearSceneOverride(sceneId);
    location.reload();
  });
  renderCats(); renderList(); updateHud();

  /* ---------- 场景交互 ---------- */
  const ray = new THREE.Raycaster();
  const ndc = new THREE.Vector2();
  /* camera 由 main 注入（避免循环依赖） */
  let cam: THREE.PerspectiveCamera | null = null;
  const setCamera = (c: THREE.PerspectiveCamera) => { cam = c; };

  const raycastGround = (e: PointerEvent): THREE.Vector3 | null => {
    if (!cam) return null;
    const r = canvas.getBoundingClientRect();
    ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, cam);
    const hit = ray.intersectObject(terrain, false)[0];
    return hit ? hit.point.clone() : null;
  };

  let drag: { po: PlacedObject; yOff: number } | null = null;
  let downAt = { x: 0, y: 0, t: 0 };
  let moved = 0;

  const findPlaced = (e: PointerEvent): PlacedObject | null => {
    if (!cam) return null;
    const r = canvas.getBoundingClientRect();
    ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, cam);
    const hits = ray.intersectObjects(group.children, true);
    if (!hits.length) return null;
    let o: THREE.Object3D | null = hits[0].object;
    while (o && o.parent !== group) o = o.parent;
    if (!o) return null;
    return placed.find(p => p.obj === o) ?? null;
  };

  const select = (po: PlacedObject | null) => {
    if (selected && selected !== po) {
      selected.obj.traverse(m => {
        if (m instanceof THREE.Mesh) (m.material as THREE.Material & { emissive?: THREE.Color }).emissive?.setHex(0x000000);
      });
    }
    selected = po;
    if (po) {
      po.obj.traverse(m => {
        if (m instanceof THREE.Mesh) {
          const mat = m.material as THREE.Material & { emissive?: THREE.Color };
          if (mat.emissive) mat.emissive.setHex(0x332211);
        }
      });
      toast('已选中：' + po.asset);
    }
    updateHud();
  };

  canvas.addEventListener('pointerdown', e => {
    downAt = { x: e.clientX, y: e.clientY, t: performance.now() };
    moved = 0;
    const po = findPlaced(e);
    if (po) {
      select(po);
      drag = { po, yOff: po.obj.position.y - terrainHeight(po.obj.position.x, po.obj.position.z) };
      opts.setOrbit(false);
    }
  });
  canvas.addEventListener('pointermove', e => {
    moved = Math.max(moved, Math.hypot(e.clientX - downAt.x, e.clientY - downAt.y));
    if (drag) {
      const p = raycastGround(e);
      if (p) {
        drag.po.obj.position.set(p.x, terrainHeight(p.x, p.z) + drag.yOff, p.z);
      }
    }
  });
  const endDrag = () => {
    if (drag) { opts.setOrbit(true); persist(); drag = null; }
  };
  canvas.addEventListener('pointerup', e => {
    const wasDrag = !!drag;
    endDrag();
    if (wasDrag || moved > 6) return;              // 属于环视/物体拖动，不算点击
    /* 纯点击：放置 或 取消选择 */
    if (activeAsset) {
      const p = raycastGround(e);
      if (p) {
        const yaw = Math.random() * Math.PI * 2;
        spawn(activeAsset, +p.x.toFixed(2), +p.z.toFixed(2), yaw, 1.2).then(ok => {
          if (ok) toast('已放置（仍可继续点地连放，点面板取消）');
        });
      }
    } else {
      select(findPlaced(e));
    }
  });
  canvas.addEventListener('pointercancel', endDrag);

  /* ---------- 键盘 ---------- */
  const applyPersistSelect = () => { if (selected) persist(); updateHud(); };
  window.addEventListener('keydown', e => {
    if (!selected) {
      if (e.key === 'Escape') { activeAsset = null; renderList(); }
      return;
    }
    if (e.key === 'r' || e.key === 'R') {
      selected.yaw += (e.shiftKey ? -1 : 1) * Math.PI / 12;
      selected.obj.rotation.y = selected.yaw;
      applyPersistSelect();
      e.preventDefault();
    } else if (e.key === '+' || e.key === '=') {
      selected.obj.scale.multiplyScalar(1.15);
      selected.scale *= 1.15;
      applyPersistSelect();
    } else if (e.key === '-' || e.key === '_') {
      selected.obj.scale.multiplyScalar(1 / 1.15);
      selected.scale /= 1.15;
      applyPersistSelect();
    } else if (e.key === 'Delete' || e.key === 'Backspace') {
      group.remove(selected.obj);
      placed.splice(placed.indexOf(selected), 1);
      selected = null;
      applyPersistSelect();
      toast('已删除');
      e.preventDefault();
    } else if (e.key === 'Escape') {
      select(null);
    }
  });

  return {
    place: (asset, x, z, yaw = 0, scale = 1.2) => {
      void spawn(asset, x, z, yaw, scale);         // 异步；断言侧可轮询 count
      return true;
    },
    count: () => placed.length,
    selected: () => selected?.asset ?? null,
    exportJson,
    setCamera,
    state: () => ({ count: placed.length, asset: activeAsset, selected: selected?.asset ?? null }),
  };
}
