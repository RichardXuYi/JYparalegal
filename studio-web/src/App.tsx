/**
 * Root Application Component
 * Handles routing and global providers. Renders the desktop shell
 * (MainLayout) or the mobile shell (MobileLayout) based on runtime device
 * detection (see use-device-shell); both shells share stores and routes.
 */
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { Component, Suspense, lazy, useEffect, useRef, useState } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Toaster } from 'sonner';
import i18n from './i18n';
import { TooltipProvider } from '@/components/ui/tooltip';
import { useSettingsStore } from './stores/settings';
import { useAuthStore } from './stores/auth';
import { useSkillsStore } from './stores/skills';
import { useGatewayStore } from './stores/gateway';
import { useProviderStore } from './stores/providers';
import { rendererExtensionRegistry } from './extensions/registry';
import { loadExternalRendererExtensions } from './extensions/_ext-bridge.generated';

import { GatewayFailureDialog } from './components/common/GatewayFailureDialog';
import { InitializingScreen } from './components/common/InitializingScreen';
import { useNewChatAction } from './components/layout/use-new-chat-action';
import { hostEvents } from './lib/host-events';
import { BOOT_SCREEN_CAP_MS, shouldHoldBootScreen } from './lib/connection-status';
import { useDeviceShell } from '@/hooks/use-device-shell';

// Route-level code splitting: both shells and all pages load on demand so
// each device only pays for the shell it renders.
const MainLayout = lazy(() => import('./components/layout/MainLayout').then((m) => ({ default: m.MainLayout })));
const MobileLayout = lazy(() => import('./mobile/layout/MobileLayout').then((m) => ({ default: m.MobileLayout })));
const Chat = lazy(() => import('./pages/Chat').then((m) => ({ default: m.Chat })));
const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })));
const SsoBridge = lazy(() => import('./pages/SsoBridge').then((m) => ({ default: m.SsoBridge })));
const More = lazy(() => import('./mobile/pages/More').then((m) => ({ default: m.More })));
// V3 平台页（docs/06-前端改造与IA-V3.md）：总览 / 签署 / 详情 / 模拟法庭
const Overview = lazy(() => import('./pages/Overview'));
const Signing = lazy(() => import('./pages/Signing'));
const TaskNew = lazy(() => import('./pages/CreateTask/TaskNew'));
const TaskSetup = lazy(() => import('./pages/CreateTask/TaskSetup'));
const TaskCompose = lazy(() => import('./pages/CreateTask/TaskCompose'));
const TaskDetail = lazy(() => import('./pages/TaskDetail'));
const MockCourt = lazy(() => import('./pages/MockCourt'));
const Voice = lazy(() => import('./pages/Voice'));
const Templates = lazy(() => import('./pages/Templates'));
const Evidence = lazy(() => import('./pages/Evidence'));
const CompanyManage = lazy(() => import('./pages/CompanyManage'));
const Compare = lazy(() => import('./pages/Compare'));

function CenteredSpinner() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-modal">
      <div className="flex flex-col items-center gap-4">
        <div className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        <span className="text-sm text-muted-foreground">{i18n.t('auth.loading', '正在加载...')}</span>
      </div>
    </div>
  );
}


/**
 * Error Boundary to catch and display React rendering errors
 */
class ErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean; error: Error | null }
> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('React Error Boundary caught error:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen p-10 bg-surface-modal text-red-400 font-mono">
          <h1 className="text-2xl mb-4">{i18n.t('error.title', 'Something went wrong')}</h1>
          <pre className="whitespace-pre-wrap break-all bg-surface-input p-4 rounded-lg text-sm">
            {this.state.error?.message}
            {'\n\n'}
            {this.state.error?.stack}
          </pre>
          <button
            onClick={() => { this.setState({ hasError: false, error: null }); window.location.reload(); }}
            className="mt-4 px-4 py-2 bg-primary text-primary-foreground rounded-xl cursor-pointer hover:opacity-90"
          >
            {i18n.t('error.reload', 'Reload')}
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

function App() {
  const navigate = useNavigate();
  const shell = useDeviceShell();
  const isMobileShell = shell === 'mobile';
  const initSettings = useSettingsStore((state) => state.init);
  const settingsInitCompleted = useSettingsStore((state) => state.initCompleted);
  const theme = useSettingsStore((state) => state.theme);
  const language = useSettingsStore((state) => state.language);
  const autoSyncSkills = useSettingsStore((state) => state.autoSyncSkills);
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const authUser = useAuthStore((state) => state.user);
  const restoreAuth = useAuthStore((state) => state.restore);
  const syncSkills = useSkillsStore((state) => state.syncSkills);
  const autoSyncTriggeredRef = useRef(false);
  const initGateway = useGatewayStore((state) => state.init);
  const gatewayInitialized = useGatewayStore((state) => state.isInitialized);
  const gatewayStatus = useGatewayStore((state) => state.status);
  const gatewaySeenRunning = useGatewayStore((state) => state.hasSeenRunningThisSession);
  const initProviders = useProviderStore((state) => state.init);
  const providersInitCompleted = useProviderStore((state) => state.initCompleted);
  const handleNewChat = useNewChatAction();

  // Per-account stores are wiped on every user switch (see user-session-reset),
  // so their init must run again whenever the signed-in account changes —
  // not just once on mount.
  const authUserId = authUser?.id ?? null;

  // All initialization complete?
  const allInitDone = authStatus === 'ready'
    && settingsInitCompleted
    && gatewayInitialized
    && providersInitCompleted;

  // 加载动画的硬上限：从"各 store 初始化完成"起算。网关若永不回报状态，
  // 到点也放行主界面（以非阻塞横幅呈现连接态），绝不无限停在加载页。
  const [bootCapElapsed, setBootCapElapsed] = useState(false);
  useEffect(() => {
    if (!allInitDone) return undefined;
    const timer = setTimeout(() => setBootCapElapsed(true), BOOT_SCREEN_CAP_MS);
    return () => clearTimeout(timer);
  }, [allInitDone]);

  // 网关就绪（或终态/超时）之前，主界面不放出来：画好的页面再盖模态，观感等于卡死。
  const holdBootScreen = shouldHoldBootScreen({
    status: gatewayStatus,
    hasSeenRunningThisSession: gatewaySeenRunning,
    capElapsed: bootCapElapsed,
  });

  useEffect(() => {
    if (authUserId === null) return;
    void initSettings();
  }, [authUserId, initSettings]);

  // Validate the backend session on boot (tokens live in the main process).
  useEffect(() => {
    void restoreAuth();
  }, [restoreAuth]);

  // Sync i18n language with persisted settings on mount
  useEffect(() => {
    if (language && language !== i18n.language) {
      i18n.changeLanguage(language);
    }
  }, [language]);

  // Initialize Gateway connection once authenticated, and again after every
  // account switch (the reset marks it uninitialized).
  useEffect(() => {
    if (authUserId === null) return;
    initGateway();
  }, [authUserId, initGateway]);

  // Initialize provider snapshot once authenticated, and again after every
  // account switch.
  useEffect(() => {
    if (authUserId === null) return;
    initProviders();
  }, [authUserId, initProviders]);

  // Auto-sync user skills once per session after login (opt-in setting).
  useEffect(() => {
    if (!isAuthenticated) {
      autoSyncTriggeredRef.current = false;
      return;
    }
    if (authStatus !== 'ready' || !autoSyncSkills) {
      return;
    }
    if (autoSyncTriggeredRef.current) {
      return;
    }
    autoSyncTriggeredRef.current = true;
    void syncSkills();
  }, [authStatus, isAuthenticated, autoSyncSkills, syncSkills]);

  // Listen for navigation events from main process
  useEffect(() => {
    const unsubscribe = hostEvents.onNavigate((path) => {
      navigate(path);
    });

    return () => {
      if (typeof unsubscribe === 'function') {
        unsubscribe();
      }
    };
  }, [navigate]);

  useEffect(() => {
    const unsubscribe = hostEvents.onNewChat(handleNewChat);

    return () => {
      if (typeof unsubscribe === 'function') {
        unsubscribe();
      }
    };
  }, [handleNewChat]);

  // Apply theme (with live system theme tracking)
  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove('light', 'dark');

    // 缓存有效模式,index.html 首屏同步预读,消除「先亮后暗」闪白(D8)
    const cacheMode = (mode: 'light' | 'dark') => {
      try { localStorage.setItem('jy.theme', mode); } catch { /* 隐私模式等场景忽略 */ }
    };

    if (theme === 'system') {
      const mql = window.matchMedia('(prefers-color-scheme: dark)');
      const applySystemTheme = () => {
        root.classList.remove('light', 'dark');
        const mode = mql.matches ? 'dark' : 'light';
        root.classList.add(mode);
        cacheMode(mode);
      };
      applySystemTheme();
      mql.addEventListener('change', applySystemTheme);
      return () => mql.removeEventListener('change', applySystemTheme);
    } else {
      root.classList.add(theme);
      cacheMode(theme);
    }
  }, [theme]);

  // Load external renderer extensions (generated by scripts/generate-ext-bridge.mjs)
  // and initialize all registered extensions.
  useEffect(() => {
    loadExternalRendererExtensions();
    void rendererExtensionRegistry.initializeAll();
    return () => rendererExtensionRegistry.teardownAll();
  }, []);

  const extraRoutes = rendererExtensionRegistry.getExtraRoutes();

  // Check if we're on the SSO bridge route - allow access even during initialization
  const isSsoRoute = typeof window !== 'undefined' && window.location.hash.startsWith('#/sso');

  // Auth gate: block until the backend session has been validated.
  // Exception: SSO bridge route is accessible during initialization.
  if (authStatus !== 'ready' && !isSsoRoute) {
    return (
      <ErrorBoundary>
        <InitializingScreen visible={true} />
      </ErrorBoundary>
    );
  }

  // SSO bridge route - accessible even when not authenticated
  if (isSsoRoute) {
    return (
      <ErrorBoundary>
        <TooltipProvider delayDuration={300}>
          <Suspense fallback={<CenteredSpinner />}>
            <Routes>
              <Route path="/sso" element={<SsoBridge />} />
            </Routes>
          </Suspense>
          <Toaster
            position={isMobileShell ? 'top-center' : 'bottom-right'}
            richColors
            closeButton
            theme={theme}
            style={{ zIndex: 99999 }}
          />
        </TooltipProvider>
      </ErrorBoundary>
    );
  }

  // Not signed in: only the login screen is reachable.
  if (!isAuthenticated) {
    return (
      <ErrorBoundary>
        <TooltipProvider delayDuration={300}>
          <Suspense fallback={<CenteredSpinner />}>
            <Routes>
              <Route path="*" element={<Login />} />
            </Routes>
          </Suspense>
          <Toaster
            position={isMobileShell ? 'top-center' : 'bottom-right'}
            richColors
            closeButton
            theme={theme}
            style={{ zIndex: 99999 }}
          />
        </TooltipProvider>
      </ErrorBoundary>
    );
  }

  // Authenticated but still loading (incl. gateway startup): keep the brand
  // loading animation up instead of revealing a shell we'd have to dim again.
  if (!allInitDone || holdBootScreen) {
    return (
      <ErrorBoundary>
        <InitializingScreen visible={true} />
      </ErrorBoundary>
    );
  }

  return (
    <ErrorBoundary>
      <TooltipProvider delayDuration={300}>
        <Suspense fallback={<CenteredSpinner />}>
          <Routes>
            {/* Main application routes inside the shell picked for this device */}
            <Route element={isMobileShell ? <MobileLayout /> : <MainLayout />}>
              <Route path="/" element={<Chat />} />
              <Route path="/overview" element={<Overview />} />
              <Route path="/signing" element={<Signing />} />
              <Route path="/signing/new" element={<TaskNew />} />
              <Route path="/signing/:id/setup" element={<TaskSetup />} />
              <Route path="/signing/:id/compose" element={<TaskCompose />} />
              <Route path="/signing/:id" element={<TaskDetail />} />
              <Route path="/moot" element={<MockCourt />} />
              <Route path="/voice" element={<Voice />} />
              <Route path="/templates" element={<Templates />} />
              <Route path="/evidence" element={<Evidence />} />
              <Route path="/company" element={<CompanyManage />} />
              <Route path="/compare" element={<Compare />} />
              {isMobileShell && <Route path="/more" element={<More />} />}
              {extraRoutes.map((r) => (
                <Route key={r.path} path={r.path} element={<r.component />} />
              ))}
              {/* Unknown paths (e.g. /more after switching to the desktop shell) */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </Suspense>

        {/* Non-blocking banner + terminal failure dialog */}
        <GatewayFailureDialog />

        {/* Global toast notifications (mobile: bottom is taken by tab bar / input) */}
        <Toaster
          position={isMobileShell ? 'top-center' : 'bottom-right'}
          richColors
          closeButton
          theme={theme}
          style={{ zIndex: 99999 }}
        />
      </TooltipProvider>
    </ErrorBoundary>
  );
}

export default App;
