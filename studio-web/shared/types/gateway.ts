/**
 * Gateway Type Definitions
 * Types for Gateway communication and data structures
 */

export type GatewayRuntimeJsonValue =
  | string
  | number
  | boolean
  | null
  | GatewayRuntimeJsonValue[]
  | { [key: string]: GatewayRuntimeJsonValue | undefined };

export type GatewayRuntimePayload = GatewayRuntimeJsonValue | undefined;
export type GatewayRuntimeRecord = { [key: string]: GatewayRuntimeJsonValue | undefined };

/**
 * Structured classification of a gateway startup/lifecycle failure.
 * Carried on {@link GatewayStatus.failure} so the renderer can show an
 * actionable, localized reason instead of a generic error string.
 */
export type GatewayFailureCode =
  | 'state-migration-failed'
  | 'invalid-config'
  | 'port-occupied'
  | 'spawn-failed'
  | 'startup-timeout'
  | 'max-reconnects-exhausted'
  | 'unknown';

/** Which readiness tier failed (spawn → port → handshake → rpc), per the
 *  gateway-startup-diagnostics spec. `config` covers config-validation refusals. */
export type GatewayReadinessTier = 'spawn' | 'port' | 'handshake' | 'rpc' | 'config';

export type GatewaySuggestedAction = 'retry' | 'viewLogs' | 'copyReport' | 'runDoctor';

export interface GatewayFailureInfo {
  code: GatewayFailureCode;
  tier: GatewayReadinessTier;
  exitCode?: number | null;
  /** false ⇒ terminal: the manager will not auto-recover; only an explicit
   *  start()/restart() leaves the `failed` state. */
  retryable: boolean;
  /** i18n key under the `common` namespace, e.g. `gateway.failure.stateMigration`. */
  reasonKey: string;
  reasonParams?: Record<string, string | number>;
  suggestedActions: GatewaySuggestedAction[];
  detectedAt: number;
}

/**
 * Gateway connection status
 */
export interface GatewayStatus {
  state: 'stopped' | 'starting' | 'running' | 'error' | 'reconnecting' | 'failed';
  port: number;
  pid?: number;
  uptime?: number;
  error?: string;
  connectedAt?: number;
  version?: string;
  reconnectAttempts?: number;
  /** True once the gateway's internal subsystems (skills, plugins) are ready for RPC calls. */
  gatewayReady?: boolean;
  /** Structured failure info; present on `error`/`failed`, cleared on success/stop. */
  failure?: GatewayFailureInfo | null;
  /** Epoch ms of the next scheduled reconnect attempt (banner countdown). */
  nextRetryAt?: number;
  /** Reconnect budget ceiling, for "attempt n/max" rendering. */
  reconnectMaxAttempts?: number;
}

/**
 * Gateway RPC response
 */
export interface GatewayRpcResponse<T = unknown> {
  success: boolean;
  result?: T;
  error?: string;
}

/**
 * Gateway health check response
 */
export interface GatewayCapabilityProbe {
  state: 'unknown' | 'healthy' | 'degraded';
  checkedAt?: number;
  durationMs?: number;
  error?: string;
  payload?: GatewayRuntimePayload;
}

export interface GatewayCapabilitySnapshot {
  core: {
    process: GatewayStatus['state'];
    transport: 'connected' | 'disconnected';
    rpcRouter: 'unknown' | 'ready' | 'blocked';
    lastProbe?: {
      ok: boolean;
      checkedAt: number;
      durationMs?: number;
      error?: string;
    };
  };
  openclawHealth: GatewayCapabilityProbe;
  openclawStatus: GatewayCapabilityProbe;
  presence: GatewayCapabilityProbe;
  channels: GatewayCapabilityProbe;
  memory: GatewayCapabilityProbe;
  diagnostics: {
    lastAliveAt?: number;
    lastRpcSuccessAt?: number;
    lastRpcFailureAt?: number;
    lastRpcFailureMethod?: string;
    lastHeartbeatTimeoutAt?: number;
    consecutiveHeartbeatMisses: number;
    lastSocketCloseAt?: number;
    lastSocketCloseCode?: number;
    consecutiveRpcFailures: number;
  };
}

export interface GatewayHealth {
  ok: boolean;
  error?: string;
  uptime?: number;
  version?: string;
  capabilities?: GatewayCapabilitySnapshot;
  openclawHealth?: GatewayRuntimePayload;
  presence?: GatewayRuntimePayload;
}

/**
 * Gateway notification (server-initiated event)
 */
export interface GatewayNotification {
  method: string;
  params?: GatewayRuntimePayload;
}

/**
 * Provider configuration
 */
export interface ProviderConfig {
  id: string;
  name: string;
  type: 'openai' | 'anthropic' | 'ollama' | 'custom';
  apiKey?: string;
  baseUrl?: string;
  model?: string;
  enabled: boolean;
}
