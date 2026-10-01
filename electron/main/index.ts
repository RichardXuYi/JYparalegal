/**
 * Electron Main Process Entry
 * Manages window creation, system tray, and IPC handlers
 */
import { app, BrowserWindow, nativeImage, nativeTheme, session, shell } from 'electron';
import { join } from 'path';
import { GatewayManager } from '../gateway/manager';
import { registerIpcHandlers } from './ipc-handlers';
import { HostApiRegistry } from './ipc/host-invoke';
import { createTray } from './tray';
import { createMenu } from './menu';
import { registerZoomShortcuts } from './zoom-shortcuts';
import { getWindowState, trackWindowState } from './window';

import { appUpdater, registerUpdateHandlers } from './updater';
import { logger } from '../utils/logger';
import { markStartup, logStartupSummary } from '../utils/startup-timeline';
import { warmupNetworkOptimization } from '../utils/uv-env';
import { initTelemetry } from '../utils/telemetry';

import { ClawHubService } from '../gateway/clawhub';
import { extensionRegistry } from '../extensions/registry';
import { loadExtensionsFromManifest } from '../extensions/loader';
import { registerAllBuiltinExtensions } from '../extensions/builtin';
import { loadExternalMainExtensions } from '../extensions/_ext-bridge.generated';
import {
  ensureGrandPoemStudioContext,
  ensureGrandPoemStudioDefaultIdentity,
  repairGrandPoemStudioOnlyBootstrapFiles,
} from '../utils/openclaw-workspace';
import { autoInstallCliIfNeeded, generateCompletionCache, installCompletionToProfile } from '../utils/openclaw-cli';
import { isQuitting, setQuitting } from './app-state';
import { getMacTrafficLightPosition, syncMacTrafficLightPosition } from './traffic-light-layout';
import { getSetting } from '../utils/store';
import { applyProxySettings } from './proxy';
import { syncLaunchAtStartupSettingFromStore } from './launch-at-startup';
import {
  clearPendingSecondInstanceFocus,
  consumeMainWindowReady,
  createMainWindowFocusState,
  requestSecondInstanceFocus,
} from './main-window-focus';
import {
  createQuitLifecycleState,
  markQuitCleanupCompleted,
  requestQuitLifecycleAction,
} from './quit-lifecycle';
import { createSignalQuitHandler } from './signal-quit';
import { acquireProcessInstanceFileLock } from './process-instance-lock';
import { ensureBuiltinSkillsInstalled, ensurePreinstalledSkillsInstalled, trimBundledOpenClawSkillsAndConfigs } from '../utils/skill-config';

import { deviceOAuthManager } from '../utils/device-oauth';
import { browserOAuthManager } from '../utils/browser-oauth';
import { syncAllProviderAuthToRuntime } from '../services/providers/provider-runtime-sync';
import { validateSessionForBoot, peekCachedAuthState } from '../services/backend-auth-api';

const WINDOWS_APP_USER_MODEL_ID = 'app.grandpoem-studio.desktop';
const isE2EMode = process.env.CLAWX_E2E === '1';
const requestedUserDataDir = process.env.CLAWX_USER_DATA_DIR?.trim();
const requestedRemoteDebuggingPort = process.env.CLAWX_REMOTE_DEBUGGING_PORT?.trim();

// Installer-driven pre-warm mode.
//
// Goal: pay the one-time cold-start cost (V8 compile-cache population for the
// ~10k-module OpenClaw tree, plus the OS/antivirus first pass over the install
// directory) during installation instead of on the user's first click.
//
// Contract, because a user may launch the app while pre-warm is still running:
//   - it never takes the Electron single-instance lock or the userData file lock
//   - it never creates a window, tray, menu or IPC surface
//   - it stops the Gateway before exiting
// See docs/startup-performance-plan.md (scheme 5).
const isPrewarmMode = process.env.GRANDPOEM_PREWARM === '1'
  || process.argv.includes('--grandpoem-prewarm');

// Dev only: the bundled OpenClaw binary may be older than whatever last wrote
// the shared ~/.openclaw state dir (e.g. a newer global OpenClaw). The older
// binary then refuses the newer config/sqlite schema and the Gateway exits
// code=1, leaving the UI stuck on "connecting". Isolate the dev Gateway state
// dir so it seeds a fresh, version-compatible one. Respects an explicit
// OPENCLAW_STATE_DIR and never applies to packaged builds.
//
// 2026-09-25: bumped to a 9.6-era dir name. The previous `openclaw-dev` dir holds
// old-format legacy workspace state (workspace/, state/, workspace-attestations/);
// openclaw 9.6's legacy workspace-state migration fsyncs it and gets EPERM on
// Windows, so the gateway exits code=78 and the UI reconnects forever. A fresh dir
// skips that migration and reaches ready in ~10s. The old dir is left on disk
// untouched (unused).
if (!app.isPackaged && !process.env.OPENCLAW_STATE_DIR?.trim()) {
  process.env.OPENCLAW_STATE_DIR = join(app.getPath('home'), '.grandpoem-studio', 'openclaw-dev-96');
}

if (requestedRemoteDebuggingPort) {
  app.commandLine.appendSwitch('remote-debugging-port', requestedRemoteDebuggingPort);
}

if (isE2EMode && requestedUserDataDir) {
  app.setPath('userData', requestedUserDataDir);
}

// Disable GPU hardware acceleration globally for maximum stability across
// all GPU configurations (no GPU, integrated, discrete).
//
// Rationale (following VS Code's philosophy):
// - Page/file loading is async data fetching —zero GPU dependency.
// - The original per-platform GPU branching was added to avoid CPU rendering
//   competing with sync I/O on Windows, but all file I/O is now async
//   (fs/promises), so that concern no longer applies.
// - Software rendering is deterministic across all hardware; GPU compositing
//   behaviour varies between vendors (Intel, AMD, NVIDIA, Apple Silicon) and
//   driver versions, making it the #1 source of rendering bugs in Electron.
//
// Users who want GPU acceleration can pass `--enable-gpu` on the CLI or
// set `"disable-hardware-acceleration": false` in the app config (future).
app.disableHardwareAcceleration();

// On Linux, set CHROME_DESKTOP so Chromium can find the correct .desktop file.
// On Wayland this maps the running window to grandpoem-studio.desktop (鈫?icon + app grouping);
// on X11 it supplements the StartupWMClass matching.
// Must be called before app.whenReady() / before any window is created.
if (process.platform === 'linux') {
  const linuxApp = app as typeof app & { setDesktopName?: (desktopName: string) => void };
  linuxApp.setDesktopName?.('grandpoem-studio.desktop');
}

// Prevent multiple instances of the app from running simultaneously.
// Without this, two instances each spawn their own gateway process on the
// same port, then each treats the other's gateway as "orphaned" and kills
// it —creating an infinite kill/restart loop on Windows.
// The losing process must exit immediately so it never reaches Gateway startup.
//
// Pre-warm deliberately opts out: it must never be able to block (or be blocked
// by) the user's real launch, so it neither reads nor writes the instance locks.
const gotElectronLock = (isE2EMode || isPrewarmMode) ? true : app.requestSingleInstanceLock();
if (!gotElectronLock) {
  console.info('[GrandPoem Studio] Another instance already holds the single-instance lock; exiting duplicate process');
  app.exit(0);
}
let releaseProcessInstanceFileLock: () => void = () => {};
let gotFileLock = true;
if (gotElectronLock && !isE2EMode && !isPrewarmMode) {
  try {
    const fileLock = acquireProcessInstanceFileLock({
      userDataDir: app.getPath('userData'),
      lockName: 'grandpoem-studio',
      force: true, // Electron lock already guarantees exclusivity; force-clean orphan/recycled-PID locks
    });
    gotFileLock = fileLock.acquired;
    releaseProcessInstanceFileLock = fileLock.release;
    if (!fileLock.acquired) {
      const ownerDescriptor = fileLock.ownerPid
        ? `${fileLock.ownerFormat ?? 'legacy'} pid=${fileLock.ownerPid}`
        : fileLock.ownerFormat === 'unknown'
          ? 'unknown lock format/content'
          : 'unknown owner';
      console.info(
        `[GrandPoem Studio] Another instance already holds process lock (${fileLock.lockPath}, ${ownerDescriptor}); exiting duplicate process`,
      );
      app.exit(0);
    }
  } catch (error) {
    console.warn('[GrandPoem Studio] Failed to acquire process instance file lock; continuing with Electron single-instance lock only', error);
  }
}
const gotTheLock = gotElectronLock && gotFileLock;

// Global references
let mainWindow: BrowserWindow | null = null;
let gatewayManager!: GatewayManager;
let clawHubService!: ClawHubService;
const hostApiRegistry = new HostApiRegistry();
const mainWindowFocusState = createMainWindowFocusState();
const quitLifecycleState = createQuitLifecycleState();

function sendMainWindowEvent(channel: string, payload: unknown): void {
  const win = mainWindow;
  if (!win || win.isDestroyed()) return;
  win.webContents.send(channel, payload);
}

// Spawn the Gateway concurrently with the boot session validation by default.
// Set to false to restore the strict "validate the session, THEN spawn" order if
// a stale-scope double-boot is ever observed in the field. See `initialize()`.
const PARALLEL_SESSION_VALIDATION_AT_BOOT = true;

/**
 * Sync provider credentials into the OpenClaw runtime and start the Gateway.
 * Shared by both the parallel and serial auto-start paths so the failure
 * handling stays identical. Resolves once the Gateway reports readiness; the
 * boot session validation runs independently alongside it.
 */
async function startGatewayAuto(): Promise<void> {
  try {
    markStartup('gateway-spawn-requested');
    await syncAllProviderAuthToRuntime();
    logger.debug('Auto-starting Gateway...');
    await gatewayManager.start();
    logger.info('Gateway auto-start succeeded');
  } catch (error) {
    logger.error('Gateway auto-start failed:', error);
    sendMainWindowEvent('gateway:error', String(error));
  }
}

/**
 * Resolve the icons directory path (works in both dev and packaged mode)
 */
function getIconsDir(): string {
  if (app.isPackaged) {
    // Packaged: icons are in extraResources 鈫?process.resourcesPath/resources/icons
    return join(process.resourcesPath, 'resources', 'icons');
  }
  // Development: relative to dist-electron/main/
  return join(__dirname, '../../resources/icons');
}

/**
 * Get the app icon for the current platform
 */
function getAppIcon(): Electron.NativeImage | undefined {
  if (process.platform === 'darwin') return undefined; // macOS uses the app bundle icon

  const iconsDir = getIconsDir();
  const iconPath =
    process.platform === 'win32'
      ? join(iconsDir, 'icon.ico')
      : join(iconsDir, 'icon.png');
  const icon = nativeImage.createFromPath(iconPath);
  return icon.isEmpty() ? undefined : icon;
}

/**
 * Create the main application window
 */
async function createWindow(): Promise<BrowserWindow> {
  const isMac = process.platform === 'darwin';
  const isWindows = process.platform === 'win32';
  const useCustomTitleBar = isWindows;

  // Restore persisted window state (position/size/maximized)
  let windowState: { x?: number; y?: number; width: number; height: number; isMaximized: boolean };
  try {
    windowState = await getWindowState();
  } catch (error) {
    logger.warn('Failed to load window state, using defaults:', error);
    windowState = { width: 1280, height: 800, isMaximized: false };
  }

  const win = new BrowserWindow({
    width: windowState.width,
    height: windowState.height,
    x: windowState.x,
    y: windowState.y,
    minWidth: 960,
    minHeight: 600,
    icon: getAppIcon(),
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webviewTag: true, // Enable <webview> for embedding OpenClaw Control UI
    },
    titleBarStyle: isMac ? 'hiddenInset' : useCustomTitleBar ? 'hidden' : 'default',
    trafficLightPosition: isMac
      ? getMacTrafficLightPosition(false)
      : undefined,
    frame: isMac || !useCustomTitleBar,
    // Show immediately instead of waiting for `ready-to-show`.
    //
    // The main-process path to "window created" is ~200ms, but everything that
    // still has to happen before a first paint (IPC registration, extension
    // init, Gateway prelaunch) used to be invisible to the user. index.html
    // ships a static `.app-init-loading` placeholder (public/bootstrap/
    // loading.css), so an early window shows the branded loading screen rather
    // than a blank frame. The background colour matches that placeholder to
    // avoid a flash between window creation and first paint.
    show: true,
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#0f0f23' : '#f8fafc',
    paintWhenInitiallyHidden: true,
  });

  if (windowState.isMaximized) {
    win.maximize();
  }

  // Persist window state on resize/move/close
  trackWindowState(win);

  registerZoomShortcuts(win);

  // Handle external links —only allow safe protocols to prevent arbitrary
  // command execution via shell.openExternal() (e.g. file://, ms-msdt:, etc.)
  win.webContents.setWindowOpenHandler(({ url }) => {
    try {
      const parsed = new URL(url);
      if (parsed.protocol === 'https:' || parsed.protocol === 'http:') {
        shell.openExternal(url);
      } else {
        logger.warn(`Blocked openExternal for disallowed protocol: ${parsed.protocol}`);
      }
    } catch {
      logger.warn(`Blocked openExternal for malformed URL: ${url}`);
    }
    return { action: 'deny' };
  });

  // Load the app
  if (process.env.VITE_DEV_SERVER_URL) {
    const rendererUrl = new URL(process.env.VITE_DEV_SERVER_URL);
    // Normalize localhost / IPv6 loopback to 127.0.0.1 so the renderer
    // always connects over IPv4.  Node.js 18+ resolves `localhost` to ::1
    // first; if the Vite server is bound to 127.0.0.1 (or the OS blocks
    // IPv6 binding entirely) the loadURL call would fail with ECONNREFUSED.
    if (rendererUrl.hostname === 'localhost' || rendererUrl.hostname === '[::1]') {
      rendererUrl.hostname = '127.0.0.1';
    }
    win.loadURL(rendererUrl.toString());
    if (!isE2EMode) {
      win.webContents.openDevTools();
    }
  } else {
    win.loadFile(join(__dirname, '../../dist/index.html'));
  }

  return win;
}

function focusWindow(win: BrowserWindow): void {
  if (win.isDestroyed()) {
    return;
  }

  if (win.isMinimized()) {
    win.restore();
  }

  win.show();
  win.focus();
}

function focusMainWindow(): void {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }

  clearPendingSecondInstanceFocus(mainWindowFocusState);
  focusWindow(mainWindow);
}

async function createMainWindow(): Promise<BrowserWindow> {
  const win = await createWindow();
  markStartup('window-created');

  win.webContents.on('did-finish-load', () => {
    markStartup('renderer-loaded');
  });

  win.once('ready-to-show', () => {
    if (mainWindow !== win) {
      return;
    }

    markStartup('window-shown');

    if (process.platform === 'darwin') {
      void getSetting('sidebarCollapsed').then((sidebarCollapsed) => {
        syncMacTrafficLightPosition(win, sidebarCollapsed);
      });
    }

    const action = consumeMainWindowReady(mainWindowFocusState);
    if (action === 'focus') {
      focusWindow(win);
      return;
    }

    win.show();
  });

  win.on('close', (event) => {
    if (!isQuitting() && !isE2EMode) {
      event.preventDefault();
      win.hide();
    }
  });

  win.on('closed', () => {
    if (mainWindow === win) {
      mainWindow = null;
    }
  });

  mainWindow = win;
  return win;
}

/**
 * First-run / periodic maintenance that used to run at the same instant as the
 * first frame and the Gateway cold boot. It is idempotent housekeeping, so it is
 * safe to move behind the first paint and a short settle delay: doing so keeps
 * disk I/O (and any antivirus scan it triggers) off the critical path to a
 * usable UI.
 */
function scheduleDeferredStartupTasks(window: BrowserWindow, delayMs = 2500): void {
  const run = (): void => {
    markStartup('deferred-tasks-start');
    void ensureGrandPoemStudioDefaultIdentity().catch((error) => {
      logger.warn('Failed to seed default GrandPoem Studio identity:', error);
    });
    void repairGrandPoemStudioOnlyBootstrapFiles().catch((error) => {
      logger.warn('Failed to repair bootstrap files:', error);
    });
    void ensureBuiltinSkillsInstalled().catch((error) => {
      logger.warn('Failed to install built-in skills:', error);
    });
    void ensurePreinstalledSkillsInstalled().catch((error) => {
      logger.warn('Failed to install preinstalled skills:', error);
    });
    void trimBundledOpenClawSkillsAndConfigs().then(({ removed, removedConfigs, kept }) => {
      if (removed > 0 || removedConfigs > 0) {
        logger.info(
          `Trimmed bundled OpenClaw skills: removed ${removed}, pruned configs ${removedConfigs}, kept ${kept.join(', ')}`,
        );
      }
    });
  };

  const schedule = (): void => {
    const timer = setTimeout(run, delayMs);
    timer.unref?.();
  };

  if (window.isDestroyed()) {
    return;
  }
  if (window.webContents.isLoadingMainFrame()) {
    window.webContents.once('did-finish-load', schedule);
  } else {
    schedule();
  }
}

/**
 * Pre-warm mode (installer driven, `--grandpoem-prewarm` or
 * `GRANDPOEM_PREWARM=1`).
 *
 * Populates the Gateway's V8 compile cache (NODE_COMPILE_CACHE -> userData/
 * openclaw-compile-cache, see gateway/process-launcher.ts) so the user's first
 * real launch does not pay for compiling ~10k loose modules, and lets the OS /
 * antivirus finish its first pass over the install tree.
 *
 * Never creates a window, never takes the single-instance lock, and stops the
 * Gateway before exiting so a concurrent user launch is unaffected.
 */
async function runPrewarm(): Promise<void> {
  const startedAt = Date.now();
  markStartup('prewarm-start');
  logger.info(`[prewarm] starting (pid=${process.pid}, userData=${app.getPath('userData')})`);

  const timeoutMs = 90_000;

  try {
    await applyProxySettings();
    await syncAllProviderAuthToRuntime();
    await gatewayManager.start();
    await Promise.race([
      waitForGatewayReadyOnce(60_000),
      new Promise<void>((resolve) => {
        const timer = setTimeout(resolve, timeoutMs);
        timer.unref?.();
      }),
    ]);
    markStartup('prewarm-gateway-ready');
  } catch (error) {
    logger.warn('[prewarm] gateway warm-up failed (ignored):', error);
  }

  try {
    await gatewayManager.stop();
  } catch (error) {
    logger.warn('[prewarm] failed to stop Gateway:', error);
  }

  logger.info(`[prewarm] done in ${Date.now() - startedAt}ms`);
  markStartup('prewarm-done');

  // Exit without running the normal quit lifecycle: pre-warm owns no window and
  // no locks, and before-quit would block on Gateway cleanup it already did.
  app.exit(0);
}

/**
 * Initialize the application
 */
async function initialize(): Promise<void> {
  // Initialize logger first
  logger.init();

  // First milestone: `uptime` here is the authoritative segment A measurement
  // (real process start -> app.whenReady()). It is the only log line that can
  // see the pre-whenReady cost, because everything before it has no logger.
  // See docs/startup-performance-plan.md.
  markStartup('whenReady');
  logger.info('=== GrandPoem Studio Application Starting ===');
  logger.debug(
    `Runtime: platform=${process.platform}/${process.arch}, electron=${process.versions.electron}, node=${process.versions.node}, packaged=${app.isPackaged}, pid=${process.pid}, ppid=${process.ppid}`
  );

  // Installer-driven pre-warm: compile/cache the OpenClaw module graph and exit.
  // Never creates a window and never touches the instance locks.
  if (isPrewarmMode) {
    await runPrewarm();
    return;
  }

  if (!isE2EMode) {
    // Warm up network optimization (non-blocking)
    void warmupNetworkOptimization();
  } else {
    logger.info('Running in E2E mode: startup side effects minimized');
  }

  // Set application menu
  await createMenu();
  markStartup('menu-ready');

  // Create the main window before any remaining side effect.
  //
  // Previously initTelemetry() / applyProxySettings() /
  // syncLaunchAtStartupSettingFromStore() were awaited before the window
  // existed, which delayed the first visible pixel by the sum of their
  // latencies — including a synchronous REG.exe spawn on a first run
  // (machineIdSync() in utils/telemetry.ts). The window now comes first; the
  // renderer ships its own loading placeholder (index.html `.app-init-loading`).
  const window = await createMainWindow();

  if (!isE2EMode) {
    // Proxy settings must be applied before any network request. The window has
    // only loaded local file:// content at this point, and the Gateway has not
    // been spawned yet, so awaiting here is still early enough.
    await applyProxySettings();

    // These no longer gate the window.
    void initTelemetry();
    void syncLaunchAtStartupSettingFromStore();
  }

  // Create system tray
  if (!isE2EMode) {
    createTray(window);
  }

  // First-run workspace/skill maintenance is deferred until after the first
  // paint. It is pure housekeeping, but it used to start at the same instant as
  // the first frame and the Gateway cold boot, competing for the same disk.
  if (!isE2EMode) {
    scheduleDeferredStartupTasks(window);
  }

  // Override security headers ONLY for the OpenClaw Gateway Control UI.
  // The URL filter ensures this callback only fires for gateway requests,
  // avoiding unnecessary overhead on every other HTTP response.
  //
  // STU-004: Converge frame-ancestors to specific origins instead of wildcard '*'.
  // Only the Studio app itself and the local Gateway are allowed to embed this content.
  session.defaultSession.webRequest.onHeadersReceived(
    { urls: ['http://127.0.0.1:18789/*', 'http://localhost:18789/*'] },
    (details, callback) => {
      const headers = { ...details.responseHeaders };
      headers['X-Frame-Options'] = ['SAMEORIGIN'];
      // Converge frame-ancestors to specific trusted origins
      const allowedFrameAncestors = "frame-ancestors 'self' http://127.0.0.1:18789 http://localhost:18789";
      if (headers['Content-Security-Policy']) {
        headers['Content-Security-Policy'] = headers['Content-Security-Policy'].map(
          (csp) => csp.replace(/frame-ancestors\s+[^;]+/g, allowedFrameAncestors)
        );
      }
      if (headers['content-security-policy']) {
        headers['content-security-policy'] = headers['content-security-policy'].map(
          (csp) => csp.replace(/frame-ancestors\s+[^;]+/g, allowedFrameAncestors)
        );
      }
      callback({ responseHeaders: headers });
    },
  );

  // Register IPC handlers
  registerIpcHandlers(gatewayManager, clawHubService, window, hostApiRegistry);
  markStartup('ipc-ready');

  // Initialize extension system
  await extensionRegistry.initialize({
    gatewayManager,
    getMainWindow: () => mainWindow,
    hostApi: {
      register: (extensionId, contributions) => (
        hostApiRegistry.registerExtensionContributions(extensionId, contributions)
      ),
    },
  });
  markStartup('extensions-ready');

  // Wire marketplace provider to ClawHubService if an extension provides one
  const marketplaceProvider = extensionRegistry.getMarketplaceProvider();
  if (marketplaceProvider) {
    clawHubService.setMarketplaceProvider(marketplaceProvider);
  }

  // Register update handlers
  registerUpdateHandlers(appUpdater, window);

  // Note: Auto-check for updates is driven by the renderer (update store init)
  // so it respects the user's "Auto-check for updates" setting.

  // First-run workspace/skill maintenance now runs from
  // scheduleDeferredStartupTasks() (after the first paint) instead of here —
  // see the call site above and docs/startup-performance-plan.md (scheme 4.3).

  // Plugin installation is now configuration-driven:
  // - When a channel is added via UI: ensureXxxPluginInstalled() in IPC handlers
  // - When Gateway starts: ensureConfiguredPluginsUpgraded() in config-sync.ts
  // No need to pre-install all bundled plugins at app startup.

  // Bridge gateway and host-side events before any auto-start logic runs, so
  // renderer subscribers observe the full startup lifecycle.
  let gatewayReadyMarked = false;
  gatewayManager.on('status', (status: { state: string; gatewayReady?: boolean }) => {
    sendMainWindowEvent('gateway:status-changed', status);
    if (!gatewayReadyMarked && status.state === 'running' && status.gatewayReady) {
      gatewayReadyMarked = true;
      markStartup('gateway-ready');
      logStartupSummary('gateway-ready');
    }
    if (status.state === 'running' && !isE2EMode) {
      void ensureGrandPoemStudioContext().catch((error) => {
        logger.warn('Failed to re-merge GrandPoem Studio context after gateway reconnect:', error);
      });
    }
  });

  gatewayManager.on('error', (error) => {
    sendMainWindowEvent('gateway:error', { message: error.message });
  });

  gatewayManager.on('notification', (notification) => {
    sendMainWindowEvent('gateway:notification', notification);
  });

  gatewayManager.on('gateway:health', (data) => {
    sendMainWindowEvent('gateway:health-changed', data);
  });

  gatewayManager.on('gateway:presence', (data) => {
    sendMainWindowEvent('gateway:presence-changed', data);
  });

  gatewayManager.on('chat:message', (data) => {
    sendMainWindowEvent('gateway:chat-message', data);
  });

  gatewayManager.on('chat:runtime-event', (data) => {
    sendMainWindowEvent('chat:runtime-event', data);
  });

  gatewayManager.on('channel:status', (data) => {
    sendMainWindowEvent('gateway:channel-status', data);
  });

  gatewayManager.on('exit', (code) => {
    sendMainWindowEvent('gateway:exit', { code });
  });

  deviceOAuthManager.on('oauth:code', (payload) => {
    sendMainWindowEvent('oauth:code', payload);
  });

  deviceOAuthManager.on('oauth:success', (payload) => {
    sendMainWindowEvent('oauth:success', { ...payload, success: true });
  });

  deviceOAuthManager.on('oauth:error', (error) => {
    sendMainWindowEvent('oauth:error', error);
  });

  browserOAuthManager.on('oauth:code', (payload) => {
    sendMainWindowEvent('oauth:code', payload);
  });

  browserOAuthManager.on('oauth:success', (payload) => {
    sendMainWindowEvent('oauth:success', { ...payload, success: true });
  });

  browserOAuthManager.on('oauth:error', (error) => {
    sendMainWindowEvent('oauth:error', error);
  });

  // Note: WhatsApp login event listeners are registered in
  // registerWhatsAppHandlers() (ipc-handlers.ts) with isDestroyed() guards.
  // Do NOT register duplicate listeners here.

  // Start Gateway automatically (this seeds missing bootstrap files with full templates).
  //
  // The Gateway cold boot (~15-25s) is the long pole on the path to "usable", so
  // we begin it as early as safely possible. Two orderings:
  //
  //  - PARALLEL (default): decide auto-start from the persisted (cached) session
  //    and spawn immediately, running the authoritative `validateSessionForBoot()`
  //    round trip concurrently. If that check hits a DEFINITIVE 401 it already
  //    clears auth and flips the OpenClaw scope via `applyScopeChange` (see
  //    `resolveSessionState`), which the restart governor coalesces into a single
  //    reboot. A merely-slow/erroring backend never logs us out (Phase 1.2), so
  //    the common case boots the Gateway exactly once — just ~1-3s sooner, because
  //    the backend round trip overlaps the boot instead of preceding it.
  //
  //  - SERIAL (fallback): validate BEFORE spawning so a stale scope is corrected
  //    pre-flight. Flip PARALLEL_SESSION_VALIDATION_AT_BOOT to false to restore
  //    this strict order if a stale-scope double-boot is ever observed.
  const gatewayAutoStart = await getSetting('gatewayAutoStart');
  if (isE2EMode) {
    logger.info('Gateway auto-start skipped in E2E mode');
  } else if (!gatewayAutoStart) {
    logger.info('Gateway auto-start disabled in settings');
  } else if (PARALLEL_SESSION_VALIDATION_AT_BOOT) {
    // Kick the authoritative check (cached for the renderer's me() reuse) but do
    // NOT await it before spawning: decide from the persisted session and start.
    const validation = validateSessionForBoot();
    const cachedSession = await peekCachedAuthState();
    if (cachedSession.isAuthenticated) {
      await startGatewayAuto();
      // The boot-time check calls resolveSessionState(undefined) with no ctx, so
      // a definitive 401 clears auth + flips the scope but cannot restart the
      // Gateway itself. If it concluded logged-out, we optimistically spawned
      // into a now-stale scope — reconcile by stopping it (mirrors the serial
      // path's "don't run the Gateway while logged out"); login starts it fresh.
      // The common authenticated case awaits the already-settled check and skips
      // this, so we still save the pre-spawn round trip.
      const authoritative = await validation;
      if (!authoritative.isAuthenticated) {
        logger.warn(
          '[startup] boot session logged out during parallel spawn; stopping the optimistically-started Gateway',
        );
        await gatewayManager.stop().catch((error) => {
          logger.warn('[startup] failed to stop Gateway after boot logout reconciliation:', error);
        });
      }
    } else {
      logger.info('Gateway auto-start deferred until login (no cached session at boot)');
      await validation;
    }
  } else {
    const bootSession = await validateSessionForBoot();
    if (bootSession.isAuthenticated) {
      await startGatewayAuto();
    } else {
      logger.info('Gateway auto-start deferred until login (no authenticated session at boot)');
    }
  }

  // Merge GrandPoem Studio context snippets into the workspace bootstrap files.
  // The gateway seeds workspace files asynchronously after its HTTP server
  // is ready, so ensureGrandPoemStudioContext will retry until the target files appear.
  if (!isE2EMode) {
    void ensureGrandPoemStudioContext().catch((error) => {
      logger.warn('Failed to merge GrandPoem Studio context into workspace:', error);
    });
  }

  // Auto-install openclaw CLI and shell completions. Deferred until the Gateway
  // is fully ready: generateCompletionCache() spawns a second full OpenClaw
  // process that competes with the Gateway cold boot for disk/CPU (and, on
  // Windows, antivirus scanning of the module tree), measurably slowing it down.
  if (!isE2EMode) {
    void waitForGatewayReadyOnce().then(() => {
      void autoInstallCliIfNeeded((installedPath) => {
        mainWindow?.webContents.send('openclaw:cli-installed', installedPath);
      }).then(() => {
        generateCompletionCache();
        installCompletionToProfile();
      }).catch((error) => {
        logger.warn('CLI auto-install failed:', error);
      });
    });
  }
}

/**
 * Resolves the first time the Gateway reaches running with subsystems ready
 * (or after a safety timeout, so background setup is never blocked forever —
 * e.g. when the user stays logged out and the Gateway never starts).
 */
function waitForGatewayReadyOnce(timeoutMs = 90_000): Promise<void> {
  return new Promise<void>((resolve) => {
    let settled = false;
    const done = (): void => {
      if (settled) return;
      settled = true;
      gatewayManager.off('status', onStatus);
      clearTimeout(timer);
      resolve();
    };
    const onStatus = (status: { state: string; gatewayReady?: boolean }): void => {
      if (status.state === 'running' && status.gatewayReady) done();
    };
    const timer = setTimeout(done, timeoutMs);
    gatewayManager.on('status', onStatus);
    const current = gatewayManager.getStatus();
    if (current.state === 'running' && current.gatewayReady) done();
  });
}

if (gotTheLock) {
  const requestQuitOnSignal = createSignalQuitHandler({
    logInfo: (message) => logger.info(message),
    requestQuit: () => app.quit(),
  });

  process.on('exit', () => {
    releaseProcessInstanceFileLock();
  });

  process.once('SIGINT', () => requestQuitOnSignal('SIGINT'));
  process.once('SIGTERM', () => requestQuitOnSignal('SIGTERM'));

  app.on('will-quit', () => {
    releaseProcessInstanceFileLock();
  });

  if (process.platform === 'win32') {
    app.setAppUserModelId(WINDOWS_APP_USER_MODEL_ID);
  }

  gatewayManager = new GatewayManager();
  clawHubService = new ClawHubService();

  // Register builtin extensions and load manifest
  registerAllBuiltinExtensions();
  loadExternalMainExtensions();
  void loadExtensionsFromManifest().catch((err) => {
    logger.warn('Failed to load extensions from manifest:', err);
  });

  // ============================================================================
  // Security: Webview and navigation hardening (STU-001/STU-003)
  // ============================================================================
  // Intercept all web-contents creation to enforce security policies on webviews
  // and restrict navigation to trusted origins only.
  app.on('web-contents-created', (_event, contents) => {
    // Harden webview tags: disable nodeIntegration, enforce contextIsolation,
    // and restrict preload/src to whitelisted paths.
    contents.on('will-attach-webview', (event, webPreferences, params) => {
      // Disable Node.js integration in webview
      webPreferences.nodeIntegration = false;
      // Enforce context isolation
      webPreferences.contextIsolation = true;
      // Enable sandbox for webview content
      webPreferences.sandbox = true;
      // Keep webSecurity enabled; the Gateway must use proper CORS headers
      // instead of disabling same-origin policy, which would expose the
      // webview to cross-origin attacks.
      
      // Validate preload script path (must be within app directory)
      if (webPreferences.preload) {
        const preloadPath = webPreferences.preload;
        const appPath = app.getAppPath();
        if (!preloadPath.startsWith(appPath)) {
          logger.warn(`Blocked webview with external preload: ${preloadPath}`);
          event.preventDefault();
          return;
        }
      }
      
      // Validate src URL (must be http/https or file from app directory)
      const srcUrl = params.src;
      try {
        const parsed = new URL(srcUrl);
        const isLocalFile = parsed.protocol === 'file:' && parsed.pathname.startsWith(app.getAppPath());
        const isLocalHttp = parsed.protocol === 'http:' || parsed.protocol === 'https:';
        if (!isLocalFile && !isLocalHttp) {
          logger.warn(`Blocked webview with disallowed src: ${srcUrl}`);
          event.preventDefault();
        }
      } catch {
        logger.warn(`Blocked webview with malformed src URL: ${srcUrl}`);
        event.preventDefault();
      }
    });

    // Restrict navigation to trusted origins only. The main window carries the
    // preload bridge (full host capabilities), so it must never navigate to an
    // arbitrary external origin — a clicked link switching this window to an
    // attacker site would hand that site the host API. External http/https links
    // are opened in the system browser by setWindowOpenHandler above instead.
    contents.on('will-navigate', (event, url) => {
      try {
        const parsed = new URL(url);
        const isLocalhost = parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1';
        // Allow only:
        // - file:// inside the packaged app directory (local app resources)
        // - http://localhost / 127.0.0.1 (Vite dev server, Gateway)
        const isAppFile = parsed.protocol === 'file:' && parsed.pathname.startsWith(app.getAppPath());
        const isLocalHttp = parsed.protocol === 'http:' && isLocalhost;

        if (!isAppFile && !isLocalHttp) {
          logger.warn(`Blocked navigation to disallowed URL: ${url}`);
          event.preventDefault();
        }
      } catch {
        logger.warn(`Blocked navigation to malformed URL: ${url}`);
        event.preventDefault();
      }
    });
  });

  // When a second instance is launched, focus the existing window instead.
  app.on('second-instance', () => {
    logger.info('Second GrandPoem Studio instance detected; redirecting to the existing window');

    const focusRequest = requestSecondInstanceFocus(
      mainWindowFocusState,
      Boolean(mainWindow && !mainWindow.isDestroyed()),
    );

    if (focusRequest === 'focus-now') {
      focusMainWindow();
      return;
    }

    logger.debug('Main window is not ready yet; deferring second-instance focus until ready-to-show');
  });

  // Application lifecycle
  app.whenReady().then(() => {
    void initialize().catch((error) => {
      logger.error('Application initialization failed:', error);
    });

    // Register activate handler AFTER app is ready to prevent
    // "Cannot create BrowserWindow before app is ready" on macOS.
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        void createMainWindow();
      } else {
        focusMainWindow();
      }
    });
  });

  app.on('window-all-closed', () => {
    // In E2E mode the close handler does NOT prevent close, so the window
    // actually closes and we should quit the app to let the test runner finish.
    // In normal mode the close button hides the window to tray; quit is handled
    // exclusively via the tray context-menu "退出" item (app.quit()).
    // Previously this called app.quit() on Windows/Linux, which defeated the
    // close-to-tray behaviour because hiding the last window can fire
    // window-all-closed on Windows.
    if (isE2EMode) {
      app.quit();
    }
  });

  app.on('before-quit', (event) => {
    setQuitting();
    const action = requestQuitLifecycleAction(quitLifecycleState);

    if (action === 'allow-quit') {
      return;
    }

    event.preventDefault();

    if (action === 'cleanup-in-progress') {
      logger.debug('Quit requested while cleanup already in progress; waiting for shutdown task to finish');
      return;
    }

    void extensionRegistry.teardownAll();

    const stopPromise = gatewayManager.stop().catch((err) => {
      logger.warn('gatewayManager.stop() error during quit:', err);
    });
    const timeoutPromise = new Promise<'timeout'>((resolve) => {
      setTimeout(() => resolve('timeout'), 5000);
    });

    void Promise.race([stopPromise.then(() => 'stopped' as const), timeoutPromise]).then((result) => {
      if (result === 'timeout') {
        logger.warn('Gateway shutdown timed out during app quit; proceeding with forced quit');
        void gatewayManager.forceTerminateOwnedProcessForQuit().then((terminated) => {
          if (terminated) {
            logger.warn('Forced gateway process termination completed after quit timeout');
          }
        }).catch((err) => {
          logger.warn('Forced gateway termination failed after quit timeout:', err);
        });
      }
      markQuitCleanupCompleted(quitLifecycleState);
      app.quit();
    });
  });

  // Best-effort Gateway cleanup on unexpected crashes.
  // These handlers attempt to terminate the Gateway child process within a
  // short timeout before force-exiting, preventing orphaned processes.
  const emergencyGatewayCleanup = (reason: string, error: unknown): void => {
    logger.error(`${reason}:`, error);
    try {
      void gatewayManager?.stop().catch(() => { /* ignore */ });
    } catch {
      // ignore —stop() may not be callable if state is corrupted
    }
    // Give Gateway stop a brief window, then force-exit.
    setTimeout(() => {
      process.exit(1);
    }, 3000).unref();
  };

  process.on('uncaughtException', (error) => {
    emergencyGatewayCleanup('Uncaught exception in main process', error);
  });

  process.on('unhandledRejection', (reason) => {
    logger.error('Unhandled promise rejection in main process:', reason);
  });
}

// Export for testing
export { mainWindow, gatewayManager };
