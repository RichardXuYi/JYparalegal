/**
 * Backend Auth API (main process)
 *
 * Handles all HTTP communication with the JY backend for cross-platform login.
 * The renderer never talks to the backend directly (see harness backend
 * communication boundary) — it only calls `invokeHost('auth', ...)`, which is
 * dispatched here. Tokens are persisted in a dedicated electron-store instance
 * and an expired access token is transparently refreshed once.
 */
import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import type { GatewayManager } from '../gateway/manager';
import type {
  AuthLoginResult,
  AuthStateSnapshot,
  AuthUser,
  AuthDeviceListResult,
  HostSuccess,
} from '@shared/host-api/contract';
import { isRecord } from './payload-utils';
import { setActiveScopeUser } from '../utils/user-scope';
import { platform, release, arch } from 'node:os';

const DEFAULT_BASE_URL = 'http://localhost:8181';

function getBaseUrl(): string {
  const raw = process.env.JY_API_BASE_URL?.trim();
  const base = raw && raw.length > 0 ? raw : DEFAULT_BASE_URL;
  return base.replace(/\/+$/, '');
}

/** Envelope returned by the backend: { code, msg, data }. code === 0 means success. */
type ApiEnvelope<T> = { code?: number; msg?: string; data?: T };

type StoredAuth = {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  deviceId: number | null;
};

const EMPTY_AUTH: StoredAuth = { accessToken: null, refreshToken: null, user: null, deviceId: null };

// Lazy-load electron-store (ESM module) with a dedicated file for auth tokens.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let authStoreInstance: any = null;

async function getAuthStore() {
  if (!authStoreInstance) {
    const Store = (await import('electron-store')).default;
    authStoreInstance = new Store<StoredAuth>({
      name: 'auth',
      defaults: EMPTY_AUTH,
    });
  }
  return authStoreInstance;
}

async function readAuth(): Promise<StoredAuth> {
  const store = await getAuthStore();
  return {
    accessToken: store.get('accessToken') ?? null,
    refreshToken: store.get('refreshToken') ?? null,
    user: store.get('user') ?? null,
    deviceId: store.get('deviceId') ?? null,
  };
}

async function writeAuth(next: StoredAuth): Promise<void> {
  const store = await getAuthStore();
  store.set('accessToken', next.accessToken);
  store.set('refreshToken', next.refreshToken);
  store.set('user', next.user);
  store.set('deviceId', next.deviceId);
}

async function clearAuth(): Promise<void> {
  await writeAuth({ ...EMPTY_AUTH });
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

async function postJson<T>(path: string, body: unknown, accessToken?: string | null): Promise<{
  status: number;
  envelope: ApiEnvelope<T> | null;
}> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(`${getBaseUrl()}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body ?? {}),
  });
  const envelope = await safeJson<T>(response);
  return { status: response.status, envelope };
}

async function getJson<T>(path: string, accessToken?: string | null): Promise<{
  status: number;
  envelope: ApiEnvelope<T> | null;
}> {
  const headers: Record<string, string> = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(`${getBaseUrl()}${path}`, { method: 'GET', headers });
  const envelope = await safeJson<T>(response);
  return { status: response.status, envelope };
}

async function safeJson<T>(response: Response): Promise<ApiEnvelope<T> | null> {
  try {
    return (await response.json()) as ApiEnvelope<T>;
  } catch {
    return null;
  }
}

function isOk(status: number, envelope: ApiEnvelope<unknown> | null): boolean {
  return status >= 200 && status < 300 && (!envelope || envelope.code === 0 || envelope.code === undefined);
}

type LoginData = {
  user?: unknown;
  accessToken?: string;
  refreshToken?: string;
  deviceId?: number;
};

/** Try to rotate the stored refresh token. Returns the new access token on success. */
async function tryRefresh(): Promise<string | null> {
  const { refreshToken } = await readAuth();
  if (!refreshToken) return null;
  const { status, envelope } = await postJson<LoginData>('/api/auth/refresh', { refreshToken });
  if (!isOk(status, envelope) || !isRecord(envelope?.data)) {
    await clearAuth();
    return null;
  }
  const data = envelope!.data as LoginData;
  const current = await readAuth();
  await writeAuth({
    accessToken: data.accessToken ?? null,
    refreshToken: data.refreshToken ?? current.refreshToken,
    user: current.user,
    deviceId: data.deviceId ?? current.deviceId ?? null,
  });
  return data.accessToken ?? null;
}

async function toState(): Promise<AuthStateSnapshot> {
  const { accessToken, user } = await readAuth();
  return { isAuthenticated: Boolean(accessToken && user), user };
}

/** Result of an authenticated backend call made from a main-process service. */
export type AuthorizedResult<T> = {
  ok: boolean;
  status: number;
  data: T | null;
  error?: string;
  requiresAuth?: boolean;
};

/**
 * Make an authenticated JSON request to the JY backend from any main-process
 * service, reusing the stored access token and transparently refreshing once on
 * a 401. Renderer code must never call this directly — it goes through the
 * host-api bridge (see the backend communication boundary).
 */
export async function authorizedJsonRequest<T>(
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<AuthorizedResult<T>> {
  const { accessToken } = await readAuth();
  if (!accessToken) {
    return { ok: false, status: 401, data: null, error: 'not_authenticated', requiresAuth: true };
  }

  const runFetch = async (token: string) => {
    const headers: Record<string, string> = {};
    if (init.body !== undefined) headers['Content-Type'] = 'application/json';
    headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${getBaseUrl()}${path}`, {
      method: init.method ?? 'GET',
      headers,
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
    });
    const envelope = await safeJson<T>(response);
    return { response, envelope };
  };

  let { response, envelope } = await runFetch(accessToken);
  if (response.status === 401) {
    const refreshed = await tryRefresh().catch(() => null);
    if (!refreshed) {
      return { ok: false, status: 401, data: null, error: 'not_authenticated', requiresAuth: true };
    }
    ({ response, envelope } = await runFetch(refreshed));
  }

  const ok = isOk(response.status, envelope);
  return {
    ok,
    status: response.status,
    data: (envelope?.data ?? null) as T | null,
    error: ok ? undefined : (envelope?.msg || `HTTP ${response.status}`),
    requiresAuth: response.status === 401,
  };
}

type AuthApiContext = {
  gatewayManager: GatewayManager;
};

/**
 * Switch the active OpenClaw user scope after a login/logout. When the
 * scoped state dir changes, restart the Gateway in the background so it
 * relaunches with the new `OPENCLAW_STATE_DIR` (and per-scope settings /
 * provider stores). Auth responses are never blocked on the restart.
 */
function applyScopeChange(ctx: AuthApiContext | undefined, userId: number | null): void {
  const scopeChanged = setActiveScopeUser(userId);
  if (!scopeChanged || !ctx) return;
  if (ctx.gatewayManager.getStatus().state === 'stopped') return;
  void ctx.gatewayManager.restart().then(
    () => console.log('[auth] Gateway restarted after account scope change'),
    (err) => console.warn('[auth] Gateway restart after account scope change failed:', err),
  );
}

export function createAuthApi(ctx?: AuthApiContext): CompleteHostServiceRegistry['auth'] {
  return {
    login: async (payload): Promise<AuthLoginResult> => {
      const username = typeof payload?.username === 'string' ? payload.username.trim() : '';
      const password = typeof payload?.password === 'string' ? payload.password : '';
      if (!username || !password) {
        return { success: false, error: '请输入账号和密码' };
      }

      let status: number;
      let envelope: ApiEnvelope<LoginData> | null;
      const deviceName = `${platform() === 'win32' ? 'Windows' : platform() === 'darwin' ? 'macOS' : `Linux (${arch()})`} ${release()} - Studio Web`;
      try {
        ({ status, envelope } = await postJson<LoginData>('/api/auth/login', { username, password, deviceName }));
      } catch (error) {
        return { success: false, error: error instanceof Error ? error.message : '无法连接到服务器' };
      }

      if (!isOk(status, envelope) || !isRecord(envelope?.data)) {
        return { success: false, error: envelope?.msg || '用户名或密码错误' };
      }

      const data = envelope!.data as LoginData;
      const user = parseUser(data.user);
      if (!data.accessToken || !user) {
        return { success: false, error: '登录响应无效' };
      }

      await writeAuth({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken ?? null,
        user,
        deviceId: typeof data.deviceId === 'number' ? data.deviceId : null,
      });
      applyScopeChange(ctx, user.id);
      return { success: true, user, deviceId: typeof data.deviceId === 'number' ? data.deviceId : undefined };
    },

    logout: async () => {
      const { refreshToken, accessToken } = await readAuth();
      try {
        await postJson('/api/auth/logout', refreshToken ? { refreshToken } : {}, accessToken);
      } catch {
        // Ignore network errors on logout — always clear local state.
      }
      await clearAuth();
      applyScopeChange(ctx, null);
      return { success: true };
    },

    me: async (): Promise<AuthStateSnapshot> => {
      const { accessToken, user } = await readAuth();
      if (!accessToken || !user) {
        return { isAuthenticated: false, user: null };
      }

      let result = await getJson<{ user?: unknown }>('/api/auth/me', accessToken).catch(() => null);
      // Access token expired — attempt a one-time refresh then retry.
      if (result && result.status === 401) {
        const refreshed = await tryRefresh().catch(() => null);
        if (refreshed) {
          result = await getJson<{ user?: unknown }>('/api/auth/me', refreshed).catch(() => null);
        }
      }

      if (!result) {
        // Network failure: keep the cached session so offline restarts still work.
        return { isAuthenticated: true, user };
      }
      if (!isOk(result.status, result.envelope) || !isRecord(result.envelope?.data)) {
        // Session rejected by the backend — treat as a logout for scoping too.
        await clearAuth();
        applyScopeChange(ctx, null);
        return { isAuthenticated: false, user: null };
      }

      const freshUser = parseUser((result.envelope!.data as { user?: unknown }).user) ?? user;
      const current = await readAuth();
      await writeAuth({ ...current, user: freshUser });
      return { isAuthenticated: true, user: freshUser };
    },

    getState: () => toState(),

    listDevices: async (): Promise<AuthDeviceListResult> => {
      const { accessToken } = await readAuth();
      if (!accessToken) {
        return { success: false, error: 'not_authenticated' };
      }
      try {
        const { status, envelope } = await getJson<AuthDeviceListResult['devices']>('/api/auth/devices', accessToken);
        if (isOk(status, envelope)) {
          return { success: true, devices: envelope?.data ?? [] };
        }
        return { success: false, error: envelope?.msg || `HTTP ${status}` };
      } catch (error) {
        return { success: false, error: String(error) };
      }
    },

    revokeDevice: async (payload): Promise<HostSuccess> => {
      const { accessToken } = await readAuth();
      if (!accessToken) {
        return { success: false, error: 'not_authenticated' };
      }
      const deviceId = typeof payload?.deviceId === 'number' ? payload.deviceId : null;
      if (deviceId === null) {
        return { success: false, error: 'invalid deviceId' };
      }
      try {
        const headers: Record<string, string> = { Authorization: `Bearer ${accessToken}` };
        const response = await fetch(`${getBaseUrl()}/api/auth/devices/${deviceId}`, { method: 'DELETE', headers });
        const envelope = await safeJson<{ success?: boolean }>(response);
        if (response.status >= 200 && response.status < 300) {
          return { success: true };
        }
        return { success: false, error: (envelope as { msg?: string })?.msg || `HTTP ${response.status}` };
      } catch (error) {
        return { success: false, error: String(error) };
      }
    },
  };
}
