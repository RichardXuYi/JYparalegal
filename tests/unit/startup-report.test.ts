import { describe, expect, it } from 'vitest';
import {
  STARTUP_REPORT_STDERR_MAX_LINES,
  buildGatewayStartupFailureReport,
  redactReportText,
} from '../../electron/gateway/startup-report';
import type { GatewayFailureInfo } from '@shared/types/gateway';

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

function buildReport(stderrTail: string[], lastSpawnSummary?: string) {
  return buildGatewayStartupFailureReport({
    platform: 'win32',
    appVersion: '1.5.0',
    openclawVersion: '2026.9.6',
    stateDir: 'C:\\Users\\richa\\AppData\\Roaming\\JYStudio\\openclaw-96',
    failure,
    exitCode: 78,
    stderrTail,
    startupMetric: { configSyncMs: 120, totalMs: 900 },
    readinessTiers: { spawn: false, port: true },
    lastSpawnSummary,
  });
}

describe('redactReportText', () => {
  it('scrubs gateway tokens, bearer values, api keys and identities but preserves paths', () => {
    const redacted = redactReportText(
      'gateway --port 18789 --token supersecret123 C:\\Users\\richa\\state\n'
      + 'Authorization: Bearer abcdef.ghijkl-mnop\n'
      + 'apiKey="sk-ABCDEFGH12345678"\n'
      + 'accountId: 9f8e7d6c deviceId=dev-abc123',
    );
    expect(redacted).not.toContain('supersecret123');
    expect(redacted).not.toContain('abcdef.ghijkl-mnop');
    expect(redacted).not.toContain('sk-ABCDEFGH12345678');
    expect(redacted).not.toContain('9f8e7d6c');
    expect(redacted).not.toContain('dev-abc123');
    expect(redacted).toContain('C:\\Users\\richa\\state');
  });

  it('redacts the JSON "token" form used in gateway config dumps', () => {
    const redacted = redactReportText('{"gatewayToken":"deadbeefcafe01"}');
    expect(redacted).not.toContain('deadbeefcafe01');
  });
});

describe('buildGatewayStartupFailureReport', () => {
  it('caps the stderr tail and redacts each kept line', () => {
    const lines = Array.from({ length: STARTUP_REPORT_STDERR_MAX_LINES + 40 }, (_, i) =>
      i === 0 ? 'oldest line --token leakme12345678' : `line ${i}`);
    const report = buildReport(lines);
    expect(report.stderrTail.length).toBe(STARTUP_REPORT_STDERR_MAX_LINES);
    expect(report.stderrTail.some((line) => line.includes('leakme12345678'))).toBe(false);
    expect(report.stderrTail[report.stderrTail.length - 1]).toBe(
      `line ${STARTUP_REPORT_STDERR_MAX_LINES + 39}`,
    );
  });

  it('keeps the diagnostic frame: version, state dir, failure, tiers and timings', () => {
    const report = buildReport(['migration refused: EPERM'], 'mode=dev, entry="openclaw.mjs", args="gateway --token topsecret99"');
    expect(report.schemaVersion).toBe(1);
    expect(report.platform).toBe('win32');
    expect(report.openclawVersion).toBe('2026.9.6');
    expect(report.stateDir).toContain('openclaw-96');
    expect(report.failure.code).toBe('state-migration-failed');
    expect(report.readinessTiers).toEqual({ spawn: false, port: true });
    expect(report.startupMetric.totalMs).toBe(900);
    expect(report.lastSpawnSummary).toBeDefined();
    expect(report.lastSpawnSummary).not.toContain('topsecret99');
  });
});
