/**
 * Shared dependency provisioning for channel plugin mirrors.
 *
 * Build-time deduplication (scripts/after-pack.cjs
 * `deduplicatePluginAgainstRuntime`) deletes every package from a plugin mirror
 * whose name is also bundled with the OpenClaw runtime, and records the dropped
 * names in the mirror's `shared-deps.json`. This module puts those packages back
 * where Node can find them.
 *
 * Where they go: `~/.openclaw/extensions/node_modules/<pkg>`. A plugin runs from
 * `~/.openclaw/extensions/<pluginId>/`, so Node's upward module walk from any
 * file inside it visits `<pluginId>/node_modules`, then
 * `~/.openclaw/extensions/node_modules` — which is exactly the shared root. One
 * physical copy per package serves every installed mirror.
 *
 * Versions: the runtime's copy wins unconditionally (that is the whole point of
 * the forced dedup), so provisioning never compares versions. Re-running is
 * idempotent and cheap.
 *
 * Why not symlink: Windows requires elevation or Developer Mode for symlinks,
 * and this codebase already removed symlink-based skill installs for that reason.
 */
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync } from 'fs';
import { join } from 'path';
import { logger } from './logger';
import { getOpenClawDir } from './paths';

export const SHARED_DEPS_FILE_NAME = 'shared-deps.json';
/** Directory Node resolves as `<extensions>/node_modules` for every plugin. */
export const SHARED_DEPS_DIR_NAME = 'node_modules';

export interface SharedDepsManifest {
  schema: number;
  /** Package names dropped at build time, e.g. `ws`, `@scope/pkg`. */
  sharedDeps: string[];
}

export interface ProvisionResult {
  copied: string[];
  alreadyPresent: string[];
  /** Names with no copy in the runtime bundle — a build inconsistency. */
  missing: string[];
}

function fsPath(p: string): string {
  // Electron's fs wrappers accept plain strings; keep one place for clarity.
  return p;
}

/** Read a mirror's shared-deps.json, or null when it carries its own deps. */
export function readSharedDepsManifest(pluginDir: string): SharedDepsManifest | null {
  const manifestPath = join(pluginDir, SHARED_DEPS_FILE_NAME);
  if (!existsSync(fsPath(manifestPath))) return null;

  try {
    // Strip a UTF-8 BOM: the file is written on Windows during packaging.
    const parsed = JSON.parse(readFileSync(manifestPath, 'utf-8').replace(/^\uFEFF/, '')) as Partial<SharedDepsManifest>;
    if (!Array.isArray(parsed?.sharedDeps)) {
      logger.warn(`[plugin-shared-deps] ${manifestPath} has no sharedDeps array; ignoring.`);
      return null;
    }
    return {
      schema: typeof parsed.schema === 'number' ? parsed.schema : 1,
      sharedDeps: parsed.sharedDeps.filter((name): name is string => typeof name === 'string' && name.length > 0),
    };
  } catch (error) {
    logger.warn(`[plugin-shared-deps] Failed to parse ${manifestPath}:`, error);
    return null;
  }
}

/** Absolute path of a package inside a node_modules directory. */
function packagePathWithin(nodeModulesDir: string, packageName: string): string {
  return join(nodeModulesDir, ...packageName.split('/'));
}

/**
 * Ensure every package listed in the mirror's shared-deps.json exists in the
 * shared root, copying it from the bundled runtime when missing.
 *
 * Never throws: a failure here degrades that one channel (its plugin will fail
 * to load with a missing-module error, which the gateway surfaces), and must not
 * break the channel-setup flow itself.
 */
export function provisionSharedDeps(pluginDir: string, extensionsRoot: string): ProvisionResult {
  const result: ProvisionResult = { copied: [], alreadyPresent: [], missing: [] };

  const manifest = readSharedDepsManifest(pluginDir);
  if (!manifest || manifest.sharedDeps.length === 0) return result;

  const runtimeNodeModules = join(getOpenClawDir(), 'node_modules');
  if (!existsSync(fsPath(runtimeNodeModules))) {
    logger.warn(
      `[plugin-shared-deps] Runtime node_modules not found at ${runtimeNodeModules}; ` +
      `${manifest.sharedDeps.length} shared dependencies cannot be provisioned.`,
    );
    result.missing.push(...manifest.sharedDeps);
    return result;
  }

  const sharedRoot = join(extensionsRoot, SHARED_DEPS_DIR_NAME);

  for (const packageName of manifest.sharedDeps) {
    const targetPath = packagePathWithin(sharedRoot, packageName);
    if (existsSync(fsPath(targetPath))) {
      result.alreadyPresent.push(packageName);
      continue;
    }

    const sourcePath = packagePathWithin(runtimeNodeModules, packageName);
    if (!existsSync(fsPath(sourcePath))) {
      // The build dropped a package the runtime does not actually carry: a
      // packaging bug, not a runtime one. Record it so the caller can log it.
      result.missing.push(packageName);
      continue;
    }

    try {
      mkdirSync(fsPath(join(sharedRoot, ...packageName.split('/').slice(0, -1))), { recursive: true });
      cpSync(fsPath(sourcePath), fsPath(targetPath), { recursive: true, dereference: true });
      result.copied.push(packageName);
    } catch (error) {
      // A partial copy would shadow a good one, so clean up before giving up.
      try { rmSync(fsPath(targetPath), { recursive: true, force: true }); } catch { /* ignore */ }
      logger.warn(`[plugin-shared-deps] Failed to provision ${packageName}:`, error);
      result.missing.push(packageName);
    }
  }

  if (result.copied.length > 0) {
    logger.info(
      `[plugin-shared-deps] Provisioned ${result.copied.length} shared dependencies ` +
      `into ${sharedRoot} (${result.alreadyPresent.length} already present)`,
    );
  }
  if (result.missing.length > 0) {
    logger.warn(`[plugin-shared-deps] Missing from the runtime bundle: ${result.missing.join(', ')}`);
  }

  return result;
}

/**
 * Every package name currently present in the shared root — used by the startup
 * self-check to confirm that installed mirrors can actually resolve.
 */
export function listProvisionedSharedDeps(extensionsRoot: string): string[] {
  const sharedRoot = join(extensionsRoot, SHARED_DEPS_DIR_NAME);
  if (!existsSync(fsPath(sharedRoot))) return [];

  const names: string[] = [];
  let entries;
  try {
    entries = readdirSync(sharedRoot, { withFileTypes: true });
  } catch {
    return [];
  }

  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    if (entry.name.startsWith('@')) {
      try {
        for (const scoped of readdirSync(join(sharedRoot, entry.name), { withFileTypes: true })) {
          if (scoped.isDirectory()) names.push(`${entry.name}/${scoped.name}`);
        }
      } catch {
        // ignore unreadable scope directories
      }
    } else if (entry.name !== '.bin') {
      names.push(entry.name);
    }
  }
  return names;
}

export interface SharedDepsAudit {
  /** Mirrors that declared shared dependencies, with what is still missing. */
  broken: Array<{ pluginId: string; missing: string[] }>;
  installedMirrorCount: number;
  provisionedCount: number;
}

/**
 * Startup self-check: for every installed mirror that declares shared
 * dependencies, confirm they exist in the shared root — and re-provision any that
 * do not. Without this, a missing package surfaces later as an opaque
 * "Cannot find module" inside a channel, which is exactly the failure mode the
 * forced dedup could introduce.
 *
 * Never throws; intended to run as part of startup housekeeping.
 */
export function auditAndRepairSharedDeps(extensionsRoot: string): SharedDepsAudit {
  const audit: SharedDepsAudit = { broken: [], installedMirrorCount: 0, provisionedCount: 0 };

  if (!existsSync(fsPath(extensionsRoot))) return audit;

  let entries;
  try {
    entries = readdirSync(extensionsRoot, { withFileTypes: true });
  } catch {
    return audit;
  }

  for (const entry of entries) {
    if (!entry.isDirectory() || entry.name === SHARED_DEPS_DIR_NAME || entry.name.startsWith('.')) continue;

    const pluginDir = join(extensionsRoot, entry.name);
    const manifest = readSharedDepsManifest(pluginDir);
    if (!manifest || manifest.sharedDeps.length === 0) continue;

    audit.installedMirrorCount += 1;
    // Re-running provisioning fills in anything a previous partial run left out.
    const result = provisionSharedDeps(pluginDir, extensionsRoot);
    audit.provisionedCount += result.copied.length;
    if (result.missing.length > 0) {
      audit.broken.push({ pluginId: entry.name, missing: result.missing });
    }
  }

  if (audit.broken.length > 0) {
    logger.warn(
      '[plugin-shared-deps] Some installed plugins cannot resolve their shared dependencies; ' +
      'those channels may fail to load:',
      audit.broken,
    );
  }

  return audit;
}