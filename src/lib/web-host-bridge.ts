/**
 * Web Host Bridge
 *
 * Replaces the Electron preload script (contextBridge) for the browser build.
 * Injects `window.electron` and `window['grandpoem-studio']` before the app
 * renders, so the rest of the renderer code runs unchanged:
 *   - hostInvoke  → WebSocket request/response matched by request id
 *   - ipcRenderer → legacy channels forwarded over the same WebSocket;
 *                   events are pushed by the server and fanned out locally
 *
 * Must be imported FIRST in src/main.tsx.
 */
import type { HostRequest, HostResponse } from '@shared/host-api/types';

type PendingEntry = {
  resolve: (value: unknown) => void;
  reject: (reason: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

type ServerMessage =
  | { kind: 'hello'; platform: string; isDev?: boolean }
  | { kind: 'response'; response: HostResponse }
  | { kind: 'legacy-response'; id: string; ok: boolean; data?: unknown; error?: string }
  | { kind: 'event'; channel: string; payload?: unknown };

const REQUEST_TIMEOUT_MS = 120_000;
const RECONNECT_BASE_DELAY_MS = 500;
const RECONNECT_MAX_DELAY_MS = 10_000;

const pending = new Map<string, PendingEntry>();
const eventListeners = new Map<string, Set<(...args: unknown[]) => void>>();
const sendQueue: string[] = [];

let ws: WebSocket | null = null;
let reconnectAttempt = 0;
let manualClose = false;
let hostPlatform: string = 'linux';

declare global {
  interface Window {
    __grandpoemBridge?: {
      disconnect: () => void;
      reconnect: () => void;
    };
  }
}

function wsUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const base = `${proto}//${window.location.host}/ws`;
  // Support iframe embedding: pass token via URL query param
  const studioToken = localStorage.getItem('studio-jwt');
  if (studioToken) {
    return `${base}?token=${encodeURIComponent(studioToken)}`;
  }
  return base;
}

function flushQueue(): void {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;
  while (sendQueue.length > 0) {
    ws.send(sendQueue.shift()!);
  }
}

/** Reject every in-flight request so callers never hang on a dead session. */
function rejectAllPending(reason: Error): void {
  for (const entry of pending.values()) {
    clearTimeout(entry.timer);
    entry.reject(reason);
  }
  pending.clear();
}

/**
 * Close the current socket and stop the auto-reconnect loop. Used on logout:
 * the server has cleared the session cookie, so any reconnect would be
 * rejected with 4401 — and requests queued for the previous account must
 * never reach the next one.
 */
function disconnect(): void {
  manualClose = true;
  const socket = ws;
  ws = null;
  sendQueue.length = 0;
  rejectAllPending(new Error('Session closed — reconnect required'));
  if (socket) {
    try {
      socket.close();
    } catch {
      // already closing/closed
    }
  }
  dispatchEvent('bridge:disconnected', {});
}

/**
 * Force a fresh connection bound to the current session. Used on login: the
 * existing socket may still be bound to the previous account's worker scope
 * (the server fixes the scope at handshake time), so it must be dropped and
 * re-established with the new session cookie.
 */
function reconnect(): void {
  manualClose = true;
  const socket = ws;
  ws = null;
  if (socket) {
    try {
      socket.close();
    } catch {
      // already closing/closed
    }
  }
  reconnectAttempt = 0;
  manualClose = false;
  connect();
}

/**
 * The server encodes binary payloads (e.g. file:readBinary data) as
 * { __u8: base64 }; restore them to Uint8Array like Electron's structured
 * clone would deliver.
 */
function wireReviver(_key: string, value: unknown): unknown {
  if (value && typeof value === 'object' && typeof (value as { __u8?: unknown }).__u8 === 'string') {
    const bin = atob((value as { __u8: string }).__u8);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i += 1) bytes[i] = bin.charCodeAt(i);
    return bytes;
  }
  return value;
}

function dispatchEvent(channel: string, payload: unknown): void {
  const listeners = eventListeners.get(channel);
  if (!listeners) return;
  for (const listener of [...listeners]) {
    try {
      listener(payload);
    } catch (error) {
      console.error(`[web-host-bridge] event listener error on ${channel}:`, error);
    }
  }
}

function handleMessage(raw: string): void {
  let message: ServerMessage;
  try {
    message = JSON.parse(raw, wireReviver) as ServerMessage;
  } catch {
    console.warn('[web-host-bridge] non-JSON message ignored');
    return;
  }

  switch (message.kind) {
    case 'hello': {
      hostPlatform = message.platform || 'linux';
      dispatchEvent('bridge:connected', { platform: hostPlatform });
      break;
    }
    case 'response': {
      const id = message.response?.id;
      if (!id) return;
      const entry = pending.get(id);
      if (!entry) return;
      pending.delete(id);
      clearTimeout(entry.timer);
      entry.resolve(message.response);
      break;
    }
    case 'legacy-response': {
      const entry = pending.get(message.id);
      if (!entry) return;
      pending.delete(message.id);
      clearTimeout(entry.timer);
      if (message.ok) {
        entry.resolve(message.data);
      } else {
        entry.reject(new Error(message.error || 'Legacy IPC request failed'));
      }
      break;
    }
    case 'event': {
      dispatchEvent(message.channel, message.payload);
      break;
    }
  }
}

function connect(): void {
  const socket = new WebSocket(wsUrl());
  ws = socket;

  socket.onopen = () => {
    if (ws !== socket) return;
    reconnectAttempt = 0;
    flushQueue();
  };

  socket.onmessage = (event) => {
    // Ignore late frames from a superseded socket (user switched, reconnect
    // already replaced it) so stale-account events never leak in.
    if (ws !== socket) return;
    if (typeof event.data === 'string') handleMessage(event.data);
  };

  socket.onclose = (event) => {
    if (ws !== socket) return;
    ws = null;
    dispatchEvent('bridge:disconnected', {});
    // Never auto-reconnect when the socket was dropped deliberately (logout /
    // login rebind) or the server rejected the handshake as unauthorized —
    // the login flow triggers an explicit reconnect() instead.
    if (manualClose || event.code === 4401) return;
    const baseDelay = Math.min(
      RECONNECT_BASE_DELAY_MS * 2 ** reconnectAttempt,
      RECONNECT_MAX_DELAY_MS,
    );
    // Jittered backoff: avoids a reconnect stampede when many clients lose
    // the connection at once (e.g. access-layer restart).
    const delay = Math.round(baseDelay * (0.7 + Math.random() * 0.6));
    reconnectAttempt += 1;
    setTimeout(() => {
      if (!ws && !manualClose) connect();
    }, delay);
  };

  socket.onerror = () => {
    socket.close();
  };
}

function sendRaw(payload: string): void {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(payload);
  } else {
    sendQueue.push(payload);
    // While logged out the reconnect loop is disabled; the login flow
    // reconnects explicitly and flushes the queue.
    if (!ws && !manualClose) connect();
  }
}

function trackRequest<T>(id: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error('Host request timed out'));
    }, REQUEST_TIMEOUT_MS);
    pending.set(id, { resolve: resolve as (value: unknown) => void, reject, timer });
  });
}

/**
 * Auth calls go over plain HTTP instead of the WebSocket: before login there
 * is no session cookie yet, so the /ws upgrade itself is rejected (4401) and
 * an invoke over it could never succeed. The server sets the HttpOnly cookie
 * on /auth/login, after which the socket can (re)connect.
 *
 * For iframe embedding scenarios, supports Bearer token authentication
 * via localStorage 'studio-jwt'.
 */
async function authInvoke<T>(request: HostRequest): Promise<HostResponse<T>> {
  const action = request.action;
  const init: RequestInit = {};
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };

  // Check for iframe embedding mode: use Bearer token from localStorage
  const studioToken = localStorage.getItem('studio-jwt');
  if (studioToken) {
    headers['Authorization'] = `Bearer ${studioToken}`;
  } else {
    init.credentials = 'same-origin'; // Standalone access, use cookie
  }

  let url: string;
  if (action === 'login') {
    url = '/auth/login';
    init.method = 'POST';
    init.headers = headers;
    init.body = JSON.stringify(request.payload ?? {});
  } else if (action === 'logout') {
    url = '/auth/logout';
    init.method = 'POST';
    init.headers = headers;
    // Fastify rejects an empty body when Content-Type: application/json is
    // set (400), which would silently skip the server-side logout (worker
    // stop + token clear). Send a valid empty JSON object instead.
    init.body = '{}';
  } else if (action === 'listDevices') {
    url = '/auth/devices';
    init.headers = headers;
  } else if (action === 'revokeDevice') {
    const deviceId = (request.payload as { deviceId?: number })?.deviceId;
    url = `/auth/devices/${deviceId}`;
    init.method = 'DELETE';
    // Fastify rejects an empty body when Content-Type: application/json is
    // set (400 FST_ERR_CTP_EMPTY_JSON_BODY) — send a valid empty JSON object.
    init.body = '{}';
    init.headers = headers;
  } else {
    // me / getState
    url = '/auth/me';
    init.headers = headers;
  }

  try {
    const res = await fetch(url, init);
    const data = (await res.json().catch(() => ({}))) as T;
    if (action === 'login' && res.ok) {
      // Session cookie is set now. Force a fresh socket bound to the new
      // account — the previous one may still be bound to the old user's
      // worker scope (the server fixes the scope at handshake time).
      reconnect();
    } else if (action === 'logout' && res.ok) {
      // Cookie cleared server-side: drop the socket so the unauthorized
      // reconnect loop stops and the next login rebinds explicitly.
      disconnect();
    }
    if (!res.ok && res.status !== 401) {
      const message = (data as { error?: string })?.error || `Auth request failed (${res.status})`;
      return { id: request.id, ok: false, error: { message } } as HostResponse<T>;
    }
    // 401 bodies already carry the unauthenticated shape the app expects
    // (login: { success:false, error }, me: { isAuthenticated:false }).
    return { id: request.id, ok: true, data } as HostResponse<T>;
  } catch (error) {
    return {
      id: request.id,
      ok: false,
      error: { message: error instanceof Error ? error.message : 'Auth request failed' },
    } as HostResponse<T>;
  }
}

function hostInvoke<T = unknown>(request: HostRequest): Promise<HostResponse<T>> {
  if (request.module === 'auth') {
    return authInvoke<T>(request);
  }
  const promise = trackRequest<HostResponse<T>>(request.id);
  sendRaw(JSON.stringify({ kind: 'invoke', request }));
  return promise;
}

function legacyInvoke(channel: string, ...args: unknown[]): Promise<unknown> {
  const id = crypto.randomUUID();
  const promise = trackRequest<unknown>(id);
  sendRaw(JSON.stringify({ kind: 'legacy', id, channel, args }));
  return promise;
}

const electronAPI = {
  ipcRenderer: {
    invoke: (channel: string, ...args: unknown[]) => legacyInvoke(channel, ...args),

    on: (channel: string, callback: (...args: unknown[]) => void) => {
      let listeners = eventListeners.get(channel);
      if (!listeners) {
        listeners = new Set();
        eventListeners.set(channel, listeners);
      }
      listeners.add(callback);
      return () => {
        listeners.delete(callback);
        if (listeners.size === 0) eventListeners.delete(channel);
      };
    },

    once: (channel: string, callback: (...args: unknown[]) => void) => {
      const wrapped = (...args: unknown[]) => {
        unsubscribe();
        callback(...args);
      };
      let listeners = eventListeners.get(channel);
      if (!listeners) {
        listeners = new Set();
        eventListeners.set(channel, listeners);
      }
      listeners.add(wrapped);
      const unsubscribe = () => {
        listeners.delete(wrapped);
        if (listeners.size === 0) eventListeners.delete(channel);
      };
    },

    off: (channel: string, callback?: (...args: unknown[]) => void) => {
      if (!callback) {
        eventListeners.delete(channel);
        return;
      }
      const listeners = eventListeners.get(channel);
      if (!listeners) return;
      listeners.delete(callback);
      if (listeners.size === 0) eventListeners.delete(channel);
    },
  },

  openExternal: (url: string): Promise<void> => {
    window.open(url, '_blank', 'noopener,noreferrer');
    return Promise.resolve();
  },

  // Browsers never expose real filesystem paths; callers fall back to upload flows.
  getPathForFile: (_file: File): string => '',

  get platform() {
    return hostPlatform as NodeJS.Platform;
  },

  isDev: import.meta.env.DEV,
};

window.electron = electronAPI;
window['grandpoem-studio'] = { hostInvoke };
// Shared auth store (both frontends) reaches through this global; the desktop
// build has no bridge and the optional chain degrades to a no-op.
window.__grandpoemBridge = { disconnect, reconnect };

// Connect eagerly so gateway/app events start flowing before the first invoke.
connect();

export { hostInvoke };
