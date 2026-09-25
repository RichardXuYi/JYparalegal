/**
 * Connection Status Reason Derivation
 * Pure helpers for the global connection-status modal so the decision logic can
 * be unit tested without rendering.
 */
import type { GatewayStatus } from '@/types/gateway';
import { isGatewayRestarting } from '@/lib/gateway-status';

export type ConnectionReason = 'reconnecting' | 'connecting' | null;

/**
 * Decide which (if any) blocking connection status should be surfaced.
 * A starting or reconnecting gateway shows the overlay. Stopped/error states
 * return `null` (handled by TopBar + the existing GatewayNotRunning UI).
 * Model switches are session RPC calls and do not use this overlay.
 */
export function deriveConnectionReason(params: {
  status: GatewayStatus;
}): ConnectionReason {
  const { status } = params;
  if (isGatewayRestarting(status)) {
    if (status.state === 'reconnecting' || (status.reconnectAttempts ?? 0) > 0) {
      return 'reconnecting';
    }
    return 'connecting';
  }

  return null;
}
