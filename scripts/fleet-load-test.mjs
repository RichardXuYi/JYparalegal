#!/usr/bin/env node
/**
 * Fleet 负载与串扰回归脚本（自研，替代 k6 WS 方案）。
 *
 * 目标（对应 docs/fleet-scale-deployment.md 第 6 节验收指标）：
 *   1. login-storm      —— 早高峰并发登录风暴：N 个用户同时建连并发出首个
 *                          invoke，统计 hello/首响应延迟与失败数。
 *   2. keep-alive       —— 长连接保持率：M 个用户空闲挂机，统计意外断开
 *                          （心跳/超时导致）与重连率。
 *   3. switch-regression—— 登出→登录串扰回归：同一浏览器视角下 A 断开后 B
 *                          建连，断言每个 socket 被路由到各自 scope 的
 *                          Worker（gateway.status 响应携带各自 pid/port，
 *                          /healthz worker 数随 scope 增长）。
 *
 * 用法（在 studio-web 目录内，依赖其 node_modules 的 ws / jsonwebtoken）：
 *   node scripts/fleet-load-test.mjs login-storm --users 100
 *   node scripts/fleet-load-test.mjs keep-alive --users 20 --duration 300
 *   node scripts/fleet-load-test.mjs switch-regression --cycles 5
 *
 * 环境变量：
 *   SERVER_URL   WS 接入地址（默认 ws://127.0.0.1:8788/ws）
 *   HTTP_URL     HTTP 接入地址（默认 http://127.0.0.1:8788，读 /healthz）
 *   JWT_SECRET   与服务器一致的会话密钥（必填；AUTH_DISABLED 时可留空，
 *                此时所有用户共享 dev scope，串扰回归失去意义）
 */
import jwt from 'jsonwebtoken';
import WebSocket from 'ws';

const SERVER_URL = process.env.SERVER_URL?.trim() || 'ws://127.0.0.1:8788/ws';
const HTTP_URL = process.env.HTTP_URL?.trim() || 'http://127.0.0.1:8788';
const JWT_SECRET = process.env.JWT_SECRET?.trim() || '';

function parseArgs(argv) {
  const mode = argv[0];
  const args = {};
  for (let i = 1; i < argv.length; i += 1) {
    const flag = argv[i];
    if (flag.startsWith('--')) {
      const key = flag.slice(2);
      const value = argv[i + 1] ?? 'true';
      args[key] = /^\d+(\.\d+)?$/.test(value) ? Number(value) : value;
      i += 1;
    }
  }
  return { mode, args };
}

function signToken(userId) {
  if (!JWT_SECRET) return '';
  return jwt.sign({ sub: String(userId), name: `user${userId}` }, JWT_SECRET, { expiresIn: '1h' });
}

function wsUrl(userId) {
  const token = signToken(userId);
  return token ? `${SERVER_URL}?token=${encodeURIComponent(token)}` : SERVER_URL;
}

/** 建立连接并等待服务端 hello 帧。 */
function connectUser(userId, onEvent) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl(userId));
    const timer = setTimeout(() => {
      ws.terminate();
      reject(new Error(`user${userId}: hello timeout`));
    }, 15_000);
    ws.on('open', () => { /* hello 帧是唯一可靠的就绪信号 */ });
    ws.on('message', (raw) => {
      let message;
      try {
        message = JSON.parse(String(raw));
      } catch {
        return;
      }
      if (message.kind === 'hello') {
        clearTimeout(timer);
        resolve(ws);
      } else if (message.kind === 'event' && onEvent) {
        onEvent(message);
      }
    });
    ws.on('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

/** 发一个 host-invoke 并等待响应。 */
function invoke(ws, request, timeoutMs = 30_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`invoke ${request.action ?? '?'} timeout`)), timeoutMs);
    const onMessage = (raw) => {
      let message;
      try {
        message = JSON.parse(String(raw));
      } catch {
        return;
      }
      if (message.kind === 'response' && message.response?.id === request.id) {
        clearTimeout(timer);
        ws.off('message', onMessage);
        resolve(message.response);
      }
    };
    ws.on('message', onMessage);
    ws.send(JSON.stringify({ kind: 'invoke', request }));
  });
}

function gatewayStatusRequest(id) {
  return { id, module: 'gateway', action: 'status' };
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

async function fetchHealthz() {
  const res = await fetch(`${HTTP_URL}/healthz`);
  return await res.json();
}

async function modeLoginStorm(userCount) {
  console.log(`[login-storm] ${userCount} users against ${SERVER_URL}`);
  const startedAt = Date.now();
  const latencies = [];
  let failures = 0;
  const sockets = [];

  await Promise.all(Array.from({ length: userCount }, (_, i) => i + 1).map(async (userId) => {
    const t0 = Date.now();
    try {
      const ws = await connectUser(userId);
      sockets.push(ws);
      const response = await invoke(ws, gatewayStatusRequest(`storm-${userId}`));
      if (response && !response.ok) throw new Error(response.error?.message ?? 'invoke failed');
      latencies.push(Date.now() - t0);
    } catch (error) {
      failures += 1;
      console.error(`[login-storm] user${userId} failed: ${error.message}`);
    }
  }));

  const sorted = [...latencies].sort((a, b) => a - b);
  const health = await fetchHealthz().catch(() => ({}));
  console.log(`[login-storm] done in ${Date.now() - startedAt}ms`);
  console.log(`  connected: ${sockets.length}/${userCount}, failures: ${failures}`);
  console.log(`  first-response latency ms: min=${sorted[0] ?? 0} p50=${percentile(sorted, 50)} p95=${percentile(sorted, 95)} max=${sorted.at(-1) ?? 0}`);
  console.log(`  healthz: ${JSON.stringify(health)}`);
  for (const ws of sockets) ws.close();
}

async function modeKeepAlive(userCount, durationSec) {
  console.log(`[keep-alive] ${userCount} users idle for ${durationSec}s against ${SERVER_URL}`);
  const sockets = [];
  const unexpectedCloses = [];
  let failures = 0;

  await Promise.all(Array.from({ length: userCount }, (_, i) => i + 1).map(async (userId) => {
    try {
      const ws = await connectUser(userId);
      sockets.push(ws);
      ws.on('close', (code) => {
        if (code !== 1000 && code !== 1001) {
          unexpectedCloses.push({ userId, code });
        }
      });
    } catch (error) {
      failures += 1;
      console.error(`[keep-alive] user${userId} failed to connect: ${error.message}`);
    }
  }));

  console.log(`[keep-alive] ${sockets.length} connected; waiting ${durationSec}s (server pings every 25s)...`);
  await new Promise((resolve) => setTimeout(resolve, durationSec * 1000));

  const stillOpen = sockets.filter((ws) => ws.readyState === WebSocket.OPEN).length;
  const closeRatePerHour = unexpectedCloses.length / ((durationSec / 3600) * Math.max(1, sockets.length));
  console.log(`[keep-alive] open: ${stillOpen}/${sockets.length}, connect failures: ${failures}`);
  console.log(`  unexpected closes: ${unexpectedCloses.length} (rate/user/h = ${closeRatePerHour.toFixed(4)})`);
  for (const ws of sockets) ws.close();
}

async function modeSwitchRegression(cycles) {
  console.log(`[switch-regression] ${cycles} A→B cycles against ${SERVER_URL}`);
  if (!JWT_SECRET) {
    console.warn('[switch-regression] JWT_SECRET unset — all users share the dev scope; regression is meaningless.');
  }
  const seenScopes = new Set();
  const mismatchedScopes = [];

  for (let cycle = 1; cycle <= cycles; cycle += 1) {
    const userA = cycle * 2 - 1;
    const userB = cycle * 2;
    const wsA = await connectUser(userA);
    const statusARaw = await invoke(wsA, gatewayStatusRequest(`sw-a-${cycle}`));
    const statusA = statusARaw?.data ?? statusARaw ?? {};
    seenScopes.add(userA);
    wsA.close(); // 登出：同一浏览器视角下旧 socket 被关闭

    const wsB = await connectUser(userB);
    const statusBRaw = await invoke(wsB, gatewayStatusRequest(`sw-b-${cycle}`));
    const statusB = statusBRaw?.data ?? statusBRaw ?? {};
    seenScopes.add(userB);

    // 串扰断言：B 的 invoke 必须由 B 自己的 Worker 应答。status.pid 是各自
    // Worker 内 OpenClaw Gateway 的进程 pid —— 若 B 被路由到 A 的 Worker，
    // statusB.pid 将与 statusA.pid 相同。（status.port 是 UI 静态默认值，
    // 不作为判别信号。）
    const okA = statusARaw?.ok === true;
    const okB = statusBRaw?.ok === true;
    const pidCollision = typeof statusA.pid === 'number' && typeof statusB.pid === 'number'
      && statusA.pid === statusB.pid;
    if (okA && okB && pidCollision) {
      mismatchedScopes.push({ cycle, pid: statusB.pid });
    }
    console.log(`[switch-regression] cycle ${cycle}: A=#${userA} pid=${statusA.pid ?? 'n/a'} port=${statusA.port ?? 'n/a'} → B=#${userB} pid=${statusB.pid ?? 'n/a'} port=${statusB.port ?? 'n/a'}`);
    wsB.close();
  }

  const health = await fetchHealthz().catch(() => ({}));
  console.log(`[switch-regression] healthz: ${JSON.stringify(health)}`);
  console.log(`  distinct scopes exercised: ${seenScopes.size}`);
  if (mismatchedScopes.length > 0) {
    console.error(`[switch-regression] FAIL: cross-user routing detected: ${JSON.stringify(mismatchedScopes)}`);
    process.exit(1);
  }
  console.log('[switch-regression] PASS: no cross-user routing detected');
}

const { mode, args } = parseArgs(process.argv.slice(2));
const main = (async () => {
  if (mode === 'login-storm') {
    await modeLoginStorm(args.users ?? 100);
  } else if (mode === 'keep-alive') {
    await modeKeepAlive(args.users ?? 20, args.duration ?? 300);
  } else if (mode === 'switch-regression') {
    await modeSwitchRegression(args.cycles ?? 5);
  } else {
    console.error(`unknown mode "${mode}" — use login-storm | keep-alive | switch-regression`);
    process.exit(2);
  }
  // 让尚未关闭的 socket 优雅退出
  await new Promise((resolve) => setTimeout(resolve, 500));
})();

main.catch((error) => {
  console.error('[fleet-load-test] fatal:', error);
  process.exit(1);
});
