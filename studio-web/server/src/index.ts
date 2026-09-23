/**
 * GrandPoem Paralegal Studio Web — host server entrypoint.
 *
 *   - serves the built SPA from dist/ (SPA fallback to index.html)
 *   - /ws        WebSocket bridge (host:invoke + legacy channels + events)
 *   - /auth/*    login/logout/me backed by the JY backend
 *   - /healthz   liveness probe
 *
 * The env module must load first: it seeds CLAWX_USER_DATA_DIR /
 * JY_API_BASE_URL before any host-core/ code resolves its paths.
 *
 * Fleet mode (default): each account is routed to its own host worker via the
 * FleetSupervisor, keyed by the JWT `sub`. The access layer stays stateless and
 * never touches child processes directly. Set FLEET_DISABLED=1 to fall back to
 * the legacy single-process host (all users share one Gateway).
 */
import './env';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import Fastify from 'fastify';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import fastifyCookie from '@fastify/cookie';
import fastifyStatic from '@fastify/static';
import fastifyWebsocket from '@fastify/websocket';
import type { WebSocket } from 'ws';
import jwt from 'jsonwebtoken';
import { AUTH_DISABLED, FLEET_DISABLED, JWT_SECRET, PORT, PROJECT_ROOT } from './env';
import { registerAuthRoutes, verifySession } from './auth';
import { registerPlatformProxy } from './platform-proxy';
import { invokeLegacyChannel } from './legacy-router';
import { createWebHost } from './host';
import { FleetSupervisor } from './fleet/supervisor';
import { createFleetAuthService } from './fleet/auth-service';
import { normalizeScope } from './fleet/layout';
import { logger } from '../host-core/utils/logger';

type ClientMessage =
  | { kind: 'invoke'; request: unknown }
  | { kind: 'legacy'; id: string; channel: string; args?: unknown[] };

/**
 * The subset of behaviour the WS layer needs from whatever backs it. The fleet
 * supervisor and the legacy single-host both satisfy this; the WS handler is
 * written once against it, so the client wire protocol is identical either way.
 */
type WsBackend = {
  invoke: (scope: string, request: unknown) => Promise<unknown>;
  legacy: (scope: string, channel: string, args: unknown[]) => Promise<unknown>;
  subscribe: (scope: string, sink: (channel: string, payload: unknown) => void) => () => void;
  touch: (scope: string) => void;
};

function readAppVersion(): string {
  try {
    const pkg = JSON.parse(readFileSync(join(PROJECT_ROOT, 'package.json'), 'utf8')) as { version?: string };
    return pkg.version ?? '0.0.0';
  } catch {
    return '0.0.0';
  }
}

/** JSON.stringify replacer: binary payloads (file:readBinary) survive the wire. */
function wireReplacer(_key: string, value: unknown): unknown {
  if (value instanceof Uint8Array) {
    return { __u8: Buffer.from(value).toString('base64') };
  }
  return value;
}

function send(socket: WebSocket, message: unknown): void {
  if (socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(message, wireReplacer));
  }
}

/**
 * Register the WS bridge. `backend` routes to the right host (per-user worker in
 * fleet mode, the shared host otherwise); `scope` resolves the account from the
 * verified session so events only fan back to that account's sockets.
 */
function registerWebSocket(app: FastifyInstance, backend: WsBackend): void {
  app.register(async (scope: FastifyInstance) => {
    scope.get('/ws', { websocket: true }, (socket: WebSocket, request: FastifyRequest) => {
      // Support token from URL query param (iframe) or Authorization header
      const url = new URL(request.url, `http://${request.headers.host}`);
      const wsToken = url.searchParams.get('token');
      const authHeader = request.headers.authorization;
      const claims = wsToken
        ? jwt.verify(wsToken, JWT_SECRET) as { sub: string; name?: string }
        : verifySession(request.cookies ?? {}, authHeader);
      if (!AUTH_DISABLED && !claims) {
        socket.close(4401, 'Unauthorized');
        return;
      }
      const userScope = normalizeScope(claims?.sub ?? 'dev');

      send(socket, { kind: 'hello', platform: process.platform, isDev: !!process.env.VITE_DEV });

      // Keep-alive heartbeat: long-lived browser sockets behind LBs/proxies
      // are silently dropped after an idle timeout unless the server emits
      // frames. Browsers answer WS pings with pongs automatically, so two
      // consecutive misses mean the connection is really gone.
      const HEARTBEAT_INTERVAL_MS = 25_000;
      const HEARTBEAT_MAX_MISSES = 2;
      let heartbeatMisses = 0;
      let heartbeatAlive = true;
      socket.on('pong', () => {
        heartbeatAlive = true;
        heartbeatMisses = 0;
      });
      const heartbeat = setInterval(() => {
        if (!heartbeatAlive) {
          heartbeatMisses += 1;
          if (heartbeatMisses >= HEARTBEAT_MAX_MISSES) {
            clearInterval(heartbeat);
            try {
              socket.terminate();
            } catch {
              // already closing
            }
            return;
          }
        }
        heartbeatAlive = false;
        try {
          socket.ping();
        } catch {
          // socket closing — the close handler cleans up
        }
      }, HEARTBEAT_INTERVAL_MS);

      const unsubscribe = backend.subscribe(userScope, (channel, payload) => {
        send(socket, { kind: 'event', channel, payload });
      });
      socket.on('close', () => {
        clearInterval(heartbeat);
        unsubscribe();
      });
      socket.on('error', () => socket.close());

      socket.on('message', (raw) => {
        // Any client frame proves the connection is alive.
        heartbeatAlive = true;
        heartbeatMisses = 0;
        void (async () => {
          let message: ClientMessage;
          try {
            message = JSON.parse(String(raw)) as ClientMessage;
          } catch {
            return;
          }
          backend.touch(userScope);
          if (message.kind === 'invoke') {
            try {
              const response = await backend.invoke(userScope, message.request);
              send(socket, { kind: 'response', response });
            } catch (error) {
              logger.warn(`[web-host] invoke failed scope=${userScope}:`, error);
              // Send error response so the client doesn't wait for the 120s timeout
              send(socket, {
                kind: 'response',
                response: {
                  id: (message.request as { id?: string })?.id,
                  ok: false,
                  error: { message: error instanceof Error ? error.message : 'Internal error' },
                },
              });
            }
          } else if (message.kind === 'legacy') {
            try {
              const data = await backend.legacy(userScope, message.channel, message.args ?? []);
              send(socket, { kind: 'legacy-response', id: message.id, ok: true, data });
            } catch (error) {
              send(socket, {
                kind: 'legacy-response',
                id: message.id,
                ok: false,
                error: error instanceof Error ? error.message : String(error),
              });
            }
          }
        })();
      });
    });
  });
}

async function main(): Promise<void> {
  const app = Fastify({ logger: false, bodyLimit: 16 * 1024 * 1024 });
  await app.register(fastifyCookie);
  await app.register(fastifyWebsocket, { options: { maxPayload: 32 * 1024 * 1024 } });

  // Allow iframe embedding; defaults to 'self' (restrict via ALLOWED_FRAME_ANCESTORS)
  app.addHook('onSend', async (_request, reply) => {
    const ancestors = process.env.ALLOWED_FRAME_ANCESTORS || "'self'";
    reply.header('Content-Security-Policy', `frame-ancestors ${ancestors}`);
  });

  // --- backend selection: fleet (per-user workers) vs legacy single host ----
  let backend: WsBackend;
  let onListening: () => void = () => {};
  let shutdownBackend: () => Promise<void>;

  if (FLEET_DISABLED) {
    const host = createWebHost(readAppVersion());
    backend = {
      invoke: (_scope, req) => Promise.resolve(host.dispatch(req)),
      legacy: (_scope, channel, args) => invokeLegacyChannel(channel, args),
      subscribe: (_scope, sink) => host.onEvent(sink),
      touch: () => {},
    };
    onListening = () => void host.startGateway();
    shutdownBackend = async () => {
      try {
        await host.gatewayManager.stop();
      } catch {
        // best effort
      }
    };
    app.get('/healthz', async () => ({ ok: true, mode: 'legacy', gateway: host.gatewayManager.getStatus().state }));
    registerAuthRoutes(app, host.authApi);
    logger.info('[web-host] FLEET_DISABLED=1 — running legacy single-process host');
  } else {
    const supervisor = new FleetSupervisor();
    backend = {
      invoke: (scope, req) => supervisor.invoke(scope, req),
      legacy: (scope, channel, args) => supervisor.legacy(scope, channel, args),
      subscribe: (scope, sink) => supervisor.subscribe(scope, sink),
      touch: (scope) => supervisor.touch(scope),
    };
    shutdownBackend = () => supervisor.shutdown();
    app.get('/healthz', async () => ({ ok: true, mode: 'fleet', ...supervisor.getStats() }));
    registerAuthRoutes(app, createFleetAuthService(supervisor));
  }

  registerWebSocket(app, backend);
  registerPlatformProxy(app);

  // Built SPA (production). During development the SPA is served by Vite,
  // which proxies /ws and /auth to this server.
  const distDir = join(PROJECT_ROOT, 'dist');
  const hasDesktopDist = existsSync(join(distDir, 'index.html'));

  // Legacy mobile URLs (former studio-phone SPA under /m/): the unified SPA
  // now picks the shell at runtime, so permanently redirect to /.
  app.get('/m', (_request, reply) => reply.redirect('/', 301));
  app.get('/m/*', (_request, reply) => reply.redirect('/', 301));

  if (hasDesktopDist) {
    await app.register(fastifyStatic, { root: distDir, wildcard: true });
    app.setNotFoundHandler((request, reply) => {
      if (request.raw.method === 'GET' && !request.url.startsWith('/auth') && !request.url.startsWith('/ws')) {
        return reply.type('text/html').send(readFileSync(join(distDir, 'index.html')));
      }
      return reply.code(404).send({ error: 'Not found' });
    });
  }

  await app.listen({ port: PORT, host: '0.0.0.0' });
  logger.info(
    `[web-host] listening on http://0.0.0.0:${PORT} (auth ${AUTH_DISABLED ? 'DISABLED' : 'enabled'}, ${FLEET_DISABLED ? 'legacy' : 'fleet'})`,
  );

  onListening();

  const shutdown = async (): Promise<void> => {
    try {
      await shutdownBackend();
    } catch {
      // best effort
    }
    await app.close();
    process.exit(0);
  };
  process.on('SIGINT', () => void shutdown());
  process.on('SIGTERM', () => void shutdown());
}

main().catch((error) => {
  console.error('[web-host] fatal startup error:', error);
  process.exit(1);
});
