import { shell } from 'electron';
import { homedir } from 'node:os';
import { join, sep } from 'node:path';
import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';

function expandShellPath(input: string): string {
  if (input === '~') return homedir();
  if (input.startsWith(`~${sep}`) || input.startsWith('~/') || input.startsWith('~\\')) {
    return join(homedir(), input.slice(2));
  }
  return input;
}

function requirePath(path: unknown): string {
  if (typeof path !== 'string' || !path.trim()) {
    throw new Error('path is required');
  }
  return path;
}

function requireUrl(url: unknown): string {
  if (typeof url !== 'string' || !url.trim()) {
    throw new Error('url is required');
  }
  // host-api 的 openExternal 必须与旧 IPC 通道 shell:openExternal 同等防护：仅放行
  // http/https。渲染层渲染的是不可信的模型输出，若放任 file:/javascript:/vbscript:/
  // ms-msdt: 等协议，一次注入即可触发本机执行或文件读取。
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    throw new Error('url is invalid');
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    throw new Error(`Blocked openExternal for disallowed protocol: ${parsed.protocol}`);
  }
  return url;
}

export function createShellApi(): CompleteHostServiceRegistry['shell'] {
  return {
    openExternal: async (payload) => {
      await shell.openExternal(requireUrl(payload.url));
    },
    showItemInFolder: (payload) => {
      shell.showItemInFolder(expandShellPath(requirePath(payload.path)));
    },
    openPath: (payload) => shell.openPath(expandShellPath(requirePath(payload.path))),
  };
}
