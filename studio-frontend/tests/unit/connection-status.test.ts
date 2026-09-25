import { describe, expect, it } from 'vitest';
import { deriveGatewaySurface } from '../../src/lib/connection-status';
import type { GatewayFailureInfo, GatewayStatus } from '@/types/gateway';

const failure: GatewayFailureInfo = {
  code: 'state-migration-failed',
  tier: 'spawn',
  exitCode: 78,
  retryable: false,
  reasonKey: 'gateway.failure.stateMigration',
  reasonParams: { exitCode: 78 },
  suggestedActions: ['retry', 'viewLogs', 'copyReport', 'runDoctor'],
  detectedAt: 1_700_000_000_000,
};

function surface(
  status: Partial<GatewayStatus> & { state: GatewayStatus['state'] },
  params: { seen?: boolean; dismissed?: boolean; expired?: boolean } = {},
) {
  return deriveGatewaySurface({
    status: { port: 18789, ...status } as GatewayStatus,
    hasSeenRunningThisSession: params.seen ?? false,
    userDismissedOverlay: params.dismissed ?? false,
    overlayGraceExpired: params.expired ?? false,
  });
}

describe('deriveGatewaySurface', () => {
  it('shows the terminal dialog for a failed gateway carrying structured info', () => {
    expect(surface({ state: 'failed', failure })).toEqual({ kind: 'dialog', failure });
  });

  it('falls back to the banner when failed has no structured failure', () => {
    expect(surface({ state: 'failed' }).kind).toBe('banner');
  });

  it('never leaves error/failed as a silent nothing (the old dead end)', () => {
    expect(surface({ state: 'error' }).kind).toBe('banner');
    expect(surface({ state: 'error', failure }).kind).toBe('banner');
  });

  it('shows nothing for stopped (per-page UI) and healthy running', () => {
    expect(surface({ state: 'stopped' })).toBeNull();
    expect(surface({ state: 'running' })).toBeNull();
    expect(surface({ state: 'running', gatewayReady: true })).toBeNull();
  });

  it('degraded readiness (running but not ready) is a banner, never an overlay', () => {
    expect(surface({ state: 'running', gatewayReady: false }, { seen: true }).kind).toBe('banner');
    expect(surface({ state: 'running', gatewayReady: false }, { seen: false }).kind).toBe('banner');
  });

  it('overlays only before the session saw a running gateway', () => {
    expect(surface({ state: 'starting' }).kind).toBe('overlay');
    expect(surface({ state: 'reconnecting' }).kind).toBe('overlay');
    expect(surface({ state: 'starting' }, { seen: true }).kind).toBe('banner');
    expect(surface({ state: 'reconnecting' }, { seen: true }).kind).toBe('banner');
  });

  it('demotes the overlay to a banner once dismissed or past the grace window', () => {
    expect(surface({ state: 'starting' }, { dismissed: true }).kind).toBe('banner');
    expect(surface({ state: 'starting' }, { expired: true }).kind).toBe('banner');
  });

  it('carries reconnect progress fields on the banner for countdown rendering', () => {
    const result = surface(
      {
        state: 'reconnecting',
        reconnectAttempts: 3,
        reconnectMaxAttempts: 10,
        nextRetryAt: 1_700_000_005_000,
      },
      { seen: true },
    );
    expect(result).toMatchObject({
      kind: 'banner', attempt: 3, max: 10, nextRetryAt: 1_700_000_005_000,
    });
  });
});
