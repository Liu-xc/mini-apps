/* 场景数据（it-003，schema 见 specs/03-data-model.md）
   Placement.scale = 目标高度（米）：加载时模型先归一化到 1 米再乘。
   屏内编辑器导出的 JSON 与此结构同构，可直接粘贴入库。 */

import type { TimeId } from './style';

export interface Placement {
  asset: string;   // catalog id，如 "quaternius:CommonTree_3"
  x: number;
  z: number;
  yaw: number;     // 弧度
  scale: number;   // 目标高度（米）
}

export interface SceneDef {
  id: string;
  name: string;
  placements: Placement[];
}

/* 出生点营地（由 it-002 HERO 硬编码迁移而来） */
export const CAMP: SceneDef = {
  id: 'camp',
  name: '环线营地',
  placements: [
    { asset: 'quaternius:CommonTree_3', x: -14, z: -10, yaw: 0.7, scale: 6.2 },
    { asset: 'quaternius:Pine_1', x: 12.5, z: -17, yaw: -0.4, scale: 7.0 },
    { asset: 'quaternius:Rock_Medium_1', x: -6.5, z: -13, yaw: 1.1, scale: 1.5 },
    { asset: 'quaternius:Rock_Medium_2', x: 7, z: -8, yaw: 0, scale: 1.1 },
    { asset: 'quaternius:Pebble_Square_1', x: -17, z: -4, yaw: 2.1, scale: 0.8 },
    { asset: 'kenney:campfire_logs', x: -4.5, z: -7.5, yaw: 0.6, scale: 0.75 },
    { asset: 'kenney:tent_smallOpen', x: 6.2, z: -10.5, yaw: -2.4, scale: 1.7 },
    { asset: 'kenney:log_stack', x: -8.2, z: -4.5, yaw: 0.3, scale: 0.85 },
    { asset: 'kenney:sign', x: 2.6, z: -13.5, yaw: 0.15, scale: 1.35 },
    { asset: 'kenney:stump_round', x: 9.5, z: -5.5, yaw: 0, scale: 0.7 },
    { asset: 'kenney:fence_simple', x: -13, z: -16, yaw: -0.35, scale: 1.15 },
    { asset: 'kenney:fence_simple', x: -10.6, z: -16.9, yaw: -0.35, scale: 1.15 },
    { asset: 'kenney:fence_simpleLow', x: -8.2, z: -17.8, yaw: -0.35, scale: 1.0 },
    { asset: 'kenney:log', x: 4.2, z: -4.6, yaw: 1.2, scale: 0.45 },
    // 出口路牌（it-010 AC-1）：西北朝石林 / 西南朝乌兰布统
    { asset: 'kenney:sign', x: -14.5, z: -11, yaw: 2.4, scale: 1.35 },
    { asset: 'kenney:sign', x: -11, z: 9.5, yaw: 0.9, scale: 1.35 },
  ],
};

/* ---------- 三站（it-008 AC-3）：场景摆放集 + 时段 + 出生点 ----------
   石林 = 石阵白昼（Poly Haven boulder_01/rock_07 首次上场）；
   达里湖 = 营地湖湾（camp）；乌兰布统 = 林地黄昏。 */
export const SHILIN: SceneDef = {
  id: 'shilin',
  name: '阿斯哈图石林',
  placements: [
    { asset: 'polyhaven:boulder_01', x: -55.5, z: -44, yaw: 0.5, scale: 3.2 },
    { asset: 'polyhaven:boulder_01', x: -48, z: -46, yaw: 2.1, scale: 2.7 },
    { asset: 'polyhaven:boulder_01', x: -43, z: -40, yaw: 4.0, scale: 2.4 },
    { asset: 'polyhaven:rock_07', x: -60, z: -41, yaw: 1.2, scale: 1.7 },
    { asset: 'polyhaven:rock_07', x: -50, z: -50, yaw: 3.4, scale: 1.5 },
    { asset: 'polyhaven:rock_09', x: -40, z: -35, yaw: 0.8, scale: 1.6 },
    { asset: 'quaternius:Rock_Medium_2', x: -58, z: -48, yaw: 0.3, scale: 1.9 },
    { asset: 'quaternius:Rock_Medium_3', x: -45, z: -34, yaw: 2.6, scale: 1.7 },
    { asset: 'quaternius:Rock_Medium_1', x: -63, z: -36, yaw: 1.9, scale: 1.5 },
    { asset: 'quaternius:Pebble_Square_2', x: -53, z: -47, yaw: 0.9, scale: 0.7 },
    { asset: 'quaternius:Pebble_Square_5', x: -47, z: -43, yaw: 2.2, scale: 0.6 },
    { asset: 'quaternius:Pebble_Round_3', x: -56, z: -37, yaw: 1.4, scale: 0.55 },
    { asset: 'quaternius:DeadTree_2', x: -65, z: -46, yaw: 0.7, scale: 4.6 },
    { asset: 'quaternius:TwistedTree_2', x: -38, z: -45, yaw: 2.9, scale: 5.2 },
    { asset: 'polyhaven:tree_stump_01', x: -49, z: -36, yaw: 0.4, scale: 0.7 },
    { asset: 'polyhaven:moss_01', x: -61, z: -44, yaw: 1.1, scale: 0.8 },
    { asset: 'kenney:sign', x: -44, z: -27, yaw: -0.7, scale: 1.35 },
  ],
};

export const WULAN: SceneDef = {
  id: 'wulan',
  name: '乌兰布统',
  placements: [
    { asset: 'quaternius:CommonTree_1', x: -32, z: 41, yaw: 0.6, scale: 6.4 },
    { asset: 'quaternius:CommonTree_3', x: -17, z: 39, yaw: 2.0, scale: 5.8 },
    { asset: 'quaternius:CommonTree_4', x: -12, z: 47, yaw: 4.1, scale: 6.0 },
    { asset: 'quaternius:CommonTree_2', x: -38, z: 48, yaw: 1.2, scale: 5.6 },
    { asset: 'quaternius:Pine_1', x: -19, z: 56, yaw: 0.4, scale: 7.0 },
    { asset: 'quaternius:Pine_2', x: -34, z: 58, yaw: 2.8, scale: 6.6 },
    { asset: 'quaternius:Pine_3', x: -9, z: 52, yaw: 1.7, scale: 6.8 },
    { asset: 'kenney:tent_detailedClosed', x: -28, z: 46, yaw: 2.6, scale: 1.8 },
    { asset: 'kenney:campfire_stones', x: -26, z: 43.5, yaw: 0, scale: 0.8 },
    { asset: 'kenney:fence_simple', x: -21, z: 45, yaw: 0.2, scale: 1.15 },
    { asset: 'kenney:fence_simple', x: -18.6, z: 45.4, yaw: 0.35, scale: 1.15 },
    { asset: 'kenney:log_stack', x: -31, z: 44.5, yaw: 0.5, scale: 0.85 },
    { asset: 'kenney:stump_round', x: -23, z: 47.5, yaw: 0, scale: 0.7 },
    { asset: 'polyhaven:dandelion_01', x: -30, z: 53, yaw: 0.8, scale: 0.45 },
    { asset: 'polyhaven:dandelion_01', x: -18, z: 51, yaw: 2.1, scale: 0.4 },
    { asset: 'polyhaven:fern_02', x: -15, z: 43, yaw: 1.4, scale: 0.9 },
    { asset: 'kenney:sign', x: -21, z: 47, yaw: -0.5, scale: 1.35 },
  ],
};

/* 合并世界（it-009 AC-2）：三站 placements 同世界加载，可步行互通。
   编辑器整体编排写到 world 键（重置/导出同既有流程）。 */
export function getWorldScene(): SceneDef {
  const base: SceneDef = {
    id: 'world',
    name: '赤峰环线',
    placements: [
      ...getScene('camp').placements,
      ...getScene('shilin').placements,
      ...getScene('wulan').placements,
    ],
  };
  try {
    const raw = localStorage.getItem(LS_PREFIX + 'world');
    if (raw) {
      const placements = JSON.parse(raw) as Placement[];
      if (Array.isArray(placements)) return { ...base, placements };
    }
  } catch { /* 覆盖损坏回落合并场景 */ }
  return base;
}

/* 站点表：场景 + 时段 + 出生点（?scene= / G 键循环） */
export interface Station {
  id: string;
  name: string;
  sceneId: string;
  time: TimeId;
  spawn: [number, number];
  tint?: string;   // 区域雾色调（it-010 AC-3，缺省=时段雾色不动）
}
export const SCENES: Record<string, SceneDef> = {
  [CAMP.id]: CAMP,
  [SHILIN.id]: SHILIN,
  [WULAN.id]: WULAN,
};

export const STATIONS: Station[] = [
  { id: 'shilin', name: '石林 · 白昼', sceneId: 'shilin', time: 'day', spawn: [-50, -32], tint: '#e3d9bd' },
  { id: 'dali', name: '达里湖 · 湖湾', sceneId: 'camp', time: 'day', spawn: [0, 0] },
  { id: 'wulan', name: '乌兰布统 · 黄昏', sceneId: 'wulan', time: 'sunset', spawn: [-27, 55], tint: '#e8c9a0' },
];

const LS_PREFIX = 'travel-rpg:scene:';

/* 生效场景：localStorage 覆盖 > 代码数据（屏幕里编排的即所见） */
export function getScene(sceneId: string): SceneDef {
  const base = SCENES[sceneId];
  if (!base) throw new Error('未知场景: ' + sceneId);
  try {
    const raw = localStorage.getItem(LS_PREFIX + sceneId);
    if (raw) {
      const placements = JSON.parse(raw) as Placement[];
      if (Array.isArray(placements)) return { ...base, placements };
    }
  } catch { /* 覆盖数据损坏则回落代码场景 */ }
  return base;
}

export function saveScene(sceneId: string, placements: Placement[]): void {
  localStorage.setItem(LS_PREFIX + sceneId, JSON.stringify(placements));
}

export function clearSceneOverride(sceneId: string): void {
  localStorage.removeItem(LS_PREFIX + sceneId);
}

/* 营地避让区：从 placements 派生（单一事实源，半径 2.6m） */
export function deriveBlock(placements: Placement[]): Array<[number, number]> {
  return placements.map(p => [p.x, p.z]);
}
