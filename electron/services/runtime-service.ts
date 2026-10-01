/**
 * OpenClaw runtime provisioning service (main process).
 *
 * Responsibilities:
 *   - report readiness (is a runtime present, and does it match the manifest pin?)
 *   - download the pinned runtime archive — resumable, sha256-verified
 *   - extract it into a staging directory and activate it atomically
 *   - roll back to a previous version, or import a locally supplied archive
 *
 * Design notes:
 *   - The HTTP download uses Electron's `net` module. That inherits the proxy
 *     configured on `session.defaultSession` by `applyProxySettings()`, which a
 *     utility process (no Electron APIs) or plain `https` (no proxy awareness)
 *     would not. `https-proxy-agent` is only a devDependency, so it must not be
 *     used here.
 *   - Extraction runs in the main process through `tar` (a production
 *     dependency, already used by skillhub-service). Progress is throttled so a
 *     40k-entry archive cannot flood the IPC channel.
 *   - Nothing here blocks: the download streams, the extraction is async, and
 *     the caller (boot screen) renders progress from the emitted events.
 *
 * See docs/startup-performance-plan.md.
 */
import { existsSync, mkdirSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename, join } from 'node:path';
import type {
  RuntimeActionResult,
  RuntimeInstallPayload,
  RuntimeImportPayload,
  RuntimeManifestView,
  RuntimeManifestResult,
  RuntimePhase,
  RuntimeProgress,
  RuntimeRollbackPayload,
  RuntimeStatus,
} from '@shared/host-api/contract';
import { logger } from '../utils/logger';
import { downloadArchiveToFile, sha256File } from '../utils/archive-download';
import {
  getRuntimeRootDir,
  isRuntimeDirUsable,
  writeActiveRuntimePointer,
  clearActiveRuntimePointer,
  type ActiveRuntimePointer,
} from '../utils/paths';
import {
  RUNTIME_INSTALL_MARKER_NAME,
  getDesiredRuntimeVersion,
  getRuntimeReadiness,
  getRuntimeVersionDir,
  isRuntimeVersionCompatible,
  listInstalledRuntimeVersions,
  normalizeRuntimeVersion,
  pruneRuntimeVersions,
  readRuntimeInstallMarker,
  readRuntimeManifest,
  readRuntimePackageVersion,
  readRuntimeConsent,
  writeRuntimeConsent,
} from '../utils/runtime-manifest';

const require = createRequire(import.meta.url);

interface TarEntryLike {
  type?: string;
  path?: string;
}

const tar = require('tar') as {
  x: (options: {
    file: string;
    cwd: string;
    preservePaths?: boolean;
    onentry?: (entry: TarEntryLike) => void;
  }) => Promise<void>;
};

export interface RuntimeEventSink {
  progress: (payload: RuntimeProgress) => void;
  stateChanged: (payload: RuntimeStatus) => void;
  log: (level: 'info' | 'warn' | 'error', message: string) => void;
}

/** How many old runtime versions to keep for rollback. */
const KEEP_RUNTIME_VERSIONS = 1;
/** Emit at most one progress event per this interval (phase changes bypass it). */
const PROGRESS_THROTTLE_MS = 250;

interface ActiveRun {
  aborted: boolean;
  phase: RuntimePhase;
  progress: RuntimeProgress;
}

export interface RuntimeService {
  getStatus: () => Promise<RuntimeStatus>;
  getManifest: () => Promise<RuntimeManifestResult>;
  install: (payload: RuntimeInstallPayload) => Promise<RuntimeActionResult>;
  cancel: () => Promise<RuntimeActionResult>;
  rollback: (payload: RuntimeRollbackPayload) => Promise<RuntimeActionResult>;
  importArchive: (payload: RuntimeImportPayload) => Promise<RuntimeActionResult>;
}

/** Derive a safe archive file name from the manifest URL. */
function archiveFileNameFor(url: string, version: string): string {
  try {
    const name = basename(new URL(url).pathname);
    if (name && /\.(tar\.gz|tgz|tar)$/i.test(name)) return name;
  } catch {
    // fall through to the constructed name
  }
  return `openclaw-runtime-${version}-${process.platform}-${process.arch}.tar.gz`;
}

export function createRuntimeService(sink: RuntimeEventSink): RuntimeService {
  let activeRun: ActiveRun | null = null;
  let lastError: string | null = null;
  let lastProgressEmitAt = 0;
  let abortRequested = false;
  /** Aborts the in-flight download when the user cancels. */
  let currentAbort: AbortController | null = null;

  function log(level: 'info' | 'warn' | 'error', message: string): void {
    logger[level](`[runtime] ${message}`);
    sink.log(level, message);
  }

  function stateForPhase(phase: RuntimePhase): RuntimeStatus['state'] {
    switch (phase) {
      case 'downloading':
        return 'downloading';
      case 'verifying':
        return 'verifying';
      case 'extracting':
        return 'extracting';
      case 'activating':
        return 'activating';
      default:
        return 'downloading';
    }
  }

  async function getStatus(): Promise<RuntimeStatus> {
    const readiness = getRuntimeReadiness();
    const manifest = readRuntimeManifest();
    const base = {
      ready: readiness.ready,
      reason: readiness.reason,
      source: readiness.source,
      dir: readiness.dir,
      installedVersion: readiness.installedVersion,
      desiredVersion: readiness.desiredVersion ?? normalizeRuntimeVersion(manifest?.runtimeVersion),
      manifestPresent: readiness.manifestPresent,
      consent: readRuntimeConsent(),
      installedVersions: listInstalledRuntimeVersions(),
    } satisfies Omit<RuntimeStatus, 'state'>;

    if (activeRun) {
      return { ...base, state: stateForPhase(activeRun.phase), progress: activeRun.progress };
    }
    if (readiness.ready) {
      return { ...base, state: 'ready' };
    }
    if (lastError) {
      return { ...base, state: 'failed', error: lastError };
    }
    return { ...base, state: readiness.reason === 'missing' ? 'missing' : 'version-mismatch' };
  }

  async function getManifest(): Promise<RuntimeManifestResult> {
    const manifest = readRuntimeManifest();
    if (!manifest) {
      return { success: true, manifest: null };
    }
    const view: RuntimeManifestView = {
      runtimeVersion: manifest.runtimeVersion,
      platform: manifest.platform,
      arch: manifest.arch,
      archiveUrl: manifest.archiveUrl,
      sha256: manifest.sha256,
      size: manifest.size,
      fileCount: manifest.fileCount,
      minShellVersion: manifest.minShellVersion,
    };
    return { success: true, manifest: view };
  }

  function emitStateChanged(): void {
    void getStatus().then((status) => sink.stateChanged(status));
  }

  function emitProgress(patch: Partial<RuntimeProgress>, force = false): void {
    if (!activeRun) return;
    activeRun.progress = { ...activeRun.progress, ...patch };
    if (patch.phase && patch.phase !== activeRun.phase) {
      activeRun.phase = patch.phase;
      lastProgressEmitAt = Date.now();
      sink.progress(activeRun.progress);
      emitStateChanged();
      return;
    }
    const now = Date.now();
    if (force || now - lastProgressEmitAt >= PROGRESS_THROTTLE_MS) {
      lastProgressEmitAt = now;
      sink.progress(activeRun.progress);
    }
  }

  function fail(message: string): RuntimeActionResult {
    lastError = message;
    log('error', message);
    emitStateChanged();
    return { success: false, error: message, status: statusSnapshot() };
  }

  /** Synchronous best-effort status for return payloads while an async one is pending. */
  function statusSnapshot(): RuntimeStatus {
    const readiness = getRuntimeReadiness();
    return {
      state: activeRun
        ? stateForPhase(activeRun.phase)
        : readiness.ready
          ? 'ready'
          : lastError
            ? 'failed'
            : readiness.reason === 'missing'
              ? 'missing'
              : 'version-mismatch',
      ready: readiness.ready,
      reason: readiness.reason,
      source: readiness.source,
      dir: readiness.dir,
      installedVersion: readiness.installedVersion,
      desiredVersion: readiness.desiredVersion,
      manifestPresent: readiness.manifestPresent,
      consent: readRuntimeConsent(),
      installedVersions: listInstalledRuntimeVersions(),
      progress: activeRun?.progress,
      error: lastError ?? undefined,
    };
  }

  async function extractArchive(archivePath: string, stagingDir: string, totalFiles: number | null): Promise<number> {
    rmSync(stagingDir, { recursive: true, force: true });
    mkdirSync(stagingDir, { recursive: true });

    let extracted = 0;
    await tar.x({
      file: archivePath,
      cwd: stagingDir,
      // Never allow absolute paths or `..` escapes from the archive.
      preservePaths: false,
      onentry: (entry) => {
        if (entry.type === 'Directory') return;
        extracted += 1;
        emitProgress({ phase: 'extracting', extractedEntries: extracted, totalFiles });
      },
    });
    return extracted;
  }

  function activate(version: string, dir: string, source: ActiveRuntimePointer['source'], sha256?: string): void {
    const pointer: ActiveRuntimePointer = {
      version,
      dir,
      sha256,
      installedAt: new Date().toISOString(),
      source,
    };
    writeActiveRuntimePointer(pointer);
    const pruned = pruneRuntimeVersions(KEEP_RUNTIME_VERSIONS, [version]);
    if (pruned.length > 0) {
      log('info', `pruned old runtime versions: ${pruned.join(', ')}`);
    }
  }

  async function install(payload: RuntimeInstallPayload): Promise<RuntimeActionResult> {
    if (activeRun) {
      return { success: false, error: 'A runtime installation is already running', status: statusSnapshot() };
    }

    const manifest = readRuntimeManifest();
    const desired = normalizeRuntimeVersion(manifest?.runtimeVersion);
    lastError = null;

    if (payload?.consent === false) {
      writeRuntimeConsent(false, { desiredVersion: desired ?? undefined, source: 'app' });
      log('info', 'runtime download declined by the user');
      return { success: true, status: statusSnapshot() };
    }
    // The download is an explicit, user-approved action: an unset consent must
    // not silently start a ~300 MB transfer.
    if (payload?.consent !== true && readRuntimeConsent() !== 'granted') {
      return {
        success: false,
        error: 'User confirmation is required before downloading the runtime.',
        status: statusSnapshot(),
      };
    }
    if (payload?.consent === true) {
      writeRuntimeConsent(true, { desiredVersion: desired ?? undefined, source: 'app' });
    }

    if (!manifest?.archiveUrl) {
      return fail('This build has no runtime download URL in its manifest.');
    }
    if (!desired) {
      return fail('This build does not pin a runtime version.');
    }

    const readiness = getRuntimeReadiness();
    if (readiness.ready && isRuntimeVersionCompatible(readiness.installedVersion, desired)) {
      return { success: true, status: statusSnapshot() };
    }

    const root = getRuntimeRootDir();
    const cacheDir = join(root, '.cache');
    const stagingDir = join(root, `.staging-${process.pid}`);
    const versionDir = getRuntimeVersionDir(desired);
    const archivePath = join(cacheDir, archiveFileNameFor(manifest.archiveUrl, desired));
    const partPath = `${archivePath}.part`;

    mkdirSync(cacheDir, { recursive: true });
    abortRequested = false;
    currentAbort = new AbortController();
    activeRun = {
      aborted: false,
      phase: 'resolving',
      progress: {
        phase: 'resolving',
        receivedBytes: 0,
        totalBytes: manifest.size ?? null,
        extractedEntries: 0,
        totalFiles: manifest.fileCount ?? null,
      },
    };
    emitStateChanged();

    try {
      // 1. Download (the shared downloader reuses an already-verified archive and
      //    resumes a `.part` when the server honours range requests).
      let sha256 = '';
      let cachedOk = false;
      if (existsSync(archivePath) && manifest.sha256) {
        const cachedHash = await sha256File(archivePath);
        cachedOk = cachedHash === manifest.sha256;
        if (cachedOk) {
          sha256 = cachedHash;
          log('info', 'using cached runtime archive');
        } else {
          rmSync(archivePath, { force: true });
        }
      }

      if (!cachedOk) {
        emitProgress(
          {
            phase: 'downloading',
            receivedBytes: existsSync(partPath) ? statSync(partPath).size : 0,
            totalBytes: manifest.size ?? null,
          },
          true,
        );
        const result = await downloadArchiveToFile({
          url: manifest.archiveUrl,
          destinationPath: archivePath,
          expectedSha256: manifest.sha256,
          expectedSize: manifest.size ?? null,
          signal: currentAbort?.signal,
          onProgress: (progress) => emitProgress({
            phase: 'downloading',
            receivedBytes: progress.receivedBytes,
            totalBytes: progress.totalBytes,
          }),
        });
        sha256 = result.sha256;
      }

      if (abortRequested) {
        throw new Error('cancelled');
      }

      // 2. Verify. The downloader enforces the digest already; this also covers
      //    the reused-cache path.
      emitProgress({ phase: 'verifying' }, true);
      if (manifest.sha256 && sha256 !== manifest.sha256) {
        rmSync(archivePath, { force: true });
        throw new Error('checksum mismatch — the download was corrupted, please retry');
      }

      // 3. Extract.
      emitProgress({ phase: 'extracting', extractedEntries: 0, totalFiles: manifest.fileCount ?? null }, true);
      const extracted = await extractArchive(archivePath, stagingDir, manifest.fileCount ?? null);
      if (abortRequested) {
        throw new Error('cancelled');
      }
      if (!isRuntimeDirUsable(stagingDir)) {
        throw new Error('the downloaded archive does not contain a usable runtime');
      }

      // 4. Activate atomically.
      emitProgress({ phase: 'activating' }, true);
      const marker = {
        schema: 1,
        version: desired,
        sha256,
        size: statSync(archivePath).size,
        fileCount: extracted,
        installedAt: new Date().toISOString(),
        source: 'download' as const,
      };
      writeFileSync(join(stagingDir, RUNTIME_INSTALL_MARKER_NAME), JSON.stringify(marker, null, 2), 'utf-8');
      rmSync(versionDir, { recursive: true, force: true });
      renameSync(stagingDir, versionDir);
      activate(desired, versionDir, 'download', sha256);

      emitProgress({ phase: 'done', extractedEntries: extracted }, true);
      log('info', `runtime ${desired} installed (${extracted} files)`);
      activeRun = null;
      emitStateChanged();
      return { success: true, status: statusSnapshot() };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      rmSync(stagingDir, { recursive: true, force: true });
      activeRun = null;
      if (message === 'cancelled') {
        log('info', 'runtime installation cancelled');
        return { success: false, error: 'cancelled', status: statusSnapshot() };
      }
      return fail(message);
    }
  }

  async function cancel(): Promise<RuntimeActionResult> {
    if (!activeRun) {
      return { success: true, status: statusSnapshot() };
    }
    abortRequested = true;
    activeRun.aborted = true;
    try {
      currentAbort?.abort();
    } catch {
      // ignore: the signal may already be settled
    }
    log('info', 'cancelling runtime installation');
    return { success: true, status: statusSnapshot() };
  }

  async function rollback(payload: RuntimeRollbackPayload): Promise<RuntimeActionResult> {
    const installed = listInstalledRuntimeVersions();
    const requested = normalizeRuntimeVersion(payload?.version);
    const current = getRuntimeReadiness().installedVersion;

    const target = requested
      ? (installed.includes(requested) ? requested : null)
      : installed.find((version) => version !== current) ?? null;

    if (requested && !target) {
      return fail(`runtime ${requested} is not installed`);
    }

    if (!target) {
      clearActiveRuntimePointer();
      log('info', 'rolled back to the bundled runtime');
      lastError = null;
      emitStateChanged();
      return { success: true, status: statusSnapshot() };
    }

    const marker = readRuntimeInstallMarker(target);
    activate(target, getRuntimeVersionDir(target), marker?.source ?? 'download', marker?.sha256);
    log('info', `rolled back to runtime ${target}`);
    lastError = null;
    emitStateChanged();
    return { success: true, status: statusSnapshot() };
  }

  async function importArchive(payload: RuntimeImportPayload): Promise<RuntimeActionResult> {
    if (activeRun) {
      return { success: false, error: 'A runtime installation is already running', status: statusSnapshot() };
    }

    const archivePath = typeof payload?.path === 'string' ? payload.path : '';
    if (!archivePath || !existsSync(archivePath)) {
      return fail('The selected file does not exist.');
    }
    if (!/\.(tar\.gz|tgz)$/i.test(archivePath)) {
      return fail('Only .tar.gz runtime archives can be imported.');
    }

    const root = getRuntimeRootDir();
    const stagingDir = join(root, `.staging-import-${process.pid}`);
    lastError = null;
    activeRun = {
      aborted: false,
      phase: 'extracting',
      progress: {
        phase: 'extracting',
        receivedBytes: statSync(archivePath).size,
        totalBytes: statSync(archivePath).size,
        extractedEntries: 0,
        totalFiles: null,
      },
    };
    emitStateChanged();

    try {
      const extracted = await extractArchive(archivePath, stagingDir, null);
      if (!isRuntimeDirUsable(stagingDir)) {
        throw new Error('the selected archive does not contain a usable runtime');
      }

      const importedVersion = readRuntimePackageVersion(stagingDir);
      const desired = getDesiredRuntimeVersion();
      if (!importedVersion) {
        throw new Error('the selected archive does not declare a runtime version');
      }
      if (desired && !isRuntimeVersionCompatible(importedVersion, desired)) {
        throw new Error(
          `the selected runtime is version ${importedVersion}, but this build needs ${desired}`,
        );
      }

      const sha256 = await sha256File(archivePath);
      const versionDir = getRuntimeVersionDir(importedVersion);
      const marker = {
        schema: 1,
        version: importedVersion,
        sha256,
        size: statSync(archivePath).size,
        fileCount: extracted,
        installedAt: new Date().toISOString(),
        source: 'import' as const,
      };
      writeFileSync(join(stagingDir, RUNTIME_INSTALL_MARKER_NAME), JSON.stringify(marker, null, 2), 'utf-8');
      rmSync(versionDir, { recursive: true, force: true });
      renameSync(stagingDir, versionDir);
      activate(importedVersion, versionDir, 'import', sha256);

      emitProgress({ phase: 'done' }, true);
      log('info', `runtime ${importedVersion} imported from ${archivePath}`);
      activeRun = null;
      emitStateChanged();
      return { success: true, status: statusSnapshot() };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      rmSync(stagingDir, { recursive: true, force: true });
      activeRun = null;
      return fail(message);
    }
  }

  return { getStatus, getManifest, install, cancel, rollback, importArchive };
}

/** Re-exported for the API layer's "reveal in folder" action. */
export function getRuntimeFolderForReveal(): string {
  const root = getRuntimeRootDir();
  mkdirSync(root, { recursive: true });
  return root;
}
