import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { BACKEND_URL } from './env';
import { verifySession } from './auth';
import { getUserAuthFile, normalizeScope } from './fleet/layout';
import { getOpenClawConfigDir } from '../host-core/utils/paths';

type Backend = {
  invoke: (scope: string, request: unknown) => Promise<unknown>;
};

type HearingEvent = { event: string; data: unknown };

const ROLES: Array<[string, string, string]> = [
  ['JUDGE', 'moot-judge', '审判长'],
  ['JUROR_A', 'moot-juror-a', '陪审员甲'],
  ['JUROR_B', 'moot-juror-b', '陪审员乙'],
  ['OURS', 'moot-ours', '我方'],
  ['OPPONENT', 'moot-opponent', '对方'],
  ['CLERK', 'moot-clerk', '书记员'],
];

const QUIET_POLLS = 6;
const POLL_MS = 1200;

let rolesReady = false;
const hearingLocks = new Set<string>();
const buses = new Map<string, { listeners: Set<(item: HearingEvent) => void>; recent: HearingEvent[] }>();

function tokenFor(request: { cookies?: Record<string, string | undefined>; headers: { authorization?: string } }): { scope: string; token: string } | null {
  const claims = verifySession(request.cookies ?? {}, request.headers.authorization);
  if (!claims) return null;
  const scope = normalizeScope(String(claims.sub));
  try {
    const file = getUserAuthFile(scope);
    if (!existsSync(file)) return null;
    const stored = JSON.parse(readFileSync(file, 'utf8')) as { accessToken?: string | null };
    if (!stored.accessToken) return null;
    return { scope, token: stored.accessToken };
  } catch {
    return null;
  }
}

async function javaApi(token: string, method: string, path: string, body?: unknown): Promise<{ status: number; data: unknown }> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body == null ? {} : { 'Content-Type': 'application/json' }) },
    body: body == null ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => null);
  return { status: res.status, data };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? value as Record<string, unknown> : null;
}

function textOf(content: unknown): string {
  if (typeof content === 'string') return content;
  if (!Array.isArray(content)) return '';
  return content.map((block) => {
    const row = asRecord(block);
    if (!row) return '';
    if (typeof row.text === 'string') return row.text;
    return '';
  }).join('');
}

async function invoke(backend: Backend, scope: string, module: string, action: string, payload?: unknown): Promise<Record<string, unknown>> {
  const response = asRecord(await backend.invoke(scope, { id: crypto.randomUUID(), module, action, payload }));
  const data = asRecord(response?.data) ?? response ?? {};
  if (response && response.ok === false) {
    const err = asRecord(response.error);
    throw new Error(typeof err?.message === 'string' ? err.message : '宿主调用失败');
  }
  return data;
}

function busFor(id: string) {
  let bus = buses.get(id);
  if (!bus) {
    bus = { listeners: new Set(), recent: [] };
    buses.set(id, bus);
  }
  return bus;
}

function publish(id: string, event: string, data: unknown): void {
  const bus = busFor(id);
  const item = { event, data };
  bus.recent.push(item);
  if (bus.recent.length > 100) bus.recent.shift();
  for (const listener of bus.listeners) listener(item);
}

function snapshotRoot(hearingId: string): string {
  return join(getOpenClawConfigDir(), 'moot-memory-snapshot', 'hearings', hearingId);
}

function failedKeyFile(): string {
  return join(getOpenClawConfigDir(), 'moot-memory-snapshot', 'failed-keys.json');
}

function readFailedKeys(): string[] {
  try {
    if (!existsSync(failedKeyFile())) return [];
    const parsed = JSON.parse(readFileSync(failedKeyFile(), 'utf8')) as unknown;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

function writeFailedKeys(keys: string[]): void {
  mkdirSync(join(getOpenClawConfigDir(), 'moot-memory-snapshot'), { recursive: true });
  writeFileSync(failedKeyFile(), JSON.stringify([...new Set(keys)]));
}

function snapshotMemories(hearingId: string): void {
  const root = snapshotRoot(hearingId);
  for (const [, id] of ROLES) {
    const ws = join(getOpenClawConfigDir(), `workspace-${id}`);
    const dest = join(root, id);
    mkdirSync(dest, { recursive: true });
    const memoryFile = join(ws, 'MEMORY.md');
    if (existsSync(memoryFile)) cpSync(memoryFile, join(dest, 'MEMORY.md'));
    const memDir = join(ws, 'memory');
    rmSync(join(dest, 'memory'), { recursive: true, force: true });
    if (existsSync(memDir)) cpSync(memDir, join(dest, 'memory'), { recursive: true });
  }
}

function restoreMemories(hearingId: string): void {
  const root = snapshotRoot(hearingId);
  if (!existsSync(root)) return;
  for (const [, id] of ROLES) {
    const ws = join(getOpenClawConfigDir(), `workspace-${id}`);
    const dest = join(root, id);
    if (!existsSync(dest)) continue;
    mkdirSync(ws, { recursive: true });
    const snap = join(dest, 'MEMORY.md');
    const live = join(ws, 'MEMORY.md');
    if (existsSync(snap)) cpSync(snap, live);
    else if (existsSync(live)) rmSync(live, { force: true });
    rmSync(join(ws, 'memory'), { recursive: true, force: true });
    if (existsSync(join(dest, 'memory'))) cpSync(join(dest, 'memory'), join(ws, 'memory'), { recursive: true });
  }
}

async function deleteSession(backend: Backend, scope: string, key: string): Promise<void> {
  await invoke(backend, scope, 'sessions', 'delete', { id: key });
}

async function retryFailedDeletes(backend: Backend, scope: string): Promise<void> {
  const left: string[] = [];
  for (const key of readFailedKeys()) {
    try {
      await deleteSession(backend, scope, key);
    } catch {
      left.push(key);
    }
  }
  writeFailedKeys(left);
}

async function ensureRoles(backend: Backend, scope: string): Promise<void> {
  if (rolesReady) return;
  const listed = await invoke(backend, scope, 'agents', 'list');
  const agents = Array.isArray(listed.agents) ? listed.agents : [];
  const ids = new Set(agents.map((item) => String(asRecord(item)?.id ?? '')));
  for (const [, id, label] of ROLES) {
    if (!ids.has(id)) {
      await invoke(backend, scope, 'agents', 'create', { name: id });
      await invoke(backend, scope, 'agents', 'update', { id, name: label });
    }
    await invoke(backend, scope, 'agents', 'updateSkills', { id, skillKeys: [] });
  }
  rolesReady = true;
}

async function historyMessages(backend: Backend, scope: string, sessionKey: string): Promise<Array<Record<string, unknown>>> {
  const history = await invoke(backend, scope, 'sessions', 'history', { sessionKey, limit: 30 });
  const messages = Array.isArray(history.messages) ? history.messages : [];
  return messages.map(asRecord).filter((item): item is Record<string, unknown> => item != null);
}

async function latestAssistant(backend: Backend, scope: string, sessionKey: string): Promise<string> {
  const messages = await historyMessages(backend, scope, sessionKey);
  const assistants = messages.filter((item) => item.role === 'assistant');
  if (!assistants.length) return '';
  return textOf(assistants[assistants.length - 1]?.content).trim();
}

function hearingView(payload: unknown): Record<string, unknown> | null {
  const env = asRecord(payload);
  return asRecord(env?.data);
}

async function registerSessions(token: string, hearingId: string, view: Record<string, unknown> | null): Promise<void> {
  const roles = Array.isArray(view?.roles) ? view.roles : [];
  const map: Record<string, string> = {};
  let missing = false;
  for (const item of roles.map(asRecord)) {
    if (!item) continue;
    const code = String(item.role_code ?? '');
    if (!code) continue;
    if (typeof item.session_key === 'string' && item.session_key) {
      map[code] = item.session_key;
    } else {
      missing = true;
      map[code] = `agent:${item.agent_id}:h${hearingId}`;
    }
  }
  if (missing) await javaApi(token, 'PUT', `/api/moot/hearings/${hearingId}/sessions`, map);
}

function unauthenticated(reply: FastifyReply) {
  return reply.code(401).send({ code: 401, msg: 'unauthenticated', data: null });
}

export function registerMootDriver(app: FastifyInstance, backend: Backend): void {
  app.post('/platform/moot/cases/:caseId/open', async (request, reply) => {
    const auth = tokenFor(request);
    if (!auth) return unauthenticated(reply);
    const caseId = (request.params as { caseId: string }).caseId;
    await ensureRoles(backend, auth.scope);
    await retryFailedDeletes(backend, auth.scope);
    const body = asRecord(request.body) ?? {};
    const humanRoles = Array.isArray(body.humanRoles) ? body.humanRoles.map(String) : [];
    const opened = await javaApi(auth.token, 'POST', `/api/moot/cases/${caseId}/hearings`, { humanRoles });
    const view = hearingView(opened.data);
    const hearing = asRecord(view?.hearing);
    const hearingId = hearing?.id == null ? '' : String(hearing.id);
    if (!view || !hearingId || asRecord(opened.data)?.code !== 0) {
      return reply.code(opened.status).send(opened.data);
    }
    if (!existsSync(snapshotRoot(hearingId))) snapshotMemories(hearingId);
    await registerSessions(auth.token, hearingId, view);
    const refreshed = await javaApi(auth.token, 'GET', `/api/moot/hearings/${hearingId}`);
    return reply.code(refreshed.status).send(refreshed.data);
  });

  app.get('/platform/moot/hearings/:id/events', async (request, reply) => {
    const auth = tokenFor(request);
    if (!auth) return unauthenticated(reply);
    const id = (request.params as { id: string }).id;
    const bus = busFor(id);
    reply.hijack();
    reply.raw.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    const write = (item: HearingEvent) => {
      reply.raw.write(`event: ${item.event}\ndata: ${JSON.stringify(item.data)}\n\n`);
    };
    for (const item of bus.recent) write(item);
    bus.listeners.add(write);
    request.raw.on('close', () => {
      bus.listeners.delete(write);
    });
  });

  app.post('/platform/moot/hearings/:id/step', async (request: FastifyRequest, reply: FastifyReply) => {
    const auth = tokenFor(request);
    if (!auth) return unauthenticated(reply);
    const id = (request.params as { id: string }).id;
    if (hearingLocks.has(id)) {
      return reply.code(409).send({ code: 409, msg: '已有窗口在推进这一场', data: null });
    }
    hearingLocks.add(id);
    reply.hijack();
    reply.raw.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    const send = (event: string, data: unknown) => {
      reply.raw.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      publish(id, event, data);
    };
    const skipSpeaking = async (slotId: unknown) => {
      if (slotId == null) return;
      await javaApi(auth.token, 'POST', `/api/moot/hearings/${id}/turns/skip`, { slotId });
    };
    try {
      await ensureRoles(backend, auth.scope);
      const prior = hearingView((await javaApi(auth.token, 'GET', `/api/moot/hearings/${id}`)).data);
      const slots = Array.isArray(prior?.slots) ? prior.slots.map(asRecord) : [];
      const current = slots.find((slot) => slot?.status === 'SPEAKING') ?? slots.find((slot) => slot?.status === 'PENDING');
      const roleCode = current?.role_code == null ? '' : String(current.role_code);
      const roles = Array.isArray(prior?.roles) ? prior.roles.map(asRecord) : [];
      const role = roles.find((item) => item?.role_code === roleCode);
      const sessionKey = typeof role?.session_key === 'string' ? role.session_key : '';
      const utterances = Array.isArray(prior?.utterances) ? prior.utterances.map(asRecord) : [];
      const spokeBefore = utterances.some((line) => line?.role_code === roleCode);
      let sessionLost = false;
      if (spokeBefore && sessionKey) {
        const count = await historyMessages(backend, auth.scope, sessionKey).then((rows) => rows.length).catch(() => 0);
        sessionLost = count === 0;
      }
      const prepared = await javaApi(
        auth.token,
        'POST',
        `/api/moot/hearings/${id}/turns/prepare${sessionLost ? '?sessionLost=true' : ''}`,
      );
      const env = asRecord(prepared.data);
      if (!env || env.code !== 0) {
        send('error', { msg: String(env?.msg ?? '准备失败') });
        return;
      }
      const turn = asRecord(env.data) ?? {};
      send('slot-started', { slotId: turn.slotId, role: turn.role, plan: turn.plan });
      if (turn.awaitHuman === true) {
        send('await-human', turn);
        return;
      }
      const activeKey = typeof turn.sessionKey === 'string' && turn.sessionKey ? turn.sessionKey : sessionKey;
      if (!activeKey) throw new Error('这一槽没有会话');
      const before = await latestAssistant(backend, auth.scope, activeKey).catch(() => '');
      const sent = await invoke(backend, auth.scope, 'chat', 'sendWithMedia', {
        sessionKey: activeKey,
        message: String(turn.message ?? ''),
        deliver: false,
        idempotencyKey: `moot:${id}:${String(turn.slotId ?? '')}`,
      });
      if (sent.success === false) throw new Error(String(sent.error ?? '发送失败'));
      let shown = before;
      let stable = 0;
      let finalText = '';
      let finished = false;
      const started = Date.now();
      while (Date.now() - started < 90_000) {
        await new Promise((resolve) => setTimeout(resolve, POLL_MS));
        const latest = await latestAssistant(backend, auth.scope, activeKey).catch(() => '');
        if (!latest || latest === before) continue;
        send('delta', { text: latest });
        if (latest === shown) stable += 1;
        else { shown = latest; stable = 0; }
        finalText = latest;
        if (stable >= QUIET_POLLS) { finished = true; break; }
      }
      if (!finished || !finalText) {
        await skipSpeaking(turn.slotId);
        send('error', { msg: '这一槽超过 90 秒没有说完' });
        return;
      }
      const committed = await javaApi(auth.token, 'POST', `/api/moot/hearings/${id}/turns/commit`, {
        slotId: turn.slotId,
        body: finalText,
        speaker: 'AGENT',
      });
      const committedView = hearingView(committed.data);
      send('slot-committed', committedView ?? committed.data);
    } catch (error) {
      const prepared = await javaApi(auth.token, 'GET', `/api/moot/hearings/${id}`);
      const data = hearingView(prepared.data);
      const slots = Array.isArray(data?.slots) ? data.slots.map(asRecord) : [];
      const speaking = slots.find((slot) => slot?.status === 'SPEAKING');
      if (speaking?.id != null) await skipSpeaking(speaking.id).catch(() => undefined);
      send('error', { msg: error instanceof Error ? error.message : '发言失败' });
    } finally {
      hearingLocks.delete(id);
      reply.raw.end();
    }
  });

  app.post('/platform/moot/hearings/:id/cleanup', async (request, reply) => {
    const auth = tokenFor(request);
    if (!auth) return unauthenticated(reply);
    const id = (request.params as { id: string }).id;
    const closed = await javaApi(auth.token, 'POST', `/api/moot/hearings/${id}/close`);
    const env = asRecord(closed.data) ?? {};
    const data = asRecord(env.data) ?? {};
    const keys = Array.isArray(data.closedSessions) ? data.closedSessions : [];
    const failures: string[] = [];
    for (const item of keys.map(asRecord)) {
      const key = item?.session_key;
      if (typeof key !== 'string' || !key.startsWith('agent:')) continue;
      try {
        await deleteSession(backend, auth.scope, key);
      } catch {
        failures.push(key);
      }
    }
    writeFailedKeys([...readFailedKeys(), ...failures]);
    data.failedSessions = failures;
    env.data = data;
    restoreMemories(id);
    publish(id, 'hearing-closed', data);
    return reply.code(closed.status).send(env);
  });
}
