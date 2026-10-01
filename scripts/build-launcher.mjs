#!/usr/bin/env node
/**
 * Build the tiny native splash launcher (Windows only).
 *
 * The launcher paints instantly while the ~200 MB unsigned main executable is
 * still being scanned and loaded — a window of time in which the app itself
 * cannot draw anything because it has not started yet.
 *
 * Compilation uses the in-box .NET Framework compiler
 * (%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe), so neither Visual
 * Studio nor the .NET SDK is required. When the compiler is unavailable (or on
 * macOS/Linux) the script logs and succeeds: the launcher is an optional
 * cosmetic layer, and the installer falls back to a plain shortcut.
 *
 * Output: resources/bin/<platform>-<arch>/grandpoem-launcher.exe
 *   electron-builder copies resources/bin/win32-<arch>/** to <app>/resources/bin,
 *   and scripts/installer.nsh repoints the shortcuts at it when present.
 *
 * See docs/startup-performance-plan.md (scheme 3b).
 *
 * Usage:
 *   node scripts/build-launcher.mjs [--arch=x64|arm64] [--force]
 */
import { existsSync, mkdirSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

const SOURCE = join(ROOT, 'scripts', 'launcher', 'Splash.cs');
const ICON = join(ROOT, 'resources', 'icons', 'icon.ico');

const args = process.argv.slice(2);
const archArg = args.find((arg) => arg.startsWith('--arch='));
const arch = archArg ? archArg.slice('--arch='.length) : 'x64';
const force = args.includes('--force');

function log(message) {
  console.log(`[launcher] ${message}`);
}

if (process.platform !== 'win32') {
  log(`skipped: not a Windows host (platform=${process.platform})`);
  process.exit(0);
}

const compilerCandidates = [
  join(process.env.WINDIR ?? 'C:\\Windows', 'Microsoft.NET', 'Framework64', 'v4.0.30319', 'csc.exe'),
  join(process.env.WINDIR ?? 'C:\\Windows', 'Microsoft.NET', 'Framework', 'v4.0.30319', 'csc.exe'),
];
const compiler = compilerCandidates.find((candidate) => existsSync(candidate));

if (!compiler) {
  log('skipped: the in-box .NET Framework C# compiler (csc.exe) was not found');
  process.exit(0);
}

if (!existsSync(SOURCE)) {
  log(`ERROR: launcher source not found at ${SOURCE}`);
  process.exit(1);
}

const outDir = join(ROOT, 'resources', 'bin', `win32-${arch}`);
const outFile = join(outDir, 'grandpoem-launcher.exe');

if (!force && existsSync(outFile) && statSync(outFile).mtimeMs > statSync(SOURCE).mtimeMs) {
  log(`up to date: ${outFile}`);
  process.exit(0);
}

mkdirSync(outDir, { recursive: true });

const compileArgs = [
  '/nologo',
  '/target:winexe',
  '/optimize+',
  // The source contains Chinese UI strings; be explicit about its encoding
  // instead of relying on the machine's ANSI code page.
  '/codepage:65001',
  `/out:${outFile}`,
  '/reference:System.dll',
  '/reference:System.Drawing.dll',
  '/reference:System.Windows.Forms.dll',
];
if (existsSync(ICON)) {
  compileArgs.push(`/win32icon:${ICON}`);
}
compileArgs.push(SOURCE);

log(`compiling with ${compiler}`);
const result = spawnSync(compiler, compileArgs, { stdio: 'inherit' });

if (result.error) {
  log(`ERROR: failed to run the compiler: ${result.error.message}`);
  process.exit(1);
}
if (result.status !== 0) {
  log(`ERROR: compiler exited with code ${result.status}`);
  process.exit(1);
}

const size = statSync(outFile).size;
log(`built ${outFile} (${(size / 1024).toFixed(1)} KB)`);
