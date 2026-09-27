import { describe, expect, it } from 'vitest';
import {
  DEFAULT_RECONNECT_CONFIG,
  getDeferredRestartAction,
  getReconnectScheduleDecision,
  getReconnectSkipReason,
  shouldDeferRestart,
} from '../../electron/gateway/process-policy';

describe('getReconnectScheduleDecision', () => {
  it('skips when auto-reconnect is disabled (terminal failed sets this)', () => {
    const decision = getReconnectScheduleDecision({
      shouldReconnect: false,
      hasReconnectTimer: false,
      reconnectAttempts: 0,
      ...DEFAULT_RECONNECT_CONFIG,
    });
    expect(decision.action).toBe('skip');
  });

  it('schedules exponential backoff capped at maxDelay', () => {
    const base = {
      shouldReconnect: true,
      hasReconnectTimer: false,
      ...DEFAULT_RECONNECT_CONFIG,
    };
    expect(getReconnectScheduleDecision({ ...base, reconnectAttempts: 0 })).toEqual({
      action: 'schedule', nextAttempt: 1, maxAttempts: 10, delay: 1000,
    });
    expect(getReconnectScheduleDecision({ ...base, reconnectAttempts: 3 })).toMatchObject({
      action: 'schedule', nextAttempt: 4, delay: 8000,
    });
    expect(getReconnectScheduleDecision({ ...base, reconnectAttempts: 6 })).toMatchObject({
      action: 'schedule', delay: 30000,
    });
  });

  it('fails once the attempt budget is exhausted', () => {
    const decision = getReconnectScheduleDecision({
      shouldReconnect: true,
      hasReconnectTimer: false,
      reconnectAttempts: DEFAULT_RECONNECT_CONFIG.maxAttempts,
      ...DEFAULT_RECONNECT_CONFIG,
    });
    expect(decision).toMatchObject({ action: 'fail', attempts: 10, maxAttempts: 10 });
  });
});

describe('getReconnectSkipReason', () => {
  it('ignores stale callbacks after an epoch bump', () => {
    expect(getReconnectSkipReason({
      scheduledEpoch: 3, currentEpoch: 4, shouldReconnect: true,
    })).toContain('stale reconnect callback');
  });
});

describe('terminal failed state transitions', () => {
  it('defers restarts only mid-start/reconnect', () => {
    expect(shouldDeferRestart({ state: 'failed', startLock: false })).toBe(false);
    expect(shouldDeferRestart({ state: 'starting', startLock: false })).toBe(true);
    expect(shouldDeferRestart({ state: 'running', startLock: true })).toBe(true);
  });

  it('executes a deferred explicit restart even though failed has shouldReconnect=false', () => {
    expect(getDeferredRestartAction({
      state: 'failed', startLock: false, hasPendingRestart: true, shouldReconnect: false,
    })).toBe('execute');
  });

  it('drops a deferred restart for a non-terminal shouldReconnect=false state', () => {
    expect(getDeferredRestartAction({
      state: 'stopped', startLock: false, hasPendingRestart: true, shouldReconnect: false,
    })).toBe('drop');
  });

  it('waits while another flow is in flight', () => {
    expect(getDeferredRestartAction({
      state: 'reconnecting', startLock: false, hasPendingRestart: true, shouldReconnect: true,
    })).toBe('wait');
  });

  it('does nothing without a pending request', () => {
    expect(getDeferredRestartAction({
      state: 'failed', startLock: false, hasPendingRestart: false, shouldReconnect: false,
    })).toBe('none');
  });
});
