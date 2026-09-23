/* ICCS → 中文记谱（炮二平五）。
 * ICCS：文件 a-i 自红方左手边到右手边，rank 0 = 红方底线（与 xiangqi.js 一致）。
 * 只服务展示/导出；对局权威格式永远是 ICCS（ADR-001）。
 * get(square) => xiangqi.js 的棋.get()，需在**走子前**的局面调用。 */

const FILE = 'abcdefghi';
const RED_COL = ['九', '八', '七', '六', '五', '四', '三', '二', '一']; // 文件 a..i
const RED_NUM = ['一', '二', '三', '四', '五', '六', '七', '八', '九'];
const NAMES = {
  r: { r: '车', b: '车' },
  n: { r: '马', b: '马' },
  b: { r: '相', b: '象' },
  a: { r: '仕', b: '士' },
  k: { r: '帅', b: '将' },
  c: { r: '炮', b: '炮' },
  p: { r: '兵', b: '卒' },
};
// 马/相(象)/仕(士) 走斜线，进退跟的是**落点列号**；车炮兵帅将走直线，跟的是步数
const DIAGONAL = new Set(['n', 'b', 'a']);
const ORDERS = { 2: ['前', '后'], 3: ['前', '中', '后'] };

function colChar(color, fileIdx) {
  return color === 'r' ? RED_COL[fileIdx] : String(fileIdx + 1);
}

/**
 * @param {(sq: string) => {type: string, color: string} | null} get
 * @param {{from: string, to: string, color: 'r'|'b', piece: string}} move xiangqi.js verbose 走法对象
 * @returns {string} 中文记谱；无法消歧（同列同兵种 >3）时回退 ICCS
 */
export function iccsToZh(get, move) {
  try {
    const { from, to, color } = move;
    const type = String(move.piece).toLowerCase();
    const name = NAMES[type]?.[color];
    if (!name) return `${from}${to}`;
    const f0 = FILE.indexOf(from[0]);
    const f1 = FILE.indexOf(to[0]);
    const r0 = Number(from[1]);
    const r1 = Number(to[1]);
    if (f0 < 0 || f1 < 0 || Number.isNaN(r0) || Number.isNaN(r1)) return `${from}${to}`;

    // 同列同兵种消歧：前/中/后（前 = 更靠近对方底线）
    let prefix = name + colChar(color, f0);
    const sameFile = [];
    for (let r = 0; r <= 9; r++) {
      const p = get(from[0] + r);
      if (p && p.color === color && p.type === type) sameFile.push(r);
    }
    if (sameFile.length >= 2) {
      const order = sameFile.slice().sort((a, b) => (color === 'r' ? b - a : a - b));
      const ord = ORDERS[order.length];
      const idx = order.indexOf(r0);
      if (ord && idx >= 0) prefix = ord[idx] + name;
      else return `${from}${to}`; // >3 个同列兵种：罕见，回退 ICCS
    }

    let suffix;
    if (r1 === r0) {
      suffix = '平' + colChar(color, f1);
    } else {
      const advancing = color === 'r' ? r1 > r0 : r1 < r0;
      suffix = advancing ? '进' : '退';
      if (DIAGONAL.has(type)) {
        suffix += colChar(color, f1);
      } else {
        const steps = Math.abs(r1 - r0);
        suffix += color === 'r' ? RED_NUM[steps - 1] : String(steps);
      }
    }
    return prefix + suffix;
  } catch {
    return `${move.from}${move.to}`;
  }
}
