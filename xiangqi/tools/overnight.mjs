/* 过夜双局 runner（it-003/US-07）：
 * 顺序自动跑 A/B 两局真模型对弈 → 每局存快照 JSON + 导出棋谱 → 调 report.mjs 出报告。
 * 用法：nohup node tools/overnight.mjs > /tmp/overnight.log 2>&1 &
 * 兜底：服务无响应自动重启；单局硬上限 7h（超时记未完成继续下一局）；总跑完写 DONE 标记。 */
import { spawn } from 'node:child_process';
import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, '..');
const PORT = 8777;
const BASE = `http://127.0.0.1:${PORT}`;
const OUT = path.join(ROOT, '..', 'reports', '2026-09-24-xiangqi-showdown');
const GAME_DEADLINE_MS = 7 * 3600_000;

const GAMES = [
  { id: 'A', red: { provider: 'zai-coding-cn', model: 'glm-5.3-flash' }, black: { provider: 'xiaomi-token-plan-cn', model: 'mimo-v2.6-flash' } },
  { id: 'B', red: { provider: 'zai-coding-cn', model: 'glm-5.3' }, black: { provider: 'xiaomi-token-plan-cn', model: 'mimo-v2.6-pro' } },
];

const log = (...a) => console.log(new Date().toISOString().slice(0, 19), ...a);
mkdirSync(OUT, { recursive: true });

let serverProc = null;
async function serverUp() {
  try {
    const r = await fetch(`${BASE}/api/state`, { signal: AbortSignal.timeout(3000) });
    return r.ok;
  } catch { return false; }
}
async function ensureServer() {
  if (await serverUp()) return;
  log('服务无响应，重启…');
  try { process.kill(-serverProc?.pid ?? 0, 'SIGKILL'); } catch { /* ignore */ }
  spawn('bash', ['-lc', 'pkill -f "node server.mjs" 2>/dev/null; sleep 1; nohup node server.mjs > /tmp/arena-server.log 2>&1 &'],
    { cwd: ROOT, detached: true, stdio: 'ignore' }).unref();
  for (let i = 0; i < 30; i++) {
    await sleep(1000);
    if (await serverUp()) { log('服务已恢复'); return; }
  }
  throw new Error('服务重启失败');
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function api(pathName, body) {
  const r = await fetch(BASE + pathName, {
    method: body ? 'POST' : 'GET',
    headers: { 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  return r.json();
}

async function playGame(cfg) {
  log(`==== 局 ${cfg.id} 开局: ${cfg.red.provider}/${cfg.red.model} vs ${cfg.black.provider}/${cfg.black.model}`);
  const start = await api('/api/game', { action: 'start', red: cfg.red, black: cfg.black, swap: false, evalEnabled: true });
  if (!start.ok) throw new Error(`开局失败: ${start.error}`);
  const gameNo = start.state.gameNo;
  const t0 = Date.now();
  let lastPlies = -1;
  while (Date.now() - t0 < GAME_DEADLINE_MS) {
    await sleep(15_000);
    let s;
    try {
      await ensureServer();
      s = (await api('/api/state')).state ?? (await api('/api/state'));
    } catch (e) {
      log('状态轮询失败(重试):', e.message);
      continue;
    }
    if (s.gameNo !== gameNo) { log('WARN: gameNo 漂移（有外部开局），放弃本局'); return { aborted: true }; }
    if (s.moves.length !== lastPlies) {
      lastPlies = s.moves.length;
      log(`局 ${cfg.id}: ${s.status} plies=${lastPlies} turn=${s.turn}`);
    }
    if (s.status === 'over') {
      const elapsed = Math.round((Date.now() - t0) / 1000);
      const exp = await api('/api/game', { action: 'export' });
      const snapPath = path.join(OUT, `game-${cfg.id}.json`);
      const txtPath = path.join(OUT, `game-${cfg.id}.txt`);
      writeFileSync(snapPath, JSON.stringify({ config: cfg, elapsedSec: elapsed, state: s }, null, 2));
      if (exp.ok) writeFileSync(txtPath, exp.text);
      log(`==== 局 ${cfg.id} 终局: ${JSON.stringify(s.result)} 手数=${s.moves.length} 用时=${elapsed}s`);
      return { id: cfg.id, config: cfg, elapsedSec: elapsed, state: s, snapPath, txtPath };
    }
  }
  log(`==== 局 ${cfg.id} 超过硬上限(7h)，记为未完成`);
  return { id: cfg.id, config: cfg, incomplete: true, state: await api('/api/state') };
}

async function main() {
  await ensureServer();
  const results = [];
  for (const cfg of GAMES) {
    try {
      const r = await playGame(cfg);
      results.push(r);
      if (r.aborted) break;
    } catch (e) {
      log(`局 ${cfg.id} 异常:`, e.message);
      results.push({ id: cfg.id, config: cfg, error: e.message });
    }
  }
  // 报告
  const args = results.map((r) => path.join(OUT, `game-${r.id}.json`)).filter((p) => existsSync(p));
  if (args.length) {
    log('生成报告…');
    await new Promise((resolve) => {
      const p = spawn('node', ['tools/report.mjs', ...args], { cwd: ROOT, stdio: 'inherit' });
      p.on('exit', resolve);
    });
  }
  writeFileSync(path.join(OUT, 'DONE'), new Date().toISOString() + '\n' + JSON.stringify(results.map((r) => ({ id: r.id, result: r.state?.result, error: r.error, incomplete: r.incomplete })), null, 2));
  log('全部完成, 产物在', OUT);
}

main().catch((e) => {
  log('FATAL:', e);
  writeFileSync(path.join(OUT, 'FAILED'), String(e.stack || e));
  process.exit(1);
});
