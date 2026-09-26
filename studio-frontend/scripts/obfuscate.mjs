#!/usr/bin/env node

/**
 * obfuscate.mjs
 *
 * Code protection pipeline for GrandPoem Studio.
 *
 * This script runs AFTER `build:vite` and BEFORE electron-builder.
 * It copies the build outputs to `dist-obfuscated/`, obfuscates all JS files,
 * and optionally compiles the main process entry to V8 bytecode via bytenode.
 *
 * Original `dist/` and `dist-electron/` are NEVER modified.
 *
 * Usage:
 *   node scripts/obfuscate.mjs
 *
 * Environment variables:
 *   SKIP_BYTECODE=1   Skip bytenode bytecode compilation (useful for testing)
 */

import { cpSync, rmSync, existsSync, mkdirSync, readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');
const require = createRequire(import.meta.url);

// ─── Configuration ───────────────────────────────────────────────────────────

const DIST_DIR = join(ROOT, 'dist');
const DIST_ELECTRON_DIR = join(ROOT, 'dist-electron');
const OUTPUT_DIR = join(ROOT, 'dist-obfuscated');
const OUTPUT_DIST = join(OUTPUT_DIR, 'dist');
const OUTPUT_DIST_ELECTRON = join(OUTPUT_DIR, 'dist-electron');

const SKIP_BYTECODE = process.env.SKIP_BYTECODE === '1';

// Obfuscator options for renderer (browser) code
const BROWSER_OBFUSCATOR_OPTIONS = {
  compact: true,
  controlFlowFlattening: true,
  controlFlowFlatteningThreshold: 0.5,
  deadCodeInjection: true,
  deadCodeInjectionThreshold: 0.2,
  stringArray: true,
  stringArrayEncoding: ['base64'],
  stringArrayThreshold: 0.75,
  renameGlobals: false,
  selfDefending: false,
  debugProtection: false,
  target: 'browser',
};

// Obfuscator options for main/preload (node) code
const NODE_OBFUSCATOR_OPTIONS = {
  ...BROWSER_OBFUSCATOR_OPTIONS,
  target: 'node',
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

function getAllJsFiles(dir) {
  const results = [];
  if (!existsSync(dir)) return results;

  function walk(currentDir) {
    let entries;
    try {
      entries = readdirSync(currentDir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const fullPath = join(currentDir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.isFile() && entry.name.endsWith('.js')) {
        results.push(fullPath);
      }
    }
  }

  walk(dir);
  return results;
}

// ─── Step A: Copy build outputs to dist-obfuscated/ ─────────────────────────

function copyBuildOutputs() {
  console.log('[obfuscate] Step A: Copying build outputs to dist-obfuscated/ ...');

  // Clean output directory
  if (existsSync(OUTPUT_DIR)) {
    rmSync(OUTPUT_DIR, { recursive: true, force: true });
  }
  mkdirSync(OUTPUT_DIR, { recursive: true });

  // Copy dist/ (renderer)
  if (existsSync(DIST_DIR)) {
    cpSync(DIST_DIR, OUTPUT_DIST, { recursive: true });
    console.log('[obfuscate]   Copied dist/ -> dist-obfuscated/dist/');
  } else {
    console.warn('[obfuscate]   WARNING: dist/ not found. Run `pnpm run build:vite` first.');
    process.exit(1);
  }

  // Copy dist-electron/ (main + preload)
  if (existsSync(DIST_ELECTRON_DIR)) {
    cpSync(DIST_ELECTRON_DIR, OUTPUT_DIST_ELECTRON, { recursive: true });
    console.log('[obfuscate]   Copied dist-electron/ -> dist-obfuscated/dist-electron/');
  } else {
    console.warn('[obfuscate]   WARNING: dist-electron/ not found. Run `pnpm run build:vite` first.');
    process.exit(1);
  }
}

// ─── Step B: Obfuscate JS files ─────────────────────────────────────────────

function obfuscateFiles() {
  console.log('[obfuscate] Step B: Obfuscating JS files ...');

  const JavaScriptObfuscator = require('javascript-obfuscator');

  // Obfuscate renderer (browser) files
  const rendererFiles = getAllJsFiles(OUTPUT_DIST);
  console.log(`[obfuscate]   Obfuscating ${rendererFiles.length} renderer file(s) (target: browser) ...`);
  for (const file of rendererFiles) {
    const code = readFileSync(file, 'utf8');
    const result = JavaScriptObfuscator.obfuscate(code, BROWSER_OBFUSCATOR_OPTIONS);
    writeFileSync(file, result.getObfuscatedCode(), 'utf8');
  }
  console.log(`[obfuscate]   Done: ${rendererFiles.length} renderer file(s) obfuscated.`);

  // Obfuscate main + preload (node) files
  const electronFiles = getAllJsFiles(OUTPUT_DIST_ELECTRON);
  console.log(`[obfuscate]   Obfuscating ${electronFiles.length} electron file(s) (target: node) ...`);
  for (const file of electronFiles) {
    const code = readFileSync(file, 'utf8');
    const result = JavaScriptObfuscator.obfuscate(code, NODE_OBFUSCATOR_OPTIONS);
    writeFileSync(file, result.getObfuscatedCode(), 'utf8');
  }
  console.log(`[obfuscate]   Done: ${electronFiles.length} electron file(s) obfuscated.`);
}

// ─── Step C: Compile main process to V8 bytecode ────────────────────────────

async function compileBytecode() {
  if (SKIP_BYTECODE) {
    console.log('[obfuscate] Step C: SKIP_BYTECODE=1, skipping bytenode compilation.');
    return;
  }

  console.log('[obfuscate] Step C: Compiling main process to V8 bytecode ...');

  const mainEntry = join(OUTPUT_DIST_ELECTRON, 'main', 'index.js');
  const mainJsc = join(OUTPUT_DIST_ELECTRON, 'main', 'index.jsc');

  if (!existsSync(mainEntry)) {
    console.warn(`[obfuscate]   WARNING: ${mainEntry} not found, skipping bytecode compilation.`);
    return;
  }

  try {
    const bytenode = require('bytenode');
    await bytenode.compileFile({
      filename: mainEntry,
      output: mainJsc,
      electron: true,
    });

    // Replace the JS entry with a bytenode loader
    const loaderCode = "'use strict';\nrequire('bytenode');\nmodule.exports = require('./index.jsc');\n";
    writeFileSync(mainEntry, loaderCode, 'utf8');

    console.log('[obfuscate]   Done: main process compiled to V8 bytecode.');
    console.log(`[obfuscate]   Output: ${mainJsc}`);
  } catch (err) {
    console.warn(`[obfuscate]   WARNING: bytenode compilation failed: ${err.message}`);
    console.warn('[obfuscate]   The obfuscated JS will be used as-is (no bytecode).');
    console.warn('[obfuscate]   To skip this step in the future, set SKIP_BYTECODE=1');
  }
}

// ─── Main ────────────────────────────────────────────────────────────────────

async function main() {
  const startTime = Date.now();
  console.log('[obfuscate] Starting code protection pipeline ...');
  console.log(`[obfuscate] Output directory: ${OUTPUT_DIR}`);
  console.log(`[obfuscate] Bytecode compilation: ${SKIP_BYTECODE ? 'DISABLED' : 'ENABLED'}`);
  console.log('');

  copyBuildOutputs();
  console.log('');

  obfuscateFiles();
  console.log('');

  await compileBytecode();
  console.log('');

  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`[obfuscate] Code protection pipeline completed in ${elapsed}s.`);
}

main().catch((err) => {
  console.error('[obfuscate] FATAL:', err);
  process.exit(1);
});
