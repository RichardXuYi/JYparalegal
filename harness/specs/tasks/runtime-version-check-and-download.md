---
id: runtime-version-check-and-download
title: Check the OpenClaw runtime version at install time and provision it on demand
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: Let a build ship without the bundled OpenClaw runtime by pinning the expected runtime version in a build-time manifest, detecting a missing or mismatched runtime during installation, asking the user once, and downloading/verifying/activating the runtime from the main process.
touchedAreas:
  - shared/host-api/contract.ts
  - shared/host-events/contract.ts
  - electron/utils/paths.ts
  - electron/utils/runtime-manifest.ts
  - electron/services/runtime-service.ts
  - electron/services/runtime-api.ts
  - electron/main/ipc-handlers.ts
  - src/lib/host-api.ts
  - src/lib/host-events.ts
  - src/components/common/GatewayBootScreen.tsx
  - shared/i18n/locales/de/common.json
  - shared/i18n/locales/en/common.json
  - shared/i18n/locales/fr/common.json
  - shared/i18n/locales/zh/common.json
  - scripts/pack-openclaw-runtime.mjs
  - scripts/make-download-config.mjs
  - scripts/pack-plugin-downloads.mjs
  - scripts/build-launcher.mjs
  - scripts/after-pack.cjs
  - scripts/installer.nsh
  - resources/cli/win32/set-defender-exclusion.ps1
  - resources/cli/win32/transition-window.ps1
  - resources/cli/win32/launch-transition-window.vbs
  - electron/utils/defender-exclusion.ts
  - package.json
  - tests/unit/runtime-manifest.test.ts
  - tests/unit/comms-gate.test.ts
  - scripts/comms/replay.mjs
  - scripts/comms/compare.mjs
  - scripts/comms/baseline.mjs
  - harness/specs/tasks/plugin-runtime-forced-dedup.md
  - scripts/openclaw-bundle-config.mjs
  - scripts/assert-single-copy.mjs
  - tests/unit/plugin-dedup.test.ts
  - tests/unit/plugin-shared-deps.test.ts
  - docs/startup-performance-plan.md
  - harness/specs/tasks/runtime-version-check-and-download.md
expectedUserBehavior:
  - In download-mode builds, the installer detects that no matching runtime is present and asks once whether it may be downloaded; declining leaves the app fully usable otherwise.
  - On first launch the boot screen shows determinate download progress, then verification and extraction, and reaches the normal gateway boot when done.
  - Cancelling keeps the partial download for a later resume; retrying resumes where it stopped.
  - A failed or blocked download shows the reason plus retry and "choose a local archive"; the app window still opens.
  - Bundled-mode builds behave exactly as before; the installer does not ask anything.
requiredProfiles:
  - fast
  - comms
requiredRules:
  - renderer-main-boundary
  - host-api-fallback-policy
  - docs-sync
requiredTests:
  - pnpm run typecheck
  - pnpm run lint:check
  - pnpm test
acceptance:
  - getOpenClawDir() resolves in the order GRANDPOEM_RUNTIME_DIR -> runtime/current.json (validated) -> bundled resources/openclaw, and a stale pointer falls back instead of failing the Gateway launch.
  - The desired runtime version comes from the build-time runtime-manifest.json, never from the runtime being fetched.
  - Runtime download uses Electron net so it inherits the session proxy; no devDependency-only proxy agent is introduced.
  - The archive is sha256-verified against the manifest before extraction, and extraction refuses absolute or .. entry paths.
  - Activation is atomic (staging directory -> rename -> pointer write) and keeps one previous version for rollback.
  - Consent is recorded in runtime-consent.json shared with the installer, so the user is asked at most once.
  - The runtime host module and its events are reached only through hostApi.runtime.* and hostEvents.onRuntime* in the renderer.
  - All user-facing strings are localized with full de/en/fr/zh coverage.
docs:
  required: false
---

## Scope

Implements section 9 of `docs/startup-performance-plan.md`: a pinned runtime
version, install-time detection with a single consent prompt, and in-app
download/verify/extract/activate with progress, cancel, retry, local-archive
import and rollback.

## Out of scope

- Code signing (the root cause of the cold-start delay stays a certificate
  problem).
- A compatibility *range* for runtime versions: this change pins one exact
  version per shell build.
- In-installer downloading. The installer only detects and records consent;
  the transfer happens in the app where progress, resume, checksum and proxy
  handling are already available.
- The `installRuntime` action in the gateway failure dialog (see the plan's
  section 9.5) and the optional BITS prefetch variant.
