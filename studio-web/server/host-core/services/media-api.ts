import { dialog, nativeImage } from 'electron';
import { homedir } from 'node:os';
import { extname, join } from 'node:path';
import { getOpenClawConfigDir } from '../utils/paths';
import { logger } from '../utils/logger';
import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import {
  CLAWX_OPENAI_IMAGE_DEFAULT_MODEL,
  CLAWX_OPENAI_IMAGE_PROVIDER_KEY,
} from '../utils/openclaw-image-relay-constants';
import {
  applyOpenAiImageRelaySettings,
  getImageGenerationSettingsSnapshot,
  listImageGenerationProvidersFromRuntime,
  runImageGenerationTest,
  setImageGenerationConfig,
  type ImageGenerationModelConfig,
} from '../utils/openclaw-image-generation';
import { isRecord } from './payload-utils';
import { resolveHostReadablePath } from './files-api';

// Image format MIME type mapping (browser-supported formats)
const IMAGE_MIME_MAP: Record<string, string> = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.bmp': 'image/bmp',
  '.ico': 'image/x-icon',
  // Formats with limited browser support
  '.tif': 'image/tiff',
  '.tiff': 'image/tiff',
  '.avif': 'image/avif',
};

/** Infer image MIME type from file extension */
function inferImageMimeType(filePath: string): string {
  const ext = extname(filePath).toLowerCase();
  return IMAGE_MIME_MAP[ext] ?? 'image/png'; // fallback to PNG for unknown formats
}

type ThumbnailEntry = {
  filePath?: unknown;
  gatewayUrl?: unknown;
  mimeType?: unknown;
};

type SaveImagePayload = {
  base64?: unknown;
  mimeType?: unknown;
  filePath?: unknown;
  defaultFileName?: unknown;
};

type ImageGenerationSettingsPayload = {
  timeoutMs?: unknown;
  openAiRelayEnabled?: unknown;
  openAiRelayBaseUrl?: unknown;
  openAiRelayModel?: unknown;
  openAiRelayApiKey?: unknown;
};

async function generateImagePreview(filePath: string, mimeType: string): Promise<string | null> {
  try {
    const { readFile } = await import('node:fs/promises');

    // Infer mimeType from file extension if not reliable
    let effectiveMimeType = mimeType;
    if (!effectiveMimeType || effectiveMimeType === 'application/octet-stream') {
      effectiveMimeType = inferImageMimeType(filePath);
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

async function resolveOutgoingMediaUrl(
  gatewayUrl: string,
): Promise<{ path: string; mimeType: string } | null> {
  try {
    // Format 1: /api/chat/media/outgoing/<sessionKey>/<attachmentId>/full
    const outgoingMatch = gatewayUrl.match(/\/api\/chat\/media\/outgoing\/[^/]+\/([^/]+)\//);
    if (outgoingMatch) {
      const attachmentId = decodeURIComponent(outgoingMatch[1]);
      if (!/^[A-Za-z0-9._-]+$/.test(attachmentId)) return null;
      const recordPath = join(getOpenClawConfigDir(), 'media', 'outgoing', 'records', `${attachmentId}.json`);
      const fsP = await import('node:fs/promises');
      let raw: string;
      try {
        raw = await fsP.readFile(recordPath, 'utf8');
      } catch {
        logger.warn(`[media] outgoing record not found: ${recordPath}`);
        return null;
      }
      const record = JSON.parse(raw) as {
        original?: { path?: string; contentType?: string };
      };
      const original = record?.original;
      if (!original?.path) {
        logger.warn(`[media] outgoing record missing original.path: ${recordPath}`);
        return null;
      }
      return {
        path: original.path,
        mimeType: typeof original.contentType === 'string' && original.contentType
          ? original.contentType
          : 'application/octet-stream',
      };
    }

    // Format 2: /__openclaw__/assistant-media?source=<path>&mediaTicket=<jwt>
    // Gateway sends non-image files (e.g. .html) via this URL format because
    // the renderer cannot open local file paths directly.  The `source` query
    // parameter carries the original absolute file path.
    if (gatewayUrl.includes('/__openclaw__/assistant-media')) {
      // gatewayUrl may be relative (no scheme); prepend a dummy base for URL parsing
      const url = new URL(gatewayUrl, 'http://localhost');
      const source = url.searchParams.get('source');
      if (!source) return null;
      const fsP = await import('node:fs/promises');
      const stat = await fsP.stat(source);
      if (!stat.isFile()) return null;
      // Derive mimeType from file extension
      const ext = source.split('.').pop()?.toLowerCase() ?? '';
      const mimeMap: Record<string, string> = {
        // Documents
        html: 'text/html', htm: 'text/html',
        pdf: 'application/pdf',
        doc: 'application/msword',
        docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        xls: 'application/vnd.ms-excel',
        xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        md: 'text/markdown',
        txt: 'text/plain',
        csv: 'text/csv',
        json: 'application/json',
        // Images (browser-supported)
        png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg',
        gif: 'image/gif', webp: 'image/webp', svg: 'image/svg+xml',
        bmp: 'image/bmp', ico: 'image/x-icon',
        // Images (limited browser support)
        tif: 'image/tiff', tiff: 'image/tiff', avif: 'image/avif',
      };
      return {
        path: source,
        mimeType: mimeMap[ext] ?? 'application/octet-stream',
      };
    }

    return null;
  } catch {
    return null;
  }
}

function normalizeThumbnailEntries(payload: unknown): ThumbnailEntry[] {
  const value = isRecord(payload) ? payload.paths : payload;
  return Array.isArray(value) ? value as ThumbnailEntry[] : [];
}

export function createMediaApi(): CompleteHostServiceRegistry['media'] {
  return {
    thumbnails: async (payload) => {
      const entries = normalizeThumbnailEntries(payload);
      const fsP = await import('node:fs/promises');
      const results: Record<string, { preview: string | null; fileSize: number }> = {};
      for (const entry of entries) {
        const mimeType = typeof entry.mimeType === 'string' ? entry.mimeType : 'application/octet-stream';
        if (typeof entry.filePath === 'string' && entry.filePath) {
          try {
            const source = await resolveHostReadablePath(entry.filePath);
            const stat = await fsP.stat(source);
            const preview = mimeType.startsWith('image/')
              ? await generateImagePreview(source, mimeType)
              : null;
            results[entry.filePath] = { preview, fileSize: stat.size };
          } catch {
            results[entry.filePath] = { preview: null, fileSize: 0 };
          }
          continue;
        }

        if (typeof entry.gatewayUrl === 'string' && entry.gatewayUrl) {
          const resolved = await resolveOutgoingMediaUrl(entry.gatewayUrl);
          if (!resolved) {
            logger.warn(`[media] Failed to resolve gatewayUrl: ${entry.gatewayUrl}`);
            results[entry.gatewayUrl] = { preview: null, fileSize: 0 };
            continue;
          }
          try {
            const stat = await fsP.stat(resolved.path);
            const preview = resolved.mimeType.startsWith('image/')
              ? await generateImagePreview(resolved.path, resolved.mimeType)
              : null;
            results[entry.gatewayUrl] = { preview, fileSize: stat.size };
          } catch {
            results[entry.gatewayUrl] = { preview: null, fileSize: 0 };
          }
        }
      }
      return results;
    },
    resolveGatewayUrl: async (payload) => {
      const body = isRecord(payload) ? payload as { gatewayUrl?: string } : {};
      const gatewayUrl = typeof body.gatewayUrl === 'string' ? body.gatewayUrl : '';
      if (!gatewayUrl) {
        return { filePath: null, fileSize: 0, mimeType: 'application/octet-stream' };
      }
      const resolved = await resolveOutgoingMediaUrl(gatewayUrl);
      if (!resolved) {
        return { filePath: null, fileSize: 0, mimeType: 'application/octet-stream' };
      }
      try {
        const fsP = await import('node:fs/promises');
        const stat = await fsP.stat(resolved.path);
        return {
          filePath: resolved.path,
          fileSize: stat.size,
          mimeType: resolved.mimeType,
        };
      } catch {
        return { filePath: null, fileSize: 0, mimeType: resolved.mimeType };
      }
    },
    saveImage: async (payload) => {
      const body = isRecord(payload) ? payload as SaveImagePayload : {};
      const defaultFileName = typeof body.defaultFileName === 'string' && body.defaultFileName
        ? body.defaultFileName
        : 'image.png';
      const mimeType = typeof body.mimeType === 'string' ? body.mimeType : undefined;
      const ext = defaultFileName.includes('.')
        ? defaultFileName.split('.').pop()!
        : (mimeType?.split('/')[1] || 'png');
      const result = await dialog.showSaveDialog({
        defaultPath: join(homedir(), 'Downloads', defaultFileName),
        filters: [
          { name: 'Images', extensions: [ext, 'png', 'jpg', 'jpeg', 'webp', 'gif'] },
          { name: 'All Files', extensions: ['*'] },
        ],
      });
      if (result.canceled || !result.filePath) return { success: false };

      const fsP = await import('node:fs/promises');
      if (typeof body.filePath === 'string' && body.filePath) {
        try {
          const source = await resolveHostReadablePath(body.filePath);
          await fsP.access(source);
          await fsP.copyFile(source, result.filePath);
        } catch {
          return { success: false, error: 'Source file not found' };
        }
      } else if (typeof body.base64 === 'string' && body.base64) {
        await fsP.writeFile(result.filePath, Buffer.from(body.base64, 'base64'));
      } else {
        return { success: false, error: 'No image data provided' };
      }
      return { success: true, savedPath: result.filePath };
    },
    imageGenerationSettings: async () => ({
      success: true,
      ...(await getImageGenerationSettingsSnapshot()),
    }),
    saveImageGenerationSettings: async (payload) => {
      const body = isRecord(payload) ? payload as ImageGenerationSettingsPayload : {};
      const current = await getImageGenerationSettingsSnapshot();
      const normalizeRelayModel = (value: unknown): string => {
        const raw = typeof value === 'string' && value.trim()
          ? value.trim()
          : (current.openAiRelay.model || CLAWX_OPENAI_IMAGE_DEFAULT_MODEL);
        const slash = raw.indexOf('/');
        return (slash > 0 ? raw.slice(slash + 1) : raw).trim() || CLAWX_OPENAI_IMAGE_DEFAULT_MODEL;
      };
      const relayModel = normalizeRelayModel(body.openAiRelayModel);
      let nextPrimary = current.config.primary;
      if (body.openAiRelayEnabled === true) {
        nextPrimary = `${CLAWX_OPENAI_IMAGE_PROVIDER_KEY}/${relayModel}`;
      } else if (body.openAiRelayEnabled === false) {
        nextPrimary = null;
      }
      const next: ImageGenerationModelConfig = {
        primary: nextPrimary,
        fallbacks: [],
        timeoutMs: body.timeoutMs !== undefined
          ? (typeof body.timeoutMs === 'number' && body.timeoutMs > 0 ? Math.floor(body.timeoutMs) : null)
          : current.config.timeoutMs,
      };

      if (typeof body.openAiRelayEnabled === 'boolean') {
        await applyOpenAiImageRelaySettings({
          enabled: body.openAiRelayEnabled,
          baseUrl: typeof body.openAiRelayBaseUrl === 'string' ? body.openAiRelayBaseUrl : null,
          apiKey: typeof body.openAiRelayApiKey === 'string' ? body.openAiRelayApiKey : undefined,
          model: relayModel,
        });
      }

      const config = await setImageGenerationConfig(next);
      return {
        success: true,
        ...(await getImageGenerationSettingsSnapshot()),
        config,
      };
    },
    imageGenerationProviders: async () => ({
      success: true,
      providers: await listImageGenerationProvidersFromRuntime(),
    }),
    testImageGeneration: async (payload) => runImageGenerationTest(isRecord(payload) ? payload : {}),
  };
}
