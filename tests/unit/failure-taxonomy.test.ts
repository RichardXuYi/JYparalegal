import { describe, expect, it } from 'vitest';
import {
  EXIT_CODE_STATE_MIGRATION,
  buildMaxReconnectsExhaustedFailure,
  classifyGatewayStartupFailure,
  isDeterministicGatewayStartupFailure,
  type GatewayStartupFailureInput,
} from '../../electron/gateway/failure-taxonomy';

function input(overrides: Partial<GatewayStartupFailureInput> = {}): GatewayStartupFailureInput {
  return {
    error: new Error('boom'),
    exitCode: null,
    stderrLines: [],
    configRepairAttempted: false,
    phase: 'spawn',
    ...overrides,
  };
}

describe('classifyGatewayStartupFailure', () => {
  it('classifies exit 78 as a deterministic state-migration failure at tier spawn', () => {
    const result = classifyGatewayStartupFailure(input({ exitCode: EXIT_CODE_STATE_MIGRATION }));
    expect(result.deterministic).toBe(true);
    expect(result.info.code).toBe('state-migration-failed');
    expect(result.info.tier).toBe('spawn');
    expect(result.info.retryable).toBe(false);
    expect(result.info.reasonKey).toBe('gateway.failure.stateMigration');
    expect(result.info.reasonParams).toEqual({ exitCode: 78 });
    expect(result.info.suggestedActions).toEqual(['retry', 'viewLogs', 'copyReport', 'runDoctor']);
  });

  it('classifies the migration-refusal stderr signature even without the exit code', () => {
    const result = classifyGatewayStartupFailure(input({
      error: new Error('Gateway startup aborted'),
      stderrLines: ['[gateway] startup migrations did not complete cleanly; refusing to report the gateway ready'],
    }));
    expect(result.deterministic).toBe(true);
    expect(result.info.code).toBe('state-migration-failed');
  });

  it('classifies invalid config after a repair attempt as deterministic at tier config', () => {
    const result = classifyGatewayStartupFailure(input({
      error: new Error('invalid config: unrecognized key "foo"'),
      invalidConfigSignal: true,
      configRepairAttempted: true,
    }));
    expect(result.deterministic).toBe(true);
    expect(result.info.code).toBe('invalid-config');
    expect(result.info.tier).toBe('config');
  });

  it('escalates "port still occupied" from transient to deterministic on a second consecutive flow', () => {
    const first = classifyGatewayStartupFailure(input({
      error: new Error('Port 18789 still occupied'),
      portOccupiedStreak: 1,
    }));
    expect(first.deterministic).toBe(false);
    expect(first.info.code).toBe('port-occupied');
    expect(first.info.tier).toBe('port');
    expect(first.info.retryable).toBe(true);

    const second = classifyGatewayStartupFailure(input({
      error: new Error('Port 18789 still occupied'),
      portOccupiedStreak: 2,
    }));
    expect(second.deterministic).toBe(true);
    expect(second.info.code).toBe('port-occupied');
  });

  it('keeps handshake-shaped startup errors transient', () => {
    for (const message of [
      'Error: connect ECONNREFUSED 127.0.0.1:18789',
      'Error: Gateway process exited before becoming ready (code=1)',
      'Error: gateway starting, retry later',
    ]) {
      const result = classifyGatewayStartupFailure(input({ error: new Error(message) }));
      expect(result.deterministic, message).toBe(false);
      expect(result.info.retryable, message).toBe(true);
    }
  });

  it('treats an exhausted ready-poll as a deterministic startup-timeout', () => {
    const result = classifyGatewayStartupFailure(input({
      error: new Error('Gateway not ready in time'),
      readyPollExhausted: true,
      phase: 'wait-ready',
    }));
    expect(result.deterministic).toBe(true);
    expect(result.info.code).toBe('startup-timeout');
  });

  it('treats a plain non-zero exit without signatures as transient spawn-failed', () => {
    const result = classifyGatewayStartupFailure(input({
      error: new Error('child died'),
      exitCode: 1,
    }));
    expect(result.deterministic).toBe(false);
    expect(result.info.code).toBe('spawn-failed');
    expect(result.info.reasonParams).toEqual({ exitCode: 1 });
  });

  it('fails closed into deterministic unknown for anything else', () => {
    const result = classifyGatewayStartupFailure(input({ error: new Error('???') }));
    expect(result.deterministic).toBe(true);
    expect(result.info.code).toBe('unknown');
  });

  it('marks a wait-port unknown failure at tier port', () => {
    const result = classifyGatewayStartupFailure(input({
      error: new Error('???'),
      phase: 'wait-port',
    }));
    expect(result.info.tier).toBe('port');
  });
});

describe('buildMaxReconnectsExhaustedFailure', () => {
  it('produces a terminal handshake-tier failure carrying the attempt budget', () => {
    const info = buildMaxReconnectsExhaustedFailure(10);
    expect(info.code).toBe('max-reconnects-exhausted');
    expect(info.tier).toBe('handshake');
    expect(info.retryable).toBe(false);
    expect(info.reasonParams).toEqual({ attempts: 10 });
    expect(info.reasonKey).toBe('gateway.failure.maxReconnects');
    expect(info.detectedAt).toBeGreaterThan(0);
  });
});

describe('isDeterministicGatewayStartupFailure', () => {
  it('mirrors the classification flag', () => {
    expect(isDeterministicGatewayStartupFailure(input({ exitCode: 78 }))).toBe(true);
    expect(isDeterministicGatewayStartupFailure(input({
      error: new Error('connect ECONNREFUSED'),
    }))).toBe(false);
  });
});
