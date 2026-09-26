/**
 * Device shell detection.
 *
 * Decides whether the app renders the desktop shell (MainLayout) or the
 * mobile shell (MobileLayout). Priority:
 *   1. manual override persisted per-device in localStorage ("ui-shell")
 *   2. touch-device signals (Client Hints > UA > iPadOS quirk > coarse pointer)
 *   3. viewport width fallback (desktop browsers resized below the breakpoint)
 */
import { MOBILE_MEDIA_QUERY } from '@/hooks/use-is-mobile';

export type DeviceShell = 'desktop' | 'mobile';
export type ShellPreference = 'auto' | DeviceShell;

export const SHELL_STORAGE_KEY = 'ui-shell';
/** Fired on window whenever the manual preference changes (same-tab). */
export const SHELL_PREFERENCE_EVENT = 'ui-shell-preference-change';

/** Coarse-pointer devices narrower than this are treated as mobile. */
const COARSE_POINTER_MAX_WIDTH = 1024;

const MOBILE_UA_RE = /Android|iPhone|iPad|iPod|Mobile|windows phone/i;

interface NavigatorUAData {
  mobile?: boolean;
}

export function getShellPreference(): ShellPreference {
  try {
    const raw = window.localStorage.getItem(SHELL_STORAGE_KEY);
    if (raw === 'desktop' || raw === 'mobile') return raw;
  } catch {
    // storage unavailable (private mode / disabled) — fall back to auto
  }
  return 'auto';
}

export function setShellPreference(preference: ShellPreference): void {
  try {
    if (preference === 'auto') {
      window.localStorage.removeItem(SHELL_STORAGE_KEY);
    } else {
      window.localStorage.setItem(SHELL_STORAGE_KEY, preference);
    }
  } catch {
    // best effort — in-memory listeners still get the event below
  }
  window.dispatchEvent(new CustomEvent(SHELL_PREFERENCE_EVENT, { detail: preference }));
}

/** True when the hardware itself looks like a phone/tablet (not just a narrow window). */
export function isTouchDevice(): boolean {
  const nav = window.navigator;

  // 1. Client Hints — most reliable where supported (Chromium).
  const uaData = (nav as Navigator & { userAgentData?: NavigatorUAData }).userAgentData;
  if (uaData?.mobile === true) return true;

  // 2. Classic UA sniffing fallback.
  if (MOBILE_UA_RE.test(nav.userAgent)) return true;

  // 3. iPadOS 13+ masquerades as Macintosh but exposes multi-touch.
  if (nav.platform === 'MacIntel' && (nav.maxTouchPoints ?? 0) > 1) return true;

  // 4. Coarse primary pointer on a smallish screen (touch laptops excluded
  //    by the width guard — their primary pointer is fine anyway).
  if (
    typeof window.matchMedia === 'function'
    && window.matchMedia('(pointer: coarse)').matches
    && window.innerWidth < COARSE_POINTER_MAX_WIDTH
  ) {
    return true;
  }

  return false;
}

/** Resolve the shell for the current environment (override > device > viewport). */
export function resolveDeviceShell(): DeviceShell {
  const preference = getShellPreference();
  if (preference !== 'auto') return preference;

  if (isTouchDevice()) return 'mobile';

  // Pure desktop browser: follow the structural breakpoint so resizing
  // across it swaps shells live.
  if (typeof window.matchMedia === 'function' && window.matchMedia(MOBILE_MEDIA_QUERY).matches) {
    return 'mobile';
  }

  return 'desktop';
}

/** Mirror the active shell on <html data-shell> for scoped CSS overrides. */
export function applyShellToDocument(shell: DeviceShell): void {
  document.documentElement.dataset.shell = shell;
}
