/**
 * Fleet gateway port registry (Phase 1, single node).
 *
 * Each per-user worker runs its own OpenClaw Gateway, which needs a unique
 * loopback port. OpenClaw requires adjacent base ports to be spaced by at
 * least 20 (it derives browser/CDP ports from the base), so slots step by 20
 * starting at 19000. Assignments are persisted to `<DATA_DIR>/gateway-ports.json`
 * so a restarted node reuses the same port per scope (deterministic, avoids
 * churn). Only base ports are stored; occupancy conflicts (GatewayLockError)
 * are handled by the supervisor advancing to the next free slot.
 *
 * All ports bind to loopback on the node; only the access layer (8788) is
 * exposed. Phase 2 replaces this with per-node registries behind the router.
 */
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { DATA_DIR } from '../env';
import { normalizeScope } from './layout';

const BASE_PORT = 19000;
const PORT_STEP = 20;
const MAX_SLOTS = 2000; // 19000 .. 58980

type PortRegistryFile = {
  /** scope → assigned base port. */
  assignments: Record<string, number>;
};

function getRegistryPath(): string {
  return join(DATA_DIR, 'gateway-ports.json');
}

function readRegistry(): PortRegistryFile {
  try {
    const raw = readFileSync(getRegistryPath(), 'utf8');
    const parsed = JSON.parse(raw) as Partial<PortRegistryFile>;
    const assignments = parsed.assignments;
    if (assignments && typeof assignments === 'object') {
      return { assignments: assignments as Record<string, number> };
    }
  } catch {
    // Missing/corrupt registry starts empty.
  }
  return { assignments: {} };
}

function writeRegistry(file: PortRegistryFile): void {
  const path = getRegistryPath();
  mkdirSync(dirname(path), { recursive: true });
  const tmp = `${path}.tmp`;
  writeFileSync(tmp, JSON.stringify(file, null, 2), 'utf8');
  renameSync(tmp, path);
}

/**
 * Resolve the base port for a scope, allocating (and persisting) a new slot the
 * first time. Deterministic per scope across restarts.
 */
export function acquireGatewayPort(scope: string): number {
  const key = normalizeScope(scope);
  const file = readRegistry();
  const existing = file.assignments[key];
  if (typeof existing === 'number') {
    return existing;
  }

  const used = new Set(Object.values(file.assignments));
  for (let slot = 0; slot < MAX_SLOTS; slot += 1) {
    const port = BASE_PORT + slot * PORT_STEP;
    if (!used.has(port)) {
      file.assignments[key] = port;
      writeRegistry(file);
      return port;
    }
  }
  throw new Error(`Gateway port registry exhausted (>${MAX_SLOTS} scopes on one node)`);
}

/**
 * Reassign a scope to the next free slot after a GatewayLockError (the current
 * slot is occupied by a stale/foreign process). Returns the new base port.
 */
export function reassignGatewayPort(scope: string): number {
  const key = normalizeScope(scope);
  const file = readRegistry();
  const current = file.assignments[key];
  const used = new Set(Object.values(file.assignments));

  for (let slot = 0; slot < MAX_SLOTS; slot += 1) {
    const port = BASE_PORT + slot * PORT_STEP;
    if (port === current) continue;
    if (!used.has(port)) {
      file.assignments[key] = port;
      writeRegistry(file);
      return port;
    }
  }
  throw new Error(`Gateway port registry exhausted while reassigning scope ${key}`);
}

/** Testing/inspection helper: whether a registry file exists yet. */
export function hasPortRegistry(): boolean {
  return existsSync(getRegistryPath());
}
