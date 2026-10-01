/**
 * Manifest and cache locations for per-channel plugin archives.
 *
 * Channel plugins (dingtalk / wecom / feishu / discord / qqbot / whatsapp /
 * weixin) are ~255 MB of node_modules that a user only needs for the channels
 * they actually configure. They are already archived one zip per plugin by
 * scripts/after-pack.cjs; this module lets a build ship *no* plugin archives at
 * all and fetch the one that is needed:
 *
 *   resources/lazy-assets/openclaw-plugins/<id>.zip  (bundled, extracted on use)
 *   userData/lazy-cache/archives/<id>-<version>.zip  (downloaded, then extracted)
 *
 * `plugins-manifest.json` (built by scripts/pack-plugin-downloads.mjs) lists the
 * archives that live on the release host, with size and sha256.
 *
 * Why the plugin mirrors must stay self-contained: they are copied out to
 * `~/.openclaw/extensions/<id>/` when a channel is configured, and Node module
 * resolution from that path cannot see the app's own runtime node_modules.
 * So this module only moves the *bytes*, never the dependency graph.
 */
import { existsSync, readFileSync } from 'fs';
import { join } from 'path';
import { getLazyCacheRoot } from './lazy-asset';
import { logger } from './logger';

export const PLUGIN_ARCHIVE_MANIFEST_FILE_NAME = 'plugins-manifest.json';
export const BUNDLED_PLUGIN_ARCHIVE_DIR = join('lazy-assets', 'openclaw-plugins');

export interface PluginArchiveEntry {
  id: string;
  fileName?: string;
  version?: string;
  sha256?: string;
  size?: number;
  fileCount?: number;
}

export interface PluginArchiveManifest {
  schema: number;
  platform?: string;
  arch?: string;
  baseUrl?: string;
  archives: PluginArchiveEntry[];
}

/** Candidate manifest locations, most specific first. */
function manifestCandidates(): string[] {
  const candidates: string[] = [];
  const resourcesPath = (process as NodeJS.Process & { resourcesPath?: string }).resourcesPath;
  if (resourcesPath) {
    candidates.push(join(resourcesPath, PLUGIN_ARCHIVE_MANIFEST_FILE_NAME));
  }
  // Dev / unbundled builds: repo root (__dirname is electron/utils or dist-electron/main).
  candidates.push(join(__dirname, '..', '..', PLUGIN_ARCHIVE_MANIFEST_FILE_NAME));
  return candidates;
}

function parseJsonFile(filePath: string): unknown {
  // Strip a UTF-8 BOM: Windows editors add one, and JSON.parse rejects it.
  return JSON.parse(readFileSync(filePath, 'utf-8').replace(/^\uFEFF/, ''));
}

export function readPluginArchiveManifest(): PluginArchiveManifest | null {
  for (const candidate of manifestCandidates()) {
    if (!existsSync(candidate)) continue;
    try {
      const parsed = parseJsonFile(candidate) as Partial<PluginArchiveManifest>;
      if (!Array.isArray(parsed?.archives)) {
        logger.warn(`[plugin-archive] Manifest at ${candidate} has no archives array; ignoring.`);
        continue;
      }
      return {
        schema: typeof parsed.schema === 'number' ? parsed.schema : 1,
        platform: parsed.platform,
        arch: parsed.arch,
        baseUrl: typeof parsed.baseUrl === 'string' ? parsed.baseUrl : undefined,
        archives: parsed.archives.filter(
          (entry): entry is PluginArchiveEntry => Boolean(entry) && typeof entry.id === 'string',
        ),
      };
    } catch (error) {
      logger.warn(`[plugin-archive] Failed to parse plugin manifest at ${candidate}:`, error);
    }
  }
  return null;
}

export function getPluginArchiveEntry(id: string): PluginArchiveEntry | null {
  const manifest = readPluginArchiveManifest();
  return manifest?.archives.find((entry) => entry.id === id) ?? null;
}

/** Absolute URL of a plugin archive, when the manifest declares one. */
export function getPluginArchiveUrl(id: string): string | null {
  const manifest = readPluginArchiveManifest();
  const entry = manifest?.archives.find((candidate) => candidate.id === id);
  if (!manifest?.baseUrl || !entry) return null;
  const fileName = entry.fileName ?? `${id}.zip`;
  return `${manifest.baseUrl.replace(/\/+$/, '')}/${fileName}`;
}

/** Directory holding downloaded plugin archives. */
export function getPluginArchiveCacheDir(): string {
  return join(getLazyCacheRoot(), 'archives');
}

export function getCachedPluginArchivePath(id: string, entry?: PluginArchiveEntry | null): string {
  const suffix = entry?.version ? `${id}-${entry.version}.zip` : `${id}.zip`;
  return join(getPluginArchiveCacheDir(), suffix);
}

/** The archive shipped inside the application package, when present. */
export function findBundledPluginArchive(id: string): string | null {
  const resourcesPath = (process as NodeJS.Process & { resourcesPath?: string }).resourcesPath;
  const roots: string[] = [];
  if (resourcesPath) roots.push(join(resourcesPath, BUNDLED_PLUGIN_ARCHIVE_DIR));
  // Dev: the repo's own resources directory.
  roots.push(join(__dirname, '..', '..', 'resources', BUNDLED_PLUGIN_ARCHIVE_DIR));

  for (const root of roots) {
    const candidate = join(root, `${id}.zip`);
    if (existsSync(candidate)) return candidate;
  }
  return null;
}
