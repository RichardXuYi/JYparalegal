/**
 * Verify dedup inside the packaged plugin archives.
 *
 * assert-single-copy.mjs compares runtime vs on-disk plugin mirrors. In the real
 * packaged output the mirrors are compressed into lazy-assets/openclaw-plugins/
 * *.zip, so the assertion finds nothing to compare and passes vacuously. This
 * script opens the archives and checks the same invariant for real: no package
 * name may exist both in the runtime bundle and inside a plugin archive.
 */
import { readdirSync, existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';

const ROOT = process.cwd();
const PACKED = join(ROOT, 'release', 'win-unpacked', 'resources');
const runtimeNM = join(PACKED, 'openclaw', 'node_modules');
const archiveDir = join(PACKED, 'lazy-assets', 'openclaw-plugins');

function listPackages(nodeModulesDir) {
  const names = new Set();
  if (!existsSync(nodeModulesDir)) return names;
  for (const entry of readdirSync(nodeModulesDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    if (entry.name === '.bin' || entry.name === '.pnpm') continue;
    if (entry.name.startsWith('@')) {
      for (const inner of readdirSync(join(nodeModulesDir, entry.name), { withFileTypes: true })) {
        if (inner.isDirectory()) names.add(`${entry.name}/${inner.name}`);
      }
    } else {
      names.add(entry.name);
    }
  }
  return names;
}

const runtimeNames = listPackages(runtimeNM);
console.log(`runtime packages: ${runtimeNames.size}`);

if (!existsSync(archiveDir)) {
  console.error('no plugin archive dir found');
  process.exit(1);
}

const work = mkdtempSync(join(tmpdir(), 'gp-archive-audit-'));
let totalDropped = 0;
let duplicated = 0;

try {
  for (const zip of readdirSync(archiveDir).filter((f) => f.endsWith('.zip')).sort()) {
    const pluginId = zip.replace(/\.zip$/, '');
    const out = join(work, pluginId);
    execFileSync('powershell', [
      '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command',
      `Expand-Archive -LiteralPath '${join(archiveDir, zip)}' -DestinationPath '${out}' -Force`,
    ], { stdio: 'pipe' });

    const mirrorNM = join(out, 'node_modules');
    const mirrorNames = listPackages(mirrorNM);
    totalDropped += 1; // placeholder, replaced below

    // shared-deps.json lists what dedup removed.
    const manifestPath = join(out, 'shared-deps.json');
    let declared = [];
    if (existsSync(manifestPath)) {
      declared = JSON.parse(readFileSync(manifestPath, 'utf-8').replace(/^\uFEFF/, '')).sharedDeps ?? [];
    }

    const overlapping = [...mirrorNames].filter((n) => runtimeNames.has(n));
    const returned = declared.filter((n) => mirrorNames.has(n));
    duplicated += overlapping.length;

    console.log(
      `${pluginId.padEnd(24)} mirror=${String(mirrorNames.size).padStart(3)} ` +
      `sharedDeclared=${String(declared.length).padStart(3)} ` +
      `overlap=${overlapping.length} returned=${returned.length}`,
    );
    if (overlapping.length > 0) console.log(`   DUPLICATES: ${overlapping.slice(0, 8).join(', ')}`);
    if (returned.length > 0) console.log(`   RETURNED (declared shared but still present): ${returned.slice(0, 8).join(', ')}`);
  }
} finally {
  rmSync(work, { recursive: true, force: true });
}

console.log(`\ntotal overlapping package names inside archives: ${duplicated}`);
console.log(duplicated === 0
  ? 'OK: packaged plugin archives contain no package that the runtime also ships'
  : 'FAIL: archives carry duplicates of runtime packages');
process.exit(duplicated === 0 ? 0 : 1);