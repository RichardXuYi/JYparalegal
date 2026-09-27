import type { GatewayStatus } from '@/types/gateway';

export function isGatewayStopped(status: GatewayStatus): boolean {
  return status.state === 'stopped' || status.state === 'error' || status.state === 'failed';
}
