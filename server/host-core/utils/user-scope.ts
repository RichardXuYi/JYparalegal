/**
 * User Scope
 *
 * Resolves which OpenClaw state directory belongs to the currently
 * logged-in JY account so multiple accounts on the same machine stay
 * isolated (data dirs, workspaces, provider config, sessions).
 *
 * Claim model ("认领制"): the first account that ever logs in claims the
 * pre-existing legacy `~/.openclaw` directory (zero migration). That owner —
 * and the logged-out state — keep using `~/.openclaw`; every other account
 * gets its own directory under `~/.grandpoem-studio/openclaw/u<id>`.
 *
 * This module must stay dependency-free towards `./paths` (paths delegates
 * `getOpenClawConfigDir()` here), so it resolves base directories itself.
 */
import { join } from 'path';
import { homedir } from 'os';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

type ScopeState = {
  /** JY userId that owns the legacy ~/.openclaw directory; null = unclaimed. */
  openclawOwnerUserId: number | null;
};

/** In-memory active account; undefined = not yet initialized from persisted auth. */
let activeUserId: number | null | undefined;

/**
 * Explicit state-dir override for fleet workers (studio-web): when the
 * process is launched with OPENCLAW_STATE_DIR, the per-user data root is
 * fully decided by the supervisor and the claim model is bypassed.
 */
function getStateDirOverride(): string | null {
  const value = process.env.OPENCLAW_STATE_DIR?.trim();
  return value && value.length > 0 ? value : null;
}

function getStudioConfigDir(): string {
  return join(homedir(), '.grandpoem-studio');
}

function getScopeStatePath(): string {
  return join(getStudioConfigDir(), 'user-scope.json');
}

function getLegacyOpenClawDir(): string {
  return join(homedir(), '.openclaw');
}

function readScopeState(): ScopeState {
  try {
    const raw = readFileSync(getScopeStatePath(), 'utf-8');
    const parsed = JSON.parse(raw) as Partial<ScopeState>;
    const owner = parsed.openclawOwnerUserId;
    return { openclawOwnerUserId: typeof owner === 'number' ? owner : null };
  } catch {
    return { openclawOwnerUserId: null };
  }
}

function writeScopeState(state: ScopeState): void {
  try {
    mkdirSync(getStudioConfigDir(), { recursive: true });
    writeFileSync(getScopeStatePath(), JSON.stringify(state, null, 2), 'utf-8');
  } catch {
    // Best-effort persistence; scope falls back to legacy dir on failure.
  }
}

/**
 * Resolve the electron-store userData dir the same way `paths.ts` does,
 * without importing it (avoids a module cycle).
 */
function getUserDataDir(): string {
  if (process.versions?.electron) {
    try {
      return (require('electron') as typeof import('electron')).app.getPath('userData');
    } catch {
      // Fall through to env/home fallback (unit tests, scripts).
    }
  }
  return process.env.CLAWX_USER_DATA_DIR?.trim() || getStudioConfigDir();
}

/**
 * Restore the active account from the persisted auth store (electron-store
 * file `auth.json`) so the scope is correct before any login IPC arrives —
 * e.g. when the Gateway autostarts with a remembered session.
 */
function readPersistedAuthUserId(): number | null {
  try {
    const raw = readFileSync(join(getUserDataDir(), 'auth.json'), 'utf-8');
    const parsed = JSON.parse(raw) as { accessToken?: unknown; user?: { id?: unknown } };
    if (!parsed.accessToken) return null;
    const id = parsed.user?.id;
    return typeof id === 'number' ? id : null;
  } catch {
    return null;
  }
}

function ensureInitialized(): void {
  if (activeUserId === undefined) {
    activeUserId = readPersistedAuthUserId();
  }
}

/**
 * 读取当前 scope 的后端 accessToken（`auth.json`）。供启动期注入（如 openclaw
 * `mcp.servers.jyparalegal.headers.Authorization`）取用——**只读不刷新**，
 * 刷新仍由 backend-auth-api 的请求路径负责。
 */
export function getPersistedAccessToken(): string | null {
  try {
    const raw = readFileSync(join(getUserDataDir(), 'auth.json'), 'utf-8');
    const parsed = JSON.parse(raw) as { accessToken?: unknown };
    return typeof parsed.accessToken === 'string' && parsed.accessToken.length > 0
      ? parsed.accessToken
      : null;
  } catch {
    return null;
  }
}

/** Current active JY userId (null when logged out). */
export function getActiveScopeUserId(): number | null {
  ensureInitialized();
  return activeUserId ?? null;
}

/**
 * Update the active account. Returns true when the resolved OpenClaw dir
 * changed (callers should then restart the Gateway and refresh caches).
 */
export function setActiveScopeUser(userId: number | null): boolean {
  ensureInitialized();
  if (getStateDirOverride() !== null) {
    // Scope is pinned by the environment (fleet worker) — never changes.
    activeUserId = userId;
    return false;
  }
  const before = getScopedOpenClawDir();
  activeUserId = userId;
  const after = getScopedOpenClawDir();
  return before !== after;
}

/**
 * The OpenClaw state directory for the active scope.
 *
 * - logged out          → legacy `~/.openclaw`
 * - owner (or first login, which claims ownership) → legacy `~/.openclaw`
 * - any other account   → `~/.grandpoem-studio/openclaw/u<id>`
 */
export function getScopedOpenClawDir(): string {
  const override = getStateDirOverride();
  if (override !== null) {
    return override;
  }
  ensureInitialized();
  const userId = activeUserId ?? null;
  if (userId === null) {
    return getLegacyOpenClawDir();
  }
  const state = readScopeState();
  if (state.openclawOwnerUserId === null) {
    // First account ever to log in claims the legacy directory (zero migration).
    writeScopeState({ openclawOwnerUserId: userId });
    return getLegacyOpenClawDir();
  }
  if (state.openclawOwnerUserId === userId) {
    return getLegacyOpenClawDir();
  }
  const dir = join(getStudioConfigDir(), 'openclaw', `u${userId}`);
  if (!existsSync(dir)) {
    try {
      mkdirSync(dir, { recursive: true });
    } catch {
      // Directory creation failures surface later at first real file I/O.
    }
  }
  return dir;
}

/** Whether the active scope uses the legacy `~/.openclaw` directory. */
export function isLegacyScope(): boolean {
  if (getStateDirOverride() !== null) {
    return false;
  }
  return getScopedOpenClawDir() === getLegacyOpenClawDir();
}

/**
 * Suffix appended to electron-store file names (settings, provider secrets)
 * so per-account data lives in separate files. Empty for the legacy scope
 * (owner account and logged-out state) to keep existing files untouched.
 */
export function getScopeStoreSuffix(): string {
  if (getStateDirOverride() !== null) {
    // Fleet workers already get a dedicated userData dir per account.
    return '';
  }
  ensureInitialized();
  if (activeUserId === null || activeUserId === undefined || isLegacyScope()) {
    return '';
  }
  return `-u${activeUserId}`;
}
