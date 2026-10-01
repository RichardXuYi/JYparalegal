/**
 * Streaming archive downloader (main process).
 *
 * Shared by the OpenClaw runtime download and the per-channel plugin archive
 * download: both fetch a large `.zip`/`.tar.gz` from a self-hosted base URL and
 * must verify a sha256 before anything is extracted.
 *
 * Why Electron's `net` module rather than `node:https`:
 *   - it inherits the proxy configured on `session.defaultSession` by
 *     `applyProxySettings()`, so corporate proxies work without extra plumbing;
 *   - `https-proxy-agent` is only a devDependency and is not present in the
 *     packaged app, so it cannot be used here.
 *
 * Resumability: the download streams into `<destination>.part`. When the server
 * honours a `Range` request the partial file is hashed first and then appended
 * to, so the resulting digest covers the whole file. A stalled transfer is
 * aborted after `idleTimeoutMs` without progress.
 */
import { net } from 'electron';
import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream, existsSync, rmSync, renameSync, statSync } from 'node:fs';

export interface ArchiveDownloadProgress {
  receivedBytes: number;
  totalBytes: number | null;
  /** True when this event was preceded by a resumed (partial) transfer. */
  resumed: boolean;
}

export interface ArchiveDownloadOptions {
  url: string;
  /** Final path of the completed archive (a `.part` sibling is used in flight). */
  destinationPath: string;
  expectedSha256?: string;
  /** Used for progress totals when the server does not announce a length. */
  expectedSize?: number | null;
  idleTimeoutMs?: number;
  onProgress?: (progress: ArchiveDownloadProgress) => void;
  /** Aborting interrupts the in-flight request and rejects with `cancelled`. */
  signal?: AbortSignal;
}

export interface ArchiveDownloadResult {
  sha256: string;
  bytes: number;
  path: string;
  resumedFrom: number;
  /** False when an existing, already-verified archive was reused. */
  downloaded: boolean;
}

const DEFAULT_IDLE_TIMEOUT_MS = 60_000;
const HASH_CHUNK_BYTES = 1024 * 1024;

/** Hash a whole file (optionally feeding an existing hash for resume). */
export async function hashFile(
  filePath: string,
  existing?: ReturnType<typeof createHash>,
  onChunk?: (bytes: number) => void,
): Promise<string> {
  const hash = existing ?? createHash('sha256');
  await new Promise<void>((resolvePromise, rejectPromise) => {
    const stream = createReadStream(filePath, { highWaterMark: HASH_CHUNK_BYTES });
    stream.on('data', (chunk) => {
      hash.update(chunk);
      onChunk?.(chunk.length);
    });
    stream.on('error', rejectPromise);
    stream.on('end', () => resolvePromise());
  });
  return hash.digest('hex');
}

export function sha256File(filePath: string): Promise<string> {
  return hashFile(filePath);
}

/**
 * Ensure `destinationPath` holds a verified archive, reusing a previously
 * completed download when its digest already matches.
 */
export async function downloadArchiveToFile(
  options: ArchiveDownloadOptions,
): Promise<ArchiveDownloadResult> {
  const { destinationPath, expectedSha256 } = options;

  // 1. Reuse an already-downloaded archive that still matches.
  if (existsSync(destinationPath) && expectedSha256) {
    const existingHash = await sha256File(destinationPath);
    if (existingHash === expectedSha256) {
      return {
        sha256: existingHash,
        bytes: statSync(destinationPath).size,
        path: destinationPath,
        resumedFrom: 0,
        downloaded: false,
      };
    }
    rmSync(destinationPath, { force: true });
  }

  const partPath = `${destinationPath}.part`;
  let resumeFrom = 0;
  if (existsSync(partPath)) {
    try {
      resumeFrom = statSync(partPath).size;
    } catch {
      resumeFrom = 0;
    }
  }

  const hash = createHash('sha256');
  let received = resumeFrom;
  if (resumeFrom > 0) {
    // The resumed bytes must be part of the digest.
    await hashFile(partPath, hash);
  }

  const result = await new Promise<{ sha256: string; bytes: number }>((resolvePromise, rejectPromise) => {
    let settled = false;
    let idleTimer: NodeJS.Timeout | null = null;
    let request: Electron.ClientRequest | null = null;

    const onAbort = (): void => {
      try {
        request?.abort();
      } catch {
        // ignore
      }
      finish(new Error('cancelled'));
    };

    const finish = (error: Error | null, payload?: { sha256: string; bytes: number }): void => {
      if (settled) return;
      settled = true;
      if (idleTimer) clearTimeout(idleTimer);
      options.signal?.removeEventListener('abort', onAbort);
      if (error) rejectPromise(error);
      else resolvePromise(payload!);
    };

    if (options.signal?.aborted) {
      finish(new Error('cancelled'));
      return;
    }
    options.signal?.addEventListener('abort', onAbort, { once: true });

    const armIdleTimer = (): void => {
      if (idleTimer) clearTimeout(idleTimer);
      idleTimer = setTimeout(() => {
        try {
          request?.abort();
        } catch {
          // ignore
        }
        finish(new Error(`download stalled (no data for ${options.idleTimeoutMs ?? DEFAULT_IDLE_TIMEOUT_MS}ms)`));
      }, options.idleTimeoutMs ?? DEFAULT_IDLE_TIMEOUT_MS);
    };

    request = net.request({ method: 'GET', url: options.url, redirect: 'follow' });
    if (resumeFrom > 0) {
      request.setHeader('Range', `bytes=${resumeFrom}-`);
    }
    armIdleTimer();

    request.on('response', (response) => {
      const status = response.statusCode ?? 0;
      const appending = status === 206 && resumeFrom > 0;

      if (status !== 200 && status !== 206) {
        finish(new Error(`download failed with HTTP ${status}`));
        return;
      }
      if (resumeFrom > 0 && !appending) {
        // Server ignored the range: restart from zero.
        received = 0;
        resumeFrom = 0;
      }

      const announced = Number(response.headers['content-length'] ?? 0) || null;
      const totalBytes = announced
        ? announced + (appending ? resumeFrom : 0)
        : options.expectedSize ?? null;

      const stream = createWriteStream(partPath, { flags: appending ? 'a' : 'w' });

      response.on('data', (chunk: Buffer) => {
        armIdleTimer();
        stream.write(chunk);
        hash.update(chunk);
        received += chunk.length;
        options.onProgress?.({
          receivedBytes: received,
          totalBytes,
          resumed: appending,
        });
      });

      response.on('error', (error: Error) => {
        stream.end();
        finish(error);
      });

      response.on('end', () => {
        stream.end();
      });

      stream.on('error', (error) => finish(error));
      stream.on('close', () => {
        if (settled) return;
        if (received === 0) {
          finish(new Error('download produced no data'));
          return;
        }
        // digest() may only be called once — compute it after the settled guard.
        finish(null, { sha256: hash.digest('hex'), bytes: received });
      });
    });

    request.on('error', (error: Error) => finish(error));
    request.end();
  });

  if (options.signal?.aborted) {
    throw new Error('cancelled');
  }

  if (expectedSha256 && result.sha256 !== expectedSha256) {
    rmSync(partPath, { force: true });
    rmSync(destinationPath, { force: true });
    throw new Error('checksum mismatch — the download was corrupted, please retry');
  }

  // Promote the verified .part to the final path.
  rmSync(destinationPath, { force: true });
  renameSync(partPath, destinationPath);

  return {
    sha256: result.sha256,
    bytes: result.bytes,
    path: destinationPath,
    resumedFrom: resumeFrom,
    downloaded: true,
  };
}
