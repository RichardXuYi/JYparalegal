/**
 * Root Application Component
 * Handles routing and global providers. Renders the desktop shell
 * (MainLayout) or the mobile shell (MobileLayout) based on runtime device
 * detection (see use-device-shell); both shells share stores and routes.
 * Electron-only pieces (setup wizard, update notifier) stay on this build.
 */
import { Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { Component, Suspense, lazy, useEffect, useRef } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Toaster } from 'sonner';
import i18n from './i18n';
import { TooltipProvider } from '@/components/ui/tooltip';
import { useSettingsStore } from './stores/settings';
import { useAuthStore } from './stores/auth';
import { useSkillsStore } from './stores/skills';
import { useUpdateStore } from './stores/update';
import { useGatewayStore } from './stores/gateway';
import { useProviderStore } from './stores/providers';
import { rendererExtensionRegistry } from './extensions/registry';
import { loadExternalRendererExtensions } from './extensions/_ext-bridge.generated';
import { UpdateNotifier } from './components/update/UpdateNotifier';
import { ConnectionStatusModal } from './components/common/ConnectionStatusModal';
import { InitializingScreen } from './components/common/InitializingScreen';
import { useNewChatAction } from './components/layout/use-new-chat-action';
import { hostEvents } from './lib/host-events';
import { useDeviceShell } from '@/hooks/use-device-shell';

const MainLayout = lazy(() => import('./components/layout/MainLayout').then((m) => ({ default: m.MainLayout })));
const MobileLayout = lazy(() => import('./mobile/layout/MobileLayout').then((m) => ({ default: m.MobileLayout })));
const Chat = lazy(() => import('./pages/Chat').then((m) => ({ default: m.Chat })));
const Setup = lazy(() => import('./pages/Setup').then((m) => ({ default: m.Setup })));
const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })));
const SsoBridge = lazy(() => import('./pages/SsoBridge').then((m) => ({ default: m.SsoBridge })));
const More = lazy(() => import('./mobile/pages/More').then((m) => ({ default: m.More })));
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
  const location = useLocation();
  const shell = useDeviceShell();
  const isMobileShell = shell === 'mobile';
  const skipSetupForE2E = typeof window !== 'undefined'
    && new URLSearchParams(window.location.search).get('e2eSkipSetup') === '1';
  const initSettings = useSettingsStore((state) => state.init);
  const settingsInitCompleted = useSettingsStore((state) => state.initCompleted);
  const theme = useSettingsStore((state) => state.theme);
  const language = useSettingsStore((state) => state.language);
  const setupComplete = useSettingsStore((state) => state.setupComplete);
  const autoSyncSkills = useSettingsStore((state) => state.autoSyncSkills);
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const authUser = useAuthStore((state) => state.user);
  const restoreAuth = useAuthStore((state) => state.restore);
  const syncSkills = useSkillsStore((state) => state.syncSkills);
  const autoSyncTriggeredRef = useRef(false);
  const initGateway = useGatewayStore((state) => state.init);
  const gatewayInitialized = useGatewayStore((state) => state.isInitialized);
  const initUpdate = useUpdateStore((state) => state.init);
  const initProviders = useProviderStore((state) => state.init);
  const providersInitCompleted = useProviderStore((state) => state.initCompleted);
  const handleNewChat = useNewChatAction();

  const authUserId = authUser?.id ?? null;
  const allInitDone = authStatus === 'ready'
    && settingsInitCompleted
    && gatewayInitialized
    && providersInitCompleted;

  useEffect(() => {
    let cancelled = false;

    void initSettings().finally(() => {
      if (!cancelled) {
        void initUpdate();
      }
    });

    return () => {
      cancelled = true;
    };
  }, [initSettings, initUpdate]);

  useEffect(() => {
    void restoreAuth();
  }, [restoreAuth]);

  const lastInitUserIdRef = useRef<number | null | undefined>(undefined);
  useEffect(() => {
    const previous = lastInitUserIdRef.current;
    lastInitUserIdRef.current = authUserId;
    if (previous === undefined || authUserId === null || previous === authUserId) return;
    void initSettings();
    initGateway();
    initProviders();
  }, [authUserId, initSettings, initGateway, initProviders]);

  useEffect(() => {
    if (language && language !== i18n.language) {
      i18n.changeLanguage(language);
    }
  }, [language]);

  useEffect(() => {
    initGateway();
  }, [initGateway]);

  useEffect(() => {
    initProviders();
  }, [initProviders]);

  useEffect(() => {
    if (isAuthenticated && !setupComplete && !skipSetupForE2E && !location.pathname.startsWith('/setup')) {
      navigate('/setup');
    }
  }, [isAuthenticated, setupComplete, skipSetupForE2E, location.pathname, navigate]);

  useEffect(() => {
    if (!isAuthenticated) {
      autoSyncTriggeredRef.current = false;
      return;
    }
    if (authStatus !== 'ready' || !setupComplete || !autoSyncSkills) {
      return;
    }
    if (autoSyncTriggeredRef.current) {
      return;
    }
    autoSyncTriggeredRef.current = true;
    void syncSkills();
  }, [authStatus, isAuthenticated, setupComplete, autoSyncSkills, syncSkills]);

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

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove('light', 'dark');

    const cacheMode = (mode: 'light' | 'dark') => {
      try { localStorage.setItem('jy.theme', mode); } catch { /* ignore */ }
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

  useEffect(() => {
    loadExternalRendererExtensions();
    void rendererExtensionRegistry.initializeAll();
    return () => rendererExtensionRegistry.teardownAll();
  }, []);

  const extraRoutes = rendererExtensionRegistry.getExtraRoutes();
  const isSsoRoute = typeof window !== 'undefined' && window.location.hash.startsWith('#/sso');
  const toaster = (
    <Toaster
      position={isMobileShell ? 'top-center' : 'bottom-right'}
      richColors
      closeButton
      theme={theme}
      style={{ zIndex: 99999 }}
    />
  );

  if (authStatus !== 'ready' && !isSsoRoute) {
    return (
      <ErrorBoundary>
        <InitializingScreen visible={true} />
      </ErrorBoundary>
    );
  }

  if (isSsoRoute) {
    return (
      <ErrorBoundary>
        <TooltipProvider delayDuration={300}>
          <Suspense fallback={<CenteredSpinner />}>
            <Routes>
              <Route path="/sso" element={<SsoBridge />} />
            </Routes>
          </Suspense>
          {toaster}
        </TooltipProvider>
      </ErrorBoundary>
    );
  }

  if (!isAuthenticated) {
    return (
      <ErrorBoundary>
        <TooltipProvider delayDuration={300}>
          <Suspense fallback={<CenteredSpinner />}>
            <Routes>
              <Route path="*" element={<Login />} />
            </Routes>
          </Suspense>
          {toaster}
        </TooltipProvider>
      </ErrorBoundary>
    );
  }

  const needsSetup = !setupComplete && !skipSetupForE2E;
  if (!needsSetup && !allInitDone) {
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
            <Route path="/setup/*" element={<Setup />} />
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
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </Suspense>

        <UpdateNotifier />
        <ConnectionStatusModal />
        {toaster}
      </TooltipProvider>
    </ErrorBoundary>
  );
}

export default App;
