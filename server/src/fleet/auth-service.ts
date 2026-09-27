/**
 * Fleet-mode auth service (main process).
 *
 * Satisfies the AuthService contract registerAuthRoutes expects, but scoped to
 * the fleet: login authenticates against the backend and warms the account's
 * worker; logout stops that worker and clears its tokens; me hydrates the
 * session from the verified JWT claims (no worker round-trip needed, and works
 * even before the worker has finished starting).
 */
import { readFileSync } from 'node:fs';
import type {
  AuthDeviceListResult,
  AuthLoginPayload,
  AuthLoginResult,
  AuthRevokeDevicePayload,
  AuthStateSnapshot,
  AuthUser,
  HostSuccess,
} from '../../../shared/host-api/contract';
import type { SessionClaims } from '../auth';
import { BACKEND_URL } from '../env';
import { logger } from '../../host-core/utils/logger';
import type { FleetSupervisor } from './supervisor';
import { backendLogin, clearUserTokens, toLoginResult } from './backend-login';
import { getUserAuthFile, normalizeScope } from './layout';

type StoredTokens = {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  deviceId?: number | null;
};

function readStoredTokens(scope: string): StoredTokens | null {
  try {
    const raw = readFileSync(getUserAuthFile(scope), 'utf8');
    const parsed = JSON.parse(raw) as StoredTokens;
    return parsed;
  } catch {
    return null;
  }
}

function readStoredUser(scope: string): AuthUser | null {
  try {
    const raw = readFileSync(getUserAuthFile(scope), 'utf8');
    const parsed = JSON.parse(raw) as { user?: AuthUser | null };
    return parsed.user ?? null;
  } catch {
    return null;
  }
}

export type FleetAuthService = {
  login: (payload: AuthLoginPayload) => Promise<AuthLoginResult>;
  logout: (sub?: string | null) => Promise<{ success: true }>;
  me: (claims?: SessionClaims | null) => AuthStateSnapshot;
  listDevices: (claims?: SessionClaims | null) => Promise<AuthDeviceListResult>;
  revokeDevice: (payload: AuthRevokeDevicePayload, claims?: SessionClaims | null) => Promise<HostSuccess>;
};

export function createFleetAuthService(supervisor: FleetSupervisor): FleetAuthService {
  return {
    login: async (payload) => {
      const username = typeof payload?.username === 'string' ? payload.username.trim() : '';
      const password = typeof payload?.password === 'string' ? payload.password : '';
      if (!username || !password) {
        return { success: false, error: '请输入账号和密码' };
      }
      const outcome = await backendLogin(username, password);
      if (outcome.ok) {
        // Pre-start the worker so the first WS invoke doesn't pay cold start.
        supervisor.warmUp(outcome.scope);
      }
      return toLoginResult(outcome);
    },

    logout: async (sub) => {
      if (sub) {
        const scope = normalizeScope(sub);
        clearUserTokens(scope);
        try {
          await supervisor.stopWorker(scope);
        } catch (error) {
          logger.warn(`[fleet] stopWorker on logout failed scope=${scope}:`, error);
        }
      }
      return { success: true };
    },

    me: (claims) => {
      if (!claims) {
        return { isAuthenticated: false, user: null };
      }
      const scope = normalizeScope(claims.sub);
      const stored = readStoredUser(scope);
      if (stored) {
        return { isAuthenticated: true, user: stored };
      }
      const id = Number(claims.sub);
      return {
        isAuthenticated: true,
        user: { id: Number.isFinite(id) ? id : 0, username: claims.name ?? claims.sub },
      };
    },

    listDevices: async (claims) => {
      const scope = claims ? normalizeScope(claims.sub) : 'dev';
      const tokens = readStoredTokens(scope);
      if (!tokens?.accessToken) {
        return { success: false, error: 'not_authenticated' };
      }
      try {
        const baseUrl = BACKEND_URL.replace(/\/+$/, '');
        const response = await fetch(`${baseUrl}/api/auth/devices`, {
          method: 'GET',
          headers: { Authorization: `Bearer ${tokens.accessToken}` },
        });
        const envelope = await response.json() as { code?: number; msg?: string; data?: unknown };
        if (response.status === 200 && (envelope.code === 0 || envelope.code === undefined)) {
          return { success: true, devices: envelope.data as AuthDeviceListResult['devices'] };
        }
        return { success: false, error: envelope.msg || `HTTP ${response.status}` };
      } catch (error) {
        return { success: false, error: String(error) };
      }
    },

    revokeDevice: async (payload, claims) => {
      const scope = claims ? normalizeScope(claims.sub) : 'dev';
      const tokens = readStoredTokens(scope);
      if (!tokens?.accessToken) {
        return { success: false, error: 'not_authenticated' };
      }
      const deviceId = payload.deviceId;
      try {
        const baseUrl = BACKEND_URL.replace(/\/+$/, '');
        const response = await fetch(`${baseUrl}/api/auth/devices/${deviceId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${tokens.accessToken}` },
        });
        const envelope = await response.json() as { code?: number; msg?: string };
        if (response.status >= 200 && response.status < 300 && (envelope.code === 0 || envelope.code === undefined)) {
          return { success: true };
        }
        return { success: false, error: envelope.msg || `HTTP ${response.status}` };
      } catch (error) {
        return { success: false, error: String(error) };
      }
    },
  };
}
