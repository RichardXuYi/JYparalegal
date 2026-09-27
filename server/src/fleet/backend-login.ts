/**
 * Main-process backend login for fleet mode.
 *
 * Login must resolve the account BEFORE any worker exists (the worker is keyed
 * by userId), so the access layer authenticates against the JY backend itself,
 * then persists the returned tokens into the account's per-user data dir. When
 * the worker for that account starts, host-core/services/backend-auth-api.ts
 * reads that same `auth.json` and can make authenticated backend calls (skill /
 * config sync) without the browser ever seeing a token.
 */
import { mkdirSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { arch, platform, release } from 'node:os';
import { dirname } from 'node:path';
import type { AuthLoginResult, AuthUser } from '../../../shared/host-api/contract';
import { BACKEND_URL } from '../env';
import { getUserAuthFile, normalizeScope } from './layout';

type ApiEnvelope<T> = { code?: number; msg?: string; data?: T };
type LoginData = { user?: unknown; accessToken?: string; refreshToken?: string; deviceId?: number };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function parseUser(value: unknown): AuthUser | null {
  if (!isRecord(value)) return null;
  const id = value.id;
  const username = value.username;
  if (typeof id !== 'number' || typeof username !== 'string') return null;
  const rolesRaw = value.roles;
  return {
    id,
    username,
    email: typeof value.email === 'string' ? value.email : undefined,
    phone: typeof value.phone === 'string' ? value.phone : undefined,
    role: typeof value.role === 'string' ? value.role : undefined,
    roles: Array.isArray(rolesRaw) ? rolesRaw.filter((r): r is string => typeof r === 'string') : undefined,
  };
}

function baseUrl(): string {
  return BACKEND_URL.replace(/\/+$/, '');
}

/** Write the account's tokens where its worker's auth store will read them. */
export function persistUserTokens(scope: string, tokens: {
  accessToken: string;
  refreshToken: string | null;
  user: AuthUser;
  deviceId?: number | null;
}): void {
  const file = getUserAuthFile(scope);
  mkdirSync(dirname(file), { recursive: true });
  const data: Record<string, unknown> = {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    user: tokens.user,
  };
  if (tokens.deviceId != null) data.deviceId = tokens.deviceId;
  const tmp = `${file}.tmp`;
  writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
  renameSync(tmp, file);
}

/** Best-effort removal of a logged-out account's tokens. */
export function clearUserTokens(scope: string): void {
  try {
    rmSync(getUserAuthFile(scope), { force: true });
  } catch {
    // ignore
  }
}

/** Detect a human-readable device name for the server. */
export function detectDeviceName(): string {
  const osName = platform() === 'win32' ? 'Windows' : platform() === 'darwin' ? 'macOS' : `Linux (${arch()})`;
  return `${osName} ${release()} - Studio Web`;
}

/**
 * Authenticate against the JY backend. On success returns the user plus the
 * scope key the fleet routes by, and writes the tokens for the worker.
 */
export async function backendLogin(username: string, password: string): Promise<
  | { ok: true; user: AuthUser; scope: string; deviceId?: number }
  | { ok: false; error: string }
> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl()}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, deviceName: detectDeviceName() }),
    });
  } catch (error) {
    return { ok: false, error: error instanceof Error ? error.message : '无法连接到服务器' };
  }

  let envelope: ApiEnvelope<LoginData> | null;
  try {
    envelope = (await response.json()) as ApiEnvelope<LoginData>;
  } catch {
    envelope = null;
  }

  const httpOk = response.status >= 200 && response.status < 300;
  const codeOk = !envelope || envelope.code === 0 || envelope.code === undefined;
  if (!httpOk || !codeOk || !isRecord(envelope?.data)) {
    return { ok: false, error: envelope?.msg || '用户名或密码错误' };
  }

  const data = envelope!.data as LoginData;
  const user = parseUser(data.user);
  if (!data.accessToken || !user) {
    return { ok: false, error: '登录响应无效' };
  }

  const scope = normalizeScope(String(user.id));
  const deviceId = typeof data.deviceId === 'number' ? data.deviceId : undefined;
  persistUserTokens(scope, {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken ?? null,
    user,
    deviceId,
  });
  return { ok: true, user, scope, deviceId };
}

/** Shape returned to the browser after a successful/failed login. */
export function toLoginResult(
  outcome: Awaited<ReturnType<typeof backendLogin>>,
): AuthLoginResult {
  if (outcome.ok) return { success: true, user: outcome.user, deviceId: outcome.deviceId };
  return { success: false, error: outcome.error };
}
