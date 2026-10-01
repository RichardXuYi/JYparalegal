/**
 * Root Application Component
 * Handles routing and global providers. Renders the desktop shell
 * (MainLayout) or the mobile shell (MobileLayout) based on runtime device
 * detection (see use-device-shell); both shells share stores and routes.
 * Electron-only pieces (update notifier) stay on this build.
 */
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
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
import { GatewayFailureDialog } from './components/common/GatewayFailureDialog';
import { GatewayBootScreen } from './components/common/GatewayBootScreen';
import { NoticeHost } from './components/common/NoticeHost';
import { InitializingScreen } from './components/common/InitializingScreen';
import { useNewChatAction } from './components/layout/use-new-chat-action';
import { hostEvents } from './lib/host-events';
import { useDeviceShell } from '@/hooks/use-device-shell';

const MainLayout = lazy(() => import('./components/layout/MainLayout').then((m) => ({ default: m.MainLayout })));
const MobileLayout = lazy(() => import('./mobile/layout/MobileLayout').then((m) => ({ default: m.MobileLayout })));
const Chat = lazy(() => import('./pages/Chat').then((m) => ({ default: m.Chat })));
const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })));
const Register = lazy(() => import('./pages/Register').then((m) => ({ default: m.Register })));
const SsoBridge = lazy(() => import('./pages/SsoBridge').then((m) => ({ default: m.SsoBridge })));
const More = lazy(() => import('./mobile/pages/More').then((m) => ({ default: m.More })));
const Overview = lazy(() => import('./pages/Overview'));
const Signing = lazy(() => import('./pages/Signing'));
const TaskNew = lazy(() => import('./pages/CreateTask/TaskNew'));
const TaskSetup = lazy(() => import('./pages/CreateTask/TaskSetup'));
const TaskCompose = lazy(() => import('./pages/CreateTask/TaskCompose'));
const TaskDetail = lazy(() => import('./pages/TaskDetail'));
const MockCourt = lazy(() => import('./pages/MockCourt'));
const Features = lazy(() => import('./pages/Features'));

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
  const shell = useDeviceShell();
  const isMobileShell = shell === 'mobile';
  const initSettings = useSettingsStore((state) => state.init);
  const settingsInitCompleted = useSettingsStore((state) => state.initCompleted);
  const theme = useSettingsStore((state) => state.theme);
  const shellAnimation = useSettingsStore((state) => state.shellAnimation);
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
  const initUpdate = useUpdateStore((state) => state.init);
  const initProviders = useProviderStore((state) => state.init);
  const providersInitCompleted = useProviderStore((state) => state.initCompleted);
  const handleNewChat = useNewChatAction();

  const authUserId = authUser?.id ?? null;
  // Optimistic auth: a persisted (rehydrated) session counts as settled so the
  // shell paints immediately; restore()/me() revalidates in the background and
  // flips isAuthenticated if the session was actually rejected.
  const authSettled = authStatus === 'ready' || isAuthenticated;
  // The app shell is released as soon as the stores initialize. On top of it,
  // GatewayBootScreen covers the window (launcher-style) until the Gateway
  // first runs this session, so users never land on a half-ready UI with a
  // "connecting" banner; once it has run, dips surface via the failure dialog
  // and the status chip instead.
  const allInitDone = authSettled
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

  // Gates the shell aurora drift in globals.css — a CSS class keeps the switch
  // working without re-rendering the layout tree.
  useEffect(() => {
    window.document.documentElement.classList.toggle('shell-anim-off', !shellAnimation);
  }, [shellAnimation]);

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
  // The NoticeHost must be mounted on every screen: the layouts are skipped by
  // the boot/auth branches below, so a failed action there would have nowhere to
  // surface. Mounting it here also covers the mobile shell, which has no
  // layout-level host of its own.
  const noticeHost = <NoticeHost />;

  if (!authSettled && !isSsoRoute) {
    return (
      <ErrorBoundary>
        <InitializingScreen visible={true} />
        {noticeHost}
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
          {noticeHost}
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
              <Route path="/register" element={<Register />} />
              <Route path="*" element={<Login />} />
            </Routes>
          </Suspense>
          {noticeHost}
          {toaster}
        </TooltipProvider>
      </ErrorBoundary>
    );
  }

  // Release the shell as soon as the stores are initialized. GatewayBootScreen
  // covers the window until the Gateway first runs this session (launcher-style
  // boot), and per-page states take over afterwards — there is no banner.
  if (!allInitDone) {
    return (
      <ErrorBoundary>
        <InitializingScreen visible={true} />
        {noticeHost}
      </ErrorBoundary>
    );
  }

  return (
    <ErrorBoundary>
      <TooltipProvider delayDuration={300}>
        <Suspense fallback={<CenteredSpinner />}>
          <Routes>
            <Route element={isMobileShell ? <MobileLayout /> : <MainLayout />}>
              <Route path="/" element={<Chat />} />
              <Route path="/overview" element={<Overview />} />
              <Route path="/signing" element={<Signing />} />
              <Route path="/signing/new" element={<TaskNew />} />
              <Route path="/signing/:id/setup" element={<TaskSetup />} />
              <Route path="/signing/:id/compose" element={<TaskCompose />} />
              <Route path="/signing/:id" element={<TaskDetail />} />
              <Route path="/moot" element={<MockCourt />} />
              <Route path="/features" element={<Features />} />

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
        <GatewayFailureDialog />
        <GatewayBootScreen />
        {noticeHost}
        {toaster}
      </TooltipProvider>
    </ErrorBoundary>
  );
}

export default App;
