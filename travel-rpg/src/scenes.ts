/* 场景数据（it-003，schema 见 specs/03-data-model.md）
   Placement.scale = 目标高度（米）：加载时模型先归一化到 1 米再乘。
   屏内编辑器导出的 JSON 与此结构同构，可直接粘贴入库。 */

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
    // 湖湾滩涂的独木舟（it-005 AC-3，激活入库未上场资产；石径终点旁）
    { asset: 'kenney:canoe', x: 34.2, z: -24.4, yaw: -0.55, scale: 0.8 },
  ],
};

export const SCENES: Record<string, SceneDef> = { [CAMP.id]: CAMP };

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
