import { isCronSessionKey } from './cron-session-utils';
import { isChannelSessionKey } from './session-key-utils';
import type { ChatSession } from './types';

function getAgentIdFromSessionKey(sessionKey: string): string {
  if (!sessionKey.startsWith('agent:')) return 'main';
  const [, agentId] = sessionKey.split(':');
  return agentId || 'main';
}

function sortByUpdatedAtDesc(sessions: ChatSession[]): ChatSession[] {
  return [...sessions].sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
}

/**
 * Pick the most recently active regular session (cron/channel sessions are
 * excluded). Returns null when no eligible session exists.
 */
export function pickMostRecentSession(sessions: ChatSession[]): string | null {
  const eligible = sortByUpdatedAtDesc(
    sessions.filter((session) => !isCronSessionKey(session.key) && !isChannelSessionKey(session.key)),
  );
  return eligible[0]?.key ?? null;
}

/**
 * Resolve the session to show on app startup. Prefers the persisted
 * last-viewed session when it still exists; otherwise falls back to the most
 * recently active regular session so users land where they last worked
 * instead of the hard-coded main-agent session.
 */
export function resolveStartupSessionKey(
  preferredKey: string | null,
  sessions: ChatSession[],
): string | null {
  if (
    preferredKey
    && !isCronSessionKey(preferredKey)
    && !isChannelSessionKey(preferredKey)
    && sessions.some((session) => session.key === preferredKey)
  ) {
    return preferredKey;
  }
  return pickMostRecentSession(sessions);
}

/**
 * When the current session key is missing from `sessions.list`, pick a safer
 * replacement than `sessions[0]`. Cron/heartbeat sessions must never become
 * the implicit startup target just because they sort first in the gateway list.
 */
export function pickStartupSessionFallback(
  currentSessionKey: string,
  sessions: ChatSession[],
): string | null {
  if (sessions.length === 0) return null;

  const agentId = getAgentIdFromSessionKey(currentSessionKey);
  const agentMainKey = `agent:${agentId}:main`;
  const agentMain = sessions.find((session) => session.key === agentMainKey);
  if (agentMain) return agentMain.key;

  const agentNonCron = sortByUpdatedAtDesc(
    sessions.filter((session) => session.key.startsWith(`agent:${agentId}:`)
      && !isCronSessionKey(session.key)
      && !isChannelSessionKey(session.key)),
  );
  if (agentNonCron.length > 0) return agentNonCron[0]!.key;

  const nonCron = sortByUpdatedAtDesc(
    sessions.filter((session) => !isCronSessionKey(session.key) && !isChannelSessionKey(session.key)),
  );
  if (nonCron.length > 0) return nonCron[0]!.key;

  return null;
}
