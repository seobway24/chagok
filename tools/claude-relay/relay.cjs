// 로컬 테스트 전용 중계: 에뮬레이터 앱(RemoteQuip, openai 모드) → 이 서버 → 내 PC 의 CLI 에이전트.
//   BACKEND=claude : Claude Code (`claude -p`) — 내 개인 로그인을 그대로 쓴다. 다음 프로세스를 미리 띄워 두고 받는다(≈2초)
//   BACKEND=cursor : Cursor CLI (`cursor-agent -p --mode ask`) — CURSOR_API_KEY. 요청마다 새로 띄운다(≈17초)
// 앱·APK 에는 키가 들어가지 않는다. 127.0.0.1 에만 열린다(에뮬레이터는 10.0.2.2 로 들어온다). 배포·공유 금지.
//
//   node tools/claude-relay/relay.cjs                          # claude, haiku, 8787
//   BACKEND=cursor MODEL=<id> node tools/claude-relay/relay.cjs
//   BACKEND=cursor node tools/claude-relay/relay.cjs --list-models
//   node tools/claude-relay/relay.cjs --once                   # 앱 없이 멘트 3개 시간 재기
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const os = require('os');

const BACKEND = process.env.BACKEND || 'claude';
const PORT = Number(process.env.PORT || 8787);
const MODEL = process.env.MODEL || (BACKEND === 'cursor' ? 'auto' : 'haiku');
const TIMEOUT_MS = 60_000;
// 한 프로세스가 답할 횟수. 기본 1 = 요청마다 새 프로세스(미리 띄워 둔 것)라 앞 요청 내용이 섞이지 않는다.
// 2 이상이면 대화가 이어져 앞 대답을 기억한다 — 실제 앱(상태 없는 API)보다 결과가 좋아 보이니 테스트엔 1
const TURNS_PER_PROCESS = Number(process.env.TURNS_PER_PROCESS || 1);
const SYSTEM = '너는 차 안 가족 방송 진행자다. 요청한 멘트 한 줄만 답한다. 설명·따옴표·도구 사용 없이. 매 요청은 독립적이다.';

/** chagok/.env 에서 키 하나만 꺼낸다. 값은 어디에도 찍지 않는다. */
function envKey(name) {
  if (process.env[name]) return process.env[name];
  const file = path.resolve(__dirname, '..', '..', '.env');
  if (!fs.existsSync(file)) return '';
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][\w]*)\s*=\s*(.*)$/);
    if (!m || m[1] !== name) continue;
    const v = m[2].trim();
    return v.startsWith('"') ? v.replace(/^"|"$/g, '') : v.split(/\s#/)[0].trim();
  }
  return '';
}

// Windows 의 .cmd 는 shell 경유라 인자를 직접 따옴표로 감싼다(빈 문자열은 ""로 넘겨야 사라지지 않는다)
const q = (a) => (a === '' ? '""' : /\s/.test(a) ? `"${a}"` : a);
const spawnCli = (bin, args, env = process.env) =>
  // 프로젝트 CLAUDE.md·규칙을 읽지 않게 임시 폴더에서 실행한다
  spawn(bin, args.map(q), { cwd: os.tmpdir(), shell: true, windowsHide: true, env });

// ── claude: 띄워 둔 프로세스에 stream-json 으로 한 줄씩 넣는다 ──────────────────
// 플러그인·MCP·훅·도구·기본 시스템 프롬프트를 다 끈다(--bare 는 API 키 전용이라 개인 로그인으로는 못 쓴다)
const CLAUDE_ARGS = ['-p', '--model', MODEL, '--input-format', 'stream-json', '--output-format', 'stream-json', '--verbose',
  '--strict-mcp-config', '--setting-sources', '', '--tools', '', '--disable-slash-commands',
  // 작업 폴더·OS 같은 환경 정보가 시스템 프롬프트에 섞이면 멘트에 "윈도우 11" 이 튀어나온다(10/30 시뮬레이션)
  '--exclude-dynamic-system-prompt-sections',
  '--no-session-persistence', '--system-prompt', SYSTEM];

function startClaude() {
  const child = spawnCli('claude', CLAUDE_ARGS);
  const worker = { child, turns: 0, waiting: null, dead: false };
  let buf = '';
  child.stdout.on('data', (d) => {
    buf += d;
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      let ev;
      try { ev = JSON.parse(line); } catch { continue; }
      if (ev.type !== 'result' || !worker.waiting) continue;
      const { resolve, reject } = worker.waiting;
      worker.waiting = null;
      if (ev.is_error || !String(ev.result || '').trim()) reject(new Error(`claude: ${String(ev.result || ev.subtype).slice(0, 200)}`));
      else resolve(String(ev.result).trim());
    }
  });
  child.on('close', (code) => {
    worker.dead = true;
    if (worker.waiting) worker.waiting.reject(new Error(`claude exit ${code}`));
    worker.waiting = null;
  });
  return worker;
}

let current = null;
let spare = null; // 갈아탈 때 기다리지 않도록 다음 프로세스를 미리 띄워 둔다
let queue = Promise.resolve();

function claudeWorker() {
  if (!current || current.dead || current.turns >= TURNS_PER_PROCESS) {
    if (current && !current.dead) current.child.kill();
    current = spare && !spare.dead ? spare : startClaude();
    spare = null;
  }
  if (!spare && current.turns >= TURNS_PER_PROCESS - 1) spare = startClaude();
  return current;
}

function askClaude(text) {
  // 프로세스 하나는 한 번에 한 요청만 받는다 → 줄 세운다
  const job = queue.then(() => new Promise((resolve, reject) => {
    const w = claudeWorker();
    w.turns++;
    const timer = setTimeout(() => { w.child.kill(); reject(new Error('claude timeout')); }, TIMEOUT_MS);
    w.waiting = {
      resolve: (s) => { clearTimeout(timer); resolve(s); },
      reject: (e) => { clearTimeout(timer); reject(e); },
    };
    w.child.stdin.write(JSON.stringify({ type: 'user', message: { role: 'user', content: text } }) + '\n');
  }));
  queue = job.catch(() => {});
  return job;
}

// ── cursor: 요청마다 새로 띄운다 (Cursor 에는 바로 답하는 API 가 없다) ──────────
function runCursor(extraArgs, input) {
  const key = envKey('CURSOR_API_KEY');
  if (!key) return Promise.reject(new Error('CURSOR_API_KEY 없음 (환경변수 또는 chagok/.env)'));
  return new Promise((resolve, reject) => {
    const args = ['-p', '--mode', 'ask', '--trust', '--output-format', 'text', '--model', MODEL, ...extraArgs];
    const child = spawnCli('cursor-agent', args, { ...process.env, CURSOR_API_KEY: key });
    let out = '';
    let err = '';
    const timer = setTimeout(() => { child.kill(); reject(new Error('cursor timeout')); }, TIMEOUT_MS);
    child.stdout.on('data', (d) => { out += d; });
    child.stderr.on('data', (d) => { err += d; });
    child.on('close', (code) => {
      clearTimeout(timer);
      if (code === 0 && out.trim()) resolve(out.trim());
      else reject(new Error(`cursor exit ${code}: ${(err || out).slice(0, 300)}`));
    });
    child.stdin.end(input, 'utf8');
  });
}

const askAgent = (prompt) => (BACKEND === 'cursor' ? runCursor([], `${SYSTEM}\n\n${prompt}`) : askClaude(prompt));

function send(res, status, obj) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

if (process.argv.includes('--list-models')) {
  runCursor(['--list-models'], '').then(console.log, (e) => { console.error(e.message); process.exit(1); });
} else if (process.argv.includes('--once')) {
  (async () => {
    for (const p of ['비 오는 날씨 점수를 누가 7점이라고 했다. 한 줄로 받아쳐라 (40자 이내).',
      '아빠가 퀴즈 정답을 맞혔다. 축하 멘트 한 줄 (40자 이내).',
      '막내가 휴게소 간식으로 호두과자를 골랐다. 한 줄 리액션 (40자 이내).']) {
      const t = Date.now();
      try {
        const s = await askAgent(p);
        console.log(`[${BACKEND}/${MODEL}] ${Date.now() - t}ms → ${s}`);
      } catch (e) { console.error(`[${BACKEND}/${MODEL}] ${Date.now() - t}ms 실패: ${e.message}`); process.exitCode = 1; }
    }
    if (current) current.child.kill();
    if (spare) spare.child.kill();
  })();
} else {
  if (BACKEND === 'claude') current = startClaude(); // 첫 요청 전에 미리 띄워 둔다
  http.createServer((req, res) => {
    if (req.method !== 'POST' || !req.url.endsWith('/chat/completions')) {
      return send(res, 404, { error: { message: 'POST /v1/chat/completions only' } });
    }
    let raw = '';
    req.on('data', (d) => { raw += d; });
    req.on('end', async () => {
      const started = Date.now();
      try {
        const messages = JSON.parse(raw).messages || [];
        const prompt = messages.filter((m) => m.role === 'user').map((m) => m.content).join('\n');
        const text = await askAgent(prompt);
        console.log(`[relay] ${Date.now() - started}ms  ${prompt.length}자 → ${text}`);
        send(res, 200, { choices: [{ message: { role: 'assistant', content: text } }] });
      } catch (e) {
        console.log(`[relay] 실패 ${Date.now() - started}ms  ${e.message}`);
        send(res, 502, { error: { message: e.message } });
      }
    });
  }).listen(PORT, '127.0.0.1', () => {
    console.log(`[relay] ${BACKEND}/${MODEL}  http://127.0.0.1:${PORT}/v1  (에뮬레이터: http://10.0.2.2:${PORT}/v1)`);
  });
}
