/* AI 象棋竞技场 · 本地服务（零 npm 依赖）
 * 静态文件 + SSE + POST /api/game；两个 pi --mode rpc 子进程各执一方（ADR-002）。
 * 启动：node server.mjs  （PORT 默认 8777） */
import http from 'node:http';
import https from 'node:https';
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync, existsSync, statSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { iccsToZh } from './lib/zh.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 本地私密 env（.env.local 已 gitignore，存 JEV_API_KEY 等；永不进 git/HTTP 响应）
try {
  for (const line of readFileSync(path.join(__dirname, '.env.local'), 'utf8').split('\n')) {
    const m = line.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].trim();
  }
} catch { /* 无 .env.local 则跳过 */ }
const require = createRequire(import.meta.url);
const { Xiangqi } = require(path.join(__dirname, 'public/vendor/xiangqi.js'));

const PORT = Number(process.env.PORT || 8777);
const PUBLIC_DIR = path.join(__dirname, 'public');
const SESSIONS_DIR = path.join(__dirname, '.sessions');
const PI_DIR = path.join(os.homedir(), '.pi/agent');

// 对局参数（ADR-001 / ADR-004）
const TURN_TIMEOUT_MS = Number(process.env.TURN_TIMEOUT_MS || 120_000);
const MAX_RETRIES = 3; // 非法走法最多纠错重试次数
const MAX_PLIES = 300; // 150 回合上限
const DRAW_NO_CAPTURE_PLIES = 120; // 60 回合无吃子

const SYSTEM_PROMPT = [
  '你是一名正在下中国象棋的 AI 棋手（对局由裁判程序驱动）。',
  '每一步棋必须只回复一个 JSON 对象，格式为 {"move":"<ICCS坐标>"}，不要输出任何其他文字、解释或 markdown。',
  'ICCS 坐标为 4 个字符：起点和终点各由「文件字母 a-i」+「纵线数字 0-9」组成。',
  '文件 a-i 从红方左手边排到右手边；纵线 0 是红方底线，9 是黑方底线。例如 h2e2 表示从 h2 走到 e2。',
  '你只能从裁判给出的合法走法列表中选择一步。',
].join('\n');

const END_REASON_ZH = {
  checkmate: '将死',
  stalemate: '困毙',
  'forfeit-illegal': '三次非法走法判负',
  'forfeit-timeout': '超时判负',
  'draw-60': '60 回合无吃子，和棋',
  'draw-max': '150 回合上限，和棋',
};

/* ───────────────────────── 工具 ───────────────────────── */

const maskKey = (s) => (typeof s === 'string' && s.length > 10 ? `${s.slice(0, 5)}***${s.slice(-4)}` : '***');
function log(...args) {
  const line = args.map((a) => (typeof a === 'string' ? a : JSON.stringify(a))).join(' ');
  console.log(new Date().toISOString().slice(11, 19), line);
}

// 启动时把 GLM key 从 island 钥匙串注入子进程 env（不落日志明文，ADR-005）。
// 凭据已在 ~/.pi/agent/auth.json（pi 原生）时跳过：钥匙串 GUI 授权可能无限阻塞启动。
const childEnv = { ...process.env };
delete childEnv.JEV_API_KEY; // 评审 key 只留服务端进程，不下注 pi 子进程
const hasAuthJson = (() => {
  try {
    return Object.keys(JSON.parse(readFileSync(path.join(PI_DIR, 'auth.json'), 'utf8'))).length > 0;
  } catch {
    return false;
  }
})();
if (hasAuthJson) {
  log('检测到 ~/.pi/agent/auth.json，凭据由 pi 原生读取，跳过钥匙串');
} else if (!childEnv.ZAI_API_KEY) {
  try {
    const k = execFileSync('security', ['find-generic-password', '-s', 'com.spartapps.island', '-a', 'api-key', '-w'], {
      encoding: 'utf8',
      timeout: 15_000,
    }).trim();
    if (k) {
      childEnv.ZAI_API_KEY = k;
      log('GLM key 已从钥匙串注入子进程 env', maskKey(k));
    }
  } catch (e) {
    log('钥匙串读取失败（沙盒授权未通过？）:', String(e.message || e).slice(0, 120));
  }
}

/* ───────────────────────── PiClient ───────────────────────── */

let reqSeq = 0;

class TurnTimeout extends Error {
  constructor() {
    super('turn timeout');
    this.isTimeout = true;
  }
}

class PiClient {
  constructor({ provider, model, side, sessionDir, onEvent }) {
    this.provider = provider;
    this.model = model;
    this.side = side; // 'r' | 'b'
    this.sessionDir = sessionDir;
    this.onEvent = onEvent;
    this.proc = null;
    this.buf = '';
    this.pending = new Map(); // id -> {resolve, reject}
    this.runResolve = null; // 当前回合 agent_settled
    this.runTexts = []; // 本回合内的 assistant message_end 文本
    this.runErrors = []; // 本回合内 error 类事件
    this.dead = false;
    this.stderrTail = '';
    this.lastActivity = Date.now();
  }

  label() {
    return `${this.provider}/${this.model}`;
  }

  start() {
    const args = [
      '--mode', 'rpc',
      '--provider', this.provider,
      '--model', this.model,
      '--session-dir', this.sessionDir,
      '--name', `arena-${this.side}`,
      '--system-prompt', SYSTEM_PROMPT,
      '--no-tools', '--no-extensions', '--no-skills', '--no-prompt-templates',
      '--no-context-files', '--no-themes',
    ];
    log(`spawn pi [${this.side}]`, this.label());
    this.proc = spawn('pi', args, { env: childEnv, stdio: ['pipe', 'pipe', 'pipe'] });

    this.proc.stdout.on('data', (chunk) => this._onStdout(chunk));
    this.proc.stdin.on('error', () => {}); // 进程早亡时写管道的 EPIPE
    this.proc.stderr.on('data', (chunk) => {
      const text = chunk.toString();
      this.stderrTail = (this.stderrTail + text).slice(-2000);
      for (const line of text.split('\n')) if (line.trim()) log(`pi[${this.side} stderr]`, line.slice(0, 200));
    });
    this.proc.on('error', (e) => {
      // spawn 失败（如 pi 未安装）：error 事件之后不一定有 exit
      this.dead = true;
      log(`pi[${this.side}] spawn 失败:`, e.message);
      const err = new Error(`pi 启动失败: ${e.message}（请确认已安装 pi）`);
      for (const [, p] of this.pending) p.reject(err);
      this.pending.clear();
      if (this.runResolve) this.runResolve({ error: err });
    });
    this.proc.on('exit', (code, sig) => {
      this.dead = true;
      log(`pi[${this.side}] exit code=${code} sig=${sig}`);
      const err = new Error(`pi 进程退出（code=${code}）`);
      for (const [, p] of this.pending) p.reject(err);
      this.pending.clear();
      if (this.runResolve) this.runResolve({ error: err });
    });
  }

  // 严格 JSONL：只按 \n 分帧（不能用 readline，U+2028/29 是合法 JSON 字符）
  _onStdout(chunk) {
    this.lastActivity = Date.now(); // 任何输出都算活动：静默超时只掐死流
    this.buf += chunk.toString();
    let idx;
    while ((idx = this.buf.indexOf('\n')) >= 0) {
      const line = this.buf.slice(0, idx).replace(/\r$/, '');
      this.buf = this.buf.slice(idx + 1);
      if (!line.trim()) continue;
      let rec;
      try {
        rec = JSON.parse(line);
      } catch {
        continue; // 非协议行直接丢弃
      }
      this._onRecord(rec);
    }
  }

  _onRecord(rec) {
    if (rec.type === 'response') {
      const p = rec.id != null ? this.pending.get(rec.id) : undefined;
      if (p) {
        this.pending.delete(rec.id);
        p.resolve(rec);
      }
      return;
    }
    switch (rec.type) {
      case 'message_update': {
        const ev = rec.assistantMessageEvent;
        if (ev && (ev.type === 'text_delta' || ev.type === 'thinking_delta')) {
          this.onEvent?.({ side: this.side, kind: ev.type === 'text_delta' ? 'text' : 'thinking', delta: ev.delta });
        } else if (ev && ev.type === 'error') {
          this.runErrors.push(ev.error || ev.reason || 'stream error');
        }
        break;
      }
      case 'message_end': {
        const m = rec.message;
        if (m && m.role === 'assistant') this.runTexts.push(extractText(m.content));
        break;
      }
      case 'agent_settled': {
        if (this.runResolve) {
          const r = this.runResolve;
          this.runResolve = null;
          r({ settled: true });
        }
        break;
      }
      default:
        if (typeof rec.type === 'string' && rec.type.includes('error')) {
          this.runErrors.push(JSON.stringify(rec).slice(0, 300));
        }
    }
  }

  _send(cmd, timeoutMs = 15_000) {
    if (this.dead || !this.proc) return Promise.reject(new Error('pi 进程未启动'));
    const id = `r${++reqSeq}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`RPC 命令超时: ${cmd.type}`));
      }, timeoutMs);
      this.pending.set(id, {
        resolve: (v) => {
          clearTimeout(timer);
          resolve(v);
        },
        reject: (e) => {
          clearTimeout(timer);
          reject(e);
        },
      });
      this.proc.stdin.write(JSON.stringify({ id, ...cmd }) + '\n');
    });
  }

  /** 发一条 prompt，流式事件经 onEvent 转发，等 agent_settled，返回本回合最终 assistant 文本。 */
  async promptAndWait(message, { timeoutMs = TURN_TIMEOUT_MS } = {}) {
    this.runTexts = [];
    this.runErrors = [];
    // 先挂 settle 等待器再发命令，防快方抢先 settled
    const run = new Promise((resolve) => {
      this.runResolve = resolve;
    });
    const resp = await this._send({ type: 'prompt', message });
    if (!resp.success) throw new Error(resp.error || 'prompt 被拒绝');
    if (!this.runResolve) {
      // prompt 响应前后已收到 agent_settled（极快失败），直接读本回合文本
      await sleep(50);
    }

    // 静默超时：模型持续吐 token 就永不掐断（合法长思考），只掐「彻底无输出」
    this.lastActivity = Date.now();
    let idleIv;
    const idleTimeout = new Promise((_, rej) => {
      idleIv = setInterval(() => {
        if (Date.now() - this.lastActivity > timeoutMs) {
          rej(new TurnTimeout());
        }
      }, 2000);
    });
    try {
      await Promise.race([run, idleTimeout]);
    } catch (e) {
      if (e.isTimeout) {
        this._send({ type: 'abort' }).catch(() => {});
        // 给 5s 收尾；仍不 settle 视为进程不健康
        await Promise.race([run, new Promise((r) => setTimeout(r, 5000))]);
      }
      throw e;
    } finally {
      clearInterval(idleIv);
    }

    if (this.runTexts.length === 0) {
      const detail = this.runErrors.length ? this.runErrors.join(' | ') : this.stderrTail.slice(-400);
      throw new Error(`模型未返回文本${detail ? '：' + detail : ''}`);
    }
    return this.runTexts[this.runTexts.length - 1];
  }

  async stop() {
    if (!this.proc || this.dead) return;
    try {
      this.proc.stdin.end(); // 官方要求的优雅关闭
    } catch { /* ignore */ }
    const exited = new Promise((r) => this.proc.once('exit', r));
    await Promise.race([exited, new Promise((r) => setTimeout(r, 1500))]);
    if (!this.dead) {
      this.proc.kill('SIGKILL');
      await Promise.race([exited, new Promise((r) => setTimeout(r, 500))]);
    }
    this.dead = true;
  }
}

function extractText(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    return content
      .map((b) => (typeof b === 'string' ? b : b.text ?? b.thinking ?? ''))
      .join('');
  }
  return '';
}

function extractMove(text) {
  if (!text) return null;
  const json = text.match(/"move"\s*:\s*"([a-iA-I]\d\s*[a-iA-I]\d)"/);
  if (json) return json[1].replace(/\s+/g, '').toLowerCase();
  const bare = text.match(/\b([a-i]\d[a-i]\d)\b/);
  return bare ? bare[1].toLowerCase() : null;
}

/* ───────────────────────── providers ───────────────────────── */

const PRESET_MODELS = [
  { provider: 'zai', id: 'glm-4.6' },
  { provider: 'zai', id: 'glm-4.5-air' },
  { provider: 'zai', id: 'glm-4-flash' },
  { provider: 'xiaomi', id: 'mimo-v2.6-flash' },
  { provider: 'xiaomi', id: 'mimo-v2.5-pro' },
  { provider: 'deepseek', id: 'deepseek-chat' },
  { provider: 'openai', id: 'gpt-5.6' },
  { provider: 'anthropic', id: 'claude-sonnet-4-5-20250929' },
];

function readModelsJson() {
  const file = path.join(PI_DIR, 'models.json');
  if (!existsSync(file)) return [];
  try {
    const j = JSON.parse(readFileSync(file, 'utf8'));
    const out = [];
    for (const [name, p] of Object.entries(j.providers || {})) {
      for (const m of p.models || []) out.push({ provider: name, id: m.id });
    }
    return out;
  } catch {
    return [];
  }
}

/** 向 pi 查询真正可用（有凭据）的模型列表；失败则回退 presets。 */
async function queryPiModels() {
  const client = new PiClient({ provider: 'openai', model: 'none', side: '?', sessionDir: SESSIONS_DIR, onEvent: null });
  // 不走 start() 的固定参数（无 provider/model），用最小启动
  client.proc = spawn('pi', ['--mode', 'rpc', '--no-session', '--no-tools', '--no-extensions', '--no-skills', '--no-context-files', '--no-themes'], {
    env: childEnv,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  client.proc.stdout.on('data', (c) => client._onStdout(c));
  client.proc.stderr.on('data', () => {});
  client.proc.on('exit', () => {
    client.dead = true;
    for (const [, p] of client.pending) p.reject(new Error('pi 退出'));
    client.pending.clear();
  });
  try {
    const resp = await client._send({ type: 'get_available_models' }, 8000);
    if (!resp.success) throw new Error(resp.error);
    const models = (resp.data?.models || []).map((m) => ({ provider: m.provider, id: m.id, name: m.name }));
    return { source: 'pi', models };
  } finally {
    await client.stop();
  }
}

let providersCache = { at: 0, data: null };
async function getProviders() {
  if (providersCache.data && Date.now() - providersCache.at < 60_000) return providersCache.data;
  let data;
  try {
    const { source, models } = await queryPiModels();
    const seen = new Set(models.map((m) => `${m.provider}/${m.id}`));
    for (const m of [...readModelsJson(), ...PRESET_MODELS]) {
      if (!seen.has(`${m.provider}/${m.id}`)) models.push({ ...m, name: '' });
    }
    data = { source, models };
  } catch (e) {
    log('queryPiModels 失败，回退 presets:', e.message);
    const seen = new Set();
    const models = [];
    for (const m of [...readModelsJson(), ...PRESET_MODELS]) {
      const k = `${m.provider}/${m.id}`;
      if (!seen.has(k)) {
        seen.add(k);
        models.push({ ...m, name: '' });
      }
    }
    data = { source: 'preset', models };
  }
  providersCache = { at: Date.now(), data };
  return data;
}

/* ───────────────────────── 对局状态 ───────────────────────── */

const scoreboard = {}; // modelKey -> {w,d,l}
const scoreKey = (side) => `${side.provider}/${side.model}`;

const game = {
  status: 'idle', // idle | playing | paused | over
  red: null, // {provider, model}
  black: null,
  chess: new Xiangqi(),
  moves: [], // {iccs, zh, color, ply, fenAfter, by, retried}
  result: null, // {winner:'r'|'b'|null, reason}
  noCapturePlies: 0,
  retry: 0,
  gameNo: 0,
  lastError: '',
  evalEnabled: true, // it-002 修订：jev 评估按局可选（开局参数/运行中可切）
};

let clients = { r: null, b: null };
let loopRunning = false;
let stepPermits = 0;
let resumeSignal = null; // resolve fn
let humanBusy = false;

function serializeState() {
  return {
    status: game.status,
    red: game.red,
    black: game.black,
    fen: game.chess.fen(),
    turn: game.chess.turn(),
    moves: game.moves,
    result: game.result,
    retry: game.retry,
    gameNo: game.gameNo,
    lastError: game.lastError,
    scoreboard,
    jevEnabled: jevEnabled(),
    evalEnabled: game.evalEnabled,
    limits: { turnTimeoutMs: TURN_TIMEOUT_MS, maxRetries: MAX_RETRIES, maxPlies: MAX_PLIES },
  };
}

/* ───────────────────────── Jev 胜率评估（it-002）─────────────────────────
 * 每手落子后异步问 TypeSafe Jev：Choice(red_win/draw/black_win) → 概率回填。
 * 串行队列不阻塞回合；失败静默跳过；JEV_DISABLED=1（假模型冒烟）整体关停。
 * 注意：api.typesafe.ai 在本机被 LibreSSL 掐但 Node/OpenSSL 正常，故用 https.request。 */

const JEV_URL = 'https://api.typesafe.ai/v1/systemone';
const JEV_KEY = process.env.JEV_API_KEY || '';
const jevEnabled = () => Boolean(JEV_KEY) && !process.env.JEV_DISABLED;

function jevRequest(body, timeoutMs = 15_000) {
  return new Promise((resolve, reject) => {
    const req = https.request(JEV_URL, {
      method: 'POST',
      headers: { Authorization: `Bearer ${JEV_KEY}`, 'Content-Type': 'application/json' },
      timeout: timeoutMs,
    }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        try {
          if (res.statusCode !== 200) throw new Error(`HTTP ${res.statusCode}: ${data.slice(0, 200)}`);
          resolve(JSON.parse(data));
        } catch (e) { reject(e); }
      });
    });
    req.on('timeout', () => { req.destroy(new Error('jev 超时')); });
    req.on('error', reject);
    req.end(JSON.stringify(body));
  });
}

async function evalPosition(moveRec, fenAfter) {
  const chess = new Xiangqi(fenAfter);
  const hist = game.moves.slice(Math.max(0, game.moves.length - 12))
    .map((m) => m.iccs).join(' ');
  // Jev 的 prompt 必须英文（it-002 修订）：state 字段与问题/选项描述全英文
  const state = {
    game: 'Chinese chess (Xiangqi). Red moves first. Files a-i run left to right from Red\'s view; rank 0 is Red\'s back rank, rank 9 is Black\'s back rank.',
    fen: fenAfter,
    board: chess.ascii(),
    to_move: chess.turn() === 'r' ? 'Red to move' : 'Black to move',
    last_move: `${moveRec.iccs} by ${moveRec.color === 'r' ? 'Red' : 'Black'}`,
    recent_history_iccs: hist || '(opening position)',
  };
  const body = {
    state,
    model: 'jev-latest',
    questions: {
      outcome: {
        type: 'choice',
        instructions: 'Evaluate this Chinese chess (Xiangqi) position. What is the probability of each final outcome of the full game? Consider material balance, king safety, piece activity, immediate threats, and who moves next.',
        criteria: {
          red_win: 'Red ultimately wins (checkmates or stalemates Black)',
          draw: 'Draw (neither side can force a win, or the game ends by draw rules)',
          black_win: 'Black ultimately wins (checkmates or stalemates Red)',
        },
      },
    },
  };
  const resp = await jevRequest(body);
  const ans = resp.answers?.outcome?.probabilities || {};
  const r = Number(ans.red_win ?? 0), d = Number(ans.draw ?? 0), b = Number(ans.black_win ?? 0);
  return { red: r, draw: d, black: b, confidence: Number(resp.answers?.outcome?.confidence ?? 0) };
}

let evalChain = Promise.resolve();
function queueEval(rec) {
  if (!jevEnabled() || !game.evalEnabled || !rec) return; // it-002 修订：评估按局可选
  const ply = rec.ply;
  evalChain = evalChain.then(async () => {
    try {
      const ev = await evalPosition(rec, rec.fenAfter);
      const target = game.moves.find((m) => m.ply === ply);
      if (target) {
        target.eval = ev;
        broadcast('eval', { ply, eval: ev });
      }
    } catch (e) {
      log('jev 评估失败(跳过该点):', String(e.message || e).slice(0, 140));
    }
  });
}

/* ───────────────────────── SSE ───────────────────────── */

const sseClients = new Set();
function broadcast(type, data) {
  const payload = `data: ${JSON.stringify({ type, ...data })}\n\n`;
  for (const res of sseClients) res.write(payload);
}

/* ───────────────────────── prompt 拼装 ───────────────────────── */

function buildPrompt(isRetry, retryInfo) {
  const chess = game.chess;
  const color = chess.turn();
  const sideName = color === 'r' ? '红方（帅）' : '黑方（将）';
  const legal = chess.moves(); // ICCS 数组
  const history = game.moves.length
    ? game.moves.map((m, i) => (i % 2 === 0 ? `${i / 2 + 1}. ${m.zh}(${m.iccs})` : ` ${m.zh}(${m.iccs})`)).join('')
    : '（开局）';
  const fullMove = Math.floor(game.moves.length / 2) + 1;

  let prompt = [
    `你是${sideName}，第 ${fullMove} 回合，轮到你走棋。`,
    `FEN: ${chess.fen()}`,
    '棋盘（上黑下红，文件 a-i 从左到右，纵线 0 是红方底线）:',
    chess.ascii(),
    `已走着法: ${history}`,
    `合法走法（ICCS，${legal.length} 个，只能选一个）: ${legal.join(' ')}`,
    '只回复 JSON：{"move":"<ICCS>"}',
  ].join('\n');

  if (isRetry) {
    prompt = `你上一次回复的走法不合法。\n${retryInfo}\n\n请重新选择，只回复 JSON：{"move":"<ICCS>"}\n\n` + prompt;
  }
  return prompt;
}

/* ───────────────────────── 回合循环 ───────────────────────── */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitIfPaused() {
  while (game.status === 'paused' && stepPermits === 0 && game.status !== 'idle') {
    await new Promise((resolve) => {
      resumeSignal = resolve;
    });
    resumeSignal = null;
  }
  if (stepPermits > 0 && game.status === 'paused') stepPermits--;
}

function applyMove(iccs, by, retried) {
  const chess = game.chess;
  const color = chess.turn();
  const verbose = chess.moves({ verbose: true }).find((m) => m.iccs === iccs);
  if (!verbose) return null;
  const zh = iccsToZh((sq) => chess.get(sq), verbose);
  const isCapture = Boolean(verbose.flags?.includes('c')) || Boolean(verbose.captured);
  const applied = chess.move(iccs, { sloppy: true });
  if (!applied) return null;
  const ply = game.moves.length;
  const rec = { iccs, zh, color, ply, fenAfter: chess.fen(), by, retried: retried || 0 };
  game.moves.push(rec);
  game.noCapturePlies = isCapture ? 0 : game.noCapturePlies + 1;
  broadcast('move', { move: rec, fen: chess.fen(), turn: chess.turn() });
  queueEval(rec); // it-002：评估开启时异步评估本手局面胜率，回填后广播 'eval'
  return rec;
}

function checkEnd() {
  // 返回 null 或 {winner, reason}
  const chess = game.chess;
  const color = chess.turn(); // 待走方 = 被将死/困毙方
  if (chess.in_checkmate()) return { winner: color === 'r' ? 'b' : 'r', reason: 'checkmate' };
  if (chess.in_stalemate()) return { winner: color === 'r' ? 'b' : 'r', reason: 'stalemate' };
  if (game.noCapturePlies >= DRAW_NO_CAPTURE_PLIES) return { winner: null, reason: 'draw-60' };
  if (game.moves.length >= MAX_PLIES) return { winner: null, reason: 'draw-max' };
  return null;
}

function endGame(result) {
  game.result = result;
  game.status = 'over';
  const rk = scoreKey(game.red);
  const bk = scoreKey(game.black);
  scoreboard[rk] ||= { w: 0, d: 0, l: 0 };
  scoreboard[bk] ||= { w: 0, d: 0, l: 0 };
  if (result.winner === null) {
    scoreboard[rk].d++;
    scoreboard[bk].d++;
  } else if (result.winner === 'r') {
    scoreboard[rk].w++;
    scoreboard[bk].l++;
  } else {
    scoreboard[bk].w++;
    scoreboard[rk].l++;
  }
  broadcast('gameover', { result, state: serializeState() });
  log('对局结束:', result.winner ? `${result.winner === 'r' ? '红' : '黑'}方胜` : '和棋', END_REASON_ZH[result.reason]);
  teardownClients();
}

async function playOnePly() {
  const color = game.chess.turn();
  const side = color === 'r' ? game.red : game.black;
  const client = clients[color];
  const sideName = color === 'r' ? '红方' : '黑方';
  broadcast('turn', { color, side, ply: game.moves.length });

  const legal = new Set(game.chess.moves());
  let isRetry = false;
  let retried = 0;

  while (true) {
    let text;
    try {
      text = await client.promptAndWait(buildPrompt(isRetry, isRetry ? lastRetryInfo : ''), {
        timeoutMs: TURN_TIMEOUT_MS,
      });
    } catch (e) {
      if (e.isTimeout) {
        // ADR-004：超时自动重试一次，第二次判负
        if (retried < 1) {
          retried++;
          game.retry = retried;
          log(`${sideName}回合超时（${TURN_TIMEOUT_MS}ms），自动重试 1/1`);
          broadcast('retry', { color, attempt: retried, max: MAX_RETRIES, reason: 'timeout' });
          isRetry = true;
          lastRetryInfo = '原因：上一次回答超时。';
          continue;
        }
        game.lastError = `${sideName}超时判负`;
        log(`${sideName}第二次超时，判负`);
        broadcast('error', { message: game.lastError });
        endGame({ winner: color === 'r' ? 'b' : 'r', reason: 'forfeit-timeout' });
        return false;
      }
      game.lastError = `${sideName}进程错误: ${e.message}`;
      broadcast('error', { message: game.lastError });
      endGame({ winner: color === 'r' ? 'b' : 'r', reason: 'forfeit-timeout' });
      return false;
    }

    const move = extractMove(text);
    if (move && legal.has(move)) {
      applyMove(move, 'model', retried);
      game.retry = 0;
      return true;
    }

    if (retried >= MAX_RETRIES) {
      game.lastError = `${sideName}连续 ${MAX_RETRIES} 次重试仍非法，判负`;
      broadcast('error', { message: game.lastError, raw: text.slice(-400) });
      endGame({ winner: color === 'r' ? 'b' : 'r', reason: 'forfeit-illegal' });
      return false;
    }
    retried++;
    game.retry = retried;
    const got = move ?? (text ? `无法解析（${text.slice(0, 80)}…）` : '空回复');
    lastRetryInfo = `原因：你给出的 "${got}" 不在合法走法列表中。`;
    broadcast('retry', { color, attempt: retried, max: MAX_RETRIES, reason: 'illegal', got });
    log(`非法走法重试 ${retried}/${MAX_RETRIES}:`, got);
    isRetry = true;
  }
}
let lastRetryInfo = '';

async function gameLoop() {
  if (loopRunning) return;
  loopRunning = true;
  try {
    while (game.status === 'playing' || (game.status === 'paused' && stepPermits > 0)) {
      if (game.status === 'paused' && stepPermits === 0) {
        await waitIfPaused();
        continue;
      }
      await waitIfPaused();
      if (game.status !== 'playing' && game.status !== 'paused') break;

      const ok = await playOnePly();
      if (!ok || game.status === 'over') break;
      const end = checkEnd();
      if (end) {
        endGame(end);
        break;
      }
    }
  } catch (e) {
    log('回合循环异常:', e);
    game.lastError = String(e.message || e);
    broadcast('error', { message: game.lastError });
    game.status = 'idle';
    teardownClients();
  } finally {
    loopRunning = false;
    broadcast('state', { state: serializeState() });
  }
}

/* ───────────────────────── 生命周期动作 ───────────────────────── */

async function teardownClients() {
  const c = clients;
  clients = { r: null, b: null };
  await Promise.all([c.r?.stop(), c.b?.stop()]);
}

async function startGame({ red, black, swap, evalEnabled }) {
  if (game.status === 'playing' || game.status === 'paused') await stopGame();
  if (swap && game.red && game.black) {
    [red, black] = [game.black, game.red];
  }
  game.chess = new Xiangqi();
  game.moves = [];
  game.result = null;
  game.noCapturePlies = 0;
  game.retry = 0;
  game.lastError = '';
  game.gameNo++;
  game.red = { ...red };
  game.black = { ...black };
  game.evalEnabled = evalEnabled !== false; // 缺省开启；开局参数传 false 则本局不评估
  game.status = 'playing';
  stepPermits = 0;

  const gameDir = path.join(SESSIONS_DIR, `game-${Date.now()}`);
  mkdirSync(path.join(gameDir, 'red'), { recursive: true });
  mkdirSync(path.join(gameDir, 'black'), { recursive: true });
  clients = {
    r: new PiClient({ ...game.red, side: 'r', sessionDir: path.join(gameDir, 'red'), onEvent: (ev) => broadcast('thinking', ev) }),
    b: new PiClient({ ...game.black, side: 'b', sessionDir: path.join(gameDir, 'black'), onEvent: (ev) => broadcast('thinking', ev) }),
  };
  clients.r.start();
  clients.b.start();
  broadcast('state', { state: serializeState() });
  gameLoop();
}

async function stopGame() {
  if (resumeSignal) {
    resumeSignal();
    resumeSignal = null;
  }
  game.status = 'idle';
  stepPermits = 0;
  await teardownClients();
  broadcast('state', { state: serializeState() });
}

function humanMove({ from, to }) {
  if (humanBusy) throw new Error('已有操作进行中');
  if (game.status !== 'playing' && game.status !== 'paused') throw new Error('对局未在进行');
  if (loopRunning && game.status === 'playing') {
    // 模型回合进行中不允许插手
    throw new Error('模型回合进行中，请先暂停');
  }
  humanBusy = true;
  try {
    const legal = new Set(game.chess.moves());
    const iccs = `${from}${to}`.toLowerCase();
    if (!legal.has(iccs)) throw new Error(`非法手动走法 ${iccs}`);
    const rec = applyMove(iccs, 'human', 0);
    if (!rec) throw new Error('落子失败');
    const end = checkEnd();
    if (end) endGame(end);
    return rec;
  } finally {
    humanBusy = false;
  }
}

function exportGame() {
  const rk = game.red ? scoreKey(game.red) : '红方';
  const bk = game.black ? scoreKey(game.black) : '黑方';
  const res = game.result
    ? `${game.result.winner === 'r' ? '1-0' : game.result.winner === 'b' ? '0-1' : '1/2-1/2'} (${game.result.reason}: ${END_REASON_ZH[game.result.reason]})`
    : '未结束';
  const lines = [];
  lines.push('# AI 象棋竞技场棋谱');
  lines.push(`Red:  ${rk}`);
  lines.push(`Black: ${bk}`);
  lines.push(`Result: ${res}`);
  lines.push('');
  for (let i = 0; i < game.moves.length; i += 2) {
    const r = game.moves[i];
    const b = game.moves[i + 1];
    const ev = (m) => (m.eval ? ` [红${Math.round(m.eval.red * 100)}/和${Math.round(m.eval.draw * 100)}/黑${Math.round(m.eval.black * 100)}]` : '');
    lines.push(`${i / 2 + 1}. ${r.zh}(${r.iccs})${ev(r)}${b ? `  ${b.zh}(${b.iccs})${ev(b)}` : ''}`);
  }
  lines.push('');
  lines.push('FEN sequence:');
  lines.push(game.chess.fen());
  for (const m of game.moves) lines.push(m.fenAfter);
  return lines.join('\n');
}

/* ───────────────────────── HTTP ───────────────────────── */

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.json': 'application/json; charset=utf-8',
  '.woff2': 'font/woff2',
};

function serveStatic(req, res) {
  let urlPath = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (urlPath === '/') urlPath = '/index.html';
  const file = path.join(PUBLIC_DIR, urlPath);
  if (!file.startsWith(PUBLIC_DIR) || !existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404).end('not found');
    return;
  }
  res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
  res.end(readFileSync(file));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => {
      data += c;
      if (data.length > 1e6) reject(new Error('body too large'));
    });
    req.on('end', () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch (e) {
        reject(e);
      }
    });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');

    if (url.pathname === '/api/state') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify(serializeState()));
      return;
    }

    if (url.pathname === '/api/providers' && req.method === 'GET') {
      const data = await getProviders();
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify(data));
      return;
    }

    if (url.pathname === '/api/events' && req.method === 'GET') {
      res.writeHead(200, {
        'content-type': 'text/event-stream',
        'cache-control': 'no-cache',
        connection: 'keep-alive',
      });
      res.write(`data: ${JSON.stringify({ type: 'state', state: serializeState() })}\n\n`);
      sseClients.add(res);
      req.on('close', () => sseClients.delete(res));
      return;
    }

    if (url.pathname === '/api/game' && req.method === 'POST') {
      const body = await readBody(req);
      const { action } = body;
      switch (action) {
        case 'start': {
          if (!body.red?.provider || !body.red?.model || !body.black?.provider || !body.black?.model) {
            throw new Error('红黑双方都需要选择 provider/model');
          }
          await startGame({ red: body.red, black: body.black, swap: Boolean(body.swap), evalEnabled: body.evalEnabled });
          break;
        }
        case 'pause':
          if (game.status === 'playing') {
            game.status = 'paused';
            broadcast('state', { state: serializeState() });
          }
          break;
        case 'resume':
          if (game.status === 'paused') {
            game.status = 'playing';
            if (resumeSignal) {
              resumeSignal();
              resumeSignal = null;
            }
            broadcast('state', { state: serializeState() });
            gameLoop();
          }
          break;
        case 'step':
          if (game.status === 'playing') {
            game.status = 'paused';
          }
          if (game.status === 'paused') {
            stepPermits++;
            if (resumeSignal) {
              resumeSignal();
              resumeSignal = null;
            }
            broadcast('state', { state: serializeState() });
            gameLoop();
          }
          break;
        case 'stop':
          await stopGame();
          break;
        case 'eval-toggle': // it-002 修订：运行中开/关 jev 评估
          game.evalEnabled = body.enabled !== false;
          log(`jev 评估已${game.evalEnabled ? '开启' : '关闭'}`);
          broadcast('state', { state: serializeState() });
          break;
        case 'human': {
          const rec = humanMove(body);
          res.writeHead(200, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ ok: true, move: rec, state: serializeState() }));
          return;
        }
        case 'export': {
          res.writeHead(200, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ ok: true, text: exportGame(), state: serializeState() }));
          return;
        }
        default:
          throw new Error(`未知 action: ${action}`);
      }
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: true, state: serializeState() }));
      return;
    }

    if (req.method === 'GET') return serveStatic(req, res);
    res.writeHead(404).end('not found');
  } catch (e) {
    const msg = String(e.message || e);
    log('HTTP 错误:', msg);
    if (!res.headersSent) res.writeHead(400, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: false, error: msg }));
  }
});

// SSE 心跳，防代理/浏览器掐连接
setInterval(() => {
  for (const res of sseClients) res.write(': ping\n\n');
}, 15_000);

process.on('SIGINT', async () => {
  log('收到 SIGINT，回收 pi 子进程…');
  await teardownClients();
  process.exit(0);
});

mkdirSync(SESSIONS_DIR, { recursive: true });
server.listen(PORT, '127.0.0.1', () => {
  log(`AI 象棋竞技场: http://localhost:${PORT}`);
});
