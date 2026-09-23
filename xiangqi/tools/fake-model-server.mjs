/* 本地假模型服务：OpenAI 兼容 /v1/chat/completions，供全链路冒烟（不打真 API）。
 * 从 prompt 的「合法走法」白名单里随机选一步回复 {"move":"..."}。
 * 用法：node tools/fake-model-server.mjs [port=8901] */
import http from 'node:http';

const PORT = Number(process.env.FAKE_MODEL_PORT || 8901);

function pickMove(content) {
  const m = content.match(/合法走法（ICCS，\d+ 个，只能选一个）:\s*([a-i]\d[a-i]\d(?:\s+[a-i]\d[a-i]\d)*)/);
  if (m) {
    const list = m[1].split(/\s+/);
    return list[Math.floor(Math.random() * list.length)];
  }
  return null;
}

function completion(body) {
  const messages = body.messages || [];
  const lastUser = [...messages].reverse().find((x) => x.role === 'user');
  const text = typeof lastUser?.content === 'string' ? lastUser.content : JSON.stringify(lastUser?.content || '');
  const model = body.model || 'mock';
  let content;
  if (model === 'mock-bad') {
    // 首答必非法（同格 a0a0 不在白名单），收到纠错 prompt 后改邪归正 —— 用于验证重试路径
    content = text.includes('你上一次回复的走法不合法')
      ? `{"move":"${pickMove(text) || 'a3a4'}"}`
      : '{"move":"a0a0"}';
  } else {
    content = `{"move":"${pickMove(text) || 'a3a4'}"}`;
  }
  if (body.stream) {
    return {
      sse: [
        `data: ${JSON.stringify({ id: 'cmpl-1', object: 'chat.completion.chunk', model, choices: [{ index: 0, delta: { role: 'assistant', content } }] })}\n\n`,
        `data: ${JSON.stringify({ id: 'cmpl-1', object: 'chat.completion.chunk', model, choices: [{ index: 0, delta: {}, finish_reason: 'stop' }] })}\n\n`,
        'data: [DONE]\n\n',
      ].join(''),
    };
  }
  return {
    json: {
      id: 'cmpl-1',
      object: 'chat.completion',
      created: Math.floor(Date.now() / 1000),
      model,
      choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 100, completion_tokens: 10, total_tokens: 110 },
    },
  };
}

export function createFakeModelServer(port = PORT) {
  const server = http.createServer((req, res) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => {
      if (req.method === 'POST' && req.url?.includes('/chat/completions')) {
        let body = {};
        try {
          body = JSON.parse(data);
        } catch { /* ignore */ }
        const out = completion(body);
        if (out.sse) {
          res.writeHead(200, { 'content-type': 'text/event-stream' });
          res.end(out.sse);
        } else {
          res.writeHead(200, { 'content-type': 'application/json' });
          res.end(JSON.stringify(out.json));
        }
        return;
      }
      console.log('[fake-model] 404', req.method, req.url);
      res.writeHead(404).end('{"error":"not found"}');
    });
  });
  return new Promise((resolve) => server.listen(port, '127.0.0.1', () => resolve(server)));
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop())) {
  createFakeModelServer().then(() => console.log(`fake model server @ http://127.0.0.1:${PORT}/v1`));
}
