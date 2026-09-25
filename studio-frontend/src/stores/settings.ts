/**
 * Settings State Store
 * Manages application settings
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { toast } from 'sonner';
import i18n from '@/i18n';
import { hostApi } from '@/lib/host-api';
import { toUserMessage } from '@/lib/error-message';
import { resolveSupportedLanguage } from '@shared/language';

type Theme = 'light' | 'dark' | 'system';
type UpdateChannel = 'stable' | 'beta' | 'dev';

interface SettingsState {
  // General
  theme: Theme;
  language: string;
  startMinimized: boolean;
  launchAtStartup: boolean;
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

  // Update
  updateChannel: UpdateChannel;
  autoCheckUpdate: boolean;

  // Skills
  autoSyncSkills: boolean;

  // UI State
  sidebarCollapsed: boolean;
  sidebarWidth: number;
  devModeUnlocked: boolean;

  /** Renderer finished the boot settings read. */
  initCompleted: boolean;

  // Actions
  init: () => Promise<void>;
  setTheme: (theme: Theme) => void;
  setLanguage: (language: string) => void;
  setStartMinimized: (value: boolean) => void;
  setLaunchAtStartup: (value: boolean) => void;
  setTelemetryEnabled: (value: boolean) => void;
  setGatewayAutoStart: (value: boolean) => void;
  setGatewayPort: (port: number) => void;
  setProxyEnabled: (value: boolean) => void;
  setProxyServer: (value: string) => void;
  setProxyHttpServer: (value: string) => void;
  setProxyHttpsServer: (value: string) => void;
  setProxyAllServer: (value: string) => void;
  setProxyBypassRules: (value: string) => void;
  setUpdateChannel: (channel: UpdateChannel) => void;
  setAutoCheckUpdate: (value: boolean) => void;
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
  launchAtStartup: false,
  telemetryEnabled: true,
  gatewayAutoStart: true,
  gatewayPort: 18789,
  proxyEnabled: false,
  proxyServer: '',
  proxyHttpServer: '',
  proxyHttpsServer: '',
  proxyAllServer: '',
  proxyBypassRules: '<local>;localhost;127.0.0.1;::1',
  updateChannel: 'stable' as UpdateChannel,
  autoCheckUpdate: true,
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
          set({ language: effectiveLanguage, initCompleted: true });
          i18n.changeLanguage(effectiveLanguage);
        } catch {
          // Keep renderer-persisted settings as a fallback when the main
          // process store is not reachable.
          if (!get().language) {
            i18n.changeLanguage(browserLanguage);
          }
          set({ initCompleted: true });
        }
      },

      setTheme: (theme) => {
        set({ theme });
        void hostApi.settings.set('theme', theme).catch((e) => toast.error(`Failed to save theme: ${toUserMessage(e)}`));
      },
      setLanguage: (language) => {
        const resolvedLanguage = resolveSupportedLanguage(language);
        i18n.changeLanguage(resolvedLanguage);
        set({ language: resolvedLanguage });
        void markLanguageExplicit(resolvedLanguage);
        void hostApi.settings.set('language', resolvedLanguage).catch((e) => toast.error(`Failed to save language: ${toUserMessage(e)}`));
      },
      setStartMinimized: (startMinimized) => set({ startMinimized }),
      setLaunchAtStartup: (launchAtStartup) => {
        set({ launchAtStartup });
        void hostApi.settings.set('launchAtStartup', launchAtStartup).catch((e) => toast.error(`Failed to save launch at startup: ${toUserMessage(e)}`));
      },
      setTelemetryEnabled: (telemetryEnabled) => {
        set({ telemetryEnabled });
        void hostApi.settings.set('telemetryEnabled', telemetryEnabled).catch((e) => toast.error(`Failed to save telemetry setting: ${toUserMessage(e)}`));
      },
      setGatewayAutoStart: (gatewayAutoStart) => {
        set({ gatewayAutoStart });
        void hostApi.settings.set('gatewayAutoStart', gatewayAutoStart).catch((e) => toast.error(`Failed to save gateway auto-start: ${toUserMessage(e)}`));
      },
      setGatewayPort: (gatewayPort) => {
        set({ gatewayPort });
        void hostApi.settings.set('gatewayPort', gatewayPort).catch((e) => toast.error(`Failed to save gateway port: ${toUserMessage(e)}`));
      },
      setProxyEnabled: (proxyEnabled) => set({ proxyEnabled }),
      setProxyServer: (proxyServer) => set({ proxyServer }),
      setProxyHttpServer: (proxyHttpServer) => set({ proxyHttpServer }),
      setProxyHttpsServer: (proxyHttpsServer) => set({ proxyHttpsServer }),
      setProxyAllServer: (proxyAllServer) => set({ proxyAllServer }),
      setProxyBypassRules: (proxyBypassRules) => set({ proxyBypassRules }),
      setUpdateChannel: (updateChannel) => set({ updateChannel }),
      setAutoCheckUpdate: (autoCheckUpdate) => {
        set({ autoCheckUpdate });
        void hostApi.settings.set('autoCheckUpdate', autoCheckUpdate).catch((e) => toast.error(`Failed to save auto-check update: ${toUserMessage(e)}`));
      },
      setAutoSyncSkills: (autoSyncSkills) => {
        set({ autoSyncSkills });
        void hostApi.settings.set('autoSyncSkills', autoSyncSkills).catch((e) => toast.error(`Failed to save auto-sync skills: ${toUserMessage(e)}`));
      },

      setSidebarCollapsed: (sidebarCollapsed) => set({ sidebarCollapsed }),
      setSidebarWidth: (sidebarWidth) => set({ sidebarWidth: clampSidebarWidth(sidebarWidth) }),
      setDevModeUnlocked: (devModeUnlocked) => {
        set({ devModeUnlocked });
        void hostApi.settings.set('devModeUnlocked', devModeUnlocked).catch((e) => toast.error(`Failed to save developer mode: ${toUserMessage(e)}`));
      },
      resetSettings: async () => {
        try {
          await hostApi.settings.reset();
        } catch (e) {
          toast.error(`Failed to reset settings on host: ${toUserMessage(e)}`);
        }
        set(defaultSettings);
      },
      resetForUserSwitch: () => {
        // Theme/language are per-account; drop them so the next account
        // starts from system/browser defaults and re-inits from its worker.
        set({
          theme: 'system',
          language: resolveBrowserLanguage(),
        });
      },
    }),
    {
      name: 'grandpoem-studio-settings',
    }
  )
);
