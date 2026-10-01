---
id: plugin-runtime-forced-dedup
title: Force a single physical copy of every plugin dependency by deduplicating mirrors against the OpenClaw runtime
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: Delete every package from a bundled channel-plugin mirror whose name also exists in the OpenClaw runtime bundle, record the dropped names in the mirror, and copy them back from the runtime into a shared extensions node_modules root when a channel is configured — so each package name exists exactly once on disk.
touchedAreas:
  - scripts/openclaw-bundle-config.mjs
  - scripts/after-pack.cjs
  - scripts/assert-single-copy.mjs
  - scripts/pack-openclaw-runtime.mjs
  - scripts/pack-plugin-downloads.mjs
  - scripts/make-download-config.mjs
  - scripts/build-launcher.mjs
  - scripts/installer.nsh
  - resources/cli/win32/set-defender-exclusion.ps1
  - resources/cli/win32/transition-window.ps1
  - resources/cli/win32/launch-transition-window.vbs
  - electron/utils/plugin-shared-deps.ts
  - electron/utils/plugin-install.ts
  - shared/host-api/contract.ts
  - shared/host-events/contract.ts
  - shared/i18n/locales/de/common.json
  - shared/i18n/locales/en/common.json
  - shared/i18n/locales/fr/common.json
  - shared/i18n/locales/zh/common.json
  - electron/main/ipc-handlers.ts
  - src/lib/host-api.ts
  - src/lib/host-events.ts
  - src/components/common/GatewayBootScreen.tsx
  - package.json
  - tests/unit/runtime-manifest.test.ts
  - tests/unit/plugin-dedup.test.ts
  - tests/unit/plugin-shared-deps.test.ts
  - tests/unit/comms-gate.test.ts
  - scripts/comms/replay.mjs
  - scripts/comms/compare.mjs
  - scripts/comms/baseline.mjs
  - docs/startup-performance-plan.md
  - harness/specs/tasks/plugin-runtime-forced-dedup.md
  - harness/specs/tasks/runtime-version-check-and-download.md
expectedUserBehavior:
  - Channel setup still works for all seven bundled channels; the plugin resolves its dependencies from the shared extensions root transparently.
  - No user-visible change in the app: dedup is a packaging concern, and provisioning happens during install/upgrade of a channel plugin.
  - If a plugin cannot resolve a dependency, startup housekeeping logs a warning naming the plugin and the missing packages instead of failing the channel silently later.
requiredProfiles:
  - fast
  - comms
requiredRules:
  - renderer-main-boundary
  - docs-sync
requiredTests:
  - pnpm run typecheck
  - pnpm run lint:check
  - pnpm test
acceptance:
  - after-pack.cjs drops every mirror dependency whose name exists at the top level of the runtime node_modules bundle, regardless of version, except names listed in DEDUP_EXCEPTIONS.
  - The dropped names are recorded in the mirror's shared-deps.json and removed from the mirror's own dependencies/optionalDependencies/peerDependencies.
  - Scoped packages are decided per package (never per scope), and emptied scope directories are removed.
  - A mirror with nothing shared gets no shared-deps.json, so install-time provisioning stays a no-op.
  - plugin-install provisioning copies shared packages from getOpenClawDir()/node_modules into <extensionsRoot>/node_modules, is idempotent, and never throws.
  - Packages missing from the runtime bundle are reported (build inconsistency) rather than silently skipped.
  - auditAndRepairSharedDeps re-provisions installed mirrors at startup and reports mirrors that still cannot resolve.
  - scripts/assert-single-copy.mjs fails the build when any package name exists in both the runtime and a mirror, and treats DEDUP_EXCEPTIONS as informational (failing only with --strict).
  - DEDUP_EXCEPTIONS is empty unless a reproduced, documented failure requires an entry.
  - The comms replay/compare gate actually executes on Windows: scripts derive their root with fileURLToPath so replay writes metrics and compare can fail, instead of both exiting 0 without doing anything.
docs:
  required: false
---

## Scope

Implements section 11 of `docs/startup-performance-plan.md`: build-time forced
dependency deduplication between the OpenClaw runtime bundle and the seven
channel-plugin mirrors, plus install-time provisioning of the dropped packages
into a shared root that Node resolves for every installed mirror.

## Out of scope

- Changing openclaw's or the plugins' own package.json files upstream. The build
  rewrites only the mirror's copy of package.json, for the mirror it emits.
- Version unification at the pnpm resolution layer (`pnpm-workspace.yaml`
  overrides). That is a separate, complementary option; this change is
  deliberately version-agnostic so it holds regardless of what pnpm resolves.
- Per-channel connectivity smoke tests: they require real accounts and are a
  manual release step.