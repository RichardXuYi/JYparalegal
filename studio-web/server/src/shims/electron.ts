/**
 * Electron API shim for the web host server.
 *
 * The service/gateway/util modules under server/host-core/ were written for the
 * desktop app. Instead of forking them we alias the 'electron' module to this
 * file (see server/tsconfig.json "paths"), providing Node-compatible
 * implementations: paths map to DATA_DIR, UI-only APIs (dialog, Menu,
 * BrowserWindow, session) become safe no-ops, and utilityProcess maps to
 * child_process.fork.
 */
import { fork, type ChildProcess } from 'node:child_process';
import { EventEmitter } from 'node:events';
import { readFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { DATA_DIR, PROJECT_ROOT } from '../env';

// ---------------------------------------------------------------------------
// app
// ---------------------------------------------------------------------------

function readPackageVersion(): string {
  try {
    const pkg = JSON.parse(readFileSync(join(PROJECT_ROOT, 'package.json'), 'utf8')) as { version?: string };
    return pkg.version ?? '0.0.0';
  } catch {
    return '0.0.0';
  }
}

const APP_NAME = 'GrandPoem Paralegal Studio Web';
const appVersion = readPackageVersion();

const PATHS: Record<string, string> = {
  userData: DATA_DIR,
  appData: dirname(DATA_DIR),
  logs: join(DATA_DIR, 'logs'),
  temp: tmpdir(),
  home: homedir(),
  exe: process.execPath,
  desktop: join(homedir(), 'Desktop'),
  documents: join(homedir(), 'Documents'),
  downloads: join(homedir(), 'Downloads'),
};

class AppShim extends EventEmitter {
  readonly isPackaged = false;
  name = APP_NAME;

  getName(): string { return this.name; }
  setName(name: string): void { this.name = name; }
  getVersion(): string { return appVersion; }
  getLocale(): string { return process.env.LANG?.split('.')[0]?.replace('_', '-') || 'en-US'; }
  getPreferredSystemLanguages(): string[] { return [this.getLocale()]; }
  getPath(name: string): string { return PATHS[name] ?? DATA_DIR; }
  getAppPath(): string { return PROJECT_ROOT; }
  isReady(): boolean { return true; }
  requestSingleInstanceLock(): boolean { return true; }
  setAppUserModelId(_id: string): void {}
  setLoginItemSettings(_settings: unknown): void {}
  getLoginItemSettings(): { openAtLogin: boolean } { return { openAtLogin: false }; }
  whenReady(): Promise<void> { return Promise.resolve(); }
  quit(): void {}
  exit(_code?: number): void {}
  relaunch(): void {}
  focus(): void {}
}

export const app = new AppShim();

// ---------------------------------------------------------------------------
// shell / dialog / clipboard-ish UI surfaces (no-op on a headless server)
// ---------------------------------------------------------------------------

export const shell = {
  openExternal: async (_url: string): Promise<void> => {
    // Browsers open external links client-side (web-host-bridge maps
    // openExternal to window.open); nothing sensible to do on the server.
  },
  showItemInFolder: (_path: string): void => {},
  openPath: async (_path: string): Promise<string> => 'Not supported in web host',
  trashItem: async (_path: string): Promise<void> => {},
};

export type OpenDialogOptions = {
  title?: string;
  defaultPath?: string;
  buttonLabel?: string;
  filters?: Array<{ name: string; extensions: string[] }>;
  properties?: string[];
  message?: string;
  securityScopedBookmarks?: boolean;
};

export type MessageBoxOptions = {
  message: string;
  type?: string;
  buttons?: string[];
  defaultId?: number;
  cancelId?: number;
  detail?: string;
  checkboxLabel?: string;
  checkboxChecked?: boolean;
  noLink?: boolean;
  title?: string;
};

export type SaveDialogOptions = {
  title?: string;
  defaultPath?: string;
  filters?: Array<{ name: string; extensions: string[] }>;
};

export const dialog = {
  // Native pickers do not exist in a browser session; callers receive a
  // canceled result and the frontend falls back to upload/download flows.
  showOpenDialog: async (_options?: OpenDialogOptions) => ({ canceled: true, filePaths: [] as string[] }),
  showSaveDialog: async (_options?: SaveDialogOptions) => ({ canceled: true, filePath: undefined as string | undefined }),
  showMessageBox: async (_options?: MessageBoxOptions) => ({ response: 0, checkboxChecked: false }),
  showErrorBox: (_title: string, _content: string): void => {},
};

// ---------------------------------------------------------------------------
// session / Menu / screen
// ---------------------------------------------------------------------------

export const session = {
  defaultSession: {
    setProxy: async (_config: unknown): Promise<void> => {},
    resolveProxy: async (_url: string): Promise<string> => 'DIRECT',
    closeAllConnections: async (): Promise<void> => {},
  },
};

export const Menu = {
  buildFromTemplate: (_template: unknown[]): unknown => ({}),
  setApplicationMenu: (_menu: unknown): void => {},
  getApplicationMenu: (): unknown => null,
};

export const screen = {
  getPrimaryDisplay: () => ({ workAreaSize: { width: 1920, height: 1080 }, workArea: { x: 0, y: 0, width: 1920, height: 1080 } }),
  getCursorScreenPoint: () => ({ x: 0, y: 0 }),
};

// ---------------------------------------------------------------------------
// nativeImage
// ---------------------------------------------------------------------------

type NativeImageShim = {
  isEmpty: () => boolean;
  getSize: () => { width: number; height: number };
  resize: (options?: unknown) => NativeImageShim;
  toPNG: () => Buffer;
  toJPEG: (quality?: number) => Buffer;
  toDataURL: () => string;
};

function emptyImage(): NativeImageShim {
  const img: NativeImageShim = {
    isEmpty: () => true,
    getSize: () => ({ width: 0, height: 0 }),
    resize: () => img,
    toPNG: () => Buffer.alloc(0),
    toJPEG: () => Buffer.alloc(0),
    toDataURL: () => '',
  };
  return img;
}

export const nativeImage = {
  createFromPath: (_path: string): NativeImageShim => emptyImage(),
  createFromBuffer: (_buffer: Buffer): NativeImageShim => emptyImage(),
  createThumbnailFromPath: async (_path: string, _size: unknown): Promise<NativeImageShim> => emptyImage(),
};

// ---------------------------------------------------------------------------
// BrowserWindow (headless stand-in; host.ts overrides webContents.send to
// forward events onto the user's WebSocket connections)
// ---------------------------------------------------------------------------

class WebContentsShim extends EventEmitter {
  send(_channel: string, ..._args: unknown[]): void {}
  openDevTools(): void {}
  setWindowOpenHandler(_handler: unknown): void {}
}

export class BrowserWindow extends EventEmitter {
  webContents = new WebContentsShim();
  private destroyed = false;
  private maximized = false;

  constructor(_options?: unknown) {
    super();
  }

  static getFocusedWindow(): BrowserWindow | null { return null; }
  static getAllWindows(): BrowserWindow[] { return []; }
  static fromWebContents(_wc: unknown): BrowserWindow | null { return null; }

  loadURL(_url: string): Promise<void> { return Promise.resolve(); }
  loadFile(_path: string, _options?: unknown): Promise<void> { return Promise.resolve(); }
  show(): void {}
  hide(): void {}
  focus(): void {}
  close(): void { this.destroyed = true; this.emit('closed'); }
  destroy(): void { this.destroyed = true; }
  isDestroyed(): boolean { return this.destroyed; }
  minimize(): void {}
  maximize(): void { this.maximized = true; }
  unmaximize(): void { this.maximized = false; }
  isMaximized(): boolean { return this.maximized; }
  setTitle(_title: string): void {}
  setMenu(_menu: unknown): void {}
  setWindowButtonPosition(_position: { x: number; y: number } | null): void {}
}

// proxy-aware fetch falls back to global fetch when process.versions.electron
// is absent, but keep net available for completeness.
export const net = {
  fetch: (input: string | URL | Request, init?: RequestInit): Promise<Response> => fetch(input, init),
};

// ---------------------------------------------------------------------------
// ipcMain — records handlers instead of wiring Electron IPC. The server's
// legacy router (server/src/legacy-router.ts) looks handlers up in this map,
// so host-core/main/ipc-handlers.ts registers its channels unchanged.
// ---------------------------------------------------------------------------

// Handlers are written with concrete arg types (channel-specific); `any`
// keeps them assignable, mirroring Electron's own ipcMain.handle typing.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type IpcMainHandler = (event: any, ...args: any[]) => unknown;

export const ipcMainHandlers = new Map<string, IpcMainHandler>();

export const ipcMain = {
  handle: (channel: string, listener: IpcMainHandler): void => {
    ipcMainHandlers.set(channel, listener);
  },
  removeHandler: (channel: string): void => {
    ipcMainHandlers.delete(channel);
  },
  on: (_channel: string, _listener: (...args: unknown[]) => void): void => {},
  removeAllListeners: (_channel?: string): void => {},
};

// ---------------------------------------------------------------------------
// utilityProcess → child_process.fork
// ---------------------------------------------------------------------------

export type UtilityProcessForkOptions = {
  cwd?: string;
  stdio?: string;
  env?: NodeJS.ProcessEnv;
  serviceName?: string;
  execArgv?: string[];
};

export const utilityProcess = {
  fork(modulePath: string, args: string[] = [], options: UtilityProcessForkOptions = {}): ChildProcess {
    return fork(modulePath, args, {
      cwd: options.cwd,
      env: options.env ?? process.env,
      execArgv: options.execArgv ?? [],
      silent: true, // expose stdout/stderr streams like utilityProcess stdio:'pipe'
    });
  },
};

export default {
  app,
  shell,
  dialog,
  session,
  Menu,
  screen,
  nativeImage,
  BrowserWindow,
  ipcMain,
  utilityProcess,
};
