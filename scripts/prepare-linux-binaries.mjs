#!/usr/bin/env node
/**
 * Prepare Linux uv / agent-browser binaries for the studio-web Docker image.
 *
 * Reuses the existing download scripts in studio-frontend (read-only: only
 * `pnpm run` is executed there, no files in studio-frontend are modified),
 * then copies the downloaded `resources/bin/linux-*` folders into
 * `studio-web/resources/bin/` so the Dockerfile can COPY them.
 *
 * Usage (from repo root or studio-web):
 *   node studio-web/scripts/prepare-linux-binaries.mjs
 */
import { execSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(webRoot, '..');
const frontendRoot = join(repoRoot, 'studio-frontend');

if (!existsSync(join(frontendRoot, 'package.json'))) {
  console.error(`[prepare-linux-binaries] studio-frontend not found at ${frontendRoot}`);
  process.exit(1);
}

// 1. Run the existing download scripts in studio-frontend (produces
//    studio-frontend/resources/bin/linux-x64 and linux-arm64).
for (const script of ['uv:download:linux', 'agent-browser:download:linux']) {
  console.log(`[prepare-linux-binaries] pnpm run ${script} (in studio-frontend)`);
  execSync(`pnpm run ${script}`, { cwd: frontendRoot, stdio: 'inherit' });
}

// 2. Copy linux-* target folders into studio-web/resources/bin/.
const srcBin = join(frontendRoot, 'resources', 'bin');
const destBin = join(webRoot, 'resources', 'bin');
const targets = existsSync(srcBin)
  ? readdirSync(srcBin).filter((name) => name.startsWith('linux-'))
  : [];

if (targets.length === 0) {
  console.error('[prepare-linux-binaries] no linux-* folders found after download');
  process.exit(1);
}

mkdirSync(destBin, { recursive: true });
for (const target of targets) {
  const from = join(srcBin, target);
  const to = join(destBin, target);
  console.log(`[prepare-linux-binaries] copy ${from} -> ${to}`);
  cpSync(from, to, { recursive: true, force: true });
}

console.log('[prepare-linux-binaries] done');
