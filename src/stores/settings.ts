/**
 * Settings State Store
 * Manages application settings
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { reportFailure } from '@/lib/notice';
import i18n from '@/i18n';
import { hostApi } from '@/lib/host-api';
import { resolveSupportedLanguage } from '@shared/language';

type Theme = 'light' | 'dark' | 'system';

interface SettingsState {
  // General
  theme: Theme;
  language: string;
  startMinimized: boolean;
  /** Drift animation of the shell aurora background. */
  shellAnimation: boolean;
  telemetryEnabled: boolean;

  // Gateway
  gatewayAutoStart: boolean;
  gatewayPort: number;
  proxyEnabled: boolean;
  proxyServer: string;
  proxyHttpServer: string;
  proxyHttpsServer: string;
  proxyAllServer: string;
  proxyBypassRules: string;

  // Skills
  autoSyncSkills: boolean;

  // UI State
  sidebarCollapsed: boolean;
  sidebarWidth: number;
  devModeUnlocked: boolean;

  // Initialization
  initCompleted: boolean;

  // Actions
  init: () => Promise<void>;
  setTheme: (theme: Theme) => void;
  setLanguage: (language: string) => void;
  setStartMinimized: (value: boolean) => void;
  setShellAnimation: (value: boolean) => void;
  setTelemetryEnabled: (value: boolean) => void;
  setGatewayAutoStart: (value: boolean) => void;
  setGatewayPort: (port: number) => void;
  setProxyEnabled: (value: boolean) => void;
  setProxyServer: (value: string) => void;
  setProxyHttpServer: (value: string) => void;
  setProxyHttpsServer: (value: string) => void;
  setProxyAllServer: (value: string) => void;
  setProxyBypassRules: (value: string) => void;
  setAutoSyncSkills: (value: boolean) => void;
  setSidebarCollapsed: (value: boolean) => void;
  setSidebarWidth: (value: number) => void;
  setDevModeUnlocked: (value: boolean) => void;
  resetSettings: () => Promise<void>;
  /** Drop per-account settings (theme/language) when the account changes. */
  resetForUserSwitch: () => void;
}

const LANGUAGE_EXPLICIT_FLAG_PREFIX = 'grandpoem-studio-language-explicit';

/** Persist that this account explicitly chose a language (per-user flag). */
async function markLanguageExplicit(language: string): Promise<void> {
  try {
    const { useAuthStore } = await import('./auth');
    const userId = useAuthStore.getState().user?.id ?? 'anon';
    localStorage.setItem(`${LANGUAGE_EXPLICIT_FLAG_PREFIX}:${userId}`, language);
  } catch {
    // Best-effort flag; falling back to browser language is safe.
  }
}

/** Read the language this account explicitly chose on this browser, if any. */
async function readExplicitLanguage(): Promise<string | null> {
  try {
    const { useAuthStore } = await import('./auth');
    const userId = useAuthStore.getState().user?.id ?? 'anon';
    return localStorage.getItem(`${LANGUAGE_EXPLICIT_FLAG_PREFIX}:${userId}`);
  } catch {
    return null;
  }
}

function resolveBrowserLanguage(): string {
  return resolveSupportedLanguage(typeof navigator !== 'undefined' ? navigator.language : undefined);
}

const defaultSettings = {
  theme: 'system' as Theme,
  language: resolveBrowserLanguage(),
  startMinimized: false,
  shellAnimation: true,
  telemetryEnabled: true,
  gatewayAutoStart: true,
  gatewayPort: 18789,
  proxyEnabled: false,
  proxyServer: '',
  proxyHttpServer: '',
  proxyHttpsServer: '',
  proxyAllServer: '',
  proxyBypassRules: '<local>;localhost;127.0.0.1;::1',
  autoSyncSkills: true,
  sidebarCollapsed: false,
  sidebarWidth: 280,
  devModeUnlocked: false,
  initCompleted: false,
};

const clampSidebarWidth = (value: number) => Math.min(420, Math.max(220, Math.round(value)));

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set, get) => ({
      ...defaultSettings,

      init: async () => {
        const browserLanguage = resolveBrowserLanguage();
        try {
          const settings = await hostApi.settings.getAll();
          const resolvedLanguage = settings.language
            ? resolveSupportedLanguage(settings.language)
            : undefined;
          set((state) => ({
            ...state,
            ...settings,
            ...(resolvedLanguage ? { language: resolvedLanguage } : {}),
            ...(typeof settings.sidebarWidth === 'number'
              ? { sidebarWidth: clampSidebarWidth(settings.sidebarWidth) }
              : {}),
          }));
          // Language precedence: an explicit per-account choice wins; otherwise
          // the browser locale beats the server value — the server default is
          // derived from the HOST's system locale (usually `en` on Linux
          // servers), which would force English on new Chinese users.
          const explicitLanguage = await readExplicitLanguage();
          const effectiveLanguage = explicitLanguage
            ? resolveSupportedLanguage(explicitLanguage)
            : browserLanguage;
          set({ language: effectiveLanguage });
          i18n.changeLanguage(effectiveLanguage);
        } catch {
          // Keep renderer-persisted settings as a fallback when the main
          // process store is not reachable.
          if (!get().language) {
            i18n.changeLanguage(browserLanguage);
          }
        }
        set({ initCompleted: true });
      },

      setTheme: (theme) => {
        set({ theme });
        void hostApi.settings.set('theme', theme).catch(() => reportFailure('主题没有保存成功，请稍后再试。'));
      },
      setLanguage: (language) => {
        const resolvedLanguage = resolveSupportedLanguage(language);
        i18n.changeLanguage(resolvedLanguage);
        set({ language: resolvedLanguage });
        void markLanguageExplicit(resolvedLanguage);
        void hostApi.settings.set('language', resolvedLanguage).catch(() => reportFailure('语言没有保存成功，请稍后再试。'));
      },
      setStartMinimized: (startMinimized) => set({ startMinimized }),
      setShellAnimation: (shellAnimation) => {
        set({ shellAnimation });
        void hostApi.settings.set('shellAnimation', shellAnimation).catch(() => reportFailure('界面动画没有保存成功，请稍后再试。'));
      },
      setTelemetryEnabled: (telemetryEnabled) => {
        set({ telemetryEnabled });
        void hostApi.settings.set('telemetryEnabled', telemetryEnabled).catch(() => reportFailure('这项设置没有保存成功，请稍后再试。'));
      },
      setGatewayAutoStart: (gatewayAutoStart) => {
        set({ gatewayAutoStart });
        void hostApi.settings.set('gatewayAutoStart', gatewayAutoStart).catch(() => reportFailure('助手自动启动没有保存成功，请稍后再试。'));
      },
      setGatewayPort: (gatewayPort) => {
        set({ gatewayPort });
        void hostApi.settings.set('gatewayPort', gatewayPort).catch(() => reportFailure('端口没有保存成功，请稍后再试。'));
      },
      setProxyEnabled: (proxyEnabled) => set({ proxyEnabled }),
      setProxyServer: (proxyServer) => set({ proxyServer }),
      setProxyHttpServer: (proxyHttpServer) => set({ proxyHttpServer }),
      setProxyHttpsServer: (proxyHttpsServer) => set({ proxyHttpsServer }),
      setProxyAllServer: (proxyAllServer) => set({ proxyAllServer }),
      setProxyBypassRules: (proxyBypassRules) => set({ proxyBypassRules }),
      setAutoSyncSkills: (autoSyncSkills) => {
        set({ autoSyncSkills });
        void hostApi.settings.set('autoSyncSkills', autoSyncSkills).catch(() => reportFailure('技能同步没有保存成功，请稍后再试。'));
      },

      setSidebarCollapsed: (sidebarCollapsed) => set({ sidebarCollapsed }),
      setSidebarWidth: (sidebarWidth) => set({ sidebarWidth: clampSidebarWidth(sidebarWidth) }),
      setDevModeUnlocked: (devModeUnlocked) => {
        set({ devModeUnlocked });
        void hostApi.settings.set('devModeUnlocked', devModeUnlocked).catch(() => reportFailure('开发者模式没有保存成功，请稍后再试。'));
      },
      resetSettings: async () => {
        try {
          await hostApi.settings.reset();
        } catch {
          reportFailure('设置没有恢复成功，请稍后再试。');
        }
        set(defaultSettings);
      },
      resetForUserSwitch: () => {
        // Theme/language are per-account; drop them so the next account
        // starts from system/browser defaults and re-inits from its worker.
        set({
          theme: 'system',
          language: resolveBrowserLanguage(),
          initCompleted: false,
        });
      },
    }),
    {
      name: 'grandpoem-studio-settings',
    }
  )
);
