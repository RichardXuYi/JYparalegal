/**
 * Path Utilities
 * Cross-platform path resolution helpers
 */
import { createRequire } from 'node:module';
import { join } from 'path';
import { homedir } from 'os';
import { existsSync, mkdirSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'fs';
import { getScopedOpenClawDir } from './user-scope';

const require = createRequire(import.meta.url);

type ElectronAppLike = Pick<typeof import('electron').app, 'isPackaged' | 'getPath' | 'getAppPath'>;

export {
  quoteForCmd,
  needsWinShell,
  prepareWinSpawn,
  normalizeNodeRequirePathForNodeOptions,
  appendNodeRequireToNodeOptions,
} from './win-shell';

function getElectronApp() {
  if (process.versions?.electron) {
    return (require('electron') as typeof import('electron')).app;
  }

  const fallbackUserData = process.env.CLAWX_USER_DATA_DIR?.trim() || join(homedir(), '.grandpoem-studio');
  const fallbackAppPath = process.cwd();
  const fallbackApp: ElectronAppLike = {
    isPackaged: false,
    getPath: (name) => {
      if (name === 'userData') return fallbackUserData;
      return fallbackUserData;
    },
    getAppPath: () => fallbackAppPath,
  };
  return fallbackApp;
}

/**
 * Expand ~ to home directory.
 * Only expands a standalone ~ or ~ followed by a path separator (/ or \),
 * preventing accidental expansion of filenames that merely start with ~.
 */
export function expandPath(path: string): string {
  if (path === '~') return homedir();
  if (path.startsWith('~/') || path.startsWith('~\\')) {
    return join(homedir(), path.slice(2));
  }
  return path;
}

/**
 * Get OpenClaw config directory (state dir) for the active user scope.
 *
 * Must be resolved lazily at every call site — the directory changes when a
 * different JY account logs in (see `user-scope.ts`). Never cache the result
 * in a module-level constant.
 */
export function getOpenClawConfigDir(): string {
  return getScopedOpenClawDir();
}

/**
 * Durable location of OpenClaw's V8 compile cache (see process-launcher:
 * NODE_COMPILE_CACHE). Lives under userData so it survives Temp cleanup.
 */
export function getOpenClawCompileCacheDir(): string {
  return join(getElectronApp().getPath('userData'), 'openclaw-compile-cache');
}

/**
 * True when the compile cache holds no entries, i.e. the next gateway boot
 * must recompile every module from scratch (first launch after install, or a
 * cleared cache). Used to distinguish a "cold" boot worth a first-run
 * preparing screen from a warm boot that only takes a few seconds.
 */
export function isOpenClawCompileCacheCold(dir: string): boolean {
  try {
    if (!existsSync(dir)) return true;
    return readdirSync(dir).length === 0;
  } catch {
    // Unreadable cache dir: assume warm rather than block the user on a guess.
    return false;
  }
}

/**
 * Get OpenClaw skills directory
 */
export function getOpenClawSkillsDir(): string {
  return join(getOpenClawConfigDir(), 'skills');
}

/**
 * Get GrandPoem Studio config directory
 */
export function getGrandPoemStudioConfigDir(): string {
  return join(homedir(), '.grandpoem-studio');
}

/**
 * Get GrandPoem Studio logs directory
 */
export function getLogsDir(): string {
  return join(getElectronApp().getPath('userData'), 'logs');
}

/**
 * Get GrandPoem Studio data directory
 */
export function getDataDir(): string {
  return getElectronApp().getPath('userData');
}

/**
 * Ensure directory exists
 */
export function ensureDir(dir: string): void {
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }
}

/**
 * Get resources directory (for bundled assets)
 */
export function getResourcesDir(): string {
  if (getElectronApp().isPackaged) {
    return join(process.resourcesPath, 'resources');
  }
  return join(__dirname, '../../resources');
}

/**
 * Get preload script path
 */
export function getPreloadPath(): string {
  return join(__dirname, '../preload/index.js');
}

/**
 * Root directory for downloaded OpenClaw runtimes.
 *
 * Deliberately NOT under `userData`: on Windows `userData` is Roaming
 * (`%APPDATA%`), and a ~850 MB runtime would be synchronised with the roaming
 * profile. `%LOCALAPPDATA%` is machine-local, writable without elevation, and
 * survives (or is cleaned by) the uninstaller's existing app-data handling.
 */
export function getRuntimeRootDir(): string {
  if (process.platform === 'win32') {
    const localAppData = process.env.LOCALAPPDATA?.trim();
    if (localAppData) {
      return join(localAppData, 'grandpoem-studio', 'runtime');
    }
  }
  return join(getElectronApp().getPath('userData'), 'runtime');
}

/** Pointer file recording which runtime version is active. */
export function getActiveRuntimePointerPath(): string {
  return join(getRuntimeRootDir(), 'current.json');
}

export interface ActiveRuntimePointer {
  version: string;
  dir: string;
  sha256?: string;
  installedAt?: string;
  source?: 'download' | 'import' | 'bundled';
}

/** True when a directory looks like a usable OpenClaw runtime tree. */
export function isRuntimeDirUsable(dir: string): boolean {
  return existsSync(join(dir, 'package.json')) && existsSync(join(dir, 'openclaw.mjs'));
}

/**
 * Read the active-runtime pointer, validating that the target directory is
 * still a usable runtime tree. A stale pointer (uninstalled, half-extracted,
 * user deleted the folder) resolves to null so callers fall back to the
 * bundled runtime instead of failing the Gateway launch.
 */
export function readActiveRuntimePointer(): ActiveRuntimePointer | null {
  try {
    const raw = readFileSync(getActiveRuntimePointerPath(), 'utf-8');
    const parsed = JSON.parse(raw) as Partial<ActiveRuntimePointer>;
    if (typeof parsed?.dir !== 'string' || !parsed.dir) return null;
    if (!isRuntimeDirUsable(parsed.dir)) return null;
    return {
      version: typeof parsed.version === 'string' ? parsed.version : '',
      dir: parsed.dir,
      sha256: typeof parsed.sha256 === 'string' ? parsed.sha256 : undefined,
      installedAt: typeof parsed.installedAt === 'string' ? parsed.installedAt : undefined,
      source: parsed.source,
    };
  } catch {
    return null;
  }
}

/** Write the active-runtime pointer (used by the runtime installer service). */
export function writeActiveRuntimePointer(pointer: ActiveRuntimePointer): void {
  const target = getActiveRuntimePointerPath();
  mkdirSync(getRuntimeRootDir(), { recursive: true });
  writeFileSync(target, JSON.stringify(pointer, null, 2), 'utf-8');
}

/** Remove the active-runtime pointer (rollback to the bundled runtime). */
export function clearActiveRuntimePointer(): void {
  try {
    rmSync(getActiveRuntimePointerPath(), { force: true });
  } catch {
    // best effort
  }
}

/**
 * The runtime shipped inside the application package.
 * - Packaged: resources/openclaw (electron-builder extraResources)
 * - Development: node_modules/openclaw
 */
export function getBundledOpenClawDir(): string {
  if (getElectronApp().isPackaged) {
    return join(process.resourcesPath, 'openclaw');
  }
  return join(__dirname, '../../node_modules/openclaw');
}

export type OpenClawDirSource = 'override' | 'downloaded' | 'bundled';

/**
 * Resolve where the OpenClaw runtime lives, in priority order:
 *
 *   1. `GRANDPOEM_RUNTIME_DIR` — explicit override (tests, fleet workers,
 *      mirrors of the existing `GP_GATEWAY_ENTRY_OVERRIDE` escape hatch)
 *   2. the downloaded runtime recorded in `runtime/current.json`
 *   3. the runtime bundled in the application package (today's behaviour)
 *
 * This stays a single chokepoint on purpose: every consumer
 * (`getOpenClawEntryPath`, `getRuntimeModuleResolvers`, config-sync, doctor…)
 * resolves through here, so switching runtime source never needs a second
 * code path.
 */
export function getOpenClawDir(): string {
  const override = process.env.GRANDPOEM_RUNTIME_DIR?.trim();
  if (override) return override;
  const active = readActiveRuntimePointer();
  if (active) return active.dir;
  return getBundledOpenClawDir();
}

/** Which of the three sources `getOpenClawDir()` resolves to right now. */
export function getOpenClawDirSource(): OpenClawDirSource {
  if (process.env.GRANDPOEM_RUNTIME_DIR?.trim()) return 'override';
  if (readActiveRuntimePointer()) return 'downloaded';
  return 'bundled';
}

/**
 * Get OpenClaw package directory resolved to a real path.
 * Useful when consumers need deterministic module resolution under pnpm symlinks.
 */
export function getOpenClawResolvedDir(): string {
  const dir = getOpenClawDir();
  if (!existsSync(dir)) {
    return dir;
  }
  try {
    return realpathSync(dir);
  } catch {
    return dir;
  }
}

/**
 * Get OpenClaw entry script path (openclaw.mjs)
 */
export function getOpenClawEntryPath(): string {
  return join(getOpenClawDir(), 'openclaw.mjs');
}

/**
 * Get ClawHub CLI entry script path (clawdhub.js)
 */
export function getClawHubCliEntryPath(): string {
  return join(getElectronApp().getAppPath(), 'node_modules', 'clawhub', 'bin', 'clawdhub.js');
}

/**
 * Get ClawHub CLI binary path (node_modules/.bin)
 */
export function getClawHubCliBinPath(): string {
  const binName = process.platform === 'win32' ? 'clawhub.cmd' : 'clawhub';
  return join(getElectronApp().getAppPath(), 'node_modules', '.bin', binName);
}

/**
 * Check if OpenClaw package exists
 */
export function isOpenClawPresent(): boolean {
  const dir = getOpenClawDir();
  const pkgJsonPath = join(dir, 'package.json');
  return existsSync(dir) && existsSync(pkgJsonPath);
}

/**
 * Check if OpenClaw is built (has dist folder)
 * For the npm package, this should always be true since npm publishes the built dist.
 */
export function isOpenClawBuilt(): boolean {
  const dir = getOpenClawDir();
  const distDir = join(dir, 'dist');
  const hasDist = existsSync(distDir);
  return hasDist;
}

/**
 * Get OpenClaw status for environment check
 */
export interface OpenClawStatus {
  packageExists: boolean;
  isBuilt: boolean;
  entryPath: string;
  dir: string;
  version?: string;
  /** Where the runtime was resolved from (bundled package vs downloaded). */
  source?: OpenClawDirSource;
}

export function getOpenClawStatus(): OpenClawStatus {
  const dir = getOpenClawDir();
  let version: string | undefined;

  // Try to read version from package.json
  try {
    const pkgPath = join(dir, 'package.json');
    if (existsSync(pkgPath)) {
      const pkg = JSON.parse(readFileSync(pkgPath, 'utf-8'));
      version = pkg.version;
    }
  } catch {
    // Ignore version read errors
  }

  const status: OpenClawStatus = {
    packageExists: isOpenClawPresent(),
    isBuilt: isOpenClawBuilt(),
    entryPath: getOpenClawEntryPath(),
    dir,
    version,
    source: getOpenClawDirSource(),
  };

  try {
    const { logger } = require('./logger') as typeof import('./logger');
    logger.info('OpenClaw status:', status);
  } catch {
    // Ignore logger bootstrap issues in non-Electron contexts such as unit tests.
  }
  return status;
}
