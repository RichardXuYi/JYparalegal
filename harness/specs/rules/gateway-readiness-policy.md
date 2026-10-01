---
id: gateway-readiness-policy
title: Gateway Readiness Policy
type: ai-coding-rule
appliesTo:
  - gateway-backend-communication
requiredTests:
  - tests/unit/process-policy.test.ts
  - tests/unit/failure-taxonomy.test.ts
  - tests/unit/startup-recovery.test.ts
  - tests/unit/compile-cache-cold.test.ts
---

Gateway status handling must preserve `gatewayReady` semantics.

`gatewayReady: false` means runtime-dependent refreshes should wait. `gatewayReady: true` means the Gateway reported readiness. `gatewayReady: undefined` is backward-compatible with older Gateway versions and must be treated as ready when the Gateway state is running.

Gateway manager fallback may mark readiness true after its timeout, but it must not emit duplicate ready transitions. Readiness must never be marked from a pure timer alone; the fallback must probe `system-presence` first.

## Terminal-state invariants (`failed`)

- `GatewayLifecycleState` / `GatewayStatus.state` include a terminal `'failed'` state. Entering it sets `shouldReconnect = false`, clears any pending reconnect timer, and attaches a structured `failure: GatewayFailureInfo` (code / tier / retryable / i18n reasonKey / suggestedActions / detectedAt).
- `failed` has no automatic recovery. Only an explicit `start()` / `restart()` (user action; `getDeferredRestartAction` must still `execute` a deferred restart from `failed`) leaves it.
- Deterministic startup failures (per `electron/gateway/failure-taxonomy.ts`: exit 78 / migration refusal, invalid config after repair, exhausted ready-poll, second consecutive port-occupied) must reach `failed` within a single start flow — never burn the 3x10 retry budget.
- Signature-less "Gateway process exited before becoming ready" remains transient; do not remove it from the transient class.
- Exhausting the reconnect budget (`DEFAULT_RECONNECT_CONFIG.maxAttempts`) transitions to `failed` with code `max-reconnects-exhausted`, not a silent `error` loop.
- The renderer must surface `failed`/`error` through a visible surface: the terminal `GatewayFailureDialog` (auto-opened via `syncGatewayStatus` in `src/stores/gateway-ui.ts`) plus the `GatewayStatusChip`. **There is no status banner — `GatewayStatusBanner` and `deriveGatewaySurface` were deleted (2026-10-01, owner decision): banners are ugly, unreadable, and a half-ready UI with a "connecting" strip is worse than a proper boot screen.** Do not reintroduce any banner-style gateway strip.
- Every new `GatewayStatus` funnel in the renderer goes through `applyGatewayStatus` in `src/stores/gateway.ts` (records `hasSeenRunningThisSession`, syncs `src/stores/gateway-ui.ts`).

## Boot screen (`GatewayBootScreen`)

- Opening the app shows a launcher-style full-screen boot screen from the first shell paint until the gateway first reaches `running` this session (`!hasSeenRunningThisSession` and state `stopped`/`starting`/`reconnecting`). The user must never land on a browsable-but-unusable UI while the engine boots.
- The manager seeds `firstRun` (cold compile cache, `isOpenClawCompileCacheCold`) into its initial pre-spawn status at construction so the screen can appear instantly, before any gateway event; `firstRun` clears on `running`. It only switches copy (one-time ~1 min warm-up hint vs. warm-boot hold-on hint).
- The screen must show live progress feedback (animated indeterminate bar + elapsed seconds) — a blank/silent wait reads as a hang — and must stay dismissible (skip button after a grace period) so users can force into the app.
- Once the gateway has run this session, the boot screen must never return: mid-session dips (crash → reconnect, degraded readiness) surface via the failure dialog / chip only.
- The app shell itself still releases on store init (`allInitDone` in `src/App.tsx`) — the boot screen is a renderer overlay on top of it, not a gate on store init; there is no `BOOT_SCREEN_CAP_MS` timer.
