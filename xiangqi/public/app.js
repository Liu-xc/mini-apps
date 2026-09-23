/* AI 象棋竞技场 · 前端（零构建） */
/* global Xiangqi, Xiangqiboard */

// 原生 DOM 元素（不能用 jQuery 对象——.textContent/.innerHTML 挂上去是静默 no-op）
const byId = (x) => document.getElementById(x);
const els = {
  redModel: byId('red-model'),
  blackModel: byId('black-model'),
  modelList: byId('model-list'),
  start: byId('btn-start'),
  pause: byId('btn-pause'),
  step: byId('btn-step'),
  stop: byId('btn-stop'),
  again: byId('btn-again'),
  exportBtn: byId('btn-export'),
  swap: byId('chk-swap'),
  errorBar: byId('error-bar'),
  statusBar: byId('status-bar'),
  turn: byId('turn-indicator'),
  retry: byId('retry-badge'),
  streamR: byId('stream-r'),
  streamB: byId('stream-b'),
  redLabel: byId('red-label'),
  blackLabel: byId('black-label'),
  moveRows: byId('move-rows'),
  scoreRows: byId('score-rows'),
  overlay: byId('overlay'),
  overlayTitle: byId('overlay-title'),
  overlayReason: byId('overlay-reason'),
  chart: byId('winrate-chart'),
  chartLegend: byId('chart-legend'),
  jevStatus: byId('jev-status'),
  evalToggle: byId('chk-eval'),
};

let state = null;
let localGame = new Xiangqi(); // 与服务端同步的本地镜像（人机代走校验用）
let board = null;

const END_REASON_ZH = {
  checkmate: '将死',
  stalemate: '困毙',
  'forfeit-illegal': '三次非法走法判负',
  'forfeit-timeout': '超时判负',
  'draw-60': '60 回合无吃子，和棋',
  'draw-max': '150 回合上限，和棋',
};
const STATUS_ZH = {
  idle: '就绪',
  playing: '对局进行中',
  paused: '已暂停（可单步 / 手动代走）',
  over: '对局结束',
};

/* ── 棋盘 ── */

function initBoard() {
  board = Xiangqiboard('board', {
    draggable: true,
    position: 'start',
    orientation: 'red',
    onDragStart(source, piece) {
      if (localGame.game_over()) return false;
      if (state && (state.status === 'over' || state.status === 'idle') && state.moves.length === 0 && state.status === 'idle') {
        // 空闲时也允许摆一摆（走本地镜像，POST 会被服务端拒绝）
      }
      return true;
    },
    onDrop(source, target) {
      let mv = null;
      try {
        mv = localGame.move({ from: source, to: target });
      } catch {
        mv = null;
      }
      if (mv === null) return 'snapback';
      // 乐观校验：服务端拒绝则回滚
      fetch('/api/game', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ action: 'human', from: source, to: target }),
      })
        .then((r) => r.json())
        .then((j) => {
          if (!j.ok) {
            localGame.undo();
            board.position(localGame.fen());
            showError(j.error || '手动代走失败');
          } else {
            hideError();
          }
        })
        .catch(() => {
          localGame.undo();
          board.position(localGame.fen());
          showError('网络错误，手动代走失败');
        });
      return undefined; // 保留落点，等服务端确认
    },
    onSnapEnd() {
      board.position(localGame.fen());
    },
  });
}

/* ── 状态同步 ── */

function resync(s) {
  state = s;
  // 本地镜像重放
  localGame = new Xiangqi();
  for (const m of s.moves) {
    try {
      localGame.move(m.iccs, { sloppy: true });
    } catch (e) {
      console.error('重放失败', m, e);
    }
  }
  board.position(s.fen);
  renderMoves();
  renderScore();
  renderControls();
  renderOverlay();
  renderChart();
  els.redLabel.textContent = s.red ? `${s.red.provider}/${s.red.model}` : '—';
  els.blackLabel.textContent = s.black ? `${s.black.provider}/${s.black.model}` : '—';
  if (s.lastError) showError(s.lastError);
}

function renderControls() {
  const st = state?.status || 'idle';
  const active = st === 'playing' || st === 'paused';
  els.start.disabled = active;
  els.pause.disabled = !active; // playing→暂停，paused→继续，都可点
  els.step.disabled = !active;
  els.stop.disabled = !active;
  els.pause.textContent = st === 'paused' ? '继续' : '暂停';
  // it-002 修订：评估开关与服务端状态联动；key 缺失时禁用并提示
  if (state) {
    els.evalToggle.checked = state.evalEnabled !== false;
    els.evalToggle.disabled = state.jevEnabled === false;
    els.evalToggle.parentElement.title = state.jevEnabled === false ? '未配置 JEV_API_KEY' : '每手交给 Jev 模型评估红黑胜率';
  }
  const turnName = state?.turn === 'r' ? '红方' : state?.turn === 'b' ? '黑方' : '';
  els.turn.textContent = st === 'over' || st === 'idle'
    ? STATUS_ZH[st]
    : `${STATUS_ZH[st]} · 轮到${turnName}`;
  els.statusBar.textContent =
    st === 'idle'
      ? '就绪 · 选择双方模型后点「开局」'
      : `第 ${(Math.floor((state?.moves.length || 0) / 2) + 1)} 回合 · 手数 ${state?.moves.length || 0} · ${STATUS_ZH[st]}`;
}

function renderOverlay() {
  const r = state?.result;
  if (!r || state.status !== 'over') {
    els.overlay.hidden = true;
    return;
  }
  els.overlay.hidden = false;
  els.overlayTitle.textContent = r.winner === 'r' ? '红方胜' : r.winner === 'b' ? '黑方胜' : '和棋';
  els.overlayReason.textContent = END_REASON_ZH[r.reason] || r.reason;
}

function renderMoves() {
  const rows = [];
  const moves = state?.moves || [];
  for (let i = 0; i < moves.length; i += 2) {
    const r = moves[i];
    const b = moves[i + 1];
    rows.push(
      `<tr><td>${i / 2 + 1}.</td><td>${esc(r.zh)}${r.by === 'human' ? ' <span class="dim">(人)</span>' : ''}</td>` +
        `<td>${b ? esc(b.zh) + (b.by === 'human' ? ' <span class="dim">(人)</span>' : '') : ''}</td></tr>`,
    );
  }
  els.moveRows.innerHTML = rows.join('');
  const table = els.moveRows.closest('.moves');
  if (table) table.scrollTop = table.scrollHeight;
}

function renderScore() {
  const sb = state?.scoreboard || {};
  const rows = Object.entries(sb).map(
    ([k, v]) => `<tr><td>${esc(k)}</td><td>${v.w}</td><td>${v.d}</td><td>${v.l}</td></tr>`,
  );
  els.scoreRows.innerHTML = rows.join('') || '<tr><td colspan="4" class="dim">还没有对局</td></tr>';
}

function appendLine(stream, html) {
  const el = document.createElement('div');
  el.innerHTML = html;
  stream.appendChild(el);
  stream.scrollTop = stream.scrollHeight;
  while (stream.children.length > 400) stream.removeChild(stream.firstChild);
}

function esc(s) {
  return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

function showError(msg) {
  els.errorBar.hidden = false;
  els.errorBar.textContent = msg;
}
function hideError() {
  els.errorBar.hidden = true;
}

/* ── SSE 事件 ── */

function connect() {
  const es = new EventSource('/api/events');
  es.onmessage = (e) => {
    let msg;
    try {
      msg = JSON.parse(e.data);
    } catch {
      return;
    }
    handle(msg);
  };
  es.onerror = () => {
    els.statusBar.textContent = '与服务端断开，重连中…';
  };
}

function handle(msg) {
  switch (msg.type) {
    case 'state':
      resync(msg.state);
      break;
    case 'turn': {
      const stream = msg.color === 'r' ? els.streamR : els.streamB;
      stream.innerHTML = '';
      const name = msg.color === 'r' ? '红方' : '黑方';
      appendLine(stream, `<span class="sys">—— 第 ${Math.floor(msg.ply / 2) + 1} 回合 · ${name}思考 ——</span>`);
      els.turn.textContent = `对局进行中 · 轮到${name}`;
      els.retry.hidden = true;
      break;
    }
    case 'thinking': {
      const stream = msg.side === 'r' ? els.streamR : els.streamB;
      const cls = msg.kind === 'thinking' ? 'dim' : '';
      const last = stream.lastElementChild;
      // 同类增量合并进同一段，避免逐 token 碎片；kind 切换（thinking↔text）才开新段
      if (last && last.dataset && last.dataset.kind === msg.kind) {
        last.textContent += msg.delta;
      } else {
        const div = document.createElement('div');
        div.className = cls;
        div.dataset.kind = msg.kind;
        div.textContent = msg.delta;
        stream.appendChild(div);
      }
      stream.scrollTop = stream.scrollHeight;
      break;
    }
    case 'retry': {
      const name = msg.color === 'r' ? '红方' : '黑方';
      els.retry.hidden = false;
      els.retry.textContent = `${name} 重试 ${msg.attempt}/${msg.max}${msg.reason === 'timeout' ? '（超时）' : ''}`;
      const stream = msg.color === 'r' ? els.streamR : els.streamB;
      appendLine(stream, `<span class="warn">重试 ${msg.attempt}/${msg.max}：${esc(msg.got || '超时')} 不合法</span>`);
      break;
    }
    case 'move': {
      const m = msg.move;
      if ((state?.moves.length || 0) === m.ply) {
        state.moves.push(m);
        if (localGame.history().length === m.ply) localGame.move(m.iccs, { sloppy: true });
        renderMoves();
      }
      if (state) state.turn = msg.turn; // 状态条/回合指示跟着落子走
      renderControls();
      board.position(msg.fen, true);
      break;
    }
    case 'eval': {
      const m = (state?.moves || []).find((x) => x.ply === msg.ply);
      if (m) {
        m.eval = msg.eval;
        renderChart();
      }
      break;
    }
    case 'gameover':
      resync(msg.state);
      break;
    case 'error':
      showError(msg.message);
      break;
    case 'log':
      console.log('[arena]', msg.message);
      break;
  }
}

/* ── 胜率曲线（it-002，纯 SVG 零依赖） ── */

function renderChart() {
  const svg = els.chart;
  const legend = els.chartLegend;
  if (!svg || !state) return;
  const W = 400, H = 150, L = 26, R = 40, T = 8, B = 16;
  const evalOff = state.jevEnabled === false || state.evalEnabled === false;
  els.jevStatus.textContent = state.jevEnabled === false ? '未配置 JEV_API_KEY'
    : state.evalEnabled === false ? '评估已关闭' : 'jev-latest';
  els.jevStatus.style.color = evalOff ? 'var(--error)' : '';

  const moves = state.moves || [];
  const pts = moves.filter((m) => m.eval);
  if (pts.length === 0) {
    svg.innerHTML = `<text class="empty" x="${W / 2}" y="${H / 2}" text-anchor="middle">${
      state.jevEnabled === false ? '未配置 JEV_API_KEY（曲线停用）'
      : state.evalEnabled === false ? '评估已关闭（勾选「评估胜率」开启）' : '等待评估…'}</text>`;
    legend.textContent = '';
    return;
  }
  const maxPly = Math.max(moves.length - 1, 1);
  const x = (ply) => L + (ply / maxPly) * (W - L - R);
  const y = (p) => T + (1 - p) * (H - T - B);

  const parts = [];
  // 网格 25/50/75 与 50% 基线
  for (const g of [0.25, 0.5, 0.75]) {
    const cls = g === 0.5 ? 'baseline' : 'grid';
    parts.push(`<line class="${cls}" x1="${L}" y1="${y(g)}" x2="${W - R}" y2="${y(g)}"/>`);
  }
  parts.push(`<text class="axis-label" x="${L - 4}" y="${y(1) + 8}" text-anchor="end">100</text>`);
  parts.push(`<text class="axis-label" x="${L - 4}" y="${y(0.5) + 3}" text-anchor="end">50</text>`);
  parts.push(`<text class="axis-label" x="${L - 4}" y="${y(0) - 2}" text-anchor="end">0</text>`);
  parts.push(`<text class="axis-label" x="${L}" y="${H - 4}">1</text>`);
  parts.push(`<text class="axis-label" x="${W - R}" y="${H - 4}" text-anchor="end">${maxPly + 1}</text>`);

  const redLine = pts.map((m) => `${x(m.ply).toFixed(1)},${y(m.eval.red).toFixed(1)}`).join(' ');
  const blackLine = pts.map((m) => `${x(m.ply).toFixed(1)},${y(m.eval.black).toFixed(1)}`).join(' ');
  parts.push(`<polyline class="line-red" points="${redLine}"/>`);
  parts.push(`<polyline class="line-black" points="${blackLine}"/>`);
  const last = pts[pts.length - 1];
  parts.push(`<circle class="dot-red" cx="${x(last.ply).toFixed(1)}" cy="${y(last.eval.red).toFixed(1)}" r="2.6"/>`);
  parts.push(`<circle class="dot-black" cx="${x(last.ply).toFixed(1)}" cy="${y(last.eval.black).toFixed(1)}" r="2.6"/>`);
  parts.push(`<text class="axis-label" x="${W - R + 4}" y="${y(last.eval.red) + 3}" fill="var(--red-piece)">${Math.round(last.eval.red * 100)}%</text>`);
  parts.push(`<text class="axis-label" x="${W - R + 4}" y="${y(last.eval.black) + 3}" fill="var(--black-piece)">${Math.round(last.eval.black * 100)}%</text>`);
  svg.innerHTML = parts.join('');

  const pc = (v) => `${Math.round(v * 100)}%`;
  legend.innerHTML =
    `<span class="lg-red">红 ${pc(last.eval.red)}</span>` +
    `<span>和 ${pc(last.eval.draw)}</span>` +
    `<span class="lg-black">黑 ${pc(last.eval.black)}</span>` +
    `<span>置信 ${pc(last.eval.confidence)}</span>` +
    `<span>第 ${last.ply + 1} 手</span>`;
}

/* ── 动作 ── */

function parseModel(v) {
  const s = v.trim();
  const i = s.indexOf('/');
  if (i <= 0 || i === s.length - 1) return null;
  return { provider: s.slice(0, i), model: s.slice(i + 1) };
}

async function post(body) {
  const r = await fetch('/api/game', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  const j = await r.json().catch(() => ({ ok: false, error: '响应解析失败' }));
  if (!j.ok) throw new Error(j.error || '操作失败');
  if (j.state) resync(j.state);
  return j;
}

function startGame() {
  const red = parseModel(els.redModel.value);
  const black = parseModel(els.blackModel.value);
  if (!red) return showError('红方模型格式应为 provider/model，如 zai/glm-4.6');
  if (!black) return showError('黑方模型格式应为 provider/model，如 zai/glm-4-flash');
  hideError();
  els.streamR.innerHTML = '';
  els.streamB.innerHTML = '';
  post({
    action: 'start',
    red, black,
    swap: els.swap.checked,
    evalEnabled: els.evalToggle.checked, // it-002 修订：评估按局可选
  }).catch((e) => showError(e.message));
}

async function loadProviders() {
  try {
    const r = await fetch('/api/providers');
    const j = await r.json();
    els.modelList.innerHTML = (j.models || [])
      .map((m) => `<option value="${esc(m.provider)}/${esc(m.id)}">${esc(m.name || '')}</option>`)
      .join('');
  } catch {
    /* 静默：手填仍可用 */
  }
}

function exportGame() {
  post({ action: 'export' }).then((j) => {
    const blob = new Blob([j.text], { type: 'text/plain;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `xiangqi-${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

/* ── 绑定 ── */

els.start.addEventListener('click', startGame);
els.again.addEventListener('click', startGame);
els.pause.addEventListener('click', () => {
  const act = state?.status === 'paused' ? 'resume' : 'pause';
  post({ action: act }).catch((e) => showError(e.message));
});
els.step.addEventListener('click', () => post({ action: 'step' }).catch((e) => showError(e.message)));
els.stop.addEventListener('click', () => post({ action: 'stop' }).catch((e) => showError(e.message)));
els.exportBtn.addEventListener('click', exportGame);
els.evalToggle.addEventListener('change', () => {
  // 运行中即时开/关（对局未开始时仅影响下一局的开局参数）
  if (state && (state.status === 'playing' || state.status === 'paused' || state.status === 'over')) {
    post({ action: 'eval-toggle', enabled: els.evalToggle.checked }).catch((e) => showError(e.message));
  }
});

initBoard();
loadProviders();
connect();
