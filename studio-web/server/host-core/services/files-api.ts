import { app, nativeImage, shell as electronShell } from 'electron';
import crypto from 'node:crypto';
import { basename, extname, isAbsolute, join, relative, resolve, sep, win32 } from 'node:path';
import { fileURLToPath } from 'node:url';
import type {
  AttachmentAccessError,
  AttachmentFileRef,
  AttachmentSourceRef,
  FilePreviewTreeNode,
  FilePreviewTreeOptions,
  FileReadBinaryOptions,
  OpenAttachmentResult,
  ResolveAttachmentPayload,
  ResolveAttachmentResult,
} from '@shared/host-api/contract';
import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import { expandPath, getOpenClawConfigDir } from '../utils/paths';
import { isRecord } from './payload-utils';

const MAX_REFERENCE_LENGTH = 4096;
const MAX_DISPLAY_NAME_LENGTH = 160;
const SAFE_ATTACHMENT_ID = /^[A-Za-z0-9._-]+$/;

const EXT_MIME_MAP: Record<string, string> = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.bmp': 'image/bmp',
  '.ico': 'image/x-icon',
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.mov': 'video/quicktime',
  '.avi': 'video/x-msvideo',
  '.mkv': 'video/x-matroska',
  '.mp3': 'audio/mpeg',
  '.wav': 'audio/wav',
  '.ogg': 'audio/ogg',
  '.flac': 'audio/flac',
  '.pdf': 'application/pdf',
  '.zip': 'application/zip',
  '.gz': 'application/gzip',
  '.tar': 'application/x-tar',
  '.7z': 'application/x-7z-compressed',
  '.rar': 'application/vnd.rar',
  '.json': 'application/json',
  '.xml': 'application/xml',
  '.csv': 'text/csv',
  '.txt': 'text/plain',
  '.md': 'text/markdown',
  '.html': 'text/html',
  '.htm': 'text/html',
  '.css': 'text/css',
  '.js': 'text/javascript',
  '.ts': 'text/typescript',
  '.py': 'text/x-python',
  '.doc': 'application/msword',
  '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  '.xls': 'application/vnd.ms-excel',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  '.ppt': 'application/vnd.ms-powerpoint',
  '.pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
};

// Resolved lazily so the path follows the active user scope (see user-scope.ts).
function getOutboundDir(): string {
  return join(getOpenClawConfigDir(), 'media', 'outbound');
}
const DIRECTORY_MIME_TYPE = 'application/x-directory';
const FILE_PREVIEW_MAX_TEXT_BYTES = 2 * 1024 * 1024;
const FILE_PREVIEW_MAX_BINARY_BYTES = 50 * 1024 * 1024;

// ── ClawX-style attachment validation helpers ─────────────────────

function attachmentFailure(error: unknown): AttachmentAccessError {
  if (error && typeof error === 'object') {
    if ('code' in error && (error as { code?: string }).code === 'ENOENT') return 'unavailable';
    if ('attachmentCode' in error) {
      return (error as { attachmentCode: AttachmentAccessError }).attachmentCode;
    }
  }
  return 'operationFailed';
}

function hasTraversal(value: string): boolean {
  return value.split(/[\\/]+/u).includes('..');
}

function validateReferenceSyntax(uri: string): void {
  if (!uri || uri.length > MAX_REFERENCE_LENGTH || uri.includes('\0')) {
    throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
  }
  if (uri.startsWith('\\\\') || uri.startsWith('//')) {
    throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
  }
  if (hasTraversal(uri)) {
    throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
  }
}

function isInside(child: string, parent: string): boolean {
  const relativePath = relative(parent, child);
  return relativePath === ''
    || (!isAbsolute(relativePath) && relativePath !== '..' && !relativePath.startsWith(`..${sep}`));
}

function safeDisplayName(value: unknown, fallback: string): string {
  const raw = typeof value === 'string' && value.trim() ? value : fallback;
  const filename = basename(raw);
  const withoutControls = Array.from(filename.slice(0, MAX_DISPLAY_NAME_LENGTH * 4), (character) => {
    const codePoint = character.codePointAt(0) ?? 0;
    const isControl = codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f);
    return isControl ? '' : character;
  }).join('');
  const cleaned = withoutControls.replace(/\s+/gu, ' ').trim().slice(0, MAX_DISPLAY_NAME_LENGTH);
  return cleaned || 'attachment';
}

function decodeBasename(uri: string): string {
  try {
    if (/^https?:/i.test(uri)) {
      const url = new URL(uri);
      const base = url.pathname.split('/').pop() || url.hostname;
      return decodeURIComponent(base) || url.hostname;
    }
    if (/^file:/i.test(uri)) return basename(fileURLToPath(uri));
  } catch {
    // Return generic label below
  }
  return basename(uri.replace(/[?#].*$/u, '')) || 'attachment';
}

function mimeTypeForPath(path: string): string {
  return EXT_MIME_MAP[extname(path).toLowerCase()] ?? 'application/octet-stream';
}

function normalizeRemote(uri: string): string {
  let url: URL;
  try {
    url = new URL(uri);
  } catch {
    throw { attachmentCode: 'unsafeUrl' as AttachmentAccessError };
  }
  if ((url.protocol !== 'http:' && url.protocol !== 'https:') || !url.hostname || url.username || url.password) {
    throw { attachmentCode: 'unsafeUrl' as AttachmentAccessError };
  }
  return url.href;
}

function opaqueIdentity(value: string): string {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function parseOutgoingUrl(uri: string): { attachmentId: string; sessionKey: string } | null {
  // Match: /api/chat/media/outgoing/<sessionKey>/<attachmentId>/full
  // Or:   /__openclaw__/assistant-media?source=<path>
  const outgoingMatch = uri.match(/\/api\/chat\/media\/outgoing\/([^/]+)\/([^/]+)\//);
  if (outgoingMatch) {
    let sessionKey: string;
    let attachmentId: string;
    try {
      sessionKey = decodeURIComponent(outgoingMatch[1]);
      attachmentId = decodeURIComponent(outgoingMatch[2]);
    } catch {
      throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
    }
    if (!sessionKey || !SAFE_ATTACHMENT_ID.test(attachmentId)) {
      throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
    }
    return { attachmentId, sessionKey };
  }
  // Match: /__openclaw__/assistant-media?source=<path>
  if (uri.includes('/__openclaw__/assistant-media')) {
    const url = new URL(uri, 'http://localhost');
    const source = url.searchParams.get('source');
    if (source) {
      return { attachmentId: source, sessionKey: 'assistant-media' };
    }
  }
  return null;
}

async function readOutgoingMediaRecord(attachmentId: string, recordBase: string): Promise<{
  originalPath: string;
  contentType?: string;
} | null> {
  const fsP = await import('node:fs/promises');
  const recordPath = join(recordBase, `${attachmentId}.json`);
  try {
    const raw = await fsP.readFile(recordPath, 'utf8');
    const record = JSON.parse(raw) as { original?: { path?: string; contentType?: string } };
    if (!record?.original?.path) return null;
    return {
      originalPath: record.original.path,
      contentType: record.original.contentType,
    };
  } catch {
    return null;
  }
}

async function resolveOutgoingMediaAttachment(uri: string): Promise<{
  path: string;
  mimeType: string;
  size: number;
} | null> {
  try {
    const outgoing = parseOutgoingUrl(uri);
    if (!outgoing) return null;
    const fsP = await import('node:fs/promises');
    const stateDir = getOpenClawConfigDir();
    const configDir = getOpenClawConfigDir();

    let resolved: { originalPath: string; contentType?: string } | null = null;
    if (outgoing.sessionKey === 'assistant-media') {
      // For assistant-media URLs, the attachmentId is the source path itself
      resolved = { originalPath: outgoing.attachmentId };
    } else {
      // Look up record file
      const recordBase = join(stateDir, 'media', 'outgoing', 'records');
      const record = await readOutgoingMediaRecord(outgoing.attachmentId, recordBase);
      if (!record) {
        // Fallback: check config dir
        const configRecordBase = join(configDir, 'media', 'outgoing', 'records');
        resolved = await readOutgoingMediaRecord(outgoing.attachmentId, configRecordBase);
      } else {
        resolved = record;
      }
    }
    if (!resolved) return null;

    const canonicalPath = resolve(resolved.originalPath);
    const fileStat = await fsP.stat(canonicalPath);
    if (!fileStat.isFile()) return null;
    return {
      path: canonicalPath,
      mimeType: resolved.contentType || mimeTypeForPath(canonicalPath),
      size: fileStat.size,
    };
  } catch {
    return null;
  }
}

function localPathFromUri(uri: string, executionCwd: string): string {
  if (/^file:/i.test(uri)) {
    let url: URL;
    try {
      url = new URL(uri);
    } catch {
      throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
    }
    if (url.username || url.password || (url.hostname && url.hostname.toLowerCase() !== 'localhost')) {
      throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
    }
    try {
      return fileURLToPath(url);
    } catch {
      throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
    }
  }
  if (/^[A-Za-z][A-Za-z0-9+.-]*:/u.test(uri) && !win32.isAbsolute(uri) && !isAbsolute(uri)) {
    throw { attachmentCode: 'invalidReference' as AttachmentAccessError };
  }
  if (uri.startsWith('~')) return resolve(expandPath(uri));
  if (isAbsolute(uri) || win32.isAbsolute(uri)) return resolve(uri);
  return resolve(executionCwd, uri);
}

const FILE_PREVIEW_TREE_MAX_DEPTH = 6;
const FILE_PREVIEW_TREE_MAX_NODES = 5000;
const FILE_PREVIEW_DIR_BLACKLIST = new Set([
  'node_modules',
  '.venv',
  '__pycache__',
  '.git',
  'dist',
  'build',
  '.next',
  '.turbo',
  '.cache',
]);

type StagePathsPayload = {
  filePaths?: unknown;
};

type StageBufferPayload = {
  base64?: unknown;
  fileName?: unknown;
  mimeType?: unknown;
};

type PathPayload = {
  path?: unknown;
  content?: unknown;
  opts?: unknown;
};

type ResolvedSandboxedPath = {
  realPath: string;
  readOnly: boolean;
};

function getMimeType(ext: string): string {
  return EXT_MIME_MAP[ext.toLowerCase()] || 'application/octet-stream';
}

function mimeToExt(mimeType: string): string {
  for (const [ext, mime] of Object.entries(EXT_MIME_MAP)) {
    if (mime === mimeType) return ext;
  }
  return '';
}

async function generateImagePreview(filePath: string, mimeType: string): Promise<string | null> {
  try {
    const { readFile } = await import('node:fs/promises');

    // Infer mimeType from file extension if not reliable
    let effectiveMimeType = mimeType;
    if (!effectiveMimeType || effectiveMimeType === 'application/octet-stream') {
      const ext = extname(filePath).toLowerCase();
      const inferred = EXT_MIME_MAP[ext];
      if (inferred?.startsWith('image/')) {
        effectiveMimeType = inferred;
      }
    }

    if (effectiveMimeType === 'image/svg+xml') {
      const buf = await readFile(filePath);
      return `data:${effectiveMimeType};base64,${buf.toString('base64')}`;
    }

    const img = nativeImage.createFromPath(filePath);
    if (!img.isEmpty()) {
      // Electron environment: use nativeImage for resizing
      const size = img.getSize();
      const maxDim = 512;
      if (size.width > maxDim || size.height > maxDim) {
        const resized = size.width >= size.height
          ? img.resize({ width: maxDim })
          : img.resize({ height: maxDim });
        return `data:image/png;base64,${resized.toPNG().toString('base64')}`;
      }
    }
    // Fallback: read file directly as base64 data URL
    // (studio-web shim returns empty image, or nativeImage failed)
    const buf = await readFile(filePath);
    return `data:${effectiveMimeType};base64,${buf.toString('base64')}`;
  } catch {
    return null;
  }
}

function requirePath(payload: unknown): string {
  const path = isRecord(payload) ? payload.path : payload;
  if (typeof path !== 'string' || !path.trim()) {
    throw new Error('Invalid file path');
  }
  return path;
}

function isPathInside(child: string, parent: string): boolean {
  const c = resolve(child);
  const p = resolve(parent);
  if (process.platform === 'win32') {
    const cl = c.toLowerCase();
    const pl = p.toLowerCase();
    return cl === pl || cl.startsWith(pl + sep);
  }
  return c === p || c.startsWith(p + sep);
}

function getFilePreviewWriteRoots(): string[] {
  const roots: string[] = [];
  roots.push(resolve(getOpenClawConfigDir()));
  try {
    roots.push(resolve(app.getPath('userData')));
  } catch {
    // ignore
  }
  roots.push(resolve(getOutboundDir()));
  return roots;
}

async function resolveSandboxedPath(
  input: string,
  mode: 'read' | 'write' = 'read',
): Promise<ResolvedSandboxedPath> {
  if (!input.trim()) {
    throw new Error('outsideSandbox');
  }
  const expanded = expandPath(input);
  const fsP = await import('node:fs/promises');
  let real: string;
  try {
    real = await fsP.realpath(expanded);
  } catch {
    real = resolve(expanded);
  }
  const writeRoots = getFilePreviewWriteRoots();
  if (writeRoots.some((root) => isPathInside(real, root))) {
    return { realPath: real, readOnly: false };
  }
  // Reads are confined to the same per-scope roots as writes. On the
  // multi-tenant web host every worker runs with its own DATA_DIR /
  // OPENCLAW_STATE_DIR, so allowing arbitrary real paths here would let one
  // account read every other account's data dir, the server .env, etc.
  if (mode === 'write') {
    throw new Error('readOnlyRoot');
  }
  throw new Error('outsideSandbox');
}

/**
 * Resolve a client-supplied path for reading, confined to this account's host
 * roots. Throws `outsideSandbox` when the path escapes. Shared by the file and
 * media services so every read-side entry point enforces the same boundary.
 */
export async function resolveHostReadablePath(input: string): Promise<string> {
  const { realPath } = await resolveSandboxedPath(input, 'read');
  return realPath;
}

function looksLikeBinary(buf: Buffer): boolean {
  const limit = Math.min(buf.length, 8192);
  for (let i = 0; i < limit; i += 1) {
    if (buf[i] === 0) return true;
  }
  return false;
}

function shouldSkipDirEntry(name: string, includeHidden: boolean): boolean {
  if (FILE_PREVIEW_DIR_BLACKLIST.has(name)) return true;
  if (!includeHidden && name.startsWith('.')) return true;
  return false;
}

function shouldSkipFileEntry(name: string, includeHidden: boolean): boolean {
  if (!includeHidden && name.startsWith('.')) return true;
  return false;
}

function getTreeOptions(opts: unknown): FilePreviewTreeOptions {
  return isRecord(opts) ? opts as FilePreviewTreeOptions : {};
}

function getBinaryOptions(opts: unknown): FileReadBinaryOptions {
  return isRecord(opts) ? opts as FileReadBinaryOptions : {};
}

export function createFilesApi(): CompleteHostServiceRegistry['files'] {
  return {
    stagePaths: async (payload) => {
      const body = isRecord(payload) ? payload as StagePathsPayload : {};
      const filePaths = Array.isArray(body.filePaths)
        ? body.filePaths.filter((value): value is string => typeof value === 'string')
        : [];
      const fsP = await import('node:fs/promises');
      await fsP.mkdir(getOutboundDir(), { recursive: true });

      const results = [];
      for (const filePath of filePaths) {
        const id = crypto.randomUUID();
        const fileName = basename(filePath);
        // Confine to this account's roots before touching the filesystem;
        // otherwise stagePaths becomes an arbitrary-read primitive (copy any
        // server file into outbound, then read it back).
        const { realPath: source } = await resolveSandboxedPath(filePath, 'read');
        const sourceStat = await fsP.stat(source);
        if (sourceStat.isDirectory()) {
          results.push({
            id,
            fileName,
            mimeType: DIRECTORY_MIME_TYPE,
            fileSize: 0,
            stagedPath: source,
            preview: null,
          });
          continue;
        }

        const ext = extname(source);
        const stagedPath = join(getOutboundDir(), `${id}${ext}`);
        await fsP.copyFile(source, stagedPath);
        const s = await fsP.stat(stagedPath);
        const mimeType = getMimeType(ext);
        const preview = mimeType.startsWith('image/')
          ? await generateImagePreview(stagedPath, mimeType)
          : null;
        results.push({ id, fileName, mimeType, fileSize: s.size, stagedPath, preview });
      }
      return results;
    },
    stageBuffer: async (payload) => {
      const body = isRecord(payload) ? payload as StageBufferPayload : {};
      if (typeof body.base64 !== 'string' || typeof body.fileName !== 'string') {
        throw new Error('Invalid staged buffer payload');
      }
      const fsP = await import('node:fs/promises');
      await fsP.mkdir(getOutboundDir(), { recursive: true });

      const id = crypto.randomUUID();
      const payloadMimeType = typeof body.mimeType === 'string' ? body.mimeType : '';
      const ext = extname(body.fileName) || mimeToExt(payloadMimeType);
      const stagedPath = join(getOutboundDir(), `${id}${ext}`);
      const buffer = Buffer.from(body.base64, 'base64');
      await fsP.writeFile(stagedPath, buffer);

      const mimeType = payloadMimeType || getMimeType(ext);
      const preview = mimeType.startsWith('image/')
        ? await generateImagePreview(stagedPath, mimeType)
        : null;
      return {
        id,
        fileName: body.fileName,
        mimeType,
        fileSize: buffer.length,
        stagedPath,
        preview,
      };
    },
    readText: async (payload) => {
      try {
        const { realPath: real, readOnly } = await resolveSandboxedPath(requirePath(payload), 'read');
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(real);
        if (!stat.isFile()) return { ok: false, error: 'notFound' };
        if (stat.size > FILE_PREVIEW_MAX_TEXT_BYTES) return { ok: false, error: 'tooLarge', size: stat.size };
        const buf = await fsP.readFile(real);
        if (looksLikeBinary(buf)) return { ok: false, error: 'binary', size: stat.size };
        return {
          ok: true,
          content: buf.toString('utf8'),
          mimeType: getMimeType(extname(real)),
          size: stat.size,
          readOnly,
        };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'outsideSandbox') return { ok: false, error: 'outsideSandbox' };
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    readBinary: async (payload) => {
      try {
        const body = isRecord(payload) ? payload as PathPayload : {};
        const opts = getBinaryOptions(body.opts);
        const { realPath: real, readOnly } = await resolveSandboxedPath(requirePath(payload), 'read');
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(real);
        if (!stat.isFile()) return { ok: false, error: 'notFound' };
        const maxBytes = typeof opts.maxBytes === 'number' ? opts.maxBytes : undefined;
        const cap = Math.max(1, Math.min(maxBytes ?? FILE_PREVIEW_MAX_BINARY_BYTES, FILE_PREVIEW_MAX_BINARY_BYTES));
        if (stat.size > cap) return { ok: false, error: 'tooLarge', size: stat.size };
        const buf = await fsP.readFile(real);
        const view = new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength);
        return {
          ok: true,
          data: view,
          mimeType: getMimeType(extname(real)),
          size: stat.size,
          readOnly,
        };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'outsideSandbox') return { ok: false, error: 'outsideSandbox' };
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    writeText: async (payload) => {
      try {
        const body = isRecord(payload) ? payload as PathPayload : {};
        if (typeof body.content !== 'string') return { ok: false, error: 'invalidContent' };
        if (Buffer.byteLength(body.content, 'utf8') > FILE_PREVIEW_MAX_TEXT_BYTES) {
          return { ok: false, error: 'tooLarge' };
        }
        const { realPath: real } = await resolveSandboxedPath(requirePath(payload), 'write');
        const fsP = await import('node:fs/promises');
        let stat;
        try {
          stat = await fsP.stat(real);
        } catch {
          return { ok: false, error: 'notFound' };
        }
        if (!stat.isFile()) return { ok: false, error: 'notFound' };
        await fsP.writeFile(real, body.content, 'utf8');
        return { ok: true };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'outsideSandbox') return { ok: false, error: 'outsideSandbox' };
        if (message === 'readOnlyRoot') return { ok: false, error: 'readOnlyRoot' };
        return { ok: false, error: message };
      }
    },
    stat: async (payload) => {
      try {
        const { realPath: real, readOnly } = await resolveSandboxedPath(requirePath(payload), 'read');
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(real);
        return {
          ok: true,
          size: stat.size,
          mtime: stat.mtimeMs,
          isFile: stat.isFile(),
          isDir: stat.isDirectory(),
          readOnly,
        };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'outsideSandbox') return { ok: false, error: 'outsideSandbox' };
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    listDir: async (payload) => {
      try {
        const { realPath: real } = await resolveSandboxedPath(requirePath(payload), 'read');
        const fsP = await import('node:fs/promises');
        const dirents = await fsP.readdir(real, { withFileTypes: true });
        const entries = await Promise.all(dirents.map(async (entry) => {
          const abs = join(real, entry.name);
          let size = 0;
          try {
            if (entry.isFile()) size = (await fsP.stat(abs)).size;
          } catch {
            // non-fatal
          }
          return {
            name: entry.name,
            path: abs,
            isDir: entry.isDirectory(),
            size,
          };
        }));
        return { ok: true, entries };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'outsideSandbox') return { ok: false, error: 'outsideSandbox' };
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    listTree: async (payload) => {
      try {
        const body = isRecord(payload) ? payload as PathPayload : {};
        const opts = getTreeOptions(body.opts);
        const { realPath: real } = await resolveSandboxedPath(requirePath(payload), 'read');
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(real);
        if (!stat.isDirectory()) return { ok: false, error: 'notDirectory' };
        const maxDepth = Math.max(1, Math.min(opts.maxDepth ?? FILE_PREVIEW_TREE_MAX_DEPTH, 12));
        const maxNodes = Math.max(1, Math.min(opts.maxNodes ?? FILE_PREVIEW_TREE_MAX_NODES, 50000));
        const includeHidden = !!opts.includeHidden;

        let nodeCount = 0;
        let truncated = false;

        const walk = async (absDir: string, depth: number): Promise<FilePreviewTreeNode[] | undefined> => {
          if (depth > maxDepth || truncated) return undefined;
          let dirents;
          try {
            dirents = await fsP.readdir(absDir, { withFileTypes: true });
          } catch {
            return [];
          }
          const children: FilePreviewTreeNode[] = [];
          for (const entry of dirents) {
            if (truncated) break;
            const isDir = entry.isDirectory();
            const isFile = entry.isFile();
            if (!isDir && !isFile) continue;
            if (isDir && shouldSkipDirEntry(entry.name, includeHidden)) continue;
            if (isFile && shouldSkipFileEntry(entry.name, includeHidden)) continue;
            if (nodeCount >= maxNodes) {
              truncated = true;
              break;
            }
            nodeCount += 1;
            const abs = join(absDir, entry.name);
            const node: FilePreviewTreeNode = {
              name: entry.name,
              relPath: relative(real, abs).split(sep).join('/'),
              absPath: abs,
              isDir,
            };
            if (isFile) {
              try {
                const fstat = await fsP.stat(abs);
                node.size = fstat.size;
                node.mtime = fstat.mtimeMs;
              } catch {
                // non-fatal
              }
            } else {
              try {
                node.mtime = (await fsP.stat(abs)).mtimeMs;
              } catch {
                // non-fatal
              }
              node.children = await walk(abs, depth + 1) ?? [];
            }
            children.push(node);
          }
          children.sort((a, b) => {
            if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
            return a.name.localeCompare(b.name);
          });
          return children;
        };

        const root: FilePreviewTreeNode = {
          name: basename(real) || real,
          relPath: '',
          absPath: real,
          isDir: true,
          mtime: stat.mtimeMs,
          children: (await walk(real, 1)) ?? [],
        };
        return { ok: true, root, truncated };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message === 'outsideSandbox') return { ok: false, error: 'outsideSandbox' };
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    // ── ClawX-style workspace / attachment API ──────────────────
    resolveWorkspaceContext: async (payload) => {
      const body = isRecord(payload) ? payload as { workspaceRoot?: string; executionCwd?: string } : {};
      if (typeof body.workspaceRoot !== 'string' || !body.workspaceRoot.trim()
        || typeof body.executionCwd !== 'string' || !body.executionCwd.trim()) {
        return { ok: false, error: 'outsideSandbox' };
      }
      const fsP = await import('node:fs/promises');
      try {
        const [workspaceRoot, executionCwd] = await Promise.all([
          fsP.realpath(expandPath(body.workspaceRoot)),
          fsP.realpath(expandPath(body.executionCwd)),
        ]);
        const [rootStat, cwdStat] = await Promise.all([
          fsP.stat(workspaceRoot),
          fsP.stat(executionCwd),
        ]);
        if (!rootStat.isDirectory() || !cwdStat.isDirectory()) {
          return { ok: false, error: 'notDirectory' };
        }
        if (!isInside(executionCwd, workspaceRoot)) {
          return { ok: false, error: 'outsideSandbox' };
        }
        return { ok: true, workspaceRoot, executionCwd };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message.includes('ENOENT')) return { ok: false, error: 'notDirectory' };
        return { ok: false, error: 'operationFailed' };
      }
    },
    statWorkspaceFile: async (payload) => {
      const body = isRecord(payload) ? payload as { workspaceRoot?: string; relativePath?: string } : {};
      if (typeof body.workspaceRoot !== 'string' || typeof body.relativePath !== 'string') {
        return { ok: false, error: 'invalidReference' };
      }
      const fsP = await import('node:fs/promises');
      try {
        const workspaceRoot = await fsP.realpath(expandPath(body.workspaceRoot));
        const target = resolve(workspaceRoot, body.relativePath);
        const canonicalTarget = await fsP.realpath(target);
        if (!isInside(canonicalTarget, workspaceRoot)) {
          return { ok: false, error: 'outsideSandbox' };
        }
        const stat = await fsP.stat(canonicalTarget);
        return {
          ok: true,
          size: stat.size,
          mtime: stat.mtimeMs,
          isFile: stat.isFile(),
          isDir: stat.isDirectory(),
          readOnly: true,
        };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    readWorkspaceText: async (payload) => {
      const body = isRecord(payload) ? payload as { workspaceRoot?: string; relativePath?: string } : {};
      if (typeof body.workspaceRoot !== 'string' || typeof body.relativePath !== 'string') {
        return { ok: false, error: 'invalidReference' };
      }
      const fsP = await import('node:fs/promises');
      try {
        const workspaceRoot = await fsP.realpath(expandPath(body.workspaceRoot));
        const target = resolve(workspaceRoot, body.relativePath);
        const canonicalTarget = await fsP.realpath(target);
        if (!isInside(canonicalTarget, workspaceRoot)) {
          return { ok: false, error: 'outsideSandbox' };
        }
        const stat = await fsP.stat(canonicalTarget);
        if (!stat.isFile()) return { ok: false, error: 'notFound' };
        if (stat.size > FILE_PREVIEW_MAX_TEXT_BYTES) {
          return { ok: false, error: 'tooLarge', size: stat.size };
        }
        const buf = await fsP.readFile(canonicalTarget);
        // Naive binary detection
        const sample = buf.subarray(0, Math.min(buf.length, 8192));
        let looksBinary = false;
        for (let i = 0; i < sample.length; i++) {
          if (sample[i] === 0) { looksBinary = true; break; }
        }
        if (looksBinary) return { ok: false, error: 'binary', size: buf.length };
        return {
          ok: true,
          content: buf.toString('utf8'),
          mimeType: mimeTypeForPath(canonicalTarget),
          size: buf.length,
          readOnly: true,
        };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    readWorkspaceBinary: async (payload) => {
      const body = isRecord(payload) ? payload as { workspaceRoot?: string; relativePath?: string; maxBytes?: number } : {};
      if (typeof body.workspaceRoot !== 'string' || typeof body.relativePath !== 'string') {
        return { ok: false, error: 'invalidReference' };
      }
      const cap = Math.max(1, Math.min(body.maxBytes ?? FILE_PREVIEW_MAX_BINARY_BYTES, FILE_PREVIEW_MAX_BINARY_BYTES));
      const fsP = await import('node:fs/promises');
      try {
        const workspaceRoot = await fsP.realpath(expandPath(body.workspaceRoot));
        const target = resolve(workspaceRoot, body.relativePath);
        const canonicalTarget = await fsP.realpath(target);
        if (!isInside(canonicalTarget, workspaceRoot)) {
          return { ok: false, error: 'outsideSandbox' };
        }
        const stat = await fsP.stat(canonicalTarget);
        if (!stat.isFile()) return { ok: false, error: 'notFound' };
        if (stat.size > cap) return { ok: false, error: 'tooLarge', size: stat.size };
        const handle = await fsP.open(canonicalTarget, 'r');
        try {
          const buf = Buffer.alloc(cap);
          const { bytesRead } = await handle.read(buf, 0, cap, 0);
          return {
            ok: true,
            data: new Uint8Array(buf.buffer, buf.byteOffset, bytesRead),
            mimeType: mimeTypeForPath(canonicalTarget),
            size: bytesRead,
            readOnly: true,
          };
        } finally {
          await handle.close().catch(() => undefined);
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message.includes('ENOENT')) return { ok: false, error: 'notFound' };
        return { ok: false, error: message };
      }
    },
    resolveAttachment: async (payload) => {
      const body = isRecord(payload) ? payload as ResolveAttachmentPayload : {} as ResolveAttachmentPayload;
      const ref = body?.ref;
      const fallbackName = decodeBasename(typeof ref?.uri === 'string' ? ref.uri : 'attachment');
      const displayName = safeDisplayName(body?.name, fallbackName);
      try {
        if (!ref || typeof ref.sessionKey !== 'string' || typeof ref.generation !== 'number') {
          throw { attachmentCode: 'invalidReference' };
        }
        validateReferenceSyntax(ref.uri);

        const fsP = await import('node:fs/promises');
        const outgoing = parseOutgoingUrl(ref.uri);
        if (outgoing) {
          // Resolve to local file via outgoing media
          const resolved = await resolveOutgoingMediaAttachment(ref.uri);
          if (!resolved) throw { attachmentCode: 'invalidReference' };
          return {
            ok: true as const,
            identity: opaqueIdentity(resolved.path),
            displayName: safeDisplayName(body?.name, basename(resolved.path)),
            mimeType: resolved.mimeType,
            size: resolved.size,
            target: {
              kind: 'local' as const,
              scope: 'openclaw-media' as const,
              ref: ref as AttachmentFileRef,
              canonicalPath: resolved.path,
            },
          };
        }
        if (/^https?:/i.test(ref.uri)) {
          const url = normalizeRemote(ref.uri);
          return {
            ok: true as const,
            identity: opaqueIdentity(url),
            displayName: safeDisplayName(body?.name, fallbackName),
            mimeType: body.mimeType || mimeTypeForPath(new URL(ref.uri).pathname),
            size: typeof body.size === 'number' && Number.isFinite(body.size) && body.size >= 0 ? body.size : 0,
            target: { kind: 'remote' as const, ref: ref as AttachmentFileRef, url },
          };
        }
        // Local file path
        const executionCwd = process.cwd();
        const localPath = localPathFromUri(ref.uri, executionCwd);
        const canonical = await fsP.realpath(localPath);
        const stat = await fsP.stat(canonical);
        if (!stat.isFile()) throw { attachmentCode: 'notFile' };
        return {
          ok: true as const,
          identity: opaqueIdentity(canonical),
          displayName: safeDisplayName(body?.name, basename(canonical)),
          mimeType: body.mimeType || mimeTypeForPath(canonical),
          size: stat.size,
          target: {
            kind: 'local' as const,
            scope: 'workspace' as const,
            ref: ref as AttachmentFileRef,
            canonicalPath: canonical,
          },
        };
      } catch (err) {
        return { ok: false as const, displayName, error: attachmentFailure(err) };
      }
    },
    readAttachmentText: async (payload) => {
      const ref = (isRecord(payload) ? (payload as { ref?: AttachmentFileRef }).ref : null) ?? payload as unknown as AttachmentFileRef;
      if (!ref) return { ok: false as const, error: 'invalidReference' as const };
      try {
        const directResult = await resolveAttachmentImpl(ref);
        if (!directResult.ok) {
          return { ok: false as const, error: directResult.error };
        }
        if (directResult.target.kind !== 'local') {
          return { ok: false as const, error: 'invalidReference' };
        }
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(directResult.target.canonicalPath!);
        if (stat.size > FILE_PREVIEW_MAX_TEXT_BYTES) {
          return { ok: false as const, error: 'tooLarge' as const, size: stat.size };
        }
        const buf = await fsP.readFile(directResult.target.canonicalPath!);
        return {
          ok: true as const,
          content: buf.toString('utf8'),
          mimeType: directResult.mimeType,
          size: buf.length,
          readOnly: true as const,
        };
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        if (message.includes('tooLarge')) {
          return { ok: false as const, error: 'tooLarge' as const };
        }
        return { ok: false as const, error: 'operationFailed' as const };
      }
    },
    readAttachmentBinary: async (payload) => {
      try {
        const ref = (isRecord(payload) ? (payload as { ref?: AttachmentFileRef }).ref : null);
        if (!ref) return { ok: false as const, error: 'invalidReference' as const };
        const directResult = await resolveAttachmentImpl(ref);
        if (!directResult.ok) {
          return { ok: false as const, error: 'operationFailed' as const };
        }
        if (directResult.target.kind !== 'local') {
          return { ok: false as const, error: 'invalidReference' as const };
        }
        const cap = Math.max(1, Math.min(
          (isRecord(payload) ? (payload as { maxBytes?: number }).maxBytes : undefined) ?? FILE_PREVIEW_MAX_BINARY_BYTES,
          FILE_PREVIEW_MAX_BINARY_BYTES,
        ));
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(directResult.target.canonicalPath!);
        if (stat.size > cap) return { ok: false as const, error: 'tooLarge' as const, size: stat.size };
        const buf = await fsP.readFile(directResult.target.canonicalPath!);
        return {
          ok: true as const,
          data: new Uint8Array(buf.buffer, buf.byteOffset, Math.min(buf.length, cap)),
          mimeType: directResult.mimeType,
          size: Math.min(buf.length, cap),
          readOnly: true as const,
        };
      } catch {
        return { ok: false as const, error: 'operationFailed' as const };
      }
    },
    openAttachment: async (payload): Promise<OpenAttachmentResult> => {
      try {
        const ref = isRecord(payload) ? payload as unknown as AttachmentSourceRef : payload as unknown as AttachmentSourceRef;
        const result = await resolveAttachmentImpl(ref);
        if (!result.ok) return { ok: false, error: result.error };
        if (result.target.kind === 'remote') {
          // Remote: use shell.openExternal
          await electronShell.openExternal(result.target.url);
          return { ok: true };
        }
        // Local: use shell.openPath (system default app)
        const openError = await electronShell.openPath(result.target.canonicalPath!);
        if (openError) return { ok: false, error: 'operationFailed' };
        return { ok: true };
      } catch (err) {
        return { ok: false, error: attachmentFailure(err) };
      }
    },
  };
}

// Local helper for the inline implementation of readAttachment{Text,Binary} that
// delegates to the same logic as resolveAttachment but without recursive `this`.
async function resolveAttachmentImpl(ref: AttachmentSourceRef): Promise<ResolveAttachmentResult> {
  const fallbackName = decodeBasename(typeof ref?.uri === 'string' ? ref.uri : 'attachment');
  try {
    if (!ref || typeof ref.sessionKey !== 'string' || typeof ref.generation !== 'number') {
      throw { attachmentCode: 'invalidReference' };
    }
    validateReferenceSyntax(ref.uri);
    const fsP = await import('node:fs/promises');
    const outgoing = parseOutgoingUrl(ref.uri);
    if (outgoing) {
      const resolved = await resolveOutgoingMediaAttachment(ref.uri);
      if (!resolved) throw { attachmentCode: 'invalidReference' };
      return {
        ok: true,
        identity: opaqueIdentity(resolved.path),
        displayName: safeDisplayName(basename(resolved.path), basename(resolved.path)),
        mimeType: resolved.mimeType,
        size: resolved.size,
        target: { kind: 'local', scope: 'openclaw-media', ref, canonicalPath: resolved.path },
      };
    }
    if (/^https?:/i.test(ref.uri)) {
      const url = normalizeRemote(ref.uri);
      return {
        ok: true,
        identity: opaqueIdentity(url),
        displayName: fallbackName,
        mimeType: mimeTypeForPath(new URL(ref.uri).pathname),
        size: 0,
        target: { kind: 'remote', ref, url },
      };
    }
    const executionCwd = process.cwd();
    const localPath = localPathFromUri(ref.uri, executionCwd);
    const canonical = await fsP.realpath(localPath);
    const stat = await fsP.stat(canonical);
    if (!stat.isFile()) throw { attachmentCode: 'notFile' };
    return {
      ok: true,
      identity: opaqueIdentity(canonical),
      displayName: safeDisplayName(basename(canonical), basename(canonical)),
      mimeType: mimeTypeForPath(canonical),
      size: stat.size,
      target: { kind: 'local', scope: 'workspace', ref, canonicalPath: canonical },
    };
  } catch (err) {
    return { ok: false, displayName: fallbackName, error: attachmentFailure(err) };
  }
}
