import type { FastifyInstance } from 'fastify';
import { existsSync, readFileSync } from 'node:fs';
import { BACKEND_URL } from './env';
import { normalizeScope, getUserAuthFile } from './fleet/layout';
import { verifySession } from './auth';

/**
 * 平台业务代理（V3 / D10）：浏览器不持有 Java backend 的 accessToken（留在服务端 auth.json），
 * 前端平台页调用 /platform/sign/** ，本代理校验 studio 会话后以存储的 Bearer token 转发到 Java backend。
 * 契约见 docs/04-API重基线-V3.md §2。
 */
export function registerPlatformProxy(app: FastifyInstance): void {
  const routes: Array<[string, string]> = [
    ['/platform/sign/*', '/api/sign'],
    ['/platform/moot/*', '/api/moot'],
    ['/platform/voice/*', '/api/voice'],
    ['/platform/account/*', '/api/account'],
    ['/platform/templates', '/api/templates'],
    ['/platform/templates/*', '/api/templates'],
    ['/platform/evidence', '/api/evidence'],
    ['/platform/evidence/*', '/api/evidence'],
    ['/platform/companies', '/api/companies'],
    ['/platform/companies/*', '/api/companies'],
    ['/platform/review/*', '/api/review'],
  ];
  for (const [url, backendPrefix] of routes) {
    app.route({
      method: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
      url,
      handler: async (request, reply) => {
        const claims = verifySession(request.cookies ?? {}, request.headers.authorization);
        if (!claims) {
          return reply.code(401).send({ code: 401, msg: 'unauthenticated', data: null });
        }
        const scope = normalizeScope(String(claims.sub));
        const file = getUserAuthFile(scope);
        let token: string | null = null;
        try {
          if (existsSync(file)) {
            const stored = JSON.parse(readFileSync(file, 'utf8')) as { accessToken?: string | null };
            token = stored.accessToken ?? null;
          }
        } catch {
          token = null;
        }
        if (!token) {
          return reply.code(401).send({ code: 401, msg: 'no backend token for scope', data: null });
        }
        const params = request.params as Record<string, string>;
        const rest = params['*'] ?? '';
        const qs = request.url.includes('?') ? request.url.slice(request.url.indexOf('?')) : '';
        // 裸路径（rest 为空）不得追加尾斜杠：Spring 6 PathPattern 不匹配 trailing slash
        const target = `${BACKEND_URL}${backendPrefix}${rest ? `/${rest}` : ''}${qs}`;
        const hasBody = request.method === 'POST' || request.method === 'PUT' || request.method === 'PATCH';
        const res = await fetch(target, {
          method: request.method,
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: hasBody ? JSON.stringify(request.body ?? {}) : undefined,
        });
        const data = await res.json().catch(() => null);
        return reply.code(res.status).send(data);
      },
    });
  }
}
