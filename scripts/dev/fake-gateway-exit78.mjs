// Dev-only fake OpenClaw gateway for deterministic terminal-failure repro.
// Emits the exact stderr signature openclaw 2026.9.6 produces when the legacy
// workspace-state migration hits a Windows fsync EPERM, then exits 78.
//
// Usage (dev build only):
//   GP_GATEWAY_ENTRY_OVERRIDE="$(pwd)/scripts/dev/fake-gateway-exit78.mjs" pnpm dev
const hasMigrationRefusal = true;

process.stderr.write('[state-migration] workspace-state: fsync of legacy state directory failed: EPERM: operation not permitted\n');
if (hasMigrationRefusal) {
  process.stderr.write('[gateway] startup migrations did not complete cleanly; refusing to report the gateway ready\n');
  process.stderr.write('[gateway] exiting with code 78 (EX_CONFIG)\n');
}

setTimeout(() => process.exit(78), 300);
