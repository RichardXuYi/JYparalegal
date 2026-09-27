#!/usr/bin/env node

/**
 * fix-script-encoding.mjs
 *
 * Fixes build scripts that have corrupted Unicode characters (emoji that were
 * incorrectly converted to CJK characters through double-encoding).
 * 
 * This script:
 * 1. Strips BOM from files
 * 2. Replaces all non-ASCII characters with ASCII equivalents
 * 3. Fixes syntax issues caused by the replacement (e.g., broken string literals)
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const FILES_TO_FIX = [
  'bundle-openclaw.mjs',
  'bundle-openclaw-plugins.mjs',
  'bundle-preinstalled-skills.mjs',
  'after-pack.cjs',
  'patch-nsis-extract.mjs',
  'patch-nsis-install-section.mjs',
  'patch-nsis-uninstall.mjs',
];

// Map of known corrupted character sequences to their ASCII replacements.
// These are the CJK characters that resulted from emoji double-encoding.
// We replace them based on context (surrounding text).
const REPLACEMENTS = [
  // BOM
  ['\uFEFF', ''],
  
  // Common emoji replacements in echo statements
  ['\u9983\u64DD', '[BUNDLE]'],    // 📦 -> [BUNDLE] (馃摝)
  ['\u9983\u64DD', '[BUNDLE]'],    // alternate
  ['\u9F41\u7259', '[BUNDLE]'],    // 📦 variant
  ['\u2713', '[OK]'],              // ✓
  ['\u2717', '[FAIL]'],            // ✗
  ['\u274C', '[ERROR]'],           // ❌
  ['\u2705', '[OK]'],              // ✅
  ['\u26A0\uFE0F', '[WARN]'],      // ⚠️
  ['\u26A0', '[WARN]'],            // ⚠
  ['\u{1FA79}', '[FIX]'],          // 🩹
  ['\u{1F9F9}', '[CLEAN]'],        // 🧹
  ['\u23ED', '[SKIP]'],            // ⏭
  ['\u2192', '->'],                // →
  ['\u2014', '--'],                // — (em dash)
  ['\u2013', '-'],                 // – (en dash)
];

function fixContent(content) {
  // Strip BOM
  if (content.charCodeAt(0) === 0xFEFF) {
    content = content.slice(1);
  }
  
  // Apply known replacements
  for (const [search, replace] of REPLACEMENTS) {
    content = content.split(search).join(replace);
  }
  
  // Replace any remaining non-ASCII characters with their closest ASCII approximation
  // For CJK characters that are part of corrupted emoji, replace with '?'
  let result = '';
  for (let i = 0; i < content.length; i++) {
    const code = content.codePointAt(i);
    if (code > 0x7F) {
      // Check if this is a known multi-char sequence we haven't handled
      // For any remaining non-ASCII, just use '?' as placeholder
      result += '?';
      if (code > 0xFFFF) i++; // skip low surrogate
    } else {
      result += content[i];
    }
  }
  
  // Fix broken string literals: patterns like '?' where ? was a corrupted char
  // These appear in ternary expressions like: entryExists ? '?' : '?'
  // Replace with proper strings: entryExists ? 'YES' : 'NO'
  result = result.replace(/'\?' : '\?'/g, "'YES' : 'NO'");
  result = result.replace(/'\?'/g, "'OK'");
  
  return result;
}

let fixedCount = 0;
for (const fileName of FILES_TO_FIX) {
  const filePath = join(__dirname, fileName);
  try {
    const content = readFileSync(filePath, 'utf8');
    const fixed = fixContent(content);
    writeFileSync(filePath, fixed, 'utf8');
    fixedCount++;
    console.log(`[fix] OK: ${fileName}`);
  } catch (err) {
    console.error(`[fix] FAIL: ${fileName}: ${err.message}`);
  }
}

console.log(`[fix] Done: ${fixedCount}/${FILES_TO_FIX.length} file(s) fixed.`);
