/**
 * Fleet layout — deterministic per-user data placement and worker environment.
 *
 * Every logged-in JY account gets its own data root on the node's local disk
 * (sqlite must never live on NFS — see the容量与稳定性 section of the plan):
 *
 *   <DATA_DIR>/users/<scope>/            worker userData (settings, auth, secure)
 *   <DATA_DIR>/users/<scope>/openclaw/   OpenClaw state dir (config, agents, sessions)
 *
 * `<scope>` is the JWT `sub` claim (numeric userId as string, or `dev` when auth
 * is disabled). The supervisor forks the worker with these dirs pinned via env
 * so host-core resolves everything per-user without touching the shared root.
 */
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { DATA_DIR } from '../env';

/** Sanitize a JWT sub into a filesystem-safe scope segment. */
export function normalizeScope(sub: string): string {
  const trimmed = (sub ?? '').trim();
  const safe = trimmed.replace(/[^A-Za-z0-9_-]/g, '_');
  return safe.length > 0 ? safe : 'anon';
}

/** Root that holds every per-user worker directory on this node. */
export function getFleetUsersRoot(): string {
  return join(DATA_DIR, 'users');
}

// --- legacy claim -----------------------------------------------------------
//
// Before fleet mode, studio-web was single-tenant and wrote everything to the
// top-level DATA_DIR (an `openclaw/` state dir, settings, auth). To upgrade
// without losing that data, the FIRST account to resolve a data dir claims the
// top-level layout as its own (zero migration) — exactly the desktop
// `openclawOwnerUserId` scheme. Everyone else gets a fresh `users/<scope>/` dir.

const CLAIM_FILE = 'fleet-claim.json';

/** Owner resolution is cached per process (main/access layer is single-process). */
let cachedOwner: string | null | undefined;

function claimFilePath(): string {
  return join(DATA_DIR, CLAIM_FILE);
}

function readOwnerScope(): string | null {
  try {
    const parsed = JSON.parse(readFileSync(claimFilePath(), 'utf8')) as { ownerScope?: unknown };
    return typeof parsed.ownerScope === 'string' ? parsed.ownerScope : null;
  } catch {
    return null;
  }
}

/** Pre-fleet top-level data exists only if the old single-host state dir is there. */
function hasLegacyTopLevelData(): boolean {
  return existsSync(join(DATA_DIR, 'openclaw'));
}

function writeClaim(ownerScope: string): void {
  const path = claimFilePath();
  mkdirSync(DATA_DIR, { recursive: true });
  const tmp = `${path}.tmp`;
  writeFileSync(tmp, JSON.stringify({ ownerScope, claimedAt: new Date().toISOString() }, null, 2), 'utf8');
  renameSync(tmp, path);
}

/**
 * Resolve (and, on first call with legacy data present, establish) the scope
 * that owns the top-level DATA_DIR. Returns null on a fresh deployment where
 * there is nothing to claim — then every account uses `users/<scope>/`.
 */
function resolveOwner(candidate: string): string | null {
  if (cachedOwner !== undefined) return cachedOwner;
  const existing = readOwnerScope();
  if (existing) {
    cachedOwner = existing;
    return existing;
  }
  if (hasLegacyTopLevelData()) {
    writeClaim(candidate);
    cachedOwner = candidate;
    return candidate;
  }
  cachedOwner = null;
  return null;
}

/** The userData directory (settings/auth/secure) for one account's worker. */
export function getUserDataDir(scope: string): string {
  const key = normalizeScope(scope);
  const owner = resolveOwner(key);
  if (owner && owner === key) {
    // Owner reuses the pre-fleet top-level layout in place (no migration).
    return DATA_DIR;
  }
  return join(getFleetUsersRoot(), key);
}

/** The OpenClaw state directory for one account's worker. */
export function getUserOpenClawDir(scope: string): string {
  return join(getUserDataDir(scope), 'openclaw');
}

/** Path to a worker's persisted auth tokens (electron-store `auth.json`). */
export function getUserAuthFile(scope: string): string {
  return join(getUserDataDir(scope), 'auth.json');
}
