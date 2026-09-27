import { isCronSessionKey } from './cron-session-utils';
import { isChannelSessionKey } from './session-key-utils';

/**
 * Persist the last session the user was viewing so the next app launch can
 * restore it instead of always landing on the hard-coded main-agent session.
 * Stored in localStorage; all access is wrapped in try/catch so storage
 * failures (private mode, quota, SSR) never break the chat store.
 */
export const LAST_SESSION_STORAGE_KEY = 'grandpoem-studio-last-session';

interface PersistedLastSession {
  sessionKey: string;
  agentId: string;
}

function getAgentIdFromSessionKey(sessionKey: string): string {
  if (!sessionKey.startsWith('agent:')) return 'main';
  const [, agentId] = sessionKey.split(':');
  return agentId || 'main';
}

/**
 * Read the persisted last-session key. Cron/channel session keys are ignored:
 * they must never become the implicit startup target (mirrors
 * `pickStartupSessionFallback`'s filtering).
 */
export function readLastSessionKey(): string | null {
  try {
    const raw = globalThis.localStorage?.getItem(LAST_SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PersistedLastSession> | null;
    const sessionKey = typeof parsed?.sessionKey === 'string' ? parsed.sessionKey.trim() : '';
    if (!sessionKey) return null;
    if (isCronSessionKey(sessionKey) || isChannelSessionKey(sessionKey)) return null;
    return sessionKey;
  } catch {
    return null;
  }
}

export function persistLastSessionKey(sessionKey: string): void {
  try {
    if (!sessionKey) return;
    if (isCronSessionKey(sessionKey) || isChannelSessionKey(sessionKey)) return;
    const payload: PersistedLastSession = {
      sessionKey,
      agentId: getAgentIdFromSessionKey(sessionKey),
    };
    globalThis.localStorage?.setItem(LAST_SESSION_STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Ignore storage failures - persistence is best-effort.
  }
}

export function clearLastSessionKey(): void {
  try {
    globalThis.localStorage?.removeItem(LAST_SESSION_STORAGE_KEY);
  } catch {
    // Ignore storage failures.
  }
}
