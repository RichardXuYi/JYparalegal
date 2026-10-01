#!/usr/bin/env node
/**
 * Move the per-channel plugin archives out of the packaged output and describe
 * them in `plugins-manifest.json` so the app can download the one it needs.
 *
 * Why: the seven channel plugins are ~255 MB of node_modules, but a given user
 * configures at most a few channels. Shipping none of them and fetching per
 * channel keeps the installer small; the mirrors stay self-contained (they are
 * copied to ~/.openclaw/extensions/<id>/ at channel setup), so nothing about
 * plugin dependency resolution changes.
 *
 * scripts/after-pack.cjs has already produced one zip per plugin under
 * `resources/lazy-assets/openclaw-plugins/`. This script:
 *   1. copies them to build/plugins/
 *   2. writes build/plugins/plugins-manifest.json (sha256 + size + fileCount)
 *   3. deletes them from the packaged output
 *
 * Run after a directory package and before scripts/make-download-config.mjs.
 *
 * Usage:
 *   node scripts/pack-plugin-downloads.mjs [--dir <packaged output>] [--out <dir>]
 *                                          [--base-url <url>] [--keep-in-package]
 */
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const AdmZip = require('adm-zip');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_DIR_CANDIDATES = [
  join(ROOT, 'release', 'win-unpacked'),
  join(ROOT, 'release', 'linux-unpacked'),
  join(ROOT, 'release', 'mac'),
];
const BUNDLED_ARCHIVE_DIR = join('resources', 'lazy-assets', 'openclaw-plugins');

function parseArgs(argv) {
  const options = {
    dir: null,
    out: join(ROOT, 'build', 'plugins'),
    baseUrl: process.env.GRANDPOEM_RUNTIME_BASE_URL ?? null,
    keepInPackage: false,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = () => {
      i += 1;
      if (i >= argv.length) throw new Error(`${arg} requires a value`);
      return argv[i];
    };
    if (arg === '--dir') options.dir = join(ROOT, next());
    else if (arg === '--out') options.out = join(ROOT, next());
    else if (arg === '--base-url') options.baseUrl = next();
    else if (arg === '--keep-in-package') options.keepInPackage = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return options;
}

function log(message) {
  console.log(`[plugins] ${message}`);
}

function fail(message) {
  console.error(`[plugins] ERROR: ${message}`);
  process.exit(1);
}

function resolvePackagedDir(explicit) {
  if (explicit) {
    if (!existsSync(explicit)) fail(`packaged output not found: ${explicit}`);
    return explicit;
  }
  const found = DEFAULT_DIR_CANDIDATES.find((candidate) => existsSync(candidate));
  if (!found) fail(`no packaged output found. Run pnpm run package:win:dir first, or pass --dir.`);
  return found;
}

function sha256File(filePath) {
  return createHash('sha256').update(readFileSync(filePath)).digest('hex');
}

function countZipEntries(filePath) {
  try {
    return new AdmZip(filePath).getEntries().filter((entry) => !entry.isDirectory).length;
  } catch {
    return undefined;
  }
}

const options = parseArgs(process.argv.slice(2));
const packagedDir = resolvePackagedDir(options.dir);
const archiveDir = join(packagedDir, BUNDLED_ARCHIVE_DIR);

if (!existsSync(archiveDir)) {
  fail(
    `no plugin archives at ${archiveDir}.\n` +
    `       after-pack.cjs produces them; make sure this build ran with the current after-pack.cjs ` +
    `(older outputs still have an expanded resources/openclaw-plugins/ directory).`,
  );
}

const archives = [];
for (const entry of readdirSync(archiveDir, { withFileTypes: true })) {
  if (!entry.isFile() || !entry.name.endsWith('.zip')) continue;
  const id = entry.name.slice(0, -'.zip'.length);
  const sourcePath = join(archiveDir, entry.name);
  const size = statSync(sourcePath).size;

  mkdirSync(options.out, { recursive: true });
  const targetPath = join(options.out, entry.name);
  copyFileSync(sourcePath, targetPath);

  archives.push({
    id,
    fileName: entry.name,
    sha256: sha256File(sourcePath),
    size,
    fileCount: countZipEntries(sourcePath),
  });

  if (!options.keepInPackage) {
    rmSync(sourcePath, { force: true });
  }
  log(`${id}: ${(size / 1024 / 1024).toFixed(1)} MB`);
}

if (archives.length === 0) {
  fail(`no .zip archives found in ${archiveDir}`);
}

if (!options.baseUrl) {
  log('warning: no --base-url / GRANDPOEM_RUNTIME_BASE_URL given; the manifest will have no URL');
  log('         and plugin downloads will be unavailable in this build.');
}

const manifest = {
  schema: 1,
  platform: process.platform,
  arch: process.arch,
  ...(options.baseUrl ? { baseUrl: options.baseUrl.replace(/\/+$/, '') } : {}),
  archives,
};
const manifestPath = join(options.out, 'plugins-manifest.json');
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf-8');

log(`wrote ${manifestPath} (${archives.length} archives)`);
if (!options.keepInPackage) {
  log(`removed the archives from ${archiveDir} (the installer no longer carries them)`);
}
