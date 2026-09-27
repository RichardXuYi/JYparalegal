import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { GatewayManager } from '../gateway/manager';
import { getOpenClawConfigDir } from '../utils/paths';
import { createAgentsApi } from './agents-api';
import { proxyPlatformRequest } from './backend-auth-api';
import { createChatApi } from './chat-api';
import { createSessionsApi } from './sessions-api';

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
const buses = new Map<string, HearingEvent[]>();

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? value as Record<string, unknown> : null;
}

function envelope(body: string): { code?: number; msg?: string; data?: Record<string, unknown> } | null {
  try {
    return JSON.parse(body) as { code?: number; msg?: string; data?: Record<string, unknown> };
  } catch {
    return null;
  }
}

function textOf(content: unknown): string {
  if (typeof content === 'string') return content;
  if (!Array.isArray(content)) return '';
  return content.map((block) => {
    const row = asRecord(block);
    return row && typeof row.text === 'string' ? row.text : '';
  }).join('');
}

function jsonResult(status: number, body: unknown): { status: number; body: string } {
  return { status, body: JSON.stringify(body) };
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
    rmSync(join(dest, 'memory'), { recursive: true, force: true });
    const memDir = join(ws, 'memory');
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

function publish(id: string, event: string, data: unknown): void {
  const recent = buses.get(id) ?? [];
  recent.push({ event, data });
  if (recent.length > 100) recent.shift();
  buses.set(id, recent);
}

async function ensureRoles(gateway: GatewayManager): Promise<void> {
  if (rolesReady) return;
  const agents = createAgentsApi({ gatewayManager: gateway });
  const listed = await agents.list();
  const ids = new Set((listed.agents ?? []).map((item) => item.id));
  for (const [, id, label] of ROLES) {
    if (!ids.has(id)) {
      await agents.create({ name: id });
      await agents.update({ id, name: label });
    }
    await agents.updateSkills({ id, skillKeys: [] });
  }
  rolesReady = true;
}

async function deleteSession(gateway: GatewayManager, key: string): Promise<void> {
  const result = await createSessionsApi({ gatewayManager: gateway }).delete({ id: key });
  if (result && result.success === false) throw new Error(result.error || '删除会话失败');
}

async function retryFailedDeletes(gateway: GatewayManager): Promise<void> {
  const left: string[] = [];
  for (const key of readFailedKeys()) {
    try {
      await deleteSession(gateway, key);
    } catch {
      left.push(key);
    }
  }
  writeFailedKeys(left);
}

async function historyMessages(gateway: GatewayManager, sessionKey: string): Promise<Array<Record<string, unknown>>> {
  const result = await createSessionsApi({ gatewayManager: gateway }).history({ sessionKey, limit: 30 });
  if (!result || result.success === false || !Array.isArray(result.messages)) return [];
  return result.messages.map(asRecord).filter((item): item is Record<string, unknown> => item != null);
}

async function latestAssistant(gateway: GatewayManager, sessionKey: string): Promise<string> {
  const assistants = (await historyMessages(gateway, sessionKey)).filter((item) => item.role === 'assistant');
  if (!assistants.length) return '';
  return textOf(assistants[assistants.length - 1]?.content).trim();
}

async function java(path: string, method: string, body?: unknown): Promise<{ status: number; body: string }> {
  return proxyPlatformRequest({
    path,
    method,
    body: body == null ? undefined : JSON.stringify(body),
  });
}

function sse(lines: string[]): { status: number; body: string } {
  return { status: 200, body: `${lines.join('\n')}\n` };
}

export function multipartBody(fileName: string, bytes: Uint8Array): { contentType: string; body: Uint8Array } {
  const boundary = `----moot${crypto.randomUUID().replace(/-/g, '')}`;
  const safeName = fileName.replace(/["\r\n]/g, '_');
  const head = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${safeName}"\r\nContent-Type: application/octet-stream\r\n\r\n`,
  );
  const tail = Buffer.from(`\r\n--${boundary}--\r\n`);
  return {
    contentType: `multipart/form-data; boundary=${boundary}`,
    body: Buffer.concat([head, Buffer.from(bytes), tail]),
  };
}

export async function desktopHearingOpen(gateway: GatewayManager, path: string, body: string | null): Promise<{ status: number; body: string }> {
  const caseId = /cases\/([^/]+)\/open/.exec(path)?.[1];
  if (!caseId) return jsonResult(400, { code: 400, msg: '缺少案卷编号', data: null });
  await ensureRoles(gateway);
  await retryFailedDeletes(gateway);
  let parsed: Record<string, unknown> | null;
  try {
    parsed = body ? asRecord(JSON.parse(body)) : null;
  } catch {
    parsed = null;
  }
  const humanRoles = Array.isArray(parsed?.humanRoles) ? parsed.humanRoles.map(String) : [];
  const opened = await java(`/platform/moot/cases/${caseId}/hearings`, 'POST', { humanRoles });
  const env = envelope(opened.body);
  const hearingId = env?.data?.hearing && asRecord(env.data.hearing)?.id;
  if (!env || env.code !== 0 || hearingId == null) return opened;
  const id = String(hearingId);
  if (!existsSync(snapshotRoot(id))) snapshotMemories(id);
  await registerSessions(id, env.data ?? null);
  return java(`/platform/moot/hearings/${id}`, 'GET');
}

async function registerSessions(hearingId: string, view: Record<string, unknown> | null): Promise<void> {
  const roles = Array.isArray(view?.roles) ? view.roles.map(asRecord) : [];
  const map: Record<string, string> = {};
  let missing = false;
  for (const item of roles) {
    if (!item) continue;
    const code = String(item.role_code ?? '');
    if (!code) continue;
    if (typeof item.session_key === 'string' && item.session_key) map[code] = item.session_key;
    else {
      missing = true;
      map[code] = `agent:${item.agent_id}:h${hearingId}`;
    }
  }
  if (missing) await java(`/platform/moot/hearings/${hearingId}/sessions`, 'PUT', map);
}

export function desktopHearingEvents(path: string): { status: number; body: string } {
  const id = /hearings\/([^/]+)\/events/.exec(path)?.[1];
  if (!id) return jsonResult(400, { code: 400, msg: '缺少庭审编号', data: null });
  const lines: string[] = [];
  for (const item of buses.get(id) ?? []) {
    lines.push(`event: ${item.event}`, `data: ${JSON.stringify(item.data)}`, '');
  }
  return sse(lines);
}

/** 桌面版把「下一发言槽」留在本进程。返回 SSE 文本。 */
export async function desktopHearingStep(gateway: GatewayManager, path: string): Promise<{ status: number; body: string }> {
  const id = /hearings\/([^/]+)\/step/.exec(path)?.[1];
  if (!id) return jsonResult(400, { code: 400, msg: '缺少庭审编号', data: null });
  if (hearingLocks.has(id)) return jsonResult(409, { code: 409, msg: '已有窗口在推进这一场', data: null });
  hearingLocks.add(id);
  const lines: string[] = [];
  const send = (event: string, data: unknown) => {
    lines.push(`event: ${event}`, `data: ${JSON.stringify(data)}`, '');
    publish(id, event, data);
  };
  const skipSpeaking = async (slotId: unknown) => {
    if (slotId == null) return;
    await java(`/platform/moot/hearings/${id}/turns/skip`, 'POST', { slotId });
  };
  try {
    await ensureRoles(gateway);
    const prior = envelope((await java(`/platform/moot/hearings/${id}`, 'GET')).body);
    const slots = Array.isArray(prior?.data?.slots) ? prior.data.slots.map(asRecord) : [];
    const current = slots.find((slot) => slot?.status === 'SPEAKING') ?? slots.find((slot) => slot?.status === 'PENDING');
    const roleCode = current?.role_code == null ? '' : String(current.role_code);
    const roles = Array.isArray(prior?.data?.roles) ? prior.data.roles.map(asRecord) : [];
    const role = roles.find((item) => item?.role_code === roleCode);
    const sessionKey = typeof role?.session_key === 'string' ? role.session_key : '';
    const utterances = Array.isArray(prior?.data?.utterances) ? prior.data.utterances.map(asRecord) : [];
    const spokeBefore = utterances.some((line) => line?.role_code === roleCode);
    let sessionLost = false;
    if (spokeBefore && sessionKey) {
      sessionLost = (await historyMessages(gateway, sessionKey)).length === 0;
    }
    const prepared = envelope((await java(
      `/platform/moot/hearings/${id}/turns/prepare${sessionLost ? '?sessionLost=true' : ''}`,
      'POST',
      {},
    )).body);
    if (!prepared || prepared.code !== 0) {
      send('error', { msg: prepared?.msg || '准备失败' });
      return sse(lines);
    }
    const turn = prepared.data ?? {};
    send('slot-started', { slotId: turn.slotId, role: turn.role, plan: turn.plan });
    if (turn.awaitHuman === true) {
      send('await-human', turn);
      return sse(lines);
    }
    const activeKey = typeof turn.sessionKey === 'string' && turn.sessionKey ? turn.sessionKey : sessionKey;
    if (!activeKey) throw new Error('这一槽没有会话');
    const before = await latestAssistant(gateway, activeKey);
    const sent = await createChatApi({ gatewayManager: gateway }).sendWithMedia({
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
      const text = await latestAssistant(gateway, activeKey);
      if (!text || text === before) continue;
      send('delta', { text });
      if (text === shown) stable += 1;
      else { shown = text; stable = 0; }
      finalText = text;
      if (stable >= QUIET_POLLS) { finished = true; break; }
    }
    if (!finished || !finalText) {
      await skipSpeaking(turn.slotId);
      send('error', { msg: '这一槽超过 90 秒没有说完' });
      return sse(lines);
    }
    const committed = envelope((await java(`/platform/moot/hearings/${id}/turns/commit`, 'POST', {
      slotId: turn.slotId,
      body: finalText,
      speaker: 'AGENT',
    })).body);
    send('slot-committed', committed?.data ?? committed);
    return sse(lines);
  } catch (error) {
    const prepared = envelope((await java(`/platform/moot/hearings/${id}`, 'GET')).body);
    const slots = Array.isArray(prepared?.data?.slots) ? prepared.data.slots.map(asRecord) : [];
    const speaking = slots.find((slot) => slot?.status === 'SPEAKING');
    if (speaking?.id != null) await skipSpeaking(speaking.id).catch(() => undefined);
    send('error', { msg: error instanceof Error ? error.message : '发言失败' });
    return sse(lines);
  } finally {
    hearingLocks.delete(id);
  }
}

export async function desktopHearingCleanup(gateway: GatewayManager, path: string): Promise<{ status: number; body: string }> {
  const id = /hearings\/([^/]+)\/cleanup/.exec(path)?.[1];
  if (!id) return jsonResult(400, { code: 400, msg: '缺少庭审编号', data: null });
  const closed = await java(`/platform/moot/hearings/${id}/close`, 'POST', {});
  const env = envelope(closed.body);
  if (!env || !env.data) return closed;
  const keys = Array.isArray(env.data.closedSessions) ? env.data.closedSessions.map(asRecord) : [];
  const failures: string[] = [];
  for (const item of keys) {
    const key = item?.session_key;
    if (typeof key !== 'string' || !key.startsWith('agent:')) continue;
    try {
      await deleteSession(gateway, key);
    } catch {
      failures.push(key);
    }
  }
  writeFailedKeys([...readFailedKeys(), ...failures]);
  env.data.failedSessions = failures;
  restoreMemories(id);
  publish(id, 'hearing-closed', env.data);
  return jsonResult(closed.status, env);
}
