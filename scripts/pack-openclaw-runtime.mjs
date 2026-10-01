#!/usr/bin/env node
/**
 * Pack the packaged OpenClaw runtime into a downloadable archive plus manifest.
 *
 * Why: the runtime is ~850 MB / 40k files. Bundling it inside the installer
 * means every user pays for it in install time and antivirus scanning; shipping
 * it as a separately hosted archive lets the shell decide at install time
 * whether the machine already has a matching runtime
 * (see docs/startup-performance-plan.md, "运行时版本核对与按需下载").
 *
 * Input is the *already pruned* runtime from a `package:win:dir` style build
 * (after-pack removes foreign-platform binaries, docs, markdown and optionally
 * TypeScript sources), so the archive inherits every size optimisation.
 *
 * Usage:
 *   node scripts/pack-openclaw-runtime.mjs [options]
 *   node scripts/pack-openclaw-runtime.mjs --verify
 *
 * Options:
 *   --input <dir>            runtime directory (default: auto-detect under release/)
 *   --out <dir>              output directory (default: release/runtime)
 *   --archive-url <url>      public URL of the archive, stamped into the manifest
 *   --min-shell-version <v>  oldest shell allowed to use this runtime (default: package.json version)
 *   --copy-to <dir>          also copy artifacts here (staging share / upload folder)
 *   --gzip-level <0-9>       gzip level (default: 6)
 *   --verify                 verify an existing manifest against its archive instead of packing
 */
import { createHash } from 'node:crypto';
import { createReadStream, copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const tar = require('tar');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const MANIFEST_NAME = 'runtime-manifest.json';
const REQUIRED_NAME = 'runtime-required.txt';
const ARCHIVE_PREFIX = 'openclaw-runtime';

function parseArgs(argv) {
  const options = {
    input: null,
    out: join(ROOT, 'release', 'runtime'),
    archiveUrl: process.env.GRANDPOEM_RUNTIME_BASE_URL
      ? `${process.env.GRANDPOEM_RUNTIME_BASE_URL.replace(/\/+$/, '')}/`
      : null,
    minShellVersion: null,
    copyTo: null,
    gzipLevel: 6,
    verify: false,
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = () => {
      i += 1;
      if (i >= argv.length) throw new Error(`${arg} requires a value`);
      return argv[i];
    };
    if (arg === '--input') options.input = resolve(ROOT, next());
    else if (arg === '--out') options.out = resolve(ROOT, next());
    else if (arg === '--archive-url') options.archiveUrl = next();
    else if (arg === '--min-shell-version') options.minShellVersion = next();
    else if (arg === '--copy-to') options.copyTo = resolve(ROOT, next());
    else if (arg === '--gzip-level') options.gzipLevel = Number(next());
    else if (arg === '--verify') options.verify = true;
    else if (arg === '--help' || arg === '-h') options.help = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return options;
}

function log(message) {
  console.log(`[runtime] ${message}`);
}

function fail(message) {
  console.error(`[runtime] ERROR: ${message}`);
  process.exit(1);
}

/** Candidate runtime directories inside a packaged output. */
function resolveInputDir(explicit) {
  if (explicit) {
    if (!existsSync(explicit)) fail(`runtime directory not found: ${explicit}`);
    return explicit;
  }

  const candidates = [
    join(ROOT, 'release', 'win-unpacked', 'resources', 'openclaw'),
    join(ROOT, 'release', 'linux-unpacked', 'resources', 'openclaw'),
    join(ROOT, 'release', 'mac', 'GrandPoem Studio.app', 'Contents', 'Resources', 'openclaw'),
    join(ROOT, 'release', 'mac-arm64', 'GrandPoem Studio.app', 'Contents', 'Resources', 'openclaw'),
  ];
  const found = candidates.find((candidate) => existsSync(candidate));
  if (!found) {
    fail(
      `no packaged runtime found. Run a directory package first (pnpm run package:win:dir) ` +
      `or pass --input <dir>. Looked in:\n  ${candidates.join('\n  ')}`,
    );
  }
  return found;
}

/** Recursive file count and total size. */
function measureTree(dir) {
  let files = 0;
  let bytes = 0;
  const stack = [dir];
  while (stack.length > 0) {
    const current = stack.pop();
    let entries;
    try {
      entries = readdirSync(current, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      const full = join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (entry.isFile()) {
        files += 1;
        try {
          bytes += statSync(full).size;
        } catch {
          // ignore racing files
        }
      }
    }
  }
  return { files, bytes };
}

function sha256File(filePath) {
  return new Promise((resolvePromise, rejectPromise) => {
    const hash = createHash('sha256');
    const stream = createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('error', rejectPromise);
    stream.on('end', () => resolvePromise(hash.digest('hex')));
  });
}

function readJson(filePath) {
  // Strip a UTF-8 BOM: Windows editors add one, and JSON.parse rejects it.
  return JSON.parse(readFileSync(filePath, 'utf-8').replace(/^\uFEFF/, ''));
}

function formatBytes(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function pack(options) {
  const inputDir = resolveInputDir(options.input);
  const pkgPath = join(inputDir, 'package.json');
  if (!existsSync(pkgPath)) fail(`no package.json in ${inputDir}`);
  const runtimeVersion = String(readJson(pkgPath).version || '').trim();
  if (!runtimeVersion) fail(`package.json in ${inputDir} has no version`);
  if (!existsSync(join(inputDir, 'openclaw.mjs'))) {
    log(`warning: ${inputDir} has no openclaw.mjs — is this a complete runtime?`);
  }

  const platform = process.platform;
  const arch = process.arch;
  const archiveName = `${ARCHIVE_PREFIX}-${runtimeVersion}-${platform}-${arch}.tar.gz`;
  const archivePath = join(options.out, archiveName);

  mkdirSync(options.out, { recursive: true });

  const tree = measureTree(inputDir);
  log(`packing ${inputDir}`);
  log(`  version=${runtimeVersion} platform=${platform} arch=${arch} files=${tree.files} size=${formatBytes(tree.bytes)}`);

  const startedAt = Date.now();
  await tar.c(
    {
      gzip: { level: Number.isFinite(options.gzipLevel) ? options.gzipLevel : 6 },
      file: archivePath,
      cwd: inputDir,
      // Deterministic-ish metadata so rebuilds of unchanged trees stay close.
      portable: true,
      follow: false,
    },
    ['.'],
  );
  const archiveBytes = statSync(archivePath).size;
  const sha256 = await sha256File(archivePath);
  log(`  archive=${archiveName} (${formatBytes(archiveBytes)}, ${Date.now() - startedAt}ms)`);
  log(`  sha256=${sha256}`);

  const shellVersion = String(readJson(join(ROOT, 'package.json')).version || '').trim();
  let archiveUrl = options.archiveUrl;
  if (archiveUrl && !archiveUrl.endsWith('/')) archiveUrl += '/';

  const manifest = {
    schema: 1,
    runtimeVersion,
    platform,
    arch,
    ...(archiveUrl ? { archiveUrl: `${archiveUrl}${archiveName}` } : {}),
    sha256,
    size: archiveBytes,
    fileCount: tree.files,
    minShellVersion: options.minShellVersion || shellVersion || undefined,
  };

  const manifestPath = join(options.out, MANIFEST_NAME);
  const requiredPath = join(options.out, REQUIRED_NAME);
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf-8');
  // One line, no trailing whitespace beyond the newline: read by NSIS FileRead.
  writeFileSync(requiredPath, `${runtimeVersion}\n`, 'utf-8');
  log(`  wrote ${MANIFEST_NAME} and ${REQUIRED_NAME}`);

  if (!archiveUrl) {
    log('note: no --archive-url / GRANDPOEM_RUNTIME_BASE_URL given, manifest has no archiveUrl');
    log('      (download-mode builds need it; pass the public base URL when publishing)');
  }

  if (options.copyTo) {
    mkdirSync(options.copyTo, { recursive: true });
    for (const name of [archiveName, MANIFEST_NAME, REQUIRED_NAME]) {
      copyFileSync(join(options.out, name), join(options.copyTo, name));
    }
    log(`  copied artifacts to ${options.copyTo}`);
  }

  log('done. Upload the three artifacts next to your existing release files, e.g.');
  log(`  <release-base>/runtime/${archiveName}`);
  log(`  <release-base>/runtime/${MANIFEST_NAME}`);
  log(`  <release-base>/runtime/${REQUIRED_NAME}`);
}

async function verify(options) {
  const manifestPath = join(options.out, MANIFEST_NAME);
  if (!existsSync(manifestPath)) fail(`no manifest at ${manifestPath} (did you pack first?)`);
  const manifest = readJson(manifestPath);
  const archiveName = `${ARCHIVE_PREFIX}-${manifest.runtimeVersion}-${manifest.platform ?? process.platform}-${manifest.arch ?? process.arch}.tar.gz`;
  const archivePath = join(options.out, archiveName);
  if (!existsSync(archivePath)) fail(`manifest references a missing archive: ${archivePath}`);

  const size = statSync(archivePath).size;
  const sha256 = await sha256File(archivePath);

  const problems = [];
  if (manifest.size !== size) problems.push(`size mismatch: manifest=${manifest.size} actual=${size}`);
  if (manifest.sha256 !== sha256) problems.push(`sha256 mismatch: manifest=${manifest.sha256} actual=${sha256}`);

  const requiredPath = join(options.out, REQUIRED_NAME);
  if (existsSync(requiredPath)) {
    const required = readFileSync(requiredPath, 'utf-8').trim();
    if (required !== manifest.runtimeVersion) {
      problems.push(`${REQUIRED_NAME} says "${required}" but manifest says "${manifest.runtimeVersion}"`);
    }
  } else {
    problems.push(`${REQUIRED_NAME} is missing`);
  }

  if (problems.length > 0) {
    for (const problem of problems) console.error(`[runtime] FAIL: ${problem}`);
    process.exit(1);
  }

  log(`verify OK: ${archiveName} (${formatBytes(size)}, version ${manifest.runtimeVersion}, files ${manifest.fileCount})`);
}

const options = parseArgs(process.argv.slice(2));
if (options.help) {
  console.log(readFileSync(fileURLToPath(import.meta.url), 'utf-8').split('\n').slice(1, 30).join('\n'));
  process.exit(0);
}

if (options.verify) {
  await verify(options);
} else {
  await pack(options);
}
