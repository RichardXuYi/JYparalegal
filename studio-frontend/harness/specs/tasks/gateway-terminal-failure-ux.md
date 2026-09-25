---
id: gateway-terminal-failure-ux
title: Gateway terminal-failure state + connection UX redesign + startup report
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: >-
  Give the gateway lifecycle a terminal `failed` state with a structured,
  exit-code-aware failure taxonomy so deterministic boot failures (e.g. openclaw
  legacy workspace-state migration EPERM on Windows, exit 78) stop burning the
  3x10 retry budget, and surface a designed, actionable UI (bounded startup
  overlay, non-blocking reconnect banner, terminal failure dialog with
  retry/logs/startup-report/doctor) instead of an endless spinner or a silent
  red dot.
touchedAreas:
  - harness/specs/tasks/gateway-terminal-failure-ux.md
  - shared/types/gateway.ts
  - electron/gateway/failure-taxonomy.ts
  - electron/gateway/startup-report.ts
  - electron/gateway/process-policy.ts
  - electron/gateway/startup-recovery.ts
  - electron/gateway/startup-orchestrator.ts
  - electron/gateway/manager.ts
  - electron/gateway/ws-client.ts
  - electron/services/diagnostics-api.ts
  - shared/host-api/contract.ts
  - src/lib/connection-status.ts
  - src/lib/gateway-status.ts
  - src/stores/gateway.ts
  - src/stores/gateway-ui.ts
  - src/components/common/GatewayConnectOverlay.tsx
  - src/components/common/GatewayStatusBanner.tsx
  - src/components/common/GatewayFailureDialog.tsx
  - src/components/common/GatewayStatusChip.tsx
  - src/components/layout/TopBar.tsx
  - src/mobile/layout/MobileTopBar.tsx
  - src/App.tsx
  - src/components/layout/MainLayout.tsx
  - src/mobile/layout/MobileLayout.tsx
  - src/components/common/InitializingScreen.tsx
  - shared/i18n/locales/{de,en,fr,zh}/common.json
  - tests/unit/failure-taxonomy.test.ts
  - tests/unit/startup-recovery.test.ts
  - tests/unit/process-policy.test.ts
  - tests/unit/startup-report.test.ts
  - tests/unit/connection-status.test.ts
  - vitest.config.ts
  - package.json
  - scripts/dev/fake-gateway-exit78.mjs
  - electron/gateway/config-sync.ts
  - harness/src/specs.mjs
  - harness/specs/rules/gateway-readiness-policy.md
  - harness/specs/scenarios/gateway-startup-diagnostics.md
  - README.md
  - studio-frontend/AGENTS.md
  - studio-web/server/host-core/gateway/* (byte-identical mirror)
  - studio-web/src/** (byte-identical mirror of shared renderer files)
expectedUserBehavior:
  - A deterministic boot failure (exit 78 / migration refusal) reaches a terminal
    failure dialog within one start flow, showing the reason, exit code and
    readiness tier, with Retry / View logs / Copy startup report / Run Doctor.
  - No endless blocking spinner: the startup overlay is bounded and demotes to a
    non-blocking banner; a terminal failure never silently shows only a red dot.
  - Transient flaps still auto-reconnect with a visible attempt counter and
    countdown, and recover without user action.
  - After a terminal failure the client does not auto-retry; recovery is an
    explicit user action (Retry) or app restart.
requiredProfiles:
  - fast
  - comms
requiredRules:
  - gateway-readiness-policy
  - renderer-main-boundary
  - backend-communication-boundary
  - api-client-transport-policy
  - comms-regression
  - docs-sync
requiredTests:
  - tests/unit/failure-taxonomy.test.ts
  - tests/unit/startup-recovery.test.ts
  - tests/unit/process-policy.test.ts
  - tests/unit/startup-report.test.ts
  - tests/unit/connection-status.test.ts
acceptance:
  - GatewayLifecycleState and GatewayStatus.state include a terminal 'failed'
    state; entering it sets shouldReconnect=false and only an explicit
    start()/restart() leaves it.
  - classifyGatewayStartupFailure maps exit 78 / "startup migrations did not
    complete" to a deterministic, non-retryable state-migration-failed failure
    at tier 'spawn'; getGatewayStartupRecoveryAction returns 'fail' for it on
    the first attempt (no 3x10 retry burn).
  - Generic "process exited before becoming ready" without a deterministic
    signature remains transient (no wholesale removal of the transient class).
  - gatewayReady three-state semantics (false/true/undefined) are unchanged;
    readiness is never marked from a pure timer without a system-presence probe.
  - The startup failure report redacts tokens, Bearer values, account ids and
    device identities while keeping platform/version/state-dir/timings/stderr
    tail and the failed readiness tier.
  - Renderer adds no direct ipcRenderer.invoke and no direct Gateway
    fetch/WebSocket; all new surfaces go through host-api/api-client.
  - All user-facing strings route through react-i18next with de/en/fr/zh
    coverage in both apps; no hardcoded display strings remain in the touched
    connection surfaces.
  - The six electron/gateway/* files remain byte-identical with
    studio-web/server/host-core/gateway/*, and the shared renderer files
    (connection-status.ts, gateway-status.ts, stores/gateway.ts, Channels page)
    remain byte-identical across apps.
docs:
  required: true
---

## Notes

- Supersedes the uncommitted stopgap patches in
  `src/components/common/ConnectionStatusModal.tsx` (AUTO_DEMOTE_MS / sticky
  dismiss); that component is deleted and replaced by GatewayConnectOverlay +
  GatewayStatusBanner + GatewayFailureDialog.
- Decision (product): terminal `failed` gets NO automatic cold retry; recovery
  is manual only.
- Decision (scope): desktop + web are kept fully in sync, including the web
  host-core gateway mirror and the web diagnostics port (which also revives the
  currently-dead Channels copy-diagnostics path on web).
- Decision (infra): vitest is introduced in this change; `pnpm test` uses
  `--passWithNoTests` until the unit tests land in Step 5.
