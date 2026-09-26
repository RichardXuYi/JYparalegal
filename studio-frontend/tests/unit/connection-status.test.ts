import { describe, expect, it } from 'vitest';
import { BOOT_SCREEN_CAP_MS, deriveGatewaySurface, shouldHoldBootScreen } from '../../src/lib/connection-status';
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

function status(partial: Partial<GatewayStatus> & { state: GatewayStatus['state'] }): GatewayStatus {
  return { port: 18789, ...partial } as GatewayStatus;
}

function surface(partial: Partial<GatewayStatus> & { state: GatewayStatus['state'] }) {
  return deriveGatewaySurface({ status: status(partial) });
}

function hold(
  partial: Partial<GatewayStatus> & { state: GatewayStatus['state'] },
  params: { seen?: boolean; cap?: boolean } = {},
) {
  return shouldHoldBootScreen({
    status: status(partial),
    hasSeenRunningThisSession: params.seen ?? false,
    capElapsed: params.cap ?? false,
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

  it('degraded readiness (running but not ready) is a banner', () => {
    expect(surface({ state: 'running', gatewayReady: false }).kind).toBe('banner');
  });

  it('starting/reconnecting are non-blocking banners (the boot screen is the only loading surface)', () => {
    expect(surface({ state: 'starting' }).kind).toBe('banner');
    expect(surface({ state: 'reconnecting' }).kind).toBe('banner');
  });

  it('carries reconnect progress fields on the banner for countdown rendering', () => {
    const result = surface({
      state: 'reconnecting',
      reconnectAttempts: 3,
      reconnectMaxAttempts: 10,
      nextRetryAt: 1_700_000_005_000,
    });
    expect(result).toMatchObject({
      kind: 'banner', attempt: 3, max: 10, nextRetryAt: 1_700_000_005_000,
    });
  });
});

describe('shouldHoldBootScreen', () => {
  it('holds the loading screen while the gateway has never run and is still coming up', () => {
    expect(hold({ state: 'starting' })).toBe(true);
    expect(hold({ state: 'reconnecting' })).toBe(true);
  });

  it('releases once this session has seen the gateway running (later dips are runtime, not boot)', () => {
    expect(hold({ state: 'starting' }, { seen: true })).toBe(false);
    expect(hold({ state: 'reconnecting' }, { seen: true })).toBe(false);
  });

  it('releases for terminal or idle states so the failure dialog/banner can speak', () => {
    expect(hold({ state: 'failed', failure })).toBe(false);
    expect(hold({ state: 'error' })).toBe(false);
    expect(hold({ state: 'stopped' })).toBe(false);
    expect(hold({ state: 'running' })).toBe(false);
  });

  it('releases unconditionally past the cap so a silent gateway cannot pin the loading screen', () => {
    expect(hold({ state: 'starting' }, { cap: true })).toBe(false);
    expect(BOOT_SCREEN_CAP_MS).toBeGreaterThan(0);
  });
});
