---
id: instant-open-release-shell-before-gateway-ready
title: Release the app shell before the Gateway is ready (instant-open)
scenario: gateway-backend-communication
taskType: runtime-bridge
intent: Stop the full-screen InitializingScreen from blocking the whole UI until the OpenClaw Gateway reaches running. The Gateway cold-boots in ~20-25s; gating the shell on it made the app feel like it "takes minutes to open". Release the shell as soon as the renderer stores initialize, let the Gateway boot in the background, and surface its state through the existing non-blocking banner + per-page states — matching instant-open desktop apps (Cursor / Qoder / WorkBuddy-CodeBuddy). Also paint optimistically from the persisted auth session so a slow backend me() round trip does not gate the shell.
touchedAreas:
  - harness/specs/tasks/instant-open-release-shell-before-gateway-ready.md
  - harness/specs/rules/gateway-readiness-policy.md
  - harness/specs/tasks/gateway-terminal-failure-ux.md
  - src/lib/connection-status.ts
  - src/App.tsx
  - tests/unit/connection-status.test.ts
expectedUserBehavior:
  - Opening the app shows the main shell within ~1-2s (store init), regardless of Gateway state.
  - While the Gateway is starting/reconnecting, a non-blocking banner shows its progress; gateway-dependent pages (Chat sessions/history) load once gatewayReady, other pages are immediately usable.
  - A persisted (rehydrated) authenticated session paints the shell optimistically; me() revalidates in the background and only redirects to login on a definitive rejection.
  - Terminal gateway failure still surfaces the failure dialog; degraded-ready still surfaces the banner. No blocking modal is ever drawn over a rendered page.
requiredProfiles:
  - fast
  - comms
requiredRules:
  - gateway-readiness-policy
  - renderer-main-boundary
requiredTests:
  - pnpm run typecheck
  - pnpm run lint:check
  - tests/unit/connection-status.test.ts
acceptance:
  - shouldHoldBootScreen and BOOT_SCREEN_CAP_MS are removed; the shell gate in src/App.tsx depends only on store init (allInitDone), not on Gateway state.
  - deriveGatewaySurface remains the single decision function for banner/dialog and still never returns null for a broken gateway while global surfaces are mounted.
  - Optimistic auth uses the persisted isAuthenticated so the auth gate does not block a returning user on the me() round trip.
  - No component dims or blocks a rendered page while the Gateway connects.
docs:
  required: false
---

Use this task spec when changing how the renderer gates the app shell on Gateway readiness. The shell must never wait for the Gateway cold boot; Gateway state is communicated through the non-blocking banner and per-page states only.
