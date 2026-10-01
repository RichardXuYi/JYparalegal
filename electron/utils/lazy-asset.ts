/**
 * Lazy asset extraction for packaged builds.
 *
 * Problem: the installer writes ~50k files (1.6 GB) into the install directory.
 * Everything that lands there is new to Windows Defender, and per-file scanning
 * of that volume is the dominant cost of the very first launch on a machine
 * that has just installed the app.
 *
 * Solution: assets that are *not* needed to start the app are shipped as a
 * single archive under `resources/lazy-assets/` and extracted on first use into
 * the user's data directory. This trades a directory of thousands of files in
 * the install tree for one file, and moves the extraction (plus its antivirus
 * cost) to the moment the feature is actually used.
 *
 * Build side: `scripts/after-pack.cjs` produces the archives.
 * Naming convention: archive `${resourcesPath}/lazy-assets/<name>.zip` extracts
 * to `${userData}/lazy-cache/<name>/`.
 *
 * Extraction is synchronous by design: the call sites (`buildCandidateSources()`
 * in plugin-install.ts, the bundled-binary resolvers) are synchronous, and the
 * first use is already a user-visible one-off operation — adding a messaging
 * channel, or installing the Python runtime — never the startup path.
 */
import { app } from 'electron';
import AdmZip from 'adm-zip';
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from 'fs';
import { dirname, join } from 'path';
import { logger } from './logger';

const LAZY_ASSETS_DIR = 'lazy-assets';
const LAZY_CACHE_DIR = 'lazy-cache';
const MARKER_FILE = '.grandpoem-lazy-asset.json';

interface LazyAssetMarker {
  archive: string;
  size: number;
  mtimeMs: number;
  extractedAt: string;
}

function lazyAssetsRoot(): string | null {
  if (!app.isPackaged) return null;
  const root = join(process.resourcesPath, LAZY_ASSETS_DIR);
  return existsSync(root) ? root : null;
}

/** Root of the per-user extraction cache. */
export function getLazyCacheRoot(): string {
  return join(app.getPath('userData'), LAZY_CACHE_DIR);
}

/**
 * Absolute path of a shipped lazy archive, or null when the asset is not
 * packaged as an archive (dev builds) or the archive is absent.
 *
 * @param name asset name without extension, e.g. `openclaw-plugins/dingtalk`
 */
export function getLazyAssetArchivePath(name: string): string | null {
  const root = lazyAssetsRoot();
  if (!root) return null;
  const archive = join(root, `${name}.zip`);
  return existsSync(archive) ? archive : null;
}

/** True when this build ships the asset as a lazy archive. */
export function isLazyAssetPackaged(name: string): boolean {
  return getLazyAssetArchivePath(name) !== null;
}

/** Directory the asset would be extracted into (whether or not it exists yet). */
export function getLazyAssetCacheDir(name: string): string {
  return join(getLazyCacheRoot(), name);
}

function readMarker(destDir: string): LazyAssetMarker | null {
  try {
    const parsed = JSON.parse(readFileSync(join(destDir, MARKER_FILE), 'utf-8')) as LazyAssetMarker;
    if (typeof parsed?.size === 'number' && typeof parsed?.mtimeMs === 'number') {
      return parsed;
    }
  } catch {
    // missing or unreadable marker -> treat as not extracted
  }
  return null;
}

function isExtractionCurrent(archivePath: string, destDir: string): boolean {
  const marker = readMarker(destDir);
  if (!marker) return false;
  try {
    const stat = statSync(archivePath);
    // size+mtime also invalidates the cache after an overwrite upgrade, which
    // rewrites the archive with new content.
    return marker.size === stat.size && marker.mtimeMs === stat.mtimeMs;
  } catch {
    return false;
  }
}

function extractArchive(archivePath: string, destDir: string): void {
  const stat = statSync(archivePath);
  const tempDir = `${destDir}.tmp-${process.pid}`;

  rmSync(tempDir, { recursive: true, force: true });
  mkdirSync(tempDir, { recursive: true });

  try {
    new AdmZip(archivePath).extractAllTo(tempDir, true);
  } catch (error) {
    rmSync(tempDir, { recursive: true, force: true });
    throw error;
  }

  const marker: LazyAssetMarker = {
    archive: archivePath,
    size: stat.size,
    mtimeMs: stat.mtimeMs,
    extractedAt: new Date().toISOString(),
  };
  try {
    writeFileSync(join(tempDir, MARKER_FILE), JSON.stringify(marker, null, 2), 'utf-8');
  } catch (error) {
    logger.warn('[lazy-asset] Failed to write extraction marker:', error);
  }

  // Swap the freshly extracted tree into place. Remove the previous directory
  // first: Windows cannot rename a directory onto an existing one.
  rmSync(destDir, { recursive: true, force: true });
  mkdirSync(dirname(destDir), { recursive: true });
  renameSync(tempDir, destDir);
}

/**
 * Resolve the directory of a lazily shipped asset, extracting it on first use.
 *
 * @returns the extracted directory, or null when the asset is not shipped as a
 *          lazy archive — callers must then fall back to their normal lookup.
 */
export function ensureLazyAssetExtracted(name: string): string | null {
  const archivePath = getLazyAssetArchivePath(name);
  if (!archivePath) return null;

  const destDir = getLazyAssetCacheDir(name);
  if (existsSync(destDir) && isExtractionCurrent(archivePath, destDir)) {
    return destDir;
  }

  try {
    const startedAt = Date.now();
    logger.info(`[lazy-asset] Extracting "${name}" from ${archivePath}`);
    extractArchive(archivePath, destDir);
    logger.info(`[lazy-asset] Extracted "${name}" in ${Date.now() - startedAt}ms -> ${destDir}`);
    return destDir;
  } catch (error) {
    logger.error(`[lazy-asset] Failed to extract "${name}":`, error);
    return null;
  }
}

/** Remove the whole extraction cache (diagnostics / recovery). */
export function clearLazyAssetCache(): void {
  rmSync(getLazyCacheRoot(), { recursive: true, force: true });
}
