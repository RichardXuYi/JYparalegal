/**
 * Fleet host transport — the transport-agnostic message contract between the
 * Fastify access layer (Fleet Supervisor) and a per-user host worker.
 *
 * Phase 1 carries these frames over Node's `child_process` IPC channel (see
 * supervisor.ts / worker-entry.ts). Phase 2 carries the exact same frames over
 * a TCP/WS connection to a remote worker node — the access layer and routing
 * logic never change, only the pipe underneath. Keep this file free of any
 * process/IPC/socket specifics so both transports can share it.
 */

/** Frames the supervisor sends down to a worker. */
export type SupervisorToWorker =
  | { kind: 'invoke'; seq: number; request: unknown }
  | { kind: 'legacy'; seq: number; channel: string; args: unknown[] }
  | { kind: 'ping'; seq: number }
  | { kind: 'shutdown' };

/** Frames a worker sends back up to the supervisor. */
export type WorkerToSupervisor =
  | { kind: 'ready' }
  | { kind: 'invoke-result'; seq: number; response: unknown }
  | { kind: 'legacy-result'; seq: number; ok: true; data: unknown }
  | { kind: 'legacy-result'; seq: number; ok: false; error: string }
  | { kind: 'event'; channel: string; payload: unknown }
  | { kind: 'pong'; seq: number };

export function isWorkerToSupervisor(value: unknown): value is WorkerToSupervisor {
  if (!value || typeof value !== 'object') return false;
  const kind = (value as { kind?: unknown }).kind;
  return (
    kind === 'ready' ||
    kind === 'invoke-result' ||
    kind === 'legacy-result' ||
    kind === 'event' ||
    kind === 'pong'
  );
}

export function isSupervisorToWorker(value: unknown): value is SupervisorToWorker {
  if (!value || typeof value !== 'object') return false;
  const kind = (value as { kind?: unknown }).kind;
  return kind === 'invoke' || kind === 'legacy' || kind === 'ping' || kind === 'shutdown';
}
