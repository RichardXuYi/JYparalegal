#!/usr/bin/env node

/**
 * sanitize-build-scripts.mjs
 *
 * On Windows with non-UTF-8 system locale (e.g. Chinese GBK), zx/esbuild
 * may fail to parse .mjs files that contain emoji or other non-ASCII
 * characters. This script converts all non-ASCII characters in the build
 * scripts to JavaScript \uXXXX escape sequences, producing ASCII-safe
 * copies that parse correctly regardless of system locale.
 *
 * Output: scripts/ascii-safe/
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync, rmSync } from 'fs';
import { join, dirname, basename } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const SCRIPTS_DIR = join(__dirname);
const OUTPUT_DIR = join(SCRIPTS_DIR, 'ascii-safe');

const FILES_TO_SANITIZE = [
  'bundle-openclaw.mjs',
  'bundle-openclaw-plugins.mjs',
  'bundle-preinstalled-skills.mjs',
  'openclaw-bundle-config.mjs',
  'openclaw-self-import-patch.mjs',
];

function escapeNonAscii(content) {
  // Strip BOM if present (Node.js adds it when reading UTF-8 files with BOM)
  if (content.charCodeAt(0) === 0xFEFF) {
    content = content.slice(1);
  }
  let result = '';
  for (let i = 0; i < content.length; i++) {
    const code = content.codePointAt(i);
    if (code > 0x7F) {
      if (code > 0xFFFF) {
        // Surrogate pair - use extended escape
        result += `\\u{${code.toString(16)}}`;
      } else {
        result += `\\u${code.toString(16).padStart(4, '0')}`;
      }
      // Skip the low surrogate if this was a high surrogate
      if (code > 0xFFFF) i++;
    } else {
      result += content[i];
    }
  }
  return result;
}

// Clean output directory
if (existsSync(OUTPUT_DIR)) {
  rmSync(OUTPUT_DIR, { recursive: true, force: true });
}
mkdirSync(OUTPUT_DIR, { recursive: true });

let sanitizedCount = 0;
for (const fileName of FILES_TO_SANITIZE) {
  const filePath = join(SCRIPTS_DIR, fileName);
  if (!existsSync(filePath)) {
    console.log(`[sanitize] SKIP: ${fileName} (not found)`);
    continue;
  }

  const content = readFileSync(filePath, 'utf8');
  const sanitized = escapeNonAscii(content);
  const outputPath = join(OUTPUT_DIR, fileName);
  writeFileSync(outputPath, sanitized, 'utf8');
  sanitizedCount++;
  console.log(`[sanitize] OK: ${fileName}`);
}

console.log(`[sanitize] Done: ${sanitizedCount} file(s) sanitized -> ${OUTPUT_DIR}`);
