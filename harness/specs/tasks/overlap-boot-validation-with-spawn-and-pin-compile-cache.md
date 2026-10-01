---
id: overlap-boot-validation-with-spawn-and-pin-compile-cache
title: Overlap boot session validation with Gateway spawn + pin the V8 compile cache
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: Shave the last desktop-controlled seconds off the path to a usable app without a resident/prewarmed Gateway, without forking OpenClaw, and without disabling plugins. Two levers. (A) Previously the Gateway spawn was gated behind `await validateSessionForBoot()` — a backend round trip — so the ~15-25s cold boot (the long pole) only began AFTER that check returned. Now the spawn is decided from the persisted (cached) session and started immediately, with the authoritative `validateSessionForBoot()` running concurrently; the common authenticated case boots once, ~1-3s sooner. (B) OpenClaw's V8 compile cache defaults to `os.tmpdir()`, which Temp/Disk-Cleanup sweeps silently revert a warm launch into a full cold compile of ~10k loose modules; pin it to userData so compiled bytecode stays reusable across launches.
touchedAreas:
  - harness/specs/tasks/overlap-boot-validation-with-spawn-and-pin-compile-cache.md
  - harness/specs/rules/gateway-readiness-policy.md
  - electron/main/index.ts
  - electron/services/backend-auth-api.ts
  - electron/gateway/process-launcher.ts
expectedUserBehavior:
  - A returning authenticated user sees the Gateway begin booting immediately on launch; time-to-usable drops by roughly the backend round-trip (1-3s) versus the serial order.
  - A persisted logged-out state still defers the auto-start (no Gateway boot while logged out), same as before.
  - If the concurrent authoritative check hits a DEFINITIVE 401 (real token expiry, not a backend hiccup), the optimistically-started Gateway is stopped so the app ends in the logged-out state a serial boot would have produced; the running Gateway never lingers in a stale account scope.
  - Second and later launches stay fast even after Windows Temp cleanup, because the compile cache survives in userData.
requiredProfiles:
  - fast
  - comms
requiredRules:
  - gateway-readiness-policy
requiredTests:
  - pnpm run typecheck
  - pnpm run lint:check
  - pnpm run test
acceptance:
  - PARALLEL_SESSION_VALIDATION_AT_BOOT gates the ordering; with it false the boot keeps the strict validate-then-spawn order (regression escape hatch).
  - startGatewayAuto() is the single shared spawn path (sync provider auth -> gatewayManager.start()) for both parallel and serial branches.
  - peekCachedAuthState() reads the persisted session with NO network call; it is used only to decide whether to auto-start, never to conclude auth is valid.
  - Definitive-401 reconciliation calls resolveSessionState(undefined) which clears auth + flips scope (existing Phase 1.2 semantics), and index.ts stops the optimistically-started Gateway; a merely-slow/erroring backend still keeps the cached session and never logs out.
  - runtimeEnv.NODE_COMPILE_CACHE is set to path.join(app.getPath('userData'), 'openclaw-compile-cache') in the gateway spawn env (both dev and packaged).
  - No resident / prewarmed / keep-alive Gateway process is introduced; OpenClaw source is not forked; plugins are not disabled.
docs:
  required: false
---

Use this task spec when changing the boot-time ordering of session validation vs Gateway spawn, or the Gateway spawn environment. The spawn may be started from the cached session with the authoritative check running concurrently, but a logged-out cached state must still defer the auto-start, and a definitive-401 must reconcile by stopping the Gateway. Keep the V8 compile cache pointed at a durable userData path so warm launches survive temp cleanup.
