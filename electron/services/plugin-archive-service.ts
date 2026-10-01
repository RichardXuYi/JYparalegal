/**
 * Per-channel plugin archive provisioning.
 *
 * The seven bundled channel plugins are ~255 MB of node_modules that a user only
 * needs for the channels they configure. Builds can therefore ship none of them
 * and fetch just the one that is required — the same download/verify path the
 * OpenClaw runtime uses (electron/utils/archive-download.ts).
 *
 * Resolution order for a plugin id:
 *   1. `bundled`     — the zip ships in the package (extracted on first use)
 *   2. `cached`      — a previously downloaded, sha256-verified zip
 *   3. `remote`      — the manifest lists a URL; download it now
 *   4. `unavailable` — nothing to use; the caller reports a clear error
 *
 * Extraction itself stays with lazy-asset/plugin-install (synchronous, local).
 * See docs/startup-performance-plan.md §9.6.
 */
import { existsSync } from 'fs';
import { downloadArchiveToFile, sha256File } from '../utils/archive-download';
import { logger } from '../utils/logger';
import { ensureLazyAssetExtracted } from '../utils/lazy-asset';
import {
  findBundledPluginArchive,
  getCachedPluginArchivePath,
  getPluginArchiveEntry,
  getPluginArchiveUrl,
} from '../utils/plugin-manifest';

export type PluginArchiveState = 'bundled' | 'cached' | 'remote' | 'unavailable';

export interface PluginArchiveProgress {
  receivedBytes: number;
  totalBytes: number | null;
}

export interface EnsurePluginArchiveOptions {
  onProgress?: (progress: PluginArchiveProgress) => void;
  signal?: AbortSignal;
}

export interface EnsurePluginArchiveResult {
  ok: boolean;
  state: PluginArchiveState;
  /** Local zip path when the archive is available. */
  path?: string;
  error?: string;
}

/** Where the plugin archive currently has to come from. */
export function getPluginArchiveState(id: string): PluginArchiveState {
  if (findBundledPluginArchive(id)) return 'bundled';

  const entry = getPluginArchiveEntry(id);
  const cachedPath = getCachedPluginArchivePath(id, entry);
  if (existsSync(cachedPath)) return 'cached';

  if (entry && getPluginArchiveUrl(id)) return 'remote';
  return 'unavailable';
}

/** True when the plugin is already shipped inside the package (the classic build). */
export function isPluginArchiveBundled(id: string): boolean {
  return findBundledPluginArchive(id) !== null;
}

/**
 * Make sure a usable plugin archive exists locally, downloading and verifying it
 * when the build does not ship it. Never throws: callers get `ok: false` plus a
 * human-readable reason so the channel-setup flow can surface it.
 */
export async function ensurePluginArchiveCached(
  id: string,
  options: EnsurePluginArchiveOptions = {},
): Promise<EnsurePluginArchiveResult> {
  const state = getPluginArchiveState(id);
  if (state === 'bundled') {
    return { ok: true, state, path: findBundledPluginArchive(id) ?? undefined };
  }
  if (state === 'unavailable') {
    return {
      ok: false,
      state,
      error: `The ${id} plugin archive is neither bundled in this build nor listed in the download manifest.`,
    };
  }

  const entry = getPluginArchiveEntry(id);
  const cachedPath = getCachedPluginArchivePath(id, entry);

  // Re-verify a cached archive: a truncated or tampered zip must not be extracted.
  if (state === 'cached' && entry?.sha256) {
    try {
      if (await sha256File(cachedPath) === entry.sha256) {
        return { ok: true, state, path: cachedPath };
      }
      logger.warn(`[plugin-archive] Cached ${id} archive failed verification; re-downloading.`);
    } catch (error) {
      logger.warn(`[plugin-archive] Could not verify cached ${id} archive:`, error);
    }
  }

  const url = getPluginArchiveUrl(id);
  if (!url) {
    return {
      ok: false,
      state: 'unavailable',
      error: `No download URL for the ${id} plugin archive.`,
    };
  }

  try {
    logger.info(`[plugin-archive] Downloading ${id} plugin archive from ${url}`);
    const result = await downloadArchiveToFile({
      url,
      destinationPath: cachedPath,
      expectedSha256: entry?.sha256,
      expectedSize: entry?.size ?? null,
      signal: options.signal,
      onProgress: (progress) => options.onProgress?.({
        receivedBytes: progress.receivedBytes,
        totalBytes: progress.totalBytes,
      }),
    });
    logger.info(`[plugin-archive] Downloaded ${id} plugin archive (${(result.bytes / 1024 / 1024).toFixed(1)} MB)`);
    return { ok: true, state: 'cached', path: result.path };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    logger.warn(`[plugin-archive] Failed to download ${id} plugin archive:`, error);
    return { ok: false, state: 'remote', error: message };
  }
}

/**
 * Extract a plugin archive that is already available locally (bundled or
 * downloaded) and return the extracted directory.
 *
 * @returns the extracted plugin directory, or null when nothing is available —
 *          callers then fall back to their own candidate list.
 */
export function extractAvailablePluginArchive(id: string): string | null {
  const bundled = findBundledPluginArchive(id);
  if (bundled) {
    return ensureLazyAssetExtracted(`openclaw-plugins/${id}`);
  }
  const entry = getPluginArchiveEntry(id);
  const cachedPath = getCachedPluginArchivePath(id, entry);
  if (existsSync(cachedPath)) {
    return ensureLazyAssetExtracted(`openclaw-plugins/${id}`, { archivePathOverride: cachedPath });
  }
  return null;
}
