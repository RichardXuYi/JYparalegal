/**
 * Single source of truth for locating the bundled native tools
 * (`node`, `uv`, `agent-browser`) in dev and packaged builds.
 *
 * Historically every caller rebuilt the same `resources/bin` path and injected
 * it into `PATH` (config-sync, supervisor, doctor, device pairing). The packaged
 * build now ships `uv` and `agent-browser` as a lazy archive (see
 * utils/lazy-asset.ts and docs/startup-performance-plan.md, scheme 2.3), so the
 * resolution has to live in one place: a caller that recomputes the path by hand
 * would silently produce a `PATH` without `uv` in it.
 *
 * `node` deliberately stays unpacked in `resources/bin`: the Gateway needs it on
 * the very first spawn, and a single file with an established vendor signature
 * costs the virus scanner almost nothing.
 */
import { app } from 'electron';
import { existsSync } from 'fs';
import { join } from 'path';
import { ensureLazyAssetExtracted, getLazyAssetCacheDir } from './lazy-asset';

export type BundledToolName = 'node' | 'uv' | 'agent-browser';

/** Name of the single archive that carries the on-demand tools. */
const BUNDLED_TOOLS_ASSET = 'bin/tools';

function executableName(tool: BundledToolName): string {
  return process.platform === 'win32' ? `${tool}.exe` : tool;
}

/** Directory electron-builder copies `resources/bin/<platform>-<arch>` into. */
export function getBundledBinDir(): string {
  return app.isPackaged
    ? join(process.resourcesPath, 'bin')
    : join(process.cwd(), 'resources', 'bin', `${process.platform}-${process.arch}`);
}

/**
 * Directory holding the lazily extracted tools, but only when they have already
 * been extracted. Never triggers an extraction: this is called from the Gateway
 * prelaunch path, and eagerly unpacking ~70 MB there would put the cost straight
 * back onto startup.
 */
export function getExtractedBinDir(): string | null {
  const dir = getLazyAssetCacheDir(BUNDLED_TOOLS_ASSET);
  return existsSync(dir) ? dir : null;
}

/**
 * Directories that must be on `PATH` for the bundled tools to resolve, most
 * specific first. Callers should prepend them in order.
 */
export function getBundledBinDirs(): string[] {
  const dirs: string[] = [];
  const extracted = getExtractedBinDir();
  if (extracted) dirs.push(extracted);
  const bundled = getBundledBinDir();
  if (existsSync(bundled)) dirs.push(bundled);
  return dirs;
}

/** Prepend every bundled tool directory to an env map's PATH entry. */
export function withBundledBinPath<T extends Record<string, string | undefined>>(
  env: T,
  prependPathEntry: (env: Record<string, string | undefined>, entry: string) => { env: Record<string, string | undefined> },
): T {
  let next: Record<string, string | undefined> = env;
  for (const dir of getBundledBinDirs()) {
    next = prependPathEntry(next, dir).env;
  }
  return next as T;
}

/**
 * Absolute path of a bundled tool, extracting the lazy archive on first use.
 * Returns null when the tool is unavailable (e.g. not downloaded for this
 * platform).
 */
export function resolveBundledTool(tool: BundledToolName): string | null {
  if (tool === 'node') {
    const candidate = join(getBundledBinDir(), executableName('node'));
    return existsSync(candidate) ? candidate : null;
  }

  const extractedDir = ensureLazyAssetExtracted(BUNDLED_TOOLS_ASSET);
  if (extractedDir) {
    const candidate = join(extractedDir, executableName(tool));
    if (existsSync(candidate)) return candidate;
  }

  // Fall back to a non-lazy layout (dev builds, or a build without the archive).
  const fallback = join(getBundledBinDir(), executableName(tool));
  return existsSync(fallback) ? fallback : null;
}
