/**
 * HTTP auth endpoints + WS handshake guard.
 *
 * Login is forwarded to the JY backend through the reused desktop auth
 * service (host-core/services/backend-auth-api.ts → BACKEND_URL/api/auth/*).
 * On success the server issues its own JWT session cookie (HttpOnly), which
 * gates both the SPA's /ws upgrade and subsequent /auth/me checks.
 *
 * Also supports Bearer token authentication for iframe embedding scenarios
 * where cookies cannot be sent cross-origin.
 */
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import jwt from 'jsonwebtoken';
import type { AuthLoginPayload, AuthLoginResult, AuthStateSnapshot, AuthDeviceListResult, AuthRevokeDevicePayload, HostSuccess, AuthUser } from '../../shared/host-api/contract';
import { AUTH_DISABLED, BACKEND_URL, JWT_SECRET, SESSION_COOKIE } from './env';
import { persistUserTokens } from './fleet/backend-login';
import { normalizeScope } from './fleet/layout';

const SESSION_TTL_SECONDS = 7 * 24 * 60 * 60; // 7 days
const REMEMBER_ME_TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days

export type SessionClaims = {
  sub: string;
  name?: string;
};

/**
 * Auth backend. Fleet mode routes by account, so `logout` receives the JWT
 * `sub` (to stop that user's worker) and `me` receives the verified claims
 * (to hydrate the session without a worker round-trip). Legacy single-host
 * implementations may ignore both extra arguments.
 */
type AuthService = {
  login: (payload: AuthLoginPayload) => Promise<AuthLoginResult> | AuthLoginResult;
  logout: (sub?: string | null) => Promise<unknown> | unknown;
  me: (claims?: SessionClaims | null) => Promise<AuthStateSnapshot> | AuthStateSnapshot;
  listDevices?: (claims?: SessionClaims | null) => Promise<AuthDeviceListResult> | AuthDeviceListResult;
  revokeDevice?: (payload: AuthRevokeDevicePayload, claims?: SessionClaims | null) => Promise<HostSuccess> | HostSuccess;
};

export function verifySessionCookie(cookies: Record<string, string | undefined>): SessionClaims | null {
  if (AUTH_DISABLED) {
    return { sub: 'dev' };
  }
  const token = cookies[SESSION_COOKIE];
  if (!token) return null;
  try {
    return jwt.verify(token, JWT_SECRET) as SessionClaims;
  } catch {
    return null;
  }
}

/**
 * Verify session from either Authorization header (Bearer token) or cookie.
 * Used for iframe embedding scenarios where cookies cannot be sent cross-origin.
 */
export function verifySession(
  cookies: Record<string, string | undefined>,
  authHeader?: string | undefined
): SessionClaims | null {
  // 1. Try Authorization header first (Bearer token)
  if (authHeader?.startsWith('Bearer ')) {
    const token = authHeader.slice(7);
    try {
      return jwt.verify(token, JWT_SECRET) as SessionClaims;
    } catch {
      return null;
    }
  }
  // 2. Fall back to cookie (standalone access)
  return verifySessionCookie(cookies);
}

function setSessionCookie(reply: FastifyReply, claims: SessionClaims, rememberMe = false): void {
  const ttl = rememberMe ? REMEMBER_ME_TTL_SECONDS : SESSION_TTL_SECONDS;
  const token = jwt.sign(claims, JWT_SECRET, { expiresIn: ttl });
  reply.setCookie(SESSION_COOKIE, token, {
    path: '/',
    httpOnly: true,
    sameSite: 'lax',
    secure: 'auto',
    maxAge: ttl,
  });
}

export function registerAuthRoutes(app: FastifyInstance, auth: AuthService): void {
  app.post('/auth/login', async (request: FastifyRequest, reply: FastifyReply) => {
    const body = (request.body ?? {}) as Partial<AuthLoginPayload>;
    if (typeof body.username !== 'string' || typeof body.password !== 'string') {
      return reply.code(400).send({ success: false, error: 'username and password are required' });
    }
    try {
      const rememberMe = body.rememberMe === true;
      const result = await auth.login({ username: body.username, password: body.password, rememberMe });
      if (!result.success) {
        return reply.code(401).send(result);
      }
      setSessionCookie(reply, {
        sub: result.user?.id != null ? String(result.user.id) : body.username,
        name: result.user?.username ?? body.username,
      }, rememberMe);
      return reply.send(result);
    } catch (error) {
      return reply.code(502).send({ success: false, error: String(error) });
    }
  });

  app.post('/auth/logout', async (request: FastifyRequest, reply: FastifyReply) => {
    const claims = verifySession(request.cookies ?? {}, request.headers.authorization);
    reply.clearCookie(SESSION_COOKIE, { path: '/' });
    try {
      await auth.logout(claims?.sub ?? null);
    } catch {
      // Backend logout is best-effort; the cookie is gone either way.
    }
    return reply.send({ success: true });
  });

  app.get('/auth/devices', async (request: FastifyRequest, reply: FastifyReply) => {
    const claims = verifySession(request.cookies ?? {}, request.headers.authorization);
    if (!claims) {
      return reply.code(401).send({ success: false, error: 'not_authenticated' });
    }
    if (!auth.listDevices) {
      return reply.code(501).send({ success: false, error: 'not_supported' });
    }
    try {
      const result = await auth.listDevices(claims);
      return reply.send(result);
    } catch (error) {
      return reply.code(502).send({ success: false, error: String(error) });
    }
  });

  app.delete('/auth/devices/:id', async (request: FastifyRequest, reply: FastifyReply) => {
    const claims = verifySession(request.cookies ?? {}, request.headers.authorization);
    if (!claims) {
      return reply.code(401).send({ success: false, error: 'not_authenticated' });
    }
    if (!auth.revokeDevice) {
      return reply.code(501).send({ success: false, error: 'not_supported' });
    }
    const deviceId = Number((request.params as Record<string, string>).id);
    if (!Number.isFinite(deviceId)) {
      return reply.code(400).send({ success: false, error: 'invalid device id' });
    }
    try {
      const result = await auth.revokeDevice({ deviceId }, claims);
      return reply.send(result);
    } catch (error) {
      return reply.code(502).send({ success: false, error: String(error) });
    }
  });

  app.get('/auth/me', async (request: FastifyRequest, reply: FastifyReply) => {
    const claims = verifySession(request.cookies ?? {}, request.headers.authorization);
    if (!claims) {
      return reply.code(401).send({ isAuthenticated: false, user: null });
    }
    try {
      const state = await auth.me(claims);
      if (!state.isAuthenticated && AUTH_DISABLED) {
        // Auth is switched off (local dev): report a dev identity so the SPA
        // does not gate itself behind the login screen.
        return reply.send({ isAuthenticated: true, user: { id: 0, username: 'dev' } });
      }
      return reply.send(state);
    } catch {
      return reply.send({ isAuthenticated: true, user: { id: claims.sub, username: claims.name ?? claims.sub } });
    }
  });

  /**
   * SSO login endpoint for iframe embedding scenarios.
   * Accepts an app's accessToken, validates it against the Spring Boot backend,
   * and issues a studio-web JWT that can be stored in localStorage.
   */
  app.post('/auth/sso-login', async (request: FastifyRequest, reply: FastifyReply) => {
    const body = request.body as { accessToken?: string };
    if (!body.accessToken) {
      return reply.code(400).send({ success: false, error: 'accessToken required' });
    }

    // Validate the app's accessToken against the Spring Boot backend
    const meResp = await fetch(`${BACKEND_URL}/api/auth/me`, {
      headers: { Authorization: `Bearer ${body.accessToken}` }
    });
    if (!meResp.ok) {
      return reply.code(401).send({ success: false, error: 'invalid token' });
    }
    const envelope = await meResp.json() as { data?: { user?: AuthUser } };
    const user = envelope.data?.user;
    if (!user) {
      return reply.code(502).send({ success: false, error: 'user parse failed' });
    }

    // Store tokens in user directory (for Gateway API calls)
    const scope = normalizeScope(String(user.id));
    persistUserTokens(scope, { accessToken: body.accessToken, refreshToken: null, user });

    // Issue studio-web's own JWT (for frontend localStorage)
    const studioJwt = jwt.sign(
      { sub: String(user.id), name: user.username },
      JWT_SECRET,
      { expiresIn: SESSION_TTL_SECONDS }
    );

    // Also set cookie (for non-iframe scenarios)
    setSessionCookie(reply, { sub: String(user.id), name: user.username });

    return reply.send({ success: true, user, token: studioJwt });
  });
}
