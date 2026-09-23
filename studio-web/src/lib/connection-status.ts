/**
 * Connection Status Reason Derivation
 * Pure helpers for the global connection-status modal so the decision logic can
 * be unit tested without rendering.
 */
import type { GatewayStatus } from '@/types/gateway';
import { isGatewayRestarting } from '@/lib/gateway-status';

export type ConnectionReason = 'model' | 'reconnecting' | 'connecting' | null;

/**
 * Decide which (if any) blocking connection status should be surfaced.
 * Priority: an in-flight model switch wins; otherwise a starting/reconnecting
 * gateway. Stopped/error states return `null` (handled by TopBar + the existing
 * GatewayNotRunning UI, not by this overlay).
 */
export function deriveConnectionReason(params: {
  status: GatewayStatus;
  modelSwitchActive: boolean;
}): ConnectionReason {
  if (params.modelSwitchActive) {
    return 'model';
  }

  const { status } = params;
  if (isGatewayRestarting(status)) {
    if (status.state === 'reconnecting' || (status.reconnectAttempts ?? 0) > 0) {
      return 'reconnecting';
    }
    return 'connecting';
  }

  return null;
}
