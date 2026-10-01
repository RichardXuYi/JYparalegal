---
id: add-startup-acceleration-host-api
title: Add Windows Defender startup-acceleration actions to the host API
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: Expose Windows-only startup acceleration (Windows Defender exclusions for the install tree and runtime data directories) through the typed host API, with an installer opt-in and a renderer control in the developer settings section.
touchedAreas:
  - shared/host-api/contract.ts
  - src/lib/host-api.ts
  - src/components/settings/sections/DeveloperSection.tsx
  - shared/i18n/locales/de/settings.json
  - shared/i18n/locales/en/settings.json
  - shared/i18n/locales/fr/settings.json
  - shared/i18n/locales/zh/settings.json
  - electron/services/app-api.ts
  - electron/utils/defender-exclusion.ts
  - electron/utils/bundled-tool.ts
  - electron/utils/lazy-asset.ts
  - electron/utils/startup-timeline.ts
  - electron/main/index.ts
  - electron/utils/plugin-install.ts
  - electron/utils/uv-setup.ts
  - electron/utils/openclaw-cli.ts
  - electron/utils/openclaw-doctor.ts
  - electron/utils/control-ui-device-pairing.ts
  - electron/gateway/config-sync.ts
  - electron/gateway/supervisor.ts
  - electron/utils/telemetry.ts
  - resources/cli/win32/set-defender-exclusion.ps1
  - scripts/installer.nsh
  - scripts/after-pack.cjs
  - scripts/assert-pack-size.mjs
  - docs/startup-performance-plan.md
  - harness/specs/tasks/add-startup-acceleration-host-api.md
expectedUserBehavior:
  - On Windows packaged builds, Settings -> Developer shows a "Faster startup" group with the current exclusion state and an "Add exclusion" button.
  - Applying the exclusion raises a single UAC prompt; declining it leaves the app fully functional and reports "administrator approval declined".
  - The install directory is listed so the user can see exactly what would be excluded.
  - On non-Windows platforms, and in dev builds, the group is either absent or reports that the feature is unavailable.
  - Nothing about the chat, gateway, or provider flows changes when the feature is unused.
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
  - pnpm run comms:replay
  - pnpm run comms:compare
acceptance:
  - HostApiContract.app exposes startupAccelerationStatus, applyStartupAcceleration, removeStartupAcceleration and openDefenderSettings, all typed in shared/host-api/contract.ts (no unknown payloads).
  - The renderer reaches them only through hostApi.app.* in src/lib/host-api.ts; no new window.electron.ipcRenderer.invoke calls are introduced.
  - electron/services/app-api.ts owns the action handlers and delegates to electron/utils/defender-exclusion.ts.
  - The PowerShell helper is the single place that knows the exclusion targets; it exits 3 when not elevated, 4 when the write was rejected, and reports a JSON status file that Main reads back.
  - Status shown in the UI is the result of the last verified run, because reading Get-MpPreference itself requires administrator rights.
  - Installer install/uninstall both go through the same helper, so only the three known paths and two process names are ever touched.
  - All user-facing strings are localized through react-i18next with full de/en/fr/zh coverage.
docs:
  required: false
---

## Scope

Adds a Windows-only, opt-in startup acceleration path and the host API surface
needed to drive it from the renderer. Behavioural context and the measured
motivation live in `docs/startup-performance-plan.md` (scheme 1).

## Out of scope

- Code signing or SmartScreen reputation (the only complete fix for the
  unsigned-binary cost).
- Changing Windows Defender settings beyond the three known paths and two
  process names.
- Automatic elevation during install: a per-user install stays unelevated and
  the exclusion is opt-in from the finish page or from Settings.
