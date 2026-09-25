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
  - tests/unit/connection-status.test.ts
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
- The renderer must surface `failed`/`error` through a visible surface (terminal dialog / status banner). `deriveGatewaySurface` (src/lib/connection-status.ts) is the single decision function; it must never return `null` for a broken gateway while the global surfaces are mounted.
- Every new `GatewayStatus` funnel in the renderer goes through `applyGatewayStatus` in `src/stores/gateway.ts` (records `hasSeenRunningThisSession`, syncs `src/stores/gateway-ui.ts`); connection overlay/banner/dialog components must not block indefinitely (overlay grace ≤ 6s then demote).
