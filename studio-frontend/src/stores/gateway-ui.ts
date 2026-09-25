/**
 * Gateway UI State Store
 * Transient presentation state for the gateway connection surfaces (overlay
 * grace window, dismissals, terminal-failure dialog). Kept separate from the
 * data store so the gateway status store stays byte-shareable logic only.
 */
import { create } from 'zustand';
import type { GatewayStatus } from '@/types/gateway';

/** How long the blocking overlay may stay before it demotes to the banner. */
export const OVERLAY_GRACE_MS = 6_000;

interface GatewayUiState {
  /** User chose "continue in background" for the current overlay episode. */
  overlayDismissed: boolean;
  /** Grace window elapsed; the overlay demotes to the banner. */
  overlayGraceExpired: boolean;
  /** `detectedAt` of the failure episode whose dialog is currently open. */
  openFailureDetectedAt: number | null;
  /** `detectedAt` of failure episodes the user already closed the dialog on. */
  dismissedFailureDetectedAt: number | null;
  dismissOverlay: () => void;
  openFailureDialog: (detectedAt: number) => void;
  closeFailureDialog: (detectedAt: number | null) => void;
  /** React to a new gateway status; called from a single subscriber. */
  syncGatewayStatus: (status: GatewayStatus) => void;
  reset: () => void;
}

let overlayGraceTimer: ReturnType<typeof setTimeout> | null = null;

function clearOverlayGraceTimer(): void {
  if (overlayGraceTimer !== null) {
    clearTimeout(overlayGraceTimer);
    overlayGraceTimer = null;
  }
}

function failureKey(status: GatewayStatus): number {
  return status.failure?.detectedAt ?? 0;
}

export const useGatewayUiStore = create<GatewayUiState>((set, get) => ({
  overlayDismissed: false,
  overlayGraceExpired: false,
  openFailureDetectedAt: null,
  dismissedFailureDetectedAt: null,

  dismissOverlay: () => {
    clearOverlayGraceTimer();
    set({ overlayDismissed: true });
  },

  openFailureDialog: (detectedAt) => {
    set({ openFailureDetectedAt: detectedAt });
  },

  closeFailureDialog: (detectedAt) => {
    set({
      openFailureDetectedAt: null,
      ...(detectedAt !== null ? { dismissedFailureDetectedAt: detectedAt } : {}),
    });
  },

  syncGatewayStatus: (status) => {
    const { overlayGraceExpired, overlayDismissed, dismissedFailureDetectedAt } = get();

    if (status.state === 'running' || status.state === 'stopped') {
      clearOverlayGraceTimer();
      if (status.state === 'running') {
        set({ overlayDismissed: false, overlayGraceExpired: false, openFailureDetectedAt: null });
      } else {
        set({ overlayDismissed: false, overlayGraceExpired: false });
      }
      return;
    }

    if (status.state === 'starting' || status.state === 'reconnecting') {
      if (!overlayGraceExpired && !overlayDismissed && overlayGraceTimer === null) {
        overlayGraceTimer = setTimeout(() => {
          overlayGraceTimer = null;
          set({ overlayGraceExpired: true });
        }, OVERLAY_GRACE_MS);
      }
      return;
    }

    if (status.state === 'failed') {
      clearOverlayGraceTimer();
      const key = failureKey(status);
      if (dismissedFailureDetectedAt !== key) {
        set({ openFailureDetectedAt: key });
      }
    }
  },

  reset: () => {
    clearOverlayGraceTimer();
    set({
      overlayDismissed: false,
      overlayGraceExpired: false,
      openFailureDetectedAt: null,
      dismissedFailureDetectedAt: null,
    });
  },
}));
