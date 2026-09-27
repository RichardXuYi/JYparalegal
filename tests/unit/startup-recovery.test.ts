import { describe, expect, it } from 'vitest';
import {
  getGatewayStartupRecoveryAction,
  isTransientGatewayStartError,
} from '../../electron/gateway/startup-recovery';

function recovery(overrides: Partial<Parameters<typeof getGatewayStartupRecoveryAction>[0]> = {}) {
  return getGatewayStartupRecoveryAction({
    startupError: new Error('boom'),
    startupStderrLines: [],
    configRepairAttempted: false,
    attempt: 1,
    maxAttempts: 3,
    ...overrides,
  });
}

describe('getGatewayStartupRecoveryAction', () => {
  it('fails on the first attempt for a deterministic exit-78 migration refusal (no 3x10 burn)', () => {
    expect(recovery({
      exitCode: 78,
      startupStderrLines: ['[gateway] startup migrations did not complete cleanly'],
      attempt: 1,
    })).toBe('fail');
  });

  it('prefers the one-shot config repair before failing an invalid-config boot', () => {
    expect(recovery({
      startupError: new Error('invalid config: unrecognized key'),
      startupStderrLines: ['run: openclaw doctor --fix'],
    })).toBe('repair');
    // Repair already tried ⇒ no second repair, and the taxonomy makes it terminal.
    expect(recovery({
      startupError: new Error('invalid config: unrecognized key'),
      startupStderrLines: ['run: openclaw doctor --fix'],
      configRepairAttempted: true,
    })).toBe('fail');
  });

  it('keeps a generic early exit transient and retries within the budget', () => {
    expect(isTransientGatewayStartError(
      new Error('Gateway process exited before becoming ready (code=1)'),
    )).toBe(true);
    expect(recovery({
      startupError: new Error('Gateway process exited before becoming ready (code=1)'),
      attempt: 1,
      maxAttempts: 3,
    })).toBe('retry');
    // Budget exhausted ⇒ fail even for a transient class.
    expect(recovery({
      startupError: new Error('Gateway process exited before becoming ready (code=1)'),
      attempt: 3,
      maxAttempts: 3,
    })).toBe('fail');
  });

  it('fails immediately for an exhausted ready-poll', () => {
    expect(recovery({
      startupError: new Error('Gateway not ready in time'),
      readyPollExhausted: true,
      phase: 'wait-ready',
    })).toBe('fail');
  });

  it('fails closed for unclassified startup errors', () => {
    expect(recovery({ startupError: new Error('???'), attempt: 1, maxAttempts: 3 })).toBe('fail');
  });
});
