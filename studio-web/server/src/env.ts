/**
 * Server environment configuration.
 *
 * Must be imported FIRST (before any module that touches host-core/utils/paths.ts)
 * so the CLAWX_USER_DATA_DIR fallback in paths.ts resolves to our DATA_DIR.
 */
import { mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const serverDir = fileURLToPath(new URL('..', import.meta.url));

/** studio-web project root (contains package.json, dist/, resources/, server/) */
export const PROJECT_ROOT = resolve(serverDir, '..');

/** Per-deployment writable data directory (replaces app.getPath('userData')). */
export const DATA_DIR = process.env.DATA_DIR?.trim()
  ? resolve(process.env.DATA_DIR.trim())
  : join(homedir(), '.jy-studio-web');

/** HTTP/WS listen port for this host server. */
export const PORT = Number(process.env.PORT) > 0 ? Number(process.env.PORT) : 8788;

/** JY backend base URL (login/refresh/me are proxied through services). */
export const BACKEND_URL = process.env.BACKEND_URL?.trim() || 'http://localhost:8181';

/** Secret used to sign the session JWT cookie. */
const rawSecret = process.env.JWT_SECRET?.trim();
if (!rawSecret || rawSecret.length < 32) {
  console.error('JWT_SECRET environment variable must be set and at least 32 characters long');
  process.exit(1);
}
export const JWT_SECRET = rawSecret;

/** Session cookie name. */
export const SESSION_COOKIE = 'jy_studio_session';

/** Set to '1' to skip WS/page auth checks (local development only). */
export const AUTH_DISABLED = process.env.DISABLE_AUTH === '1';

/**
 * Fleet mode (default ON): the access layer routes each account to its own
 * host worker via the FleetSupervisor. Set FLEET_DISABLED=1 to fall back to the
 * legacy single-process host (all users share one Gateway) — a conservative
 * escape hatch, not the intended multi-tenant path.
 */
export const FLEET_DISABLED = process.env.FLEET_DISABLED === '1';

mkdirSync(DATA_DIR, { recursive: true });
mkdirSync(join(DATA_DIR, 'logs'), { recursive: true });

// paths.ts (host-core/utils/paths.ts) falls back to CLAWX_USER_DATA_DIR when not
// running under Electron — keep it aligned with our DATA_DIR.
process.env.CLAWX_USER_DATA_DIR = DATA_DIR;
// backend-auth-api.ts reads JY_API_BASE_URL for the backend origin.
process.env.JY_API_BASE_URL = BACKEND_URL;
