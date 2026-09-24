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
  ],
};

/* ---------- 三站（it-008 AC-3）：场景摆放集 + 时段 + 出生点 ----------
   石林 = 石阵白昼（Poly Haven boulder_01/rock_07 首次上场）；
   达里湖 = 营地湖湾（camp）；乌兰布统 = 林地黄昏。 */
export const SHILIN: SceneDef = {
  id: 'shilin',
  name: '阿斯哈图石林',
  placements: [
    { asset: 'polyhaven:boulder_01', x: -3.5, z: -6, yaw: 0.5, scale: 3.2 },
    { asset: 'polyhaven:boulder_01', x: 4, z: -8, yaw: 2.1, scale: 2.7 },
    { asset: 'polyhaven:boulder_01', x: 9, z: -2, yaw: 4.0, scale: 2.4 },
    { asset: 'polyhaven:rock_07', x: -8, z: -3, yaw: 1.2, scale: 1.7 },
    { asset: 'polyhaven:rock_07', x: 2, z: -12, yaw: 3.4, scale: 1.5 },
    { asset: 'polyhaven:rock_09', x: 12, z: 3, yaw: 0.8, scale: 1.6 },
    { asset: 'quaternius:Rock_Medium_2', x: -6, z: -10, yaw: 0.3, scale: 1.9 },
    { asset: 'quaternius:Rock_Medium_3', x: 7, z: 4, yaw: 2.6, scale: 1.7 },
    { asset: 'quaternius:Rock_Medium_1', x: -11, z: 2, yaw: 1.9, scale: 1.5 },
    { asset: 'quaternius:Pebble_Square_2', x: -1, z: -9, yaw: 0.9, scale: 0.7 },
    { asset: 'quaternius:Pebble_Square_5', x: 5, z: -5, yaw: 2.2, scale: 0.6 },
    { asset: 'quaternius:Pebble_Round_3', x: -4, z: 1, yaw: 1.4, scale: 0.55 },
    { asset: 'quaternius:DeadTree_2', x: -13, z: -8, yaw: 0.7, scale: 4.6 },
    { asset: 'quaternius:TwistedTree_2', x: 14, z: -7, yaw: 2.9, scale: 5.2 },
    { asset: 'polyhaven:tree_stump_01', x: 3, z: 2, yaw: 0.4, scale: 0.7 },
    { asset: 'polyhaven:moss_01', x: -9, z: -6, yaw: 1.1, scale: 0.8 },
  ],
};

export const WULAN: SceneDef = {
  id: 'wulan',
  name: '乌兰布统',
  placements: [
    { asset: 'quaternius:CommonTree_1', x: -7, z: -9, yaw: 0.6, scale: 6.4 },
    { asset: 'quaternius:CommonTree_3', x: 8, z: -11, yaw: 2.0, scale: 5.8 },
    { asset: 'quaternius:CommonTree_4', x: 13, z: -3, yaw: 4.1, scale: 6.0 },
    { asset: 'quaternius:CommonTree_2', x: -13, z: -2, yaw: 1.2, scale: 5.6 },
    { asset: 'quaternius:Pine_1', x: 6, z: 6, yaw: 0.4, scale: 7.0 },
    { asset: 'quaternius:Pine_2', x: -9, z: 8, yaw: 2.8, scale: 6.6 },
    { asset: 'quaternius:Pine_3', x: 16, z: 2, yaw: 1.7, scale: 6.8 },
    { asset: 'kenney:tent_detailedClosed', x: -3, z: -4, yaw: 2.6, scale: 1.8 },
    { asset: 'kenney:campfire_stones', x: -1, z: -6.5, yaw: 0, scale: 0.8 },
    { asset: 'kenney:fence_simple', x: 4, z: -5, yaw: 0.2, scale: 1.15 },
    { asset: 'kenney:fence_simple', x: 6.4, z: -4.6, yaw: 0.35, scale: 1.15 },
    { asset: 'kenney:log_stack', x: -6, z: -5.5, yaw: 0.5, scale: 0.85 },
    { asset: 'kenney:stump_round', x: 2, z: -2.5, yaw: 0, scale: 0.7 },
    { asset: 'polyhaven:dandelion_01', x: -5, z: 3, yaw: 0.8, scale: 0.45 },
    { asset: 'polyhaven:dandelion_01', x: 7, z: 1, yaw: 2.1, scale: 0.4 },
    { asset: 'polyhaven:fern_02', x: 10, z: -7, yaw: 1.4, scale: 0.9 },
  ],
};

/* 站点表：场景 + 时段 + 出生点（?scene= / G 键循环） */
export interface Station {
  id: string;
  name: string;
  sceneId: string;
  time: TimeId;
  spawn: [number, number];
}
export const SCENES: Record<string, SceneDef> = {
  [CAMP.id]: CAMP,
  [SHILIN.id]: SHILIN,
  [WULAN.id]: WULAN,
};

export const STATIONS: Station[] = [
  { id: 'shilin', name: '石林 · 白昼', sceneId: 'shilin', time: 'day', spawn: [2, 6] },
  { id: 'dali', name: '达里湖 · 湖湾', sceneId: 'camp', time: 'day', spawn: [0, 0] },
  { id: 'wulan', name: '乌兰布统 · 黄昏', sceneId: 'wulan', time: 'sunset', spawn: [-2, 5] },
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
