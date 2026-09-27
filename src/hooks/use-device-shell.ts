/**
 * useDeviceShell Hook (web-only)
 *
 * Reactive wrapper around resolveDeviceShell(): re-evaluates on viewport
 * breakpoint changes, storage changes (other tabs) and the manual preference
 * event (same tab), and mirrors the result on <html data-shell>.
 */
import { useCallback, useEffect, useSyncExternalStore } from 'react';
import {
  applyShellToDocument,
  getShellPreference,
  resolveDeviceShell,
  SHELL_PREFERENCE_EVENT,
  SHELL_STORAGE_KEY,
  type DeviceShell,
  type ShellPreference,
} from '@/lib/device-shell';
import { MOBILE_MEDIA_QUERY } from '@/hooks/use-is-mobile';

function subscribe(onStoreChange: () => void): () => void {
  const mql = window.matchMedia(MOBILE_MEDIA_QUERY);
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === SHELL_STORAGE_KEY) onStoreChange();
  };
  mql.addEventListener('change', onStoreChange);
  window.addEventListener(SHELL_PREFERENCE_EVENT, onStoreChange);
  window.addEventListener('storage', onStorage);
  return () => {
    mql.removeEventListener('change', onStoreChange);
    window.removeEventListener(SHELL_PREFERENCE_EVENT, onStoreChange);
    window.removeEventListener('storage', onStorage);
  };
}

export function useDeviceShell(): DeviceShell {
  const getSnapshot = useCallback(() => resolveDeviceShell(), []);
  const shell = useSyncExternalStore(subscribe, getSnapshot, () => 'desktop' as DeviceShell);

  // Keep <html data-shell> in sync for the scoped mobile CSS overrides.
  useEffect(() => {
    applyShellToDocument(shell);
  }, [shell]);

  return shell;
}

/** Reactive read of the manual shell preference (for the settings picker). */
export function useShellPreference(): ShellPreference {
  const getSnapshot = useCallback(() => getShellPreference(), []);
  return useSyncExternalStore(subscribe, getSnapshot, () => 'auto' as ShellPreference);
}
