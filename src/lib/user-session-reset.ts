/**
 * User Session Reset
 *
 * Central orchestrator for account switches: when the signed-in account
 * changes (login/logout/SSO), every per-account store and cache must be
 * dropped so the next user never sees the previous user's conversations,
 * settings, skills, agents, cron jobs, channels or provider configs.
 */
import { useChatStore } from '@/stores/chat';
import { useGatewayStore } from '@/stores/gateway';
import { useSkillsStore } from '@/stores/skills';
import { useAgentsStore } from '@/stores/agents';
import { useCronStore } from '@/stores/cron';
import { useChannelsStore } from '@/stores/channels';
import { useArtifactPanel } from '@/stores/artifact-panel';
import { useProviderStore } from '@/stores/providers';
import { useSettingsStore } from '@/stores/settings';
import { DELETED_SESSIONS_STORAGE_KEY } from '@/stores/chat/deleted-sessions';

/** localStorage keys holding per-account data that must not leak across users. */
const PER_USER_LOCALSTORAGE_KEYS = [
  'studio-jwt',
  'grandpoem-studio-last-session',
  'grandpoem-studio:image-cache',
  'grandpoem-studio.artifact-panel',
  DELETED_SESSIONS_STORAGE_KEY,
];

declare global {
  interface Window {
    __grandpoemBridge?: {
      disconnect: () => void;
      reconnect: () => void;
    };
  }
}

/** Drop every per-account in-memory state and per-user localStorage keys. */
export function resetAllUserStores(): void {
  useChatStore.getState().resetForUserSwitch();
  useGatewayStore.getState().resetForUserSwitch();
  useSkillsStore.getState().resetForUserSwitch();
  useAgentsStore.getState().resetForUserSwitch();
  useCronStore.getState().resetForUserSwitch();
  useChannelsStore.getState().resetForUserSwitch();
  useArtifactPanel.getState().resetForUserSwitch();
  useProviderStore.getState().resetForUserSwitch();
  useSettingsStore.getState().resetForUserSwitch();

  try {
    for (const key of PER_USER_LOCALSTORAGE_KEYS) {
      localStorage.removeItem(key);
    }
  } catch {
    // Best-effort — private mode / quota failures must not break the switch.
  }
}
