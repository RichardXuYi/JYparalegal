/**
 * Connection Status UI Store
 * Transient (non-persisted) state for the global connection-status modal.
 * Currently tracks the model-switch progress so a global overlay can react to
 * it; gateway connect/reconnect state is read directly from the gateway store.
 */
import { create } from 'zustand';

interface ConnectionStatusState {
  modelSwitch: { active: boolean; modelLabel: string | null };
  beginModelSwitch: (label: string) => void;
  endModelSwitch: () => void;
}

export const useConnectionStatusStore = create<ConnectionStatusState>()((set) => ({
  modelSwitch: { active: false, modelLabel: null },
  beginModelSwitch: (label) => set({ modelSwitch: { active: true, modelLabel: label } }),
  endModelSwitch: () => set({ modelSwitch: { active: false, modelLabel: null } }),
}));
