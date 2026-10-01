#!/usr/bin/env node
/**
 * Generate the electron-builder config for a "download-mode" build.
 *
 * Bundled mode (today's default) ships the whole OpenClaw runtime inside the
 * installer. Download mode ships only the shell plus `runtime-manifest.json`;
 * the runtime is fetched on demand (see docs/startup-performance-plan.md).
 *
 * Rather than hand-maintaining a third copy of electron-builder.yml (the repo
 * already had to copy it for the obfuscated build, and copies drift), this
 * derives the config from the canonical file:
 *   - drop the `build/openclaw/ -> openclaw/` extraResource
 *   - add `runtime-manifest.json` and `runtime-required.txt` from build/runtime/
 *
 * The generated file lives under build/ (gitignored) and is consumed via
 * `--config build/electron-builder.download.yml`.
 *
 * scripts/after-pack.cjs detects the mode from this config (presence/absence of
 * the openclaw extraResource), so no environment variable is needed.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse, stringify } from 'yaml';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE_CONFIG = join(ROOT, 'electron-builder.yml');
const RUNTIME_DIR = join(ROOT, 'build', 'runtime');
const OUTPUT_CONFIG = join(ROOT, 'build', 'electron-builder.download.yml');

function fail(message) {
  console.error(`[download-config] ERROR: ${message}`);
  process.exit(1);
}

function log(message) {
  console.log(`[download-config] ${message}`);
}

const MANIFEST_ENTRY = { from: 'build/runtime/runtime-manifest.json', to: 'runtime-manifest.json' };
const REQUIRED_ENTRY = { from: 'build/runtime/runtime-required.txt', to: 'runtime-required.txt' };
const PLUGINS_ENTRY = { from: 'build/plugins/plugins-manifest.json', to: 'plugins-manifest.json' };

for (const name of ['runtime-manifest.json', 'runtime-required.txt']) {
  if (!existsSync(join(RUNTIME_DIR, name))) {
    fail(
      `${name} is missing. Produce it first:\n` +
      `  pnpm run package:win:dir && pnpm run runtime:pack -- --archive-url <public base url> --copy-to build/runtime`,
    );
  }
}

const raw = readFileSync(BASE_CONFIG, 'utf-8');
const doc = parse(raw);
if (!doc || typeof doc !== 'object') {
  fail(`could not parse ${BASE_CONFIG}`);
}

const extraResources = Array.isArray(doc.extraResources) ? doc.extraResources : [];
const isOpenClawEntry = (entry) =>
  entry && typeof entry === 'object' && (entry.to === 'openclaw/' || entry.to === 'openclaw');

const withoutRuntime = extraResources.filter((entry) => !isOpenClawEntry(entry));
if (withoutRuntime.length === extraResources.length) {
  fail(
    'the base config no longer has a "build/openclaw -> openclaw/" extraResources entry; ' +
    'download mode would not actually drop the bundled runtime. Update this script.',
  );
}

doc.extraResources = [
  ...withoutRuntime,
  MANIFEST_ENTRY,
  REQUIRED_ENTRY,
];

// Plugin archives are optional: when scripts/pack-plugin-downloads.mjs has run,
// the per-channel zips leave the package and the manifest describes them.
const pluginsManifestPath = join(ROOT, 'build', 'plugins', 'plugins-manifest.json');
if (existsSync(pluginsManifestPath)) {
  doc.extraResources.push(PLUGINS_ENTRY);
  log('added plugins-manifest.json (channel plugins are downloaded per channel)');
} else {
  log('no build/plugins/plugins-manifest.json: channel plugin archives stay in the package');
}

mkdirSync(dirname(OUTPUT_CONFIG), { recursive: true });
writeFileSync(OUTPUT_CONFIG, stringify(doc), 'utf-8');

log(`dropped ${extraResources.length - withoutRuntime.length} bundled-runtime entry from extraResources`);
log(`added runtime-manifest.json and runtime-required.txt`);
log(`wrote ${OUTPUT_CONFIG}`);
