/**
 * Legacy IPC channel router.
 *
 * The desktop app registers ~50 request/response channels via
 * ipcMain.handle() in host-core/main/ipc-handlers.ts and updater.ts. Under the
 * electron shim those registrations land in `ipcMainHandlers`, so the web
 * host reuses the exact desktop logic. The allowlist mirrors the desktop
 * preload (studio-frontend/electron/preload/index.ts) — anything outside it
 * is rejected, matching the desktop preload behaviour.
 */
import { ipcMainHandlers } from './shims/electron';

// Keep in sync with the validChannels list in the desktop preload
// (studio-frontend/electron/preload/index.ts).
const ALLOWED_LEGACY_CHANNELS = new Set([
  // Gateway
  'gateway:status',
  // OpenClaw
  'openclaw:status',
  'openclaw:getSkillsDir',
  'openclaw:getCliCommand',
  // Shell
  'shell:openExternal',
  'shell:showItemInFolder',
  'shell:openPath',
  // Dialog
  'dialog:open',
  'dialog:message',
  // App
  'app:version',
  'app:name',
  'app:platform',
  'app:request',
  // Window controls
  'window:minimize',
  'window:maximize',
  'window:close',
  'window:isMaximized',
  'window:syncTrafficLightPosition',
  // Settings
  'settings:get',
  'settings:set',
  'settings:setMany',
  'settings:getAll',
  'settings:reset',
  'usage:recentTokenHistory',
  // Update
  'update:status',
  'update:version',
  'update:check',
  'update:download',
  'update:install',
  'update:setChannel',
  'update:setAutoDownload',
  'update:cancelAutoInstall',
  // Env (dead channels on desktop too — no main-process handler exists)
  'env:getConfig',
  'env:setApiKey',
  'env:deleteApiKey',
  // Provider
  'provider:list',
  'provider:get',
  'provider:save',
  'provider:delete',
  'provider:setApiKey',
  'provider:updateWithKey',
  'provider:deleteApiKey',
  'provider:hasApiKey',
  'provider:getApiKey',
  'provider:setDefault',
  'provider:getDefault',
  'provider:validateKey',
  // File preview (sandboxed read/write/list/tree)
  'file:readText',
  'file:readBinary',
  'file:writeText',
  'file:stat',
  'file:listDir',
  'file:listTree',
]);

export async function invokeLegacyChannel(channel: string, args: unknown[]): Promise<unknown> {
  if (!ALLOWED_LEGACY_CHANNELS.has(channel)) {
    throw new Error(`Invalid IPC channel: ${channel}`);
  }
  const handler = ipcMainHandlers.get(channel);
  if (!handler) {
    throw new Error(`No handler registered for IPC channel: ${channel}`);
  }
  // Desktop handlers receive (event, ...args); no handler in this codebase
  // dereferences the event object, so a null stand-in is safe.
  return await handler(null, ...args);
}
