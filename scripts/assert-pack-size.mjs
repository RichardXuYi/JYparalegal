#!/usr/bin/env node
/**
 * Packaged-install size guard.
 *
 * The first launch after installation is dominated by what the OS and the
 * antivirus have to inspect, and that cost scales with the number of files in
 * the install directory (see docs/startup-performance-plan.md). This script
 * fails the build when the packaged tree grows past the agreed budget, so the
 * savings do not silently creep back.
 *
 * Usage:
 *   node scripts/assert-pack-size.mjs [--dir <path>] [--json]
 *
 * Budgets can be overridden for one-off checks:
 *   GRANDPOEM_MAX_PACK_FILES=40000 GRANDPOEM_MAX_PACK_BYTES=1500000000 ...
 */
import { existsSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

const DEFAULT_CANDIDATES = [
  'release/win-unpacked',
  'release/mac',
  'release/mac-arm64',
  'release/linux-unpacked',
];

const MAX_FILES = Number(process.env.GRANDPOEM_MAX_PACK_FILES ?? 35000);
const MAX_BYTES = Number(process.env.GRANDPOEM_MAX_PACK_BYTES ?? 1.2 * 1024 ** 3);

function parseArgs(argv) {
  let target = null;
  let json = false;
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--dir') {
      target = argv[i + 1];
      i += 1;
    } else if (argv[i] === '--json') {
      json = true;
    }
  }
  return { target, json };
}

function resolveTarget(explicit) {
  if (explicit) {
    const abs = resolve(ROOT, explicit);
    if (!existsSync(abs)) throw new Error(`Packaged directory not found: ${abs}`);
    return abs;
  }
  for (const candidate of DEFAULT_CANDIDATES) {
    const abs = join(ROOT, candidate);
    if (existsSync(abs)) return abs;
  }
  throw new Error(`No packaged output found. Looked for: ${DEFAULT_CANDIDATES.join(', ')}`);
}

/** @returns {{ files: number, bytes: number, topLevel: Array<{name: string, files: number, bytes: number}> }} */
function measure(root) {
  const topLevel = new Map();

  const walk = (dir, bucket) => {
    let entries;
    try {
      entries = readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath, bucket);
        continue;
      }
      if (!entry.isFile()) continue;
      let size = 0;
      try {
        size = statSync(fullPath).size;
      } catch {
        size = 0;
      }
      bucket.files += 1;
      bucket.bytes += size;
    }
  };

  for (const entry of readdirSync(root, { withFileTypes: true })) {
    const bucket = { name: entry.name, files: 0, bytes: 0 };
    const fullPath = join(root, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, bucket);
    } else if (entry.isFile()) {
      bucket.files = 1;
      try {
        bucket.bytes = statSync(fullPath).size;
      } catch {
        bucket.bytes = 0;
      }
    } else {
      continue;
    }
    topLevel.set(entry.name, bucket);
  }

  const totals = { files: 0, bytes: 0 };
  for (const bucket of topLevel.values()) {
    totals.files += bucket.files;
    totals.bytes += bucket.bytes;
  }

  return {
    ...totals,
    topLevel: [...topLevel.values()].sort((a, b) => b.files - a.files),
  };
}

function formatBytes(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

const { target, json } = parseArgs(process.argv.slice(2));
const packDir = resolveTarget(target);
const result = measure(packDir);

const violations = [];
if (result.files > MAX_FILES) {
  violations.push(`file count ${result.files} exceeds budget ${MAX_FILES}`);
}
if (result.bytes > MAX_BYTES) {
  violations.push(`size ${formatBytes(result.bytes)} exceeds budget ${formatBytes(MAX_BYTES)}`);
}

if (json) {
  console.log(JSON.stringify({ packDir, limits: { maxFiles: MAX_FILES, maxBytes: MAX_BYTES }, result, violations }, null, 2));
} else {
  console.log(`[pack-size] ${packDir}`);
  console.log(`[pack-size] files=${result.files} (budget ${MAX_FILES})  size=${formatBytes(result.bytes)} (budget ${formatBytes(MAX_BYTES)})`);
  console.log('[pack-size] top-level breakdown (by file count):');
  for (const bucket of result.topLevel.slice(0, 10)) {
    console.log(`[pack-size]   ${bucket.name.padEnd(28)} ${String(bucket.files).padStart(7)} files  ${formatBytes(bucket.bytes).padStart(10)}`);
  }
  for (const violation of violations) {
    console.error(`[pack-size] FAIL: ${violation}`);
  }
  if (violations.length > 0) {
    console.error('[pack-size] Shrink the install tree (see docs/startup-performance-plan.md, scheme 2) or raise the budget deliberately.');
  }
}

process.exit(violations.length > 0 ? 1 : 0);
