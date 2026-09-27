/**
 * Auth State Store
 *
 * Cross-platform backend login state for the Studio app. All backend HTTP
 * happens in the server main process; this store only talks to it through
 * `hostApi.auth.*` (invokeHost). Tokens live in the server, so this
 * store persists just the last-known user for an optimistic first paint and
 * always re-validates the session via `restore()` on boot.
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthUser } from '@shared/host-api/contract';
import { hostApi } from '@/lib/host-api';
import { resetAllUserStores } from '@/lib/user-session-reset';

type AuthStatus = 'idle' | 'restoring' | 'ready';

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  status: AuthStatus;
  error: string | null;
  currentDeviceId: number | null;

  restore: () => Promise<void>;
  login: (username: string, password: string, rememberMe?: boolean) => Promise<boolean>;
  logout: () => Promise<void>;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      status: 'idle',
      error: null,
      currentDeviceId: null,

      restore: async () => {
        set({ status: 'restoring' });
        try {
          const state = await hostApi.auth.me();
          set({
            user: state.user,
            isAuthenticated: state.isAuthenticated,
            status: 'ready',
          });
        } catch {
          // Main-process bridge unavailable: fall back to unauthenticated.
          set({ user: null, isAuthenticated: false, status: 'ready' });
        }
      },

      login: async (username, password, rememberMe) => {
        set({ error: null });
        // Wipe every per-account store BEFORE the login attempt: the login
        // page only renders when unauthenticated, so the previous account's
        // state is stale anyway — and a failed attempt must not leave it
        // visible after a later successful switch.
        resetAllUserStores();
        try {
          const result = await hostApi.auth.login(username, password, rememberMe);
          if (result.success && result.user) {
            set({
              user: result.user,
              isAuthenticated: true,
              status: 'ready',
              error: null,
              currentDeviceId: result.deviceId ?? null,
            });
            return true;
          }
          set({ error: result.error || '登录失败，请稍后重试' });
          return false;
        } catch (error) {
          set({ error: error instanceof Error ? error.message : '登录失败，请稍后重试' });
          return false;
        }
      },

      logout: async () => {
        // Drop the account's in-memory state first so nothing of it survives
        // into the next login.
        resetAllUserStores();
        try {
          await hostApi.auth.logout();
        } catch {
          // Ignore — always clear local state below.
        }
        // Safety net for the web build: the bridge also disconnects inside
        // its logout HTTP path, but if that request failed the socket must
        // still be dropped (idempotent on the desktop, where the global is
        // absent and this is a no-op).
        window.__grandpoemBridge?.disconnect();
        set({ user: null, isAuthenticated: false, error: null, currentDeviceId: null });
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'grandpoem-studio-auth',
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated, currentDeviceId: state.currentDeviceId }),
    }
  )
);
