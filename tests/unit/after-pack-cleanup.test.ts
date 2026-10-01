import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readdirSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

/**
 * Guards the packaging-time cleanup added for startup performance
 * (docs/startup-performance-plan.md, schemes 2.1 and 2.4). scripts/after-pack.cjs
 * is CommonJS, so it is loaded through createRequire.
 */
const require = createRequire(import.meta.url);
const afterPack = require('../../scripts/after-pack.cjs') as {
  __test: {
    cleanupNativePlatformPackages: (nodeModulesDir: string, platform: string, arch: string) => number;
    pruneRuntimeDocsAndTypes: (
      rootDir: string,
      options?: { removeTypes?: boolean },
    ) => { removedMd: number; removedTs: number; candidateTs: number; freedBytes: number };
  };
};

let workDir: string;

beforeEach(() => {
  workDir = mkdtempSync(join(tmpdir(), 'gp-after-pack-'));
});

afterEach(() => {
  rmSync(workDir, { recursive: true, force: true });
});

describe('cleanupNativePlatformPackages (@trycua)', () => {
  const packages = [
    'cua-driver', // no platform suffix -> must always survive
    'cua-driver-darwin-arm64',
    'cua-driver-darwin-x64',
    'cua-driver-linux-arm64-gnu',
    'cua-driver-linux-x64-gnu',
    'cua-driver-win32-arm64-msvc',
    'cua-driver-win32-x64-msvc',
  ];

  it('keeps only the target platform build and the platform-less package', () => {
    const nodeModules = join(workDir, 'node_modules');
    const scope = join(nodeModules, '@trycua');
    for (const name of packages) {
      mkdirSync(join(scope, name), { recursive: true });
      writeFileSync(join(scope, name, 'driver.bin'), 'x');
    }

    const removed = afterPack.__test.cleanupNativePlatformPackages(nodeModules, 'win32', 'x64');

    expect(removed).toBe(5);
    expect(readdirSync(scope).sort()).toEqual(['cua-driver', 'cua-driver-win32-x64-msvc']);
  });
});

describe('pruneRuntimeDocsAndTypes', () => {
  function seedTree(): string {
    const root = join(workDir, 'openclaw');
    mkdirSync(join(root, 'node_modules', 'pkg'), { recursive: true });
    mkdirSync(join(root, 'dist', 'ext'), { recursive: true });
    mkdirSync(join(root, 'skills', 'my-skill'), { recursive: true });

    writeFileSync(join(root, 'node_modules', 'pkg', 'README.md'), 'docs');
    writeFileSync(join(root, 'node_modules', 'pkg', 'index.ts'), 'source');
    writeFileSync(join(root, 'node_modules', 'pkg', 'index.d.ts'), 'decl');
    writeFileSync(join(root, 'node_modules', 'pkg', 'index.js'), 'runtime');
    writeFileSync(join(root, 'dist', 'ext', 'notes.md'), 'docs');
    writeFileSync(join(root, 'skills', 'my-skill', 'SKILL.md'), 'product content');
    return root;
  }

  it('removes markdown inside runtime trees but never skill content', () => {
    const root = seedTree();

    const result = afterPack.__test.pruneRuntimeDocsAndTypes(root, { removeTypes: false });

    expect(result.removedMd).toBe(2);
    expect(existsSync(join(root, 'skills', 'my-skill', 'SKILL.md'))).toBe(true);
    expect(existsSync(join(root, 'node_modules', 'pkg', 'index.js'))).toBe(true);
    expect(existsSync(join(root, 'node_modules', 'pkg', 'README.md'))).toBe(false);
  });

  it('only counts TypeScript sources unless removal is explicitly requested', () => {
    const root = seedTree();

    const counted = afterPack.__test.pruneRuntimeDocsAndTypes(root, { removeTypes: false });
    expect(counted.candidateTs).toBe(1);
    expect(counted.removedTs).toBe(0);
    expect(existsSync(join(root, 'node_modules', 'pkg', 'index.ts'))).toBe(true);

    const removed = afterPack.__test.pruneRuntimeDocsAndTypes(root, { removeTypes: true });
    expect(removed.removedTs).toBe(1);
    expect(existsSync(join(root, 'node_modules', 'pkg', 'index.ts'))).toBe(false);
    // Declaration files are handled by the pre-existing cleanup pass.
    expect(existsSync(join(root, 'node_modules', 'pkg', 'index.d.ts'))).toBe(true);
  });
});
