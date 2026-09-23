/* it-001 全链路冒烟：假模型（本地 OpenAI 兼容服务）→ pi RPC ×2 → 编排 → SSE。
 * 不打真 API、不需要模型 key。用法：node tools/smoke.mjs
 * 通过标准：一局跑完（over，记分板更新）或 ≥30 手，且服务端无崩溃。 */
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs';
import os from 'node:os';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, '..');
const PORT = 8791;
const FAKE_PORT = 8901; // 与手动运行同一端口，避免 models.json 反复改写
const BASE = `http://127.0.0.1:${PORT}`;
const MAX_WAIT_MS = Number(process.env.SMOKE_TIMEOUT_MS || 240_000);
const TARGET_PLIES = 60; // 30 回合

// 1) 确保 models.json 有 arena-local 假 provider（幂等）
const piDir = path.join(os.homedir(), '.pi/agent');
mkdirSync(piDir, { recursive: true });
const modelsFile = path.join(piDir, 'models.json');
let modelsJson = {};
try {
  modelsJson = JSON.parse(readFileSync(modelsFile, 'utf8'));
} catch { /* new */ }
modelsJson.providers = modelsJson.providers || {};
const prev = JSON.stringify(modelsJson.providers['arena-local'] || null);
modelsJson.providers['arena-local'] = {
  baseUrl: `http://127.0.0.1:${FAKE_PORT}/v1`,
  api: 'openai-completions',
  apiKey: 'local-fake',
  models: [{ id: 'mock-dumb' }, { id: 'mock-smart' }, { id: 'mock-bad' }],
};
if (prev !== JSON.stringify(modelsJson.providers['arena-local'])) {
  writeFileSync(modelsFile, JSON.stringify(modelsJson, null, 2));
  console.log('[smoke] models.json 已写入/更新 arena-local provider');
}

const children = [];
function run(name, args, env = {}) {
  const p = spawn('node', args, { cwd: ROOT, env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] });
  p.stdout.on('data', (c) => process.stdout.write(`[${name}] ${c}`));
  p.stderr.on('data', (c) => process.stdout.write(`[${name}!] ${c}`));
  p.on('exit', (code) => console.log(`[${name}] exit ${code}`));
  children.push(p);
  return p;
}

async function waitReady(url, timeoutMs = 20_000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const r = await fetch(url);
      if (r.ok) return;
    } catch { /* retry */ }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(`服务未就绪: ${url}`);
}

const api = async (pathName, body) => {
  const r = await fetch(BASE + pathName, {
    method: body ? 'POST' : 'GET',
    headers: { 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  return r.json();
};

let failed = '';
async function main() {
  run('fake', ['tools/fake-model-server.mjs'], { FAKE_MODEL_PORT: String(FAKE_PORT) });
  run('arena', ['server.mjs'], {
    PORT: String(PORT),
    ZAI_API_KEY: 'unused-local', // 跳过钥匙串，冒烟不碰真 key
    TURN_TIMEOUT_MS: '30000',
    JEV_DISABLED: '1', // 假模型冒烟不打真评估 API
  });
  await waitReady(`${BASE}/api/state`);

  // providers 应能被 pi 枚举（含 arena-local）
  const prov = await api('/api/providers');
  console.log('[smoke] providers source =', prov.source, 'count =', prov.models?.length);
  const hasFake = (prov.models || []).some((m) => m.provider === 'arena-local');
  if (!hasFake) console.log('[smoke] WARN: 未在 providers 中看到 arena-local（不影响对局，可手填）');

  console.log('[smoke] 开局 arena-local/mock-dumb vs arena-local/mock-smart');
  const started = await api('/api/game', {
    action: 'start',
    red: { provider: 'arena-local', model: 'mock-dumb' },
    black: { provider: 'arena-local', model: 'mock-smart' },
    swap: false,
  });
  if (!started.ok) throw new Error('开局失败: ' + started.error);

  const t0 = Date.now();
  let last = null;
  let retriesSeen = 0;
  while (Date.now() - t0 < MAX_WAIT_MS) {
    await new Promise((r) => setTimeout(r, 1000));
    const s = await api('/api/state');
    if (!last || s.moves.length !== last.moves.length) {
      console.log(`[smoke] status=${s.status} plies=${s.moves.length} turn=${s.turn}`);
    }
    last = s;
    if (s.status === 'over') break;
    if (s.status === 'idle' && s.moves.length < 3) throw new Error('对局被中止: ' + s.lastError);
    if (s.moves.length >= TARGET_PLIES && process.env.SMOKE_STOP_EARLY === '1') break;
  }
  if (!last) throw new Error('无状态');

  // 断言
  const assert = (cond, msg) => {
    if (!cond) failed += msg + '\n';
  };
  assert(last.moves.length >= 30 || last.status === 'over', `手数不足：${last.moves.length} (<30 且未终局)`);
  if (last.status === 'over') {
    assert(last.result !== null, 'over 但无 result');
    const total = Object.values(last.scoreboard).reduce((a, v) => a + v.w + v.d + v.l, 0);
    assert(total >= 1, '记分板未更新');
  }
  const zhOk = last.moves.every((m) => m.zh && m.zh.length >= 3);
  assert(zhOk, '存在缺失的中文记谱');
  assert(last.lastError === '', 'lastError 非空: ' + last.lastError);

  console.log('[smoke] 最终 status =', last.status, ' plies =', last.moves.length, ' result =', JSON.stringify(last.result));
  console.log('[smoke] 记分板 =', JSON.stringify(last.scoreboard));
  console.log('[smoke] 最后 6 手:', last.moves.slice(-6).map((m) => `${m.zh}(${m.iccs})`).join(' '));

  await api('/api/game', { action: 'stop' }).catch(() => {});

  /* ── 阶段二：非法走法纠错重试路径（mock-bad 首答必非法） ── */
  console.log('[smoke] 阶段二：mock-bad vs mock-dumb，断言出现 retry');
  let retries = 0;
  const reader = (await fetch(`${BASE}/api/events`)).body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  const drain = (async () => {
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) {
          const frame = buf.slice(0, i);
          buf = buf.slice(i + 2);
          const line = frame.split('\n').find((l) => l.startsWith('data: '));
          if (!line) continue;
          try {
            const msg = JSON.parse(line.slice(6));
            if (msg.type === 'retry') retries++;
          } catch { /* ping 等 */ }
        }
      }
    } catch { /* 流被掐断（stop 时）忽略 */ }
  })();
  const started2 = await api('/api/game', {
    action: 'start',
    red: { provider: 'arena-local', model: 'mock-bad' },
    black: { provider: 'arena-local', model: 'mock-dumb' },
    swap: false,
  });
  if (!started2.ok) throw new Error('阶段二开局失败: ' + started2.error);
  const t1 = Date.now();
  while (retries < 3 && Date.now() - t1 < 60_000) {
    await new Promise((r) => setTimeout(r, 500));
    const s = await api('/api/state');
    if (s.status === 'idle') throw new Error('阶段二对局异常中止: ' + s.lastError);
  }
  assert(retries >= 1, '未观察到任何非法走法重试');
  console.log(`[smoke] 阶段二 retry 事件数 = ${retries}`);
  await api('/api/game', { action: 'stop' }).catch(() => {});
  reader.cancel().catch(() => {});
  await drain;
}

try {
  await main();
} catch (e) {
  failed += String(e.message || e) + '\n';
} finally {
  for (const p of children) p.kill('SIGKILL');
}

if (failed) {
  console.error('\n[smoke] FAIL:\n' + failed);
  process.exit(1);
}
console.log('\n[smoke] PASS');
process.exit(0);
