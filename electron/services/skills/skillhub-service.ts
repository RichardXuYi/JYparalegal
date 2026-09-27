/**
 * SkillHub store service (https://skillhub.cn).
 *
 * SkillHub ships a Python CLI. Its official installer is a bash script that
 * drops a bash wrapper into `~/.local/bin`, which does not exist on Windows, and
 * it also requires a working `python3` on PATH. Neither holds on a stock Windows
 * desktop, so instead we fetch the (tiny) kit tarball ourselves and run
 * `skills_store_cli.py` with the uv-managed Python that the app already installs
 * for its own runtime. That keeps the store working with no user-side Python and
 * no shell dependency.
 */
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { getDataDir, getOpenClawSkillsDir, needsWinShell, quoteForCmd } from '../../utils/paths';
import { isPythonReady, resolveManagedPython, setupManagedPython } from '../../utils/uv-setup';
import { logger } from '../../utils/logger';
import { isInsideRoot } from './local-skill-service';
import type { MarketplaceSkill } from '@shared/types/skill';

const require = createRequire(import.meta.url);
// tar v6 ships no type declarations and the project has no @types/tar; type the
// one entry point we use instead of adding a dependency for it.
const tar = require('tar') as { x: (options: { file: string; cwd: string }) => Promise<void> };

const KIT_URL = 'https://skillhub-1388575217.cos.ap-guangzhou.myqcloud.com/install/latest.tar.gz';
const KIT_DIR_NAME = 'skillhub';
const CLI_RELATIVE_PATH = join('cli', 'skills_store_cli.py');
const LOCK_FILE_NAME = '.skills_store_lock.json';
const SEARCH_TIMEOUT_MS = 25_000;
const INSTALL_TIMEOUT_MS = 180_000;
const MAX_OUTPUT_BYTES = 4 * 1024 * 1024;
/** `@handle/slug` or a bare `slug`. Anything else never reaches argv. */
const SLUG_PATTERN = /^@?[A-Za-z0-9._-]{1,128}(?:\/[A-Za-z0-9._-]{1,128})?$/;

type CliResult = {
  code: number | null;
  stdout: string;
  stderr: string;
  timedOut: boolean;
};

type RawSearchEntry = {
  slug?: unknown;
  publicSlug?: unknown;
  name?: unknown;
  description?: unknown;
  version?: unknown;
  namespace?: { displayName?: unknown; handle?: unknown } | null;
};

type SkillHubLockEntry = {
  name?: string;
  version?: string;
  installDir?: string;
  publicSlug?: string;
  namespace?: { handle?: string } | null;
};

type SkillHubLock = {
  version?: number;
  skills?: Record<string, SkillHubLockEntry>;
};

function toStringValue(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/**
 * The CLI prints human progress lines before its `--json` payload, so parsing
 * stdout wholesale fails. Try the whole buffer first, then fall back to the
 * outermost object literal.
 */
function extractJson<T>(text: string): T | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed) as T;
  } catch {
    // Fall through to the outermost-object scan below.
  }
  const start = trimmed.indexOf('{');
  const end = trimmed.lastIndexOf('}');
  if (start === -1 || end <= start) return null;
  try {
    return JSON.parse(trimmed.slice(start, end + 1)) as T;
  } catch {
    return null;
  }
}

export class SkillHubService {
  /** Shared in-flight prepare so concurrent opens do not double-download. */
  private preparePromise: Promise<void> | null = null;

  private getKitDir(): string {
    return join(getDataDir(), KIT_DIR_NAME);
  }

  private getCliPath(): string {
    return join(this.getKitDir(), CLI_RELATIVE_PATH);
  }

  /** Idempotent: downloads the kit and installs the managed Python if needed. */
  async prepare(): Promise<void> {
    if (this.preparePromise) return this.preparePromise;
    this.preparePromise = this.runPrepare().finally(() => {
      this.preparePromise = null;
    });
    return this.preparePromise;
  }

  private async runPrepare(): Promise<void> {
    await this.ensureKit();
    if (!(await isPythonReady())) {
      logger.info('SkillHub: installing managed Python 3.12 via uv');
      await setupManagedPython();
    }
    const python = await resolveManagedPython();
    if (!python) {
      throw new Error('Managed Python 3.12 is unavailable; cannot run SkillHub.');
    }
  }

  private async ensureKit(): Promise<void> {
    const cliPath = this.getCliPath();
    if (existsSync(cliPath)) return;

    const kitDir = this.getKitDir();
    const stagingDir = `${kitDir}-staging`;
    const archivePath = join(kitDir, 'latest.tar.gz');
    await mkdir(kitDir, { recursive: true });

    logger.info(`SkillHub: downloading kit from ${KIT_URL}`);
    const response = await fetch(KIT_URL);
    if (!response.ok) {
      throw new Error(`SkillHub kit download failed: HTTP ${response.status}`);
    }
    const archive = Buffer.from(await response.arrayBuffer());
    await writeFile(archivePath, archive);

    await rm(stagingDir, { recursive: true, force: true });
    await mkdir(stagingDir, { recursive: true });
    await tar.x({ file: archivePath, cwd: stagingDir });

    if (!existsSync(join(stagingDir, CLI_RELATIVE_PATH))) {
      await rm(stagingDir, { recursive: true, force: true });
      throw new Error('SkillHub kit archive did not contain the expected CLI entry.');
    }

    await rm(kitDir, { recursive: true, force: true });
    await rename(stagingDir, kitDir);
    logger.info('SkillHub: kit ready');
  }

  private async runCli(args: string[], timeoutMs: number): Promise<CliResult> {
    const python = await resolveManagedPython();
    if (!python) {
      throw new Error('Managed Python 3.12 is unavailable; cannot run SkillHub.');
    }
    const cliPath = this.getCliPath();
    const useShell = needsWinShell(python);

    return new Promise<CliResult>((resolve, reject) => {
      const child = spawn(
        useShell ? quoteForCmd(python) : python,
        [cliPath, ...args],
        { shell: useShell, cwd: this.getKitDir(), windowsHide: true },
      );

      let stdout = '';
      let stderr = '';
      let timedOut = false;

      const timer = setTimeout(() => {
        timedOut = true;
        child.kill();
      }, timeoutMs);

      child.stdout?.on('data', (data: Buffer) => {
        if (stdout.length < MAX_OUTPUT_BYTES) stdout += data.toString('utf-8');
      });
      child.stderr?.on('data', (data: Buffer) => {
        if (stderr.length < MAX_OUTPUT_BYTES) stderr += data.toString('utf-8');
      });

      child.on('close', (code) => {
        clearTimeout(timer);
        resolve({ code, stdout, stderr, timedOut });
      });
      child.on('error', (error) => {
        clearTimeout(timer);
        reject(error);
      });
    });
  }

  async search(query: string): Promise<MarketplaceSkill[]> {
    const trimmed = query.trim();
    const args = ['search', '--json'];
    if (trimmed) args.push(trimmed);
    const result = await this.runCli(args, SEARCH_TIMEOUT_MS);

    if (result.timedOut) {
      throw new Error('SkillHub search timed out.');
    }
    const payload = extractJson<{ results?: RawSearchEntry[] }>(result.stdout);
    if (!payload || !Array.isArray(payload.results)) {
      // The CLI answers an empty query with a plain "No skills found." and exit
      // 0 rather than an empty JSON result set.
      if (result.code === 0) return [];
      const detail = result.stderr.trim() || result.stdout.trim();
      throw new Error(detail || `SkillHub search failed (exit ${result.code}).`);
    }

    return payload.results.flatMap((entry): MarketplaceSkill[] => {
      // `install` needs the namespaced name; the bare public slug 404s.
      const slug = toStringValue(entry.slug) || toStringValue(entry.publicSlug);
      if (!slug) return [];
      const namespace = entry.namespace ?? undefined;
      return [{
        slug,
        name: toStringValue(entry.name) || slug,
        description: toStringValue(entry.description),
        version: toStringValue(entry.version),
        author: toStringValue(namespace?.displayName) || toStringValue(namespace?.handle) || undefined,
      }];
    });
  }

  async install(slug: string): Promise<void> {
    const target = this.requireSlug(slug);
    const skillsDir = getOpenClawSkillsDir();
    const result = await this.runCli(
      ['install', target, '--dir', skillsDir, '--json', '--force'],
      INSTALL_TIMEOUT_MS,
    );

    if (result.timedOut) {
      throw new Error('SkillHub install timed out.');
    }
    const payload = extractJson<{ success?: boolean; error?: string }>(result.stdout);
    if (!payload?.success) {
      const detail = toStringValue(payload?.error) || result.stderr.trim() || `exit ${result.code}`;
      throw new Error(`SkillHub install failed: ${detail}`);
    }
  }

  /**
   * SkillHub has no uninstall subcommand, so remove the skill directory
   * ourselves. The kit's lock file records the real install path inside the
   * per-account skills root — trust it (after an isInsideRoot check) and drop
   * the entry so the CLI stops reporting the skill as installed.
   */
  async uninstall(slug: string): Promise<void> {
    const target = this.requireSlug(slug);
    const skillsDir = getOpenClawSkillsDir();
    const lockPath = join(skillsDir, LOCK_FILE_NAME);
    const lock = await this.readLock(lockPath);

    const entry = lock?.skills?.[target];
    const installDir = entry?.installDir;
    if (!installDir || !isInsideRoot(skillsDir, installDir)) {
      throw new Error(`SkillHub install path for "${target}" was not found under the skills directory.`);
    }

    await rm(installDir, { recursive: true, force: true });

    // `@handle/slug` installs leave a now-empty namespace folder behind.
    const namespaceDir = dirname(installDir);
    if (isInsideRoot(skillsDir, namespaceDir) && namespaceDir !== skillsDir) {
      await rm(namespaceDir, { recursive: false, force: true }).catch(() => {
        // Not empty (other skills from the same handle) — expected, keep it.
      });
    }

    if (lock?.skills) {
      delete lock.skills[target];
      await writeFile(lockPath, `${JSON.stringify(lock, null, 2)}\n`, 'utf-8').catch(() => {
        // Lock bookkeeping is best-effort; the directory is already gone.
      });
    }
  }

  private requireSlug(slug: string): string {
    const trimmed = slug.trim();
    if (!SLUG_PATTERN.test(trimmed)) {
      throw new Error(`Invalid SkillHub slug: ${slug}`);
    }
    return trimmed;
  }

  private async readLock(lockPath: string): Promise<SkillHubLock | null> {
    try {
      return JSON.parse(await readFile(lockPath, 'utf-8')) as SkillHubLock;
    } catch {
      return null;
    }
  }
}