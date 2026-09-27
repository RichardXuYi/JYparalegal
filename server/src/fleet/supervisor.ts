/**
 * Fleet Supervisor (Phase 1, single-node process pool).
 *
 * Owns the lifecycle of per-user host workers: spawns one child process per
 * logged-in account, routes invoke/legacy requests to it, fans host events back
 * to the caller, and keeps the pool healthy (crash isolation, backoff restart,
 * per-user quarantine, office-hours residency, idle reclaim, overload LRU).
 *
 * The access layer (index.ts) never touches child processes directly — it only
 * calls invoke/legacy/subscribe with a scope (JWT `sub`). Phase 2 swaps the
 * child_process transport under `spawnWorker` for a node-agent connection using
 * the identical host-transport frames; routing above stays unchanged.
 */
import { fork, type ChildProcess } from 'node:child_process';
import { createWriteStream, existsSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DATA_DIR } from '../env';
import { logger } from '../../host-core/utils/logger';
import { getUserDataDir, getUserOpenClawDir, normalizeScope } from './layout';
import { acquireGatewayPort, reassignGatewayPort } from './port-registry';
import {
  isWorkerToSupervisor,
  type SupervisorToWorker,
  type WorkerToSupervisor,
} from './host-transport';

export type FleetEventSink = (channel: string, payload: unknown) => void;

type PendingRequest = {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
};

type WorkerHandle = {
  scope: string;
  port: number;
  child: ChildProcess | null;
  ready: boolean;
  stopping: boolean;
  seq: number;
  pending: Map<number, PendingRequest>;
  sinks: Set<FleetEventSink>;
  readyWaiters: Array<() => void>;
  lastActivityMs: number;
  crashTimestamps: number[];
  restartAttempts: number;
  quarantinedUntilMs: number;
};

type FleetConfig = {
  officeStartHour: number;
  officeEndHour: number;
  idleReclaimMs: number;
  maxWorkers: number;
  maxConcurrentSpawns: number;
  spawnStaggerMs: number;
  memReclaimThresholdMb: number;
  workerMaxOldSpaceMb: number;
  gatewayMaxOldSpaceMb: number;
};

const CRASH_WINDOW_MS = 60_000;
const CRASH_LIMIT = 5;
const QUARANTINE_MS = 5 * 60_000;
const RESTART_BASE_MS = 500;
const RESTART_MAX_MS = 30_000;
const REAPER_INTERVAL_MS = 5 * 60_000;
const READY_TIMEOUT_MS = 60_000;
/** Idle window applied to clientless workers when the node is under memory pressure. */
const MEM_PRESSURE_IDLE_MS = 5 * 60_000;

function readIntEnv(name: string, fallback: number): number {
  const raw = process.env[name];
  const value = raw ? Number(raw) : NaN;
  return Number.isFinite(value) ? value : fallback;
}

function loadConfig(): FleetConfig {
  return {
    officeStartHour: readIntEnv('FLEET_OFFICE_START_HOUR', 8),
    officeEndHour: readIntEnv('FLEET_OFFICE_END_HOUR', 20),
    idleReclaimMs: readIntEnv('FLEET_IDLE_RECLAIM_HOURS', 2) * 3_600_000,
    maxWorkers: readIntEnv('FLEET_MAX_WORKERS', 300),
    // Startup-storm guard: cap the number of workers forked per tick so a
    // morning login wave (hundreds of users at once) doesn't fork hundreds
    // of workers + gateways simultaneously.
    maxConcurrentSpawns: readIntEnv('FLEET_MAX_CONCURRENT_SPAWNS', 20),
    spawnStaggerMs: readIntEnv('FLEET_SPAWN_STAGGER_MS', 50),
    // Memory-pressure reclaim: when the node's RSS exceeds this (MB),
    // clientless idle workers are reclaimed even during office hours.
    // 0 disables the check.
    memReclaimThresholdMb: readIntEnv('FLEET_MEM_RECLAIM_THRESHOLD_MB', 0),
    // Heap caps for the per-user worker process and its OpenClaw Gateway.
    // Exceeding the cap triggers a V8 OOM abort, which the crash/backoff
    // machinery restarts — bounded blast radius per account.
    workerMaxOldSpaceMb: readIntEnv('FLEET_WORKER_MAX_OLD_SPACE_MB', 512),
    gatewayMaxOldSpaceMb: readIntEnv('FLEET_GATEWAY_MAX_OLD_SPACE_MB', 1024),
  };
}

const WORKER_ENTRY = fileURLToPath(new URL('./worker-entry.ts', import.meta.url));

export class FleetSupervisor {
  private readonly workers = new Map<string, WorkerHandle>();
  private readonly config = loadConfig();
  private reaper: NodeJS.Timeout | null = null;

  constructor() {
    this.reaper = setInterval(() => this.reap(), REAPER_INTERVAL_MS);
    this.reaper.unref?.();
  }

  /** Route a typed host-invoke request to the account's worker. */
  async invoke(scope: string, request: unknown): Promise<unknown> {
    const handle = await this.acquireReady(scope);
    return new Promise<unknown>((resolve, reject) => {
      const seq = handle.seq++;
      handle.pending.set(seq, { resolve, reject });
      this.post(handle, { kind: 'invoke', seq, request });
    });
  }

  /** Route a legacy IPC channel call to the account's worker. */
  async legacy(scope: string, channel: string, args: unknown[]): Promise<unknown> {
    const handle = await this.acquireReady(scope);
    return new Promise<unknown>((resolve, reject) => {
      const seq = handle.seq++;
      handle.pending.set(seq, { resolve, reject });
      this.post(handle, { kind: 'legacy', seq, channel, args });
    });
  }

  /** Subscribe to a scope's host events; ensures the worker is running. */
  subscribe(scope: string, sink: FleetEventSink): () => void {
    const handle = this.ensureWorker(scope);
    handle.sinks.add(sink);
    return () => {
      handle.sinks.delete(sink);
    };
  }

  /** Mark recent activity so the idle reaper keeps a busy worker resident. */
  touch(scope: string): void {
    const handle = this.workers.get(normalizeScope(scope));
    if (handle) handle.lastActivityMs = Date.now();
  }

  /** Proactively start a worker (e.g. right after login) without a request. */
  warmUp(scope: string): void {
    this.ensureWorker(scope);
  }

  /** Stop and forget a scope's worker (e.g. on logout). */
  async stopWorker(scope: string): Promise<void> {
    const key = normalizeScope(scope);
    const handle = this.workers.get(key);
    if (!handle) return;
    handle.stopping = true;
    this.workers.delete(key);
    this.rejectAllPending(handle, new Error('worker stopped'));
    try {
      this.post(handle, { kind: 'shutdown' });
    } catch {
      // channel may already be gone
    }
    await this.terminate(handle);
  }

  /** Graceful pool shutdown on server exit. */
  async shutdown(): Promise<void> {
    if (this.reaper) clearInterval(this.reaper);
    const handles = [...this.workers.values()];
    this.workers.clear();
    await Promise.all(handles.map((h) => {
      h.stopping = true;
      this.rejectAllPending(h, new Error('server shutting down'));
      try {
        this.post(h, { kind: 'shutdown' });
      } catch {
        // ignore
      }
      return this.terminate(h);
    }));
  }

  getStats(): {
    workers: number;
    ready: number;
    quarantined: number;
    rssMb: number;
    spawnQueueDepth: number;
  } {
    let ready = 0;
    let quarantined = 0;
    const now = Date.now();
    for (const h of this.workers.values()) {
      if (h.ready) ready += 1;
      if (h.quarantinedUntilMs > now) quarantined += 1;
    }
    return {
      workers: this.workers.size,
      ready,
      quarantined,
      // Node capacity observability: RSS + pending spawn queue feed the
      // memory-pressure / startup-storm alerting thresholds.
      rssMb: Math.round(process.memoryUsage().rss / (1024 * 1024)),
      spawnQueueDepth: this.spawnQueue.length,
    };
  }

  // --- internals ---------------------------------------------------------

  private async acquireReady(scope: string): Promise<WorkerHandle> {
    const handle = this.ensureWorker(scope);
    handle.lastActivityMs = Date.now();
    if (handle.ready) return handle;
    await this.waitForReady(handle);
    return handle;
  }

  private waitForReady(handle: WorkerHandle): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        const idx = handle.readyWaiters.indexOf(onReady);
        if (idx >= 0) handle.readyWaiters.splice(idx, 1);
        reject(new Error(`worker ${handle.scope} did not become ready in time`));
      }, READY_TIMEOUT_MS);
      const onReady = (): void => {
        clearTimeout(timer);
        resolve();
      };
      handle.readyWaiters.push(onReady);
    });
  }

  private ensureWorker(scope: string): WorkerHandle {
    const key = normalizeScope(scope);
    const existing = this.workers.get(key);
    if (existing) return existing;

    const now = Date.now();
    this.enforceCapacity();

    const port = acquireGatewayPort(key);
    const handle: WorkerHandle = {
      scope: key,
      port,
      child: null,
      ready: false,
      stopping: false,
      seq: 1,
      pending: new Map(),
      sinks: new Set(),
      readyWaiters: [],
      lastActivityMs: now,
      crashTimestamps: [],
      restartAttempts: 0,
      quarantinedUntilMs: 0,
    };
    this.workers.set(key, handle);
    this.spawnThroughGate(handle);
    logger.info(`[fleet] worker scheduled scope=${key} port=${port}`);
    return handle;
  }

  /**
   * Spawn-rate gate: a login stampede (morning wave) would otherwise fork
   * hundreds of workers at once, each immediately booting its own Gateway.
   * Spawns are batched (maxConcurrentSpawns per tick) and staggered
   * (spawnStaggerMs) so startup load ramps instead of spiking.
   */
  private spawnQueue: Array<() => void> = [];
  private spawnTimer: ReturnType<typeof setInterval> | null = null;

  private spawnThroughGate(handle: WorkerHandle): void {
    this.spawnQueue.push(() => {
      // The worker may have been stopped (or evicted) while queued — never
      // fork an orphan in that case.
      if (handle.stopping || this.workers.get(handle.scope) !== handle) return;
      handle.child = this.spawnWorker(handle.scope, handle.port);
      this.wireWorker(handle);
      logger.info(`[fleet] spawned worker scope=${handle.scope} port=${handle.port} pid=${handle.child?.pid}`);
    });
    if (this.spawnTimer) return;
    this.spawnTimer = setInterval(() => {
      const batch = this.spawnQueue.splice(0, Math.max(1, this.config.maxConcurrentSpawns));
      for (const spawn of batch) {
        try {
          spawn();
        } catch (error) {
          logger.error('[fleet] gated worker spawn failed:', error);
        }
      }
      if (this.spawnQueue.length === 0 && this.spawnTimer) {
        clearInterval(this.spawnTimer);
        this.spawnTimer = null;
      }
    }, Math.max(1, this.config.spawnStaggerMs));
  }

  private spawnWorker(scope: string, port: number): ChildProcess {
    const logs = this.openWorkerLogs(scope);
    const child = fork(WORKER_ENTRY, [], {
      env: this.buildWorkerEnv(scope, port),
      // Native ESM/TS worker: run under the tsx loader like the server itself.
      execArgv: ['--import', 'tsx'],
      // Advanced serialization preserves Uint8Array (file:readBinary) over IPC.
      serialization: 'advanced',
      // Per-worker log files instead of inherit: with hundreds of workers the
      // supervisor's stdout would become a single unreadable stream.
      stdio: ['ignore', 'pipe', 'pipe', 'ipc'],
    });
    child.stdout?.pipe(logs.out);
    child.stderr?.pipe(logs.err);
    return child;
  }

  /**
   * Per-worker log files under `<DATA_DIR>/logs/worker-<scope>.log`, rotated
   * by truncation once they outgrow MAX_WORKER_LOG_BYTES (simple size cap —
   * the file is reopened on every spawn).
   */
  private static readonly MAX_WORKER_LOG_BYTES = 20 * 1024 * 1024;

  private openWorkerLogs(scope: string): {
    out: ReturnType<typeof createWriteStream>;
    err: ReturnType<typeof createWriteStream>;
  } {
    const logDir = join(DATA_DIR, 'logs');
    const outPath = join(logDir, `worker-${scope}.log`);
    const errPath = join(logDir, `worker-${scope}.err.log`);
    for (const filePath of [outPath, errPath]) {
      try {
        if (existsSync(filePath) && statSync(filePath).size > FleetSupervisor.MAX_WORKER_LOG_BYTES) {
          writeFileSync(filePath, '');
        }
      } catch {
        // Best-effort rotation; logging must never break worker spawning.
      }
    }
    return {
      out: createWriteStream(outPath, { flags: 'a' }),
      err: createWriteStream(errPath, { flags: 'a' }),
    };
  }

  private buildWorkerEnv(scope: string, port: number): NodeJS.ProcessEnv {
    // Cap the worker's V8 heap; a runaway account can only OOM its own
    // worker (crash/backoff restarts it) instead of taking the node down.
    const workerNodeOptions = [`--max-old-space-size=${this.config.workerMaxOldSpaceMb}`];
    const inheritedNodeOptions = process.env.NODE_OPTIONS?.trim();
    const nodeOptions = inheritedNodeOptions
      ? `${inheritedNodeOptions} ${workerNodeOptions.join(' ')}`
      : workerNodeOptions.join(' ');
    return {
      ...process.env,
      NODE_OPTIONS: nodeOptions,
      DATA_DIR: getUserDataDir(scope),
      CLAWX_USER_DATA_DIR: getUserDataDir(scope),
      OPENCLAW_STATE_DIR: getUserOpenClawDir(scope),
      CLAWX_PORT_OPENCLAW_GATEWAY: String(port),
      OPENCLAW_ALLOW_MULTI_GATEWAY: '1',
      JY_WORKER_SCOPE: scope,
      // Read by config-sync.ts when building the Gateway launch env: the
      // Gateway gets its own (larger) heap cap.
      JY_GATEWAY_MAX_OLD_SPACE_MB: String(this.config.gatewayMaxOldSpaceMb),
    };
  }

  private wireWorker(handle: WorkerHandle): void {
    const child = handle.child;
    if (!child) return;
    child.on('message', (raw: unknown) => {
      if (!isWorkerToSupervisor(raw)) return;
      this.onWorkerMessage(handle, raw);
    });
    child.on('exit', (code) => this.onWorkerExit(handle, code));
    child.on('error', (error) => {
      logger.error(`[fleet] worker scope=${handle.scope} process error:`, error);
    });
  }

  private onWorkerMessage(handle: WorkerHandle, message: WorkerToSupervisor): void {
    switch (message.kind) {
      case 'ready': {
        handle.ready = true;
        handle.restartAttempts = 0;
        const waiters = handle.readyWaiters.splice(0);
        for (const w of waiters) w();
        return;
      }
      case 'invoke-result': {
        this.settle(handle, message.seq, (p) => p.resolve(message.response));
        return;
      }
      case 'legacy-result': {
        this.settle(handle, message.seq, (p) => {
          if (message.ok) p.resolve(message.data);
          else p.reject(new Error(message.error));
        });
        return;
      }
      case 'pong': {
        this.settle(handle, message.seq, (p) => p.resolve(undefined));
        return;
      }
      case 'event': {
        for (const sink of [...handle.sinks]) {
          try {
            sink(message.channel, message.payload);
          } catch (error) {
            logger.warn(`[fleet] event sink failed scope=${handle.scope} channel=${message.channel}:`, error);
          }
        }
        return;
      }
    }
  }

  private settle(handle: WorkerHandle, seq: number, apply: (p: PendingRequest) => void): void {
    const pending = handle.pending.get(seq);
    if (!pending) return;
    handle.pending.delete(seq);
    apply(pending);
  }

  private onWorkerExit(handle: WorkerHandle, code: number | null): void {
    handle.ready = false;
    this.rejectAllPending(handle, new Error(`worker exited (code=${code})`));

    // Planned shutdown (stopWorker/shutdown) already removed it from the map.
    if (handle.stopping || this.workers.get(handle.scope) !== handle) {
      return;
    }

    const now = Date.now();
    handle.crashTimestamps = handle.crashTimestamps.filter((t) => now - t < CRASH_WINDOW_MS);
    handle.crashTimestamps.push(now);

    if (handle.crashTimestamps.length >= CRASH_LIMIT) {
      handle.quarantinedUntilMs = now + QUARANTINE_MS;
      this.workers.delete(handle.scope);
      logger.error(
        `[fleet] worker scope=${handle.scope} crashed ${handle.crashTimestamps.length}x in ${CRASH_WINDOW_MS}ms — quarantined for ${QUARANTINE_MS}ms`,
      );
      return;
    }

    const delay = Math.min(RESTART_BASE_MS * 2 ** handle.restartAttempts, RESTART_MAX_MS);
    handle.restartAttempts += 1;
    logger.warn(`[fleet] worker scope=${handle.scope} crashed (code=${code}) — restarting in ${delay}ms`);
    const timer = setTimeout(() => this.respawn(handle), delay);
    timer.unref?.();
  }

  private respawn(handle: WorkerHandle): void {
    if (this.workers.get(handle.scope) !== handle) return;
    // Repeated fast failures often mean the pinned port is held by a stale or
    // foreign process (OpenClaw throws GatewayLockError on a taken slot). Advance
    // to the next free slot in the registry before respawning ("顺移槽位").
    if (handle.restartAttempts >= 2) {
      try {
        const next = reassignGatewayPort(handle.scope);
        if (next !== handle.port) {
          logger.warn(`[fleet] reassigning worker scope=${handle.scope} port ${handle.port} → ${next}`);
          handle.port = next;
        }
      } catch (error) {
        logger.error(`[fleet] port reassign failed scope=${handle.scope}:`, error);
      }
    }
    handle.child = this.spawnWorker(handle.scope, handle.port);
    handle.pending = new Map();
    handle.seq = 1;
    this.wireWorker(handle);
    logger.info(`[fleet] respawned worker scope=${handle.scope} port=${handle.port} pid=${handle.child.pid}`);
  }

  private rejectAllPending(handle: WorkerHandle, error: Error): void {
    for (const pending of handle.pending.values()) {
      pending.reject(error);
    }
    handle.pending.clear();
    const waiters = handle.readyWaiters.splice(0);
    for (const w of waiters) w(); // unblock; the retry path surfaces the failure
  }

  private post(handle: WorkerHandle, message: SupervisorToWorker): void {
    const child = handle.child;
    if (!child || !child.connected) {
      throw new Error(`worker ${handle.scope} channel is not connected`);
    }
    child.send(message);
  }

  private async terminate(handle: WorkerHandle): Promise<void> {
    const child = handle.child;
    if (!child || child.exitCode !== null || !child.connected) {
      child?.kill();
      return;
    }
    await new Promise<void>((resolve) => {
      const timer = setTimeout(() => {
        child.kill('SIGKILL');
        resolve();
      }, 5_000);
      timer.unref?.();
      child.once('exit', () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }

  private isOfficeHours(now = new Date()): boolean {
    const hour = now.getHours();
    const { officeStartHour, officeEndHour } = this.config;
    if (officeStartHour <= officeEndHour) {
      return hour >= officeStartHour && hour < officeEndHour;
    }
    // Overnight window (e.g. 20 → 6).
    return hour >= officeStartHour || hour < officeEndHour;
  }

  /** Overload backpressure: evict least-recently-active workers over the cap. */
  private enforceCapacity(): void {
    if (this.workers.size < this.config.maxWorkers) return;
    const sorted = [...this.workers.values()].sort((a, b) => a.lastActivityMs - b.lastActivityMs);
    const victim = sorted[0];
    if (victim) {
      logger.warn(`[fleet] at capacity (${this.workers.size}) — evicting LRU worker scope=${victim.scope}`);
      void this.stopWorker(victim.scope);
    }
  }

  /**
   * Idle reclaim (office scenario, not a cache): during office hours workers
   * stay resident; once idle past the reclaim window AND outside office hours
   * they are stopped. LRU eviction above handles in-hours overload. When the
   * node is under memory pressure (RSS above the configured threshold),
   * clientless idle workers are additionally reclaimed during office hours
   * using a shorter idle window — a 24h-operation safety valve.
   */
  private reap(): void {
    const now = Date.now();
    const memoryPressure = this.config.memReclaimThresholdMb > 0
      && process.memoryUsage().rss / (1024 * 1024) > this.config.memReclaimThresholdMb;
    const idleWindow = memoryPressure
      ? Math.min(this.config.idleReclaimMs, MEM_PRESSURE_IDLE_MS)
      : this.config.idleReclaimMs;
    for (const handle of [...this.workers.values()]) {
      // Connected clients keep their worker resident; only clientless idle
      // workers are reclaimable.
      if (handle.sinks.size > 0) continue;
      if (this.isOfficeHours() && !memoryPressure) continue;
      const idleMs = now - handle.lastActivityMs;
      if (idleMs > idleWindow) {
        logger.info(
          `[fleet] reclaiming idle worker scope=${handle.scope} (idle ${Math.round(idleMs / 60000)}m${memoryPressure ? ', memory pressure' : ''})`,
        );
        void this.stopWorker(handle.scope);
      }
    }
  }
}
