/**
 * Gateway UI State Store
 * Transient presentation state for the gateway connection surfaces (terminal
 * failure dialog). Kept separate from the data store so the gateway status
 * store stays byte-shareable logic only.
 *
 * 启动等待不再有任何阻塞面：加载期由 `InitializingScreen` 全屏承载（见 App.tsx 的
 * 启动闸门），运行期波动走非阻塞横幅，所以这里只保留终态故障对话框的状态。
 */
import { create } from 'zustand';
import type { GatewayStatus } from '@/types/gateway';

interface GatewayUiState {
  /** `detectedAt` of the failure episode whose dialog is currently open. */
  openFailureDetectedAt: number | null;
  /** `detectedAt` of failure episodes the user already closed the dialog on. */
  dismissedFailureDetectedAt: number | null;
  openFailureDialog: (detectedAt: number) => void;
  closeFailureDialog: (detectedAt: number | null) => void;
  /** React to a new gateway status; called from a single subscriber. */
  syncGatewayStatus: (status: GatewayStatus) => void;
  reset: () => void;
}

function failureKey(status: GatewayStatus): number {
  return status.failure?.detectedAt ?? 0;
}

export const useGatewayUiStore = create<GatewayUiState>((set, get) => ({
  openFailureDetectedAt: null,
  dismissedFailureDetectedAt: null,

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
    const { dismissedFailureDetectedAt } = get();

    if (status.state === 'running') {
      set({ openFailureDetectedAt: null });
      return;
    }

    if (status.state === 'failed') {
      const key = failureKey(status);
      if (dismissedFailureDetectedAt !== key) {
        set({ openFailureDetectedAt: key });
      }
    }
  },

  reset: () => {
    set({
      openFailureDetectedAt: null,
      dismissedFailureDetectedAt: null,
    });
  },
}));
