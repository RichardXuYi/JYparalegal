/**
 * Fleet host worker — one process per logged-in JY account.
 *
 * The supervisor forks this module with the account's data dirs / gateway port
 * pinned via environment (see supervisor.ts buildWorkerEnv). It reuses the exact
 * single-tenant `createWebHost()` assembly — the same battle-tested host-core
 * service layer the desktop app runs — so a worker IS a headless GrandPoem
 * Studio main process for one user, with its own OpenClaw Gateway.
 *
 * It speaks the transport-agnostic fleet protocol (host-transport.ts) over the
 * child_process IPC channel: invoke/legacy requests come down, results and host
 * events go back up. `serialization: 'advanced'` on the fork keeps binary
 * payloads (file:readBinary → Uint8Array) intact across the channel.
 */
import '../env';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { PROJECT_ROOT } from '../env';
import { createWebHost } from '../host';
import { invokeLegacyChannel } from '../legacy-router';
import { logger } from '../../host-core/utils/logger';
import {
  isSupervisorToWorker,
  type SupervisorToWorker,
  type WorkerToSupervisor,
} from './host-transport';

function readAppVersion(): string {
  try {
    const pkgPath = join(PROJECT_ROOT, 'package.json');
    if (existsSync(pkgPath)) {
      const pkg = JSON.parse(readFileSync(pkgPath, 'utf8')) as { version?: string };
      return pkg.version ?? '0.0.0';
    }
  } catch {
    // fall through
  }
  return '0.0.0';
}

function send(message: WorkerToSupervisor): void {
  process.send?.(message);
}

const scope = process.env.JY_WORKER_SCOPE ?? 'anon';
const host = createWebHost(readAppVersion());

// Fan every host event (gateway status, chat runtime events, oauth, ...) up to
// the supervisor, which routes it to that account's WebSocket clients only.
host.onEvent((channel, payload) => send({ kind: 'event', channel, payload }));

async function handle(message: SupervisorToWorker): Promise<void> {
  switch (message.kind) {
    case 'invoke': {
      const response = await host.dispatch(message.request);
      send({ kind: 'invoke-result', seq: message.seq, response });
      return;
    }
    case 'legacy': {
      try {
        const data = await invokeLegacyChannel(message.channel, message.args);
        send({ kind: 'legacy-result', seq: message.seq, ok: true, data });
      } catch (error) {
        send({
          kind: 'legacy-result',
          seq: message.seq,
          ok: false,
          error: error instanceof Error ? error.message : String(error),
        });
      }
      return;
    }
    case 'ping': {
      send({ kind: 'pong', seq: message.seq });
      return;
    }
    case 'shutdown': {
      logger.info(`[worker ${scope}] shutdown requested`);
      try {
        await host.gatewayManager.stop();
      } catch {
        // best effort
      }
      process.exit(0);
    }
  }
}

process.on('message', (raw: unknown) => {
  if (!isSupervisorToWorker(raw)) return;
  void handle(raw).catch((error) => {
    logger.error(`[worker ${scope}] message handling failed:`, error);
  });
});

// A worker whose parent channel dies must not linger as an orphan holding a
// gateway port. Exit promptly so the supervisor can respawn cleanly.
process.on('disconnect', () => {
  logger.warn(`[worker ${scope}] IPC channel disconnected — exiting`);
  process.exit(0);
});

send({ kind: 'ready' });
void host.startGateway();
logger.info(`[worker ${scope}] host ready (pid=${process.pid})`);
