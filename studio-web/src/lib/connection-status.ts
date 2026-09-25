/**
 * Gateway Surface Derivation
 * Pure decision logic mapping GatewayStatus to the connection UI surface that
 * should be shown, so the rules can be unit tested without rendering.
 */
import type { GatewayFailureInfo, GatewayStatus } from '@/types/gateway';

export type GatewaySurface =
  | { kind: 'overlay' }
  | { kind: 'banner'; attempt?: number; max?: number; nextRetryAt?: number; failure?: GatewayFailureInfo | null }
  | { kind: 'dialog'; failure: GatewayFailureInfo }
  | null;

/**
 * Decide which surface a gateway status maps to:
 * - `failed` with a structured failure → terminal dialog (banner is rendered
 *   underneath by the layout while the dialog is showing).
 * - `failed` without failure info / `error` → non-blocking banner.
 * - `stopped` → nothing global (per-page GatewayNotRunning UI handles it).
 * - `starting`/`reconnecting` before this session has seen a running gateway →
 *   blocking overlay until it is dismissed or the grace window expires, then
 *   it demotes to the banner. Once the session has seen the gateway running,
 *   transient dips never block: banner only.
 */
export function deriveGatewaySurface(params: {
  status: GatewayStatus;
  hasSeenRunningThisSession: boolean;
  userDismissedOverlay: boolean;
  overlayGraceExpired: boolean;
}): GatewaySurface {
  const { status, hasSeenRunningThisSession, userDismissedOverlay, overlayGraceExpired } = params;

  if (status.state === 'failed') {
    return status.failure
      ? { kind: 'dialog', failure: status.failure }
      : { kind: 'banner' };
  }

  if (status.state === 'error') {
    return { kind: 'banner', failure: status.failure ?? null };
  }

  if (status.state === 'stopped') {
    return null;
  }

  const banner: GatewaySurface = {
    kind: 'banner',
    attempt: status.reconnectAttempts,
    max: status.reconnectMaxAttempts,
    nextRetryAt: status.nextRetryAt,
    failure: status.failure ?? null,
  };

  if (status.state === 'running') {
    // Process alive but not ready → degraded banner; ready → nothing to show.
    return status.gatewayReady === false ? banner : null;
  }

  // starting | reconnecting
  if (hasSeenRunningThisSession) return banner;
  if (userDismissedOverlay || overlayGraceExpired) return banner;
  return { kind: 'overlay' };
}
