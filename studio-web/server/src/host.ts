/**
 * Web host assembly.
 *
 * Reuses the battle-tested service layer from server/host-core/ (running on
 * the electron→Node shims) and exposes:
 *   - dispatch(): typed host-invoke dispatcher (same contract as IPC host:invoke)
 *   - onEvent(): subscription point the WS layer uses to push host events
 *     (everything the desktop app delivered via webContents.send)
 *
 * Desktop-only modules (window/updates/uv) are inert stubs per the plan; the
 * frontend UI degrades gracefully for those.
 */
import { BrowserWindow } from 'electron';
import { GatewayManager } from '../host-core/gateway/manager';
import { ClawHubService } from '../host-core/gateway/clawhub';
import { GatewayRpcBackpressure } from '../host-core/gateway/rpc-backpressure';
import { HostApiRegistry, createHostInvokeDispatcher } from '../host-core/main/ipc/host-invoke';
import type {
  CompleteHostServiceRegistry,
  HostResponse,
  HostServiceRegistry,
} from '../host-core/main/ipc/host-contract';
import { createAppApi } from '../host-core/services/app-api';
import { createOpenClawApi } from '../host-core/services/openclaw-api';
import { createShellApi } from '../host-core/services/shell-api';
import { createDialogApi } from '../host-core/services/dialog-api';
import { createSettingsApi } from '../host-core/services/settings-api';
import { createGatewayApi } from '../host-core/services/gateway-api';
import { createLogsApi } from '../host-core/services/logs-api';
import { createDiagnosticsApi } from '../host-core/services/diagnostics-api';
import { createChannelsApi } from '../host-core/services/channels-api';
import { createAgentsApi } from '../host-core/services/agents-api';
import { createProvidersApi } from '../host-core/services/providers-api';
import { createFilesApi } from '../host-core/services/files-api';
import { createMediaApi } from '../host-core/services/media-api';
import { createSessionsApi } from '../host-core/services/sessions-api';
import { createChatApi } from '../host-core/services/chat-api';
import { createCronApi } from '../host-core/services/cron-api';
import { createSkillsApi } from '../host-core/services/skills-api';
import { createUsageApi } from '../host-core/services/usage-api';
import { createSyncApi } from '../host-core/services/sync-api';
import { createAuthApi } from '../host-core/services/backend-auth-api';
import { syncAllProviderAuthToRuntime } from '../host-core/services/providers/provider-runtime-sync';
import { registerIpcHandlers } from '../host-core/main/ipc-handlers';
import { appUpdater, registerUpdateHandlers } from '../host-core/main/updater';
import { deviceOAuthManager } from '../host-core/utils/device-oauth';
import { browserOAuthManager } from '../host-core/utils/browser-oauth';
import { getSetting } from '../host-core/utils/store';
import { logger } from '../host-core/utils/logger';

export type HostEventSink = (channel: string, payload: unknown) => void;

export type WebHost = {
  dispatch: (request: unknown) => Promise<HostResponse>;
  services: HostServiceRegistry;
  authApi: ReturnType<typeof createAuthApi>;
  gatewayManager: GatewayManager;
  onEvent: (sink: HostEventSink) => () => void;
  broadcast: (channel: string, payload: unknown) => void;
  startGateway: () => Promise<void>;
};

function createUpdatesStub(version: string): CompleteHostServiceRegistry['updates'] {
  const idle = { status: 'idle' as const };
  const disabled = { success: false, error: 'Updates are managed by the server deployment' };
  return {
    status: () => idle,
    version: () => version,
    check: () => ({ ...disabled, status: idle }),
    download: () => disabled,
    install: () => disabled,
    setChannel: () => ({ success: true }),
    setAutoDownload: () => ({ success: true }),
    cancelAutoInstall: () => ({ success: true }),
  };
}

function createWindowStub(): CompleteHostServiceRegistry['window'] {
  return {
    syncTrafficLightPosition: () => undefined,
    minimize: () => undefined,
    maximize: () => undefined,
    close: () => undefined,
    isMaximized: () => false,
  };
}

function createUvStub(): CompleteHostServiceRegistry['uv'] {
  return {
    installAll: () => ({ success: false, error: 'uv provisioning is handled by the server image' }),
  };
}

export function createWebHost(appVersion: string): WebHost {
  const sinks = new Set<HostEventSink>();
  const broadcast = (channel: string, payload: unknown): void => {
    for (const sink of [...sinks]) {
      try {
        sink(channel, payload);
      } catch (error) {
        logger.warn(`[web-host] event sink failed on ${channel}:`, error);
      }
    }
  };

  // Services written against webContents.send get a headless window whose
  // sends fan out to all connected WebSocket clients.
  const mainWindow = new BrowserWindow();
  mainWindow.webContents.send = (channel: string, ...args: unknown[]) => {
    broadcast(channel, args[0]);
  };

  const gatewayManager = new GatewayManager();
  const clawHubService = new ClawHubService();
  const gatewayRpcBackpressure = new GatewayRpcBackpressure();

  // Bridge gateway + host-side events (mirrors the desktop main process,
  // studio-frontend/electron/main/index.ts).
  gatewayManager.on('status', (status: unknown) => broadcast('gateway:status-changed', status));
  gatewayManager.on('error', (error: Error) => broadcast('gateway:error', { message: error.message }));
  gatewayManager.on('notification', (n: unknown) => broadcast('gateway:notification', n));
  gatewayManager.on('gateway:health', (data: unknown) => broadcast('gateway:health-changed', data));
  gatewayManager.on('gateway:presence', (data: unknown) => broadcast('gateway:presence-changed', data));
  gatewayManager.on('chat:message', (data: unknown) => broadcast('gateway:chat-message', data));
  gatewayManager.on('chat:runtime-event', (data: unknown) => broadcast('chat:runtime-event', data));
  gatewayManager.on('channel:status', (data: unknown) => broadcast('gateway:channel-status', data));
  gatewayManager.on('exit', (code: unknown) => broadcast('gateway:exit', { code }));

  deviceOAuthManager.on('oauth:code', (payload) => broadcast('oauth:code', payload));
  deviceOAuthManager.on('oauth:success', (payload) => broadcast('oauth:success', { ...payload, success: true }));
  deviceOAuthManager.on('oauth:error', (error) => broadcast('oauth:error', error));
  browserOAuthManager.on('oauth:code', (payload) => broadcast('oauth:code', payload));
  browserOAuthManager.on('oauth:success', (payload) => broadcast('oauth:success', { ...payload, success: true }));
  browserOAuthManager.on('oauth:error', (error) => broadcast('oauth:error', error));
  // WhatsApp login events are forwarded by registerIpcHandlers →
  // registerWhatsAppHandlers via mainWindow.webContents.send (broadcast).

  const authApi = createAuthApi();

  const services: HostServiceRegistry = {
    app: createAppApi(),
    openclaw: createOpenClawApi(),
    shell: createShellApi(),
    dialog: createDialogApi(),
    window: createWindowStub(),
    updates: createUpdatesStub(appVersion),
    uv: createUvStub(),
    settings: createSettingsApi(gatewayManager),
    gateway: createGatewayApi(gatewayManager, gatewayRpcBackpressure),
    logs: createLogsApi(),
    diagnostics: createDiagnosticsApi({ gatewayManager }),
    channels: createChannelsApi({ gatewayManager, mainWindow }),
    agents: createAgentsApi({ gatewayManager }),
    providers: createProvidersApi({ gatewayManager, mainWindow }),
    files: createFilesApi(),
    media: createMediaApi(),
    sessions: createSessionsApi({ gatewayManager }),
    chat: createChatApi({ gatewayManager }),
    cron: createCronApi({ gatewayManager }),
    skills: createSkillsApi({ clawHubService, gatewayManager }),
    usage: createUsageApi(),
    sync: createSyncApi(),
    auth: authApi,
  };

  const registry = new HostApiRegistry();
  registry.registerCoreServices(services);
  const dispatch = createHostInvokeDispatcher(registry);

  // Legacy request/response channels (settings:*, provider:*, file:*,
  // app:request, ...) reuse the desktop registration wholesale: under the
  // electron shim every ipcMain.handle() call lands in ipcMainHandlers, which
  // legacy-router.ts consults. The typed registry passed here is a throwaway;
  // WS `invoke` messages always go through `dispatch` above (with the web
  // stubs for window/updates/uv).
  registerIpcHandlers(gatewayManager, clawHubService, mainWindow, new HostApiRegistry());
  registerUpdateHandlers(appUpdater, mainWindow);

  const startGateway = async (): Promise<void> => {
    const autoStart = await getSetting('gatewayAutoStart').catch(() => true);
    if (!autoStart) {
      logger.info('[web-host] gateway auto-start disabled by settings');
      return;
    }
    try {
      await syncAllProviderAuthToRuntime();
    } catch (error) {
      logger.warn('[web-host] provider auth sync before gateway start failed:', error);
    }
    try {
      await gatewayManager.start();
      logger.info('[web-host] gateway auto-start succeeded');
    } catch (error) {
      logger.error('[web-host] gateway auto-start failed:', error);
      broadcast('gateway:error', { message: String(error) });
    }
  };

  return {
    dispatch,
    services,
    authApi,
    gatewayManager,
    onEvent: (sink) => {
      sinks.add(sink);
      return () => sinks.delete(sink);
    },
    broadcast,
    startGateway,
  };
}
