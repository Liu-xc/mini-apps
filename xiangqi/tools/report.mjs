/* 结果报告生成器（it-003/US-07）：
 * node tools/report.mjs reports/2026-09-24-xiangqi-showdown/game-A.json [game-B.json ...]
 * 输出同目录 report.html（纯静态、零依赖、纸感样式、内联胜率曲线 SVG）。 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, '..');
const require = createRequire(import.meta.url);
const { Xiangqi } = require(path.join(ROOT, 'public/vendor/xiangqi.js'));

const files = process.argv.slice(2);
if (!files.length) { console.error('用法: node tools/report.mjs <game-*.json> [...]'); process.exit(1); }
const games = files.map((f) => ({ file: f, ...JSON.parse(readFileSync(f, 'utf8')) }));
const outDir = path.dirname(files[0]);

const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const pct = (v) => `${Math.round(v * 100)}%`;
const fmtDur = (sec) => sec >= 3600 ? `${Math.floor(sec / 3600)}h${Math.round((sec % 3600) / 60)}m` : sec >= 60 ? `${Math.floor(sec / 60)}m${sec % 60}s` : `${sec}s`;
const label = (side) => `${side.provider}/${side.model}`;

function gameStats(g) {
  const s = g.state;
  const evals = s.moves.filter((m) => m.eval);
  const retries = s.moves.reduce((a, m) => a + (m.retried || 0), 0);
  let maxSwing = null; // {ply, delta, to:'red'|'black'}
  for (let i = 1; i < evals.length; i++) {
    const d = evals[i].eval.red - evals[i - 1].eval.red;
    if (!maxSwing || Math.abs(d) > Math.abs(maxSwing.delta)) {
      maxSwing = { ply: evals[i].ply, delta: d, mover: movesColor(s, evals[i].ply) };
    }
  }
  const avg = evals.length
    ? { red: evals.reduce((a, m) => a + m.eval.red, 0) / evals.length, draw: evals.reduce((a, m) => a + m.eval.draw, 0) / evals.length, black: evals.reduce((a, m) => a + m.eval.black, 0) / evals.length }
    : null;
  return { evals, retries, maxSwing, avg, lastEval: evals.at(-1)?.eval ?? null };
}
function movesColor(s, ply) { return s.moves[ply]?.color === 'r' ? '红' : '黑'; }

function curveSvg(s) {
  const W = 660, H = 190, L = 30, R = 52, T = 10, B = 20;
  const pts = s.moves.filter((m) => m.eval);
  if (!pts.length) return `<div class="muted">（无评估数据）</div>`;
  const maxPly = Math.max(s.moves.length - 1, 1);
  const x = (p) => L + (p / maxPly) * (W - L - R);
  const y = (v) => T + (1 - v) * (H - T - B);
  const parts = [];
  for (const g of [0.25, 0.5, 0.75]) {
    parts.push(`<line class="${g === 0.5 ? 'base' : 'grid'}" x1="${L}" y1="${y(g)}" x2="${W - R}" y2="${y(g)}"/>`);
  }
  parts.push(`<text class="ax" x="${L - 4}" y="${y(1) + 8}" text-anchor="end">100</text>`);
  parts.push(`<text class="ax" x="${L - 4}" y="${y(0.5) + 3}" text-anchor="end">50</text>`);
  parts.push(`<text class="ax" x="${L - 4}" y="${y(0) - 2}" text-anchor="end">0</text>`);
  parts.push(`<text class="ax" x="${L}" y="${H - 5}">第1手</text>`);
  parts.push(`<text class="ax" x="${W - R}" y="${H - 5}" text-anchor="end">第${maxPly + 1}手</text>`);
  const red = pts.map((m) => `${x(m.ply).toFixed(1)},${y(m.eval.red).toFixed(1)}`).join(' ');
  const blk = pts.map((m) => `${x(m.ply).toFixed(1)},${y(m.eval.black).toFixed(1)}`).join(' ');
  parts.push(`<polyline class="lr" points="${red}"/>`);
  parts.push(`<polyline class="lb" points="${blk}"/>`);
  const last = pts.at(-1);
  parts.push(`<circle class="dr" cx="${x(last.ply)}" cy="${y(last.eval.red)}" r="3"/>`);
  parts.push(`<circle class="db" cx="${x(last.ply)}" cy="${y(last.eval.black)}" r="3"/>`);
  parts.push(`<text class="ax rr" x="${W - R + 5}" y="${y(last.eval.red) + 4}">${pct(last.eval.red)}</text>`);
  parts.push(`<text class="ax bb" x="${W - R + 5}" y="${y(last.eval.black) + 4}">${pct(last.eval.black)}</text>`);
  return `<svg viewBox="0 0 ${W} ${H}" class="curve">${parts.join('')}</svg>`;
}

function moveRows(s) {
  const rows = [];
  const ev = (m) => (m.eval ? ` <span class="ev">红${pct(m.eval.red)}</span>` : '');
  for (let i = 0; i < s.moves.length; i += 2) {
    const r = s.moves[i], b = s.moves[i + 1];
    rows.push(`<tr><td>${i / 2 + 1}.</td><td>${esc(r.zh)}<span class="iccs">${r.iccs}</span>${ev(r)}</td><td>${b ? esc(b.zh) + `<span class="iccs">${b.iccs}</span>${ev(b)}` : ''}</td></tr>`);
  }
  return rows.join('');
}

function gameSection(g) {
  const s = g.state, st = gameStats(g);
  const reasonZh = { checkmate: '将死', stalemate: '困毙', 'forfeit-illegal': '三次非法判负', 'forfeit-timeout': '超时判负', 'draw-60': '60回合无吃子和棋', 'draw-max': '150回合上限和棋' }[s.result?.reason] || s.result?.reason || '未完成';
  const winner = s.result?.winner === 'r' ? '红方' : s.result?.winner === 'b' ? '黑方' : '和棋';
  const endBoard = (() => { try { return new Xiangqi(s.fen).ascii(); } catch { return s.fen; } })();
  const swing = st.maxSwing && Math.abs(st.maxSwing.delta) >= 0.08
    ? `第 ${st.maxSwing.ply + 1} 手（${st.maxSwing.mover}方行棋后）单手摆动 <b>${(st.maxSwing.delta > 0 ? '+' : '')}${Math.round(st.maxSwing.delta * 100)}%</b>`
    : '无显著单手摆动（≤8%）';
  return `
<section class="game">
  <h2>局 ${g.id || path.basename(g.file, '.json').slice(-1)} · ${esc(label(g.config.red))} <span class="vs">vs</span> ${esc(label(g.config.black))}</h2>
  <div class="cards">
    <div class="card"><div class="k">结果</div><div class="v ${s.result?.winner === 'r' ? 'red' : s.result?.winner === 'b' ? 'black' : ''}">${winner} · ${esc(reasonZh)}</div></div>
    <div class="card"><div class="k">手数</div><div class="v">${s.moves.length} 手 / ${Math.ceil(s.moves.length / 2)} 回合</div></div>
    <div class="card"><div class="k">用时</div><div class="v">${g.elapsedSec != null ? fmtDur(g.elapsedSec) : '—'}</div></div>
    <div class="card"><div class="k">纠错重试</div><div class="v">${st.retries} 次</div></div>
    <div class="card"><div class="k">评估覆盖</div><div class="v">${st.evals.length}/${s.moves.length}</div></div>
    <div class="card"><div class="k">均值(红/和/黑)</div><div class="v">${st.avg ? `${pct(st.avg.red)} / ${pct(st.avg.draw)} / ${pct(st.avg.black)}` : '—'}</div></div>
  </div>
  <p class="note">终局前最后一次评估：${st.lastEval ? `红 ${pct(st.lastEval.red)} · 和 ${pct(st.lastEval.draw)} · 黑 ${pct(st.lastEval.black)}（置信 ${pct(st.lastEval.confidence)}）` : '无'}<br/>关键转折：${swing}</p>
  ${curveSvg(s)}
  <div class="cols">
    <pre class="board">${esc(endBoard)}</pre>
    <table class="moves"><thead><tr><th>#</th><th>红</th><th>黑</th></tr></thead><tbody>${moveRows(s)}</tbody></table>
  </div>
</section>`;
}

const date = new Date().toISOString().slice(0, 10);
const wins = {};
for (const g of games) {
  const s = g.state; if (!s?.result) continue;
  for (const side of ['red', 'black']) {
    const k = label(g.config[side]);
    wins[k] ||= { w: 0, d: 0, l: 0 };
    const my = s.result.winner === (side === 'red' ? 'r' : 'b') ? 'w' : s.result.winner === null ? 'd' : 'l';
    wins[k][my]++;
  }
}
const scoreboardRows = Object.entries(wins).map(([k, v]) => `<tr><td>${esc(k)}</td><td>${v.w}</td><td>${v.d}</td><td>${v.l}</td></tr>`).join('');

const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"/><title>AI 象棋竞技场 · 对抗结果报告 ${date}</title>
<style>
:root{--paper:#f4f2ea;--surface:#fbfaf5;--ink:#1f2421;--faint:#6b7069;--line:#d9d5c8;--red:#b3261e;--black:#2b2b2b;--accent:#2e5e4e}
*{box-sizing:border-box} body{margin:0;background:var(--paper);color:var(--ink);font:15px/1.6 -apple-system,"PingFang SC","Noto Sans SC",sans-serif}
.wrap{max-width:1060px;margin:0 auto;padding:34px 26px 60px}
h1{font-family:"Songti SC","Noto Serif SC",Georgia,serif;font-size:30px;margin:0 0 4px}
.sub{color:var(--faint);font-size:13px;margin-bottom:24px}
h2{font-family:"Songti SC","Noto Serif SC",serif;font-size:20px;margin:34px 0 12px;padding-left:10px;border-left:4px solid var(--accent)}
.vs{color:var(--faint);font-weight:400;font-style:italic}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin:10px 0}
.card{background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:10px 12px}
.card .k{font-size:11px;color:var(--faint)}.card .v{font-size:17px;font-weight:600}.v.red{color:var(--red)}.v.black{color:var(--black)}
.note{font-size:13px;color:var(--faint);background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:8px 12px}
.curve{width:100%;height:190px;background:var(--surface);border:1px solid var(--line);border-radius:10px;margin:10px 0}
.curve .grid{stroke:var(--line);stroke-width:.5}.curve .base{stroke:var(--faint);stroke-width:.8;stroke-dasharray:4 3}
.curve .lr{fill:none;stroke:var(--red);stroke-width:2}.curve .lb{fill:none;stroke:var(--black);stroke-width:2;stroke-dasharray:5 3}
.curve .dr{fill:var(--red)}.curve .db{fill:var(--black)}.curve .ax{font:10px monospace;fill:var(--faint)}.curve .rr{fill:var(--red)}.curve .bb{fill:var(--black)}
.cols{display:grid;grid-template-columns:auto 1fr;gap:14px} @media(max-width:760px){.cols{grid-template-columns:1fr}}
.board{font:11px/1.35 "SF Mono",Menlo,monospace;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:10px;margin:0;overflow:auto}
.moves{width:100%;border-collapse:collapse;font-size:13px;background:var(--surface);border:1px solid var(--line);border-radius:10px;overflow:hidden}
.moves th{font-weight:500;color:var(--faint);font-size:11px;padding:6px 10px;border-bottom:1px solid var(--line);text-align:left}
.moves td{padding:4px 10px;border-bottom:1px solid #eeece2;font-family:"Songti SC","Noto Serif SC",serif}
.moves .iccs{font:10px monospace;color:var(--faint);margin-left:6px}.moves .ev{font:10px monospace;color:var(--accent);margin-left:6px}
table.score{border-collapse:collapse;background:var(--surface);border:1px solid var(--line);border-radius:10px;overflow:hidden}
table.score th,table.score td{padding:7px 18px;border-bottom:1px solid #eeece2;font-size:14px;text-align:left}
table.score th{font-size:11px;color:var(--faint);font-weight:500}
.muted{color:var(--faint);font-size:13px}
footer{margin-top:40px;color:var(--faint);font-size:11px;border-top:1px solid var(--line);padding-top:10px}
</style></head><body><div class="wrap">
<h1>AI 象棋竞技场 · 对抗结果报告</h1>
<div class="sub">${date} · ${games.length} 局 · 无回合时限（TURN_TIMEOUT_MS=0）· 每手经 Jev（jev-latest）评估红/和/黑胜率</div>
<h2>总记分板</h2>
<table class="score"><thead><tr><th>模型</th><th>胜</th><th>和</th><th>负</th></tr></thead><tbody>${scoreboardRows}</tbody></table>
${games.map(gameSection).join('\n')}
<footer>生成：xiangqi/tools/report.mjs · 数据：各局 game-*.json 快照 · 评估：TypeSafe Jev（Choice: red_win/draw/black_win） · 对弈：pi agent 双隔离 session</footer>
</div></body></html>`;

const out = path.join(outDir, 'report.html');
writeFileSync(out, html);
console.log('报告已生成:', out, `(${html.length} bytes, ${games.length} 局)`);
