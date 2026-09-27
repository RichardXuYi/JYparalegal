/**
 * Deleted Session Keys
 *
 * Persists the session keys hard-deleted by this browser profile so that the
 * Gateway's in-memory resurrection (it re-serves chat.history and re-persists
 * sessions.json entries for deleted sessions) cannot repopulate the sidebar
 * or the main pane after a page reload. Stored in localStorage; all access is
 * wrapped in try/catch so storage failures (private mode, quota) never break
 * the chat store. The key is wiped by `resetAllUserStores` on account switch,
 * keeping the suppression scoped to the account that performed the deletion.
 */
export const DELETED_SESSIONS_STORAGE_KEY = 'grandpoem-studio-deleted-sessions';

export function readDeletedSessionKeys(): Set<string> {
  try {
    const raw = globalThis.localStorage?.getItem(DELETED_SESSIONS_STORAGE_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return new Set();
    return new Set(
      parsed.filter((value): value is string => typeof value === 'string' && value.startsWith('agent:')),
    );
  } catch {
    return new Set();
  }
}

export function persistDeletedSessionKeys(keys: Set<string>): void {
  try {
    if (keys.size === 0) {
      globalThis.localStorage?.removeItem(DELETED_SESSIONS_STORAGE_KEY);
    } else {
      globalThis.localStorage?.setItem(DELETED_SESSIONS_STORAGE_KEY, JSON.stringify([...keys]));
    }
  } catch {
    // Ignore storage failures - persistence is best-effort.
  }
}

export function clearDeletedSessionKeys(): void {
  try {
    globalThis.localStorage?.removeItem(DELETED_SESSIONS_STORAGE_KEY);
  } catch {
    // Ignore storage failures.
  }
}
