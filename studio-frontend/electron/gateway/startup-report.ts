/**
 * Gateway startup-failure report builder.
 *
 * Produces a redacted, shareable artifact when the gateway enters the terminal
 * `failed` state, per harness/specs/scenarios/gateway-startup-diagnostics.md:
 * it must state which readiness tier failed and must redact tokens / account
 * ids / device identities while keeping platform, versions, state dir, timings
 * and a stderr tail.
 *
 * Dependency-free (pure functions) so it can be unit-tested and mirrored
 * byte-identically into studio-web/server/host-core/gateway/.
 */
import type { GatewayFailureInfo, GatewayReadinessTier } from '@shared/types/gateway';

export interface GatewayStartupMetric {
  configSyncMs?: number;
  spawnToReadyMs?: number;
  readyToConnectMs?: number;
  totalMs?: number;
}

export interface GatewayStartupReportInput {
  platform: string;
  appVersion: string;
  openclawVersion: string;
  /** Scoped OpenClaw state dir. A path is safe to share; secrets are not. */
  stateDir: string;
  failure: GatewayFailureInfo;
  exitCode: number | null;
  stderrTail: string[];
  startupMetric: GatewayStartupMetric;
  readinessTiers: Partial<Record<GatewayReadinessTier, boolean>>;
  lastSpawnSummary?: string;
}

export interface GatewayStartupFailureReport {
  schemaVersion: 1;
  capturedAt: number;
  platform: string;
  appVersion: string;
  openclawVersion: string;
  stateDir: string;
  failure: GatewayFailureInfo;
  exitCode: number | null;
  stderrTail: string[];
  startupMetric: GatewayStartupMetric;
  readinessTiers: Partial<Record<GatewayReadinessTier, boolean>>;
  lastSpawnSummary?: string;
}

/** Upper bound on how many stderr lines we keep in a report. */
export const STARTUP_REPORT_STDERR_MAX_LINES = 120;

const REDACTION_PATTERNS: Array<[RegExp, string]> = [
  // --token <value> / token=<value> / "token":"<value>" (the optional quote
  // before the separator covers JSON-key form: key": "value")
  [/(--token\s+)[^\s"']+/gi, '$1[redacted]'],
  [/\btoken["']?\s*[:=]\s*["']?[A-Za-z0-9._-]{8,}["']?/gi, 'token=[redacted]'],
  [/\bBearer\s+[A-Za-z0-9._-]{8,}/gi, 'Bearer [redacted]'],
  [/\b(gatewayToken|apiKey|api_key|authorization)["']?\s*[:=]\s*["']?[^\s"',}]+["']?/gi, '$1=[redacted]'],
  [/\b(sk-[A-Za-z0-9]{8,})\b/g, '[redacted-key]'],
  // account / device identifiers
  [/\b(accountId|account_id|userId|user_id|deviceId|device_id)["']?\s*[:=]\s*["']?[A-Za-z0-9._-]{4,}["']?/gi, '$1=[redacted]'],
];

/**
 * Scrub secrets and identifiers from free-form text (stderr lines, spawn
 * summaries). Paths are intentionally preserved — they are needed to diagnose
 * state-dir problems and are not secret.
 */
export function redactReportText(text: string): string {
  return REDACTION_PATTERNS.reduce((acc, [pattern, replacement]) => acc.replace(pattern, replacement), text);
}

export function buildGatewayStartupFailureReport(
  input: GatewayStartupReportInput,
): GatewayStartupFailureReport {
  const stderrTail = input.stderrTail
    .slice(-STARTUP_REPORT_STDERR_MAX_LINES)
    .map((line) => redactReportText(line));
  return {
    schemaVersion: 1,
    capturedAt: Date.now(),
    platform: input.platform,
    appVersion: input.appVersion,
    openclawVersion: input.openclawVersion,
    stateDir: input.stateDir,
    failure: input.failure,
    exitCode: input.exitCode,
    stderrTail,
    startupMetric: input.startupMetric,
    readinessTiers: input.readinessTiers,
    lastSpawnSummary: input.lastSpawnSummary
      ? redactReportText(input.lastSpawnSummary)
      : undefined,
  };
}
