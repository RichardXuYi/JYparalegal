/**
 * Settings UI State Store
 * Transient (non-persisted) state for the settings modal:
 * whether it is open and which section is active.
 */
import { create } from 'zustand';

export type SettingsSection =
  | 'models'
  | 'agents'
  | 'channels'
  | 'skills'
  | 'cron'
  | 'imageGeneration'
  | 'dreams'
  | 'general'
  | 'gateway'
  | 'developer'
  | 'about';

/** Default section shown when the gear icon is clicked. */
export const DEFAULT_SETTINGS_SECTION: SettingsSection = 'general';

/** Sections that are only available when developer mode is unlocked. */
export const DEV_ONLY_SETTINGS_SECTIONS: ReadonlySet<SettingsSection> = new Set<SettingsSection>([
  'imageGeneration',
  'dreams',
  'developer',
]);

interface SettingsUiState {
  open: boolean;
  section: SettingsSection;
  openSettings: (section?: SettingsSection) => void;
  closeSettings: () => void;
  setSection: (section: SettingsSection) => void;
}

export const useSettingsUiStore = create<SettingsUiState>()((set) => ({
  open: false,
  section: DEFAULT_SETTINGS_SECTION,
  openSettings: (section) =>
    set({ open: true, section: section ?? DEFAULT_SETTINGS_SECTION }),
  closeSettings: () => set({ open: false }),
  setSection: (section) => set({ section }),
}));
