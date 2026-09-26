/**
 * Gateway startup-failure taxonomy.
 *
 * Classifies a gateway boot/lifecycle failure into a structured, i18n-ready
 * {@link GatewayFailureInfo} and decides whether it is *deterministic*
 * (retrying cannot help — e.g. openclaw's legacy workspace-state migration
 * refusing on Windows with exit 78) or *transient* (worth the bounded
 * reconnect/backoff loop).
 *
 * This module is intentionally dependency-free at runtime (type-only imports)
 * so it can be unit-tested without Electron/runtime mocks, and so it can be
 * mirrored byte-identically into studio-web/server/host-core/gateway/.
 */
import type {
  GatewayFailureCode,
  GatewayFailureInfo,
  GatewayReadinessTier,
  GatewaySuggestedAction,
} from '@shared/types/gateway';

/** openclaw exits with this code when startup migrations refuse to complete. */
export const EXIT_CODE_STATE_MIGRATION = 78;

const STATE_MIGRATION_PATTERNS: RegExp[] = [
  /startup migrations did not complete/i,
  /refusing to report the gateway ready/i,
];

const PORT_OCCUPIED_PATTERN = /Port \d+ still occupied/i;

const TRANSIENT_HANDSHAKE_PATTERNS: RegExp[] = [
  /WebSocket closed before handshake/i,
  /ECONNREFUSED/i,
  /Timed out waiting for connect\.challenge/i,
  /Connect handshake timeout/i,
  /gateway starting/i,
  // A plain early exit before ready can be a genuine race; keep it transient
  // unless a deterministic signature (exit 78 etc.) says otherwise.
  /Gateway process exited before becoming ready/i,
];

const TERMINAL_ACTIONS: GatewaySuggestedAction[] = ['retry', 'viewLogs', 'copyReport', 'runDoctor'];
const TRANSIENT_ACTIONS: GatewaySuggestedAction[] = ['retry', 'viewLogs'];

const REASON_KEY_BY_CODE: Record<GatewayFailureCode, string> = {
  'state-migration-failed': 'gateway.failure.stateMigration',
  'invalid-config': 'gateway.failure.invalidConfig',
  'port-occupied': 'gateway.failure.portOccupied',
  'spawn-failed': 'gateway.failure.spawnFailed',
  'startup-timeout': 'gateway.failure.startupTimeout',
  'max-reconnects-exhausted': 'gateway.failure.maxReconnects',
  unknown: 'gateway.failure.unknown',
};

export interface GatewayStartupFailureInput {
  /** The thrown startup error (message text is matched as a last resort). */
  error: unknown;
  /** Child exit code when the gateway process died (null if alive/unknown). */
  exitCode: number | null;
  /** Recent gateway stderr ring-buffer lines. */
  stderrLines: string[];
  /** Whether the one-shot `openclaw doctor --fix` repair already ran this flow. */
  configRepairAttempted: boolean;
  /** Precomputed invalid-config signal (see startup-recovery.hasInvalidConfigFailureSignal). */
  invalidConfigSignal?: boolean;
  /** True when the ready-poll budget was exhausted while the process stayed alive. */
  readyPollExhausted?: boolean;
  /** Consecutive start-flows that hit "port still occupied" (for promotion). */
  portOccupiedStreak?: number;
  /** Which readiness phase failed, per the startup-diagnostics spec. */
  phase: 'spawn' | 'wait-port' | 'wait-ready' | 'connect';
}

export interface GatewayStartupFailureClassification {
  info: GatewayFailureInfo;
  /** true ⇒ retrying cannot help; the manager must enter the terminal state. */
  deterministic: boolean;
}

function errorText(error: unknown): string {
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error ?? '');
}

function matchesAny(text: string, patterns: RegExp[]): boolean {
  return patterns.some((pattern) => pattern.test(text));
}

function makeInfo(
  code: GatewayFailureCode,
  tier: GatewayReadinessTier,
  retryable: boolean,
  exitCode: number | null,
  reasonParams?: Record<string, string | number>,
): GatewayFailureInfo {
  return {
    code,
    tier,
    exitCode,
    retryable,
    reasonKey: REASON_KEY_BY_CODE[code],
    reasonParams,
    suggestedActions: retryable ? TRANSIENT_ACTIONS : TERMINAL_ACTIONS,
    detectedAt: Date.now(),
  };
}

/**
 * Failure info for the reconnect-budget-exhausted terminal transition.
 * Not a startup classification; exported so the manager's reconnect fail-branch
 * and this module share one construction site.
 */
export function buildMaxReconnectsExhaustedFailure(attempts: number): GatewayFailureInfo {
  return makeInfo('max-reconnects-exhausted', 'handshake', false, null, { attempts });
}

/**
 * Classify a gateway startup failure.
 *
 * Order matters: deterministic signatures are checked first so a deterministic
 * boot failure never burns the transient retry budget.
 */
export function classifyGatewayStartupFailure(
  input: GatewayStartupFailureInput,
): GatewayStartupFailureClassification {
  const combined = `${errorText(input.error)}\n${input.stderrLines.join('\n')}`;
  const exitCode = input.exitCode;

  // 1. Legacy state-dir migration refusal (openclaw 9.6 on Windows: directory
  //    fsync → EPERM → "migrations did not complete cleanly" → exit 78).
  if (exitCode === EXIT_CODE_STATE_MIGRATION || matchesAny(combined, STATE_MIGRATION_PATTERNS)) {
    return {
      deterministic: true,
      info: makeInfo(
        'state-migration-failed',
        'spawn',
        false,
        exitCode,
        { exitCode: exitCode ?? EXIT_CODE_STATE_MIGRATION },
      ),
    };
  }

  // 2. Config validation refusal after the one-shot repair was already tried
  //    (or the signal is present and repair is not going to help).
  if (input.invalidConfigSignal && input.configRepairAttempted) {
    return {
      deterministic: true,
      info: makeInfo('invalid-config', 'config', false, exitCode),
    };
  }

  // 3. Ready-poll budget exhausted while the process stayed alive: the orchestrator
  //    already retried whole start-flows, so this is terminal.
  if (input.readyPollExhausted) {
    return {
      deterministic: true,
      info: makeInfo('startup-timeout', 'handshake', false, exitCode),
    };
  }

  // 4. Port still occupied: transient once, deterministic on a second consecutive
  //    start-flow (something else owns the port and is not releasing it).
  if (matchesAny(combined, [PORT_OCCUPIED_PATTERN])) {
    const streak = input.portOccupiedStreak ?? 1;
    const deterministic = streak >= 2;
    return {
      deterministic,
      info: makeInfo('port-occupied', 'port', !deterministic, exitCode, { streak }),
    };
  }

  // 5. Known-transient handshake/connect signals: keep the bounded retry loop.
  if (matchesAny(combined, TRANSIENT_HANDSHAKE_PATTERNS)) {
    return {
      deterministic: false,
      info: makeInfo('unknown', 'handshake', true, exitCode),
    };
  }

  // 6. A plain non-zero child exit with no deterministic signature: could be a
  //    genuine race, so stay transient (the manager's bounded loop applies).
  if (exitCode !== null && exitCode !== 0) {
    return {
      deterministic: false,
      info: makeInfo('spawn-failed', 'spawn', true, exitCode, { exitCode }),
    };
  }

  // 7. Anything else: fail closed into the terminal state rather than looping
  //    forever without telling the user why.
  return {
    deterministic: true,
    info: makeInfo('unknown', input.phase === 'wait-port' ? 'port' : 'spawn', false, exitCode),
  };
}

/** Convenience predicate used by startup-recovery to short-circuit retries. */
export function isDeterministicGatewayStartupFailure(
  input: GatewayStartupFailureInput,
): boolean {
  return classifyGatewayStartupFailure(input).deterministic;
}
