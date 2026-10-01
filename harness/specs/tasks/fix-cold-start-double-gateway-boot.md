---
id: fix-cold-start-double-gateway-boot
title: Eliminate the double Gateway boot on cold start (settle auth scope before spawn)
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: Stop the packaged/desktop cold start from booting the OpenClaw Gateway twice. The Gateway auto-started into the persisted account scope before the session was validated; an expired/invalid token then flipped the scope (me() logout) and requested a restart that was deferred until the first ~25s boot finished, killing the just-booted Gateway and reboots it. Validate the session before spawn, only log out on a definitive 401, skip the auto-start when logged out (login starts it), and defer CLI/completion-cache work until the Gateway is ready.
touchedAreas:
  - harness/specs/tasks/fix-cold-start-double-gateway-boot.md
  - electron/main/index.ts
  - electron/services/backend-auth-api.ts
  - electron/gateway/restart-controller.ts
expectedUserBehavior:
  - A cold start with a valid persisted session boots the Gateway exactly once; no deferred scope-change restart follows the first boot.
  - A cold start with an expired/invalid session shows the login page without booting the Gateway into a stale scope; logging in then boots the Gateway once into the correct account scope.
  - A backend 5xx / 402 / 403 / malformed-envelope / network error during the session check no longer logs the user out, flips the OpenClaw scope, or restarts the Gateway.
  - openclaw CLI auto-install and completion-cache generation run after the Gateway reaches ready, not concurrently with its cold boot.
requiredProfiles:
  - fast
  - comms
requiredRules:
  - gateway-readiness-policy
  - backend-communication-boundary
  - renderer-main-boundary
requiredTests:
  - pnpm run typecheck
  - pnpm run lint:check
  - npx vitest run --passWithNoTests
acceptance:
  - "[metric] gateway.startup is logged once per logged-in cold-start session (no immediate second start from a deferred restart)."
  - A normal logged-in cold start logs no "Deferring Gateway restart" line; if one ever occurs, restart-controller logs the requesting stack for diagnosis.
  - me()/resolveSessionState only clears auth and flips scope on a 401 that survives the one-time refresh; other non-OK responses keep the cached session.
  - validateSessionForBoot is bounded (3s default) and its result is reused by the renderer me() so boot pays at most one /api/auth/me round trip.
  - When logged out at boot the Gateway auto-start is skipped and applyScopeChange starts it on login (respecting the gatewayAutoStart setting and syncing provider auth first).
  - Renderer/Main boundary unchanged: no new direct ipcRenderer.invoke calls, no renderer-initiated Gateway restart at boot.
docs:
  required: false
---

Use this task spec when changing desktop cold-start Gateway sequencing, auth-session validation timing, or the scope-change restart path. The root cause and timing evidence are in the cold-start double-boot investigation: the Gateway boots into the persisted scope before the session is validated, so an expired token forces a deferred restart that wastes a full cold boot.
