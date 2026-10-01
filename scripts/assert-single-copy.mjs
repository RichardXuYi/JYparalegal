#!/usr/bin/env node
/**
 * Build-time assertion: no package name may exist in both the bundled OpenClaw
 * runtime and a plugin mirror.
 *
 * scripts/after-pack.cjs `deduplicatePluginAgainstRuntime()` guarantees this by
 * deleting every mirror dependency the runtime also bundles. This script proves
 * it on the real packaged output, so a future openclaw/plugin upgrade (or a
 * change to the dedup logic) fails the build instead of silently reintroducing
 * duplicate copies.
 *
 * Names listed in DEDUP_EXCEPTIONS are expected to appear twice and are reported
 * as informational, not as failures.
 *
 * Usage:
 *   node scripts/assert-single-copy.mjs [--dir <packaged output>] [--strict]
 */
import { readdirSync, existsSync, readFileSync } from 'node:fs';
import { dirname, isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DEDUP_EXCEPTIONS } from './openclaw-bundle-config.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_DIR_CANDIDATES = [
  join(ROOT, 'release', 'win-unpacked'),
  join(ROOT, 'release', 'linux-unpacked'),
  join(ROOT, 'release', 'mac'),
];

function parseArgs(argv) {
  const options = { dir: null, strict: false };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--dir') {
      i += 1;
      if (i >= argv.length) throw new Error('--dir requires a value');
      // Relative paths resolve from the project root; absolute paths are used as-is.
      options.dir = isAbsolute(argv[i]) ? argv[i] : join(ROOT, argv[i]);
    } else if (argv[i] === '--strict') {
      options.strict = true;
    } else {
      throw new Error(`Unknown argument: ${argv[i]}`);
    }
  }
  return options;
}

function log(message) {
  console.log(`[single-copy] ${message}`);
}

function resolvePackagedDir(explicit) {
  if (explicit) {
    if (!existsSync(explicit)) {
      console.error(`[single-copy] ERROR: packaged output not found: ${explicit}`);
      process.exit(1);
    }
    return explicit;
  }
  const found = DEFAULT_DIR_CANDIDATES.find((candidate) => existsSync(candidate));
  if (!found) {
    console.error('[single-copy] ERROR: no packaged output found. Run pnpm run package:win:dir first, or pass --dir.');
    process.exit(1);
  }
  return found;
}

/** Package names directly under a node_modules directory (scoped => "scope/name"). */
function listPackages(nodeModulesDir) {
  const names = new Set();
  if (!existsSync(nodeModulesDir)) return names;

  for (const entry of readdirSync(nodeModulesDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    if (entry.name === '.bin' || entry.name === '.pnpm') continue;

    if (entry.name.startsWith('@')) {
      const scopeDir = join(nodeModulesDir, entry.name);
      let scoped = [];
      try { scoped = readdirSync(scopeDir, { withFileTypes: true }); } catch { continue; }
      for (const inner of scoped) {
        if (inner.isDirectory()) names.add(`${entry.name}/${inner.name}`);
      }
    } else {
      names.add(entry.name);
    }
  }
  return names;
}

const options = parseArgs(process.argv.slice(2));
const packagedDir = resolvePackagedDir(options.dir);
const resourcesDir = join(packagedDir, 'resources');
const runtimeNodeModules = join(resourcesDir, 'openclaw', 'node_modules');
const pluginsRoot = join(resourcesDir, 'openclaw-plugins');

if (!existsSync(runtimeNodeModules)) {
  console.error(`[single-copy] ERROR: runtime node_modules not found at ${runtimeNodeModules}`);
  process.exit(1);
}

const runtimeNames = listPackages(runtimeNodeModules);
log(`runtime: ${runtimeNames.size} packages`);

if (!existsSync(pluginsRoot)) {
  // Download-mode builds legitimately ship no plugin mirrors.
  log('no plugin mirrors in this build (download mode?) — nothing to compare');
  process.exit(0);
}

const mirrorDirs = readdirSync(pluginsRoot, { withFileTypes: true }).filter((entry) => entry.isDirectory());
if (mirrorDirs.length === 0) {
  log('no plugin mirrors in this build — nothing to compare');
  process.exit(0);
}

const duplicates = new Map(); // packageName -> [mirrorIds]
const allowed = new Map();
let totalMirrorPackages = 0;

for (const mirror of mirrorDirs) {
  const mirrorNM = join(pluginsRoot, mirror.name, 'node_modules');
  const mirrorNames = listPackages(mirrorNM);
  totalMirrorPackages += mirrorNames.size;

  // A mirror that still carries a shared-deps.json must have dropped those names.
  const manifestPath = join(pluginsRoot, mirror.name, 'shared-deps.json');
  let declaredShared = [];
  if (existsSync(manifestPath)) {
    try {
      const parsed = JSON.parse(readFileSync(manifestPath, 'utf-8').replace(/^\uFEFF/, ''));
      declaredShared = Array.isArray(parsed?.sharedDeps) ? parsed.sharedDeps : [];
    } catch {
      log(`${mirror.name}: shared-deps.json is unreadable`);
    }
  }

  for (const name of mirrorNames) {
    if (!runtimeNames.has(name)) continue;
    if (DEDUP_EXCEPTIONS.has(name)) {
      if (!allowed.has(name)) allowed.set(name, []);
      allowed.get(name).push(mirror.name);
      continue;
    }
    if (!duplicates.has(name)) duplicates.set(name, []);
    duplicates.get(name).push(mirror.name);
  }

  // A name listed as shared must NOT still be present in the mirror.
  const returnedShared = declaredShared.filter((name) => mirrorNames.has(name));
  if (returnedShared.length > 0) {
    for (const name of returnedShared) {
      if (!duplicates.has(name)) duplicates.set(name, []);
      if (!duplicates.get(name).includes(mirror.name)) duplicates.get(name).push(mirror.name);
    }
    log(`${mirror.name}: ${returnedShared.length} packages are both "shared" and present in the mirror`);
  }
}

log(`plugin mirrors: ${mirrorDirs.length} (${totalMirrorPackages} packages total)`);

if (allowed.size > 0) {
  log(`${allowed.size} package(s) keep a private copy via DEDUP_EXCEPTIONS:`);
  for (const [name, mirrors] of [...allowed.entries()].sort()) {
    log(`  - ${name} (${mirrors.join(', ')})`);
  }
}

if (duplicates.size > 0) {
  console.error(`[single-copy] FAIL: ${duplicates.size} package name(s) exist in both the runtime and a plugin mirror:`);
  for (const [name, mirrors] of [...duplicates.entries()].sort()) {
    console.error(`  - ${name} (${mirrors.join(', ')})`);
  }
  console.error('[single-copy] Expected every mirror dependency to be dropped by after-pack.cjs (or listed in DEDUP_EXCEPTIONS).');
  process.exit(1);
}

if (options.strict && DEDUP_EXCEPTIONS.size > 0) {
  console.error(`[single-copy] FAIL (--strict): ${DEDUP_EXCEPTIONS.size} DEDUP_EXCEPTIONS entr(ies) remain.`);
  process.exit(1);
}

log('OK: every package name exists at most once');