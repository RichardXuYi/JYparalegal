import { createRequire } from 'node:module';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

/**
 * Guards the forced plugin/runtime deduplication
 * (docs/startup-performance-plan.md §11).
 *
 * scripts/after-pack.cjs is CommonJS, so it is loaded through createRequire.
 */
const require = createRequire(import.meta.url);
const afterPack = require('../../scripts/after-pack.cjs') as {
  __test: {
    deduplicatePluginAgainstRuntime: (
      pluginDestDir: string,
      runtimeNodeModulesDir: string,
    ) => { dropped: string[]; kept: string[]; bytesDropped: number };
    prunePackageJsonDependencies: (pluginDestDir: string, droppedNames: Set<string>) => void;
  };
};

let workDir: string;

/** Create `<root>/<name>/index.js` so package directories look real. */
function seedPackage(nodeModulesDir: string, name: string): string {
  const dir = join(nodeModulesDir, ...name.split('/'));
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'index.js'), `module.exports = ${JSON.stringify(name)};\n`, 'utf-8');
  return dir;
}

function seedRuntime(nodeModulesDir: string, names: string[]): void {
  for (const name of names) seedPackage(nodeModulesDir, name);
}

function seedPluginMirror(pluginDir: string, names: string[], dependencies?: Record<string, string>): void {
  const nm = join(pluginDir, 'node_modules');
  for (const name of names) seedPackage(nm, name);
  writeFileSync(
    join(pluginDir, 'package.json'),
    `${JSON.stringify(
      { name: 'mirror', version: '1.0.0', dependencies: dependencies ?? {} },
      null,
      2,
    )}\n`,
    'utf-8',
  );
}

beforeEach(() => {
  workDir = mkdtempSync(join(tmpdir(), 'gp-plugin-dedup-'));
});

afterEach(() => {
  rmSync(workDir, { recursive: true, force: true });
});

describe('deduplicatePluginAgainstRuntime', () => {
  it('drops every mirror dependency the runtime also bundles and records them', () => {
    const runtimeNM = join(workDir, 'runtime', 'node_modules');
    const pluginDir = join(workDir, 'plugins', 'dingtalk');
    seedRuntime(runtimeNM, ['ws', 'zod', '@scope/shared']);
    seedPluginMirror(pluginDir, ['ws', 'zod', '@scope/shared', 'plugin-only']);

    const result = afterPack.__test.deduplicatePluginAgainstRuntime(pluginDir, runtimeNM);

    expect(result.dropped.sort()).toEqual(['@scope/shared', 'ws', 'zod']);
    expect(result.kept).toEqual(['plugin-only']);

    // Dropped packages are gone from disk...
    expect(existsSync(join(pluginDir, 'node_modules', 'ws'))).toBe(false);
    expect(existsSync(join(pluginDir, 'node_modules', '@scope', 'shared'))).toBe(false);
    // ...but the plugin's own dependency survives.
    expect(existsSync(join(pluginDir, 'node_modules', 'plugin-only'))).toBe(true);
    // ...and an emptied scope directory is removed.
    expect(existsSync(join(pluginDir, 'node_modules', '@scope'))).toBe(false);

    // The shared-deps manifest is what the install-time step reads.
    const manifest = JSON.parse(readFileSync(join(pluginDir, 'shared-deps.json'), 'utf-8'));
    expect(manifest.schema).toBe(1);
    expect([...manifest.sharedDeps].sort()).toEqual(['@scope/shared', 'ws', 'zod']);
  });

  it('prunes the dropped names from the mirror package.json', () => {
    const runtimeNM = join(workDir, 'runtime', 'node_modules');
    const pluginDir = join(workDir, 'plugins', 'wecom');
    seedRuntime(runtimeNM, ['https-proxy-agent', 'mime-types']);
    seedPluginMirror(pluginDir, ['https-proxy-agent', 'mime-types', 'silk-wasm'], {
      'https-proxy-agent': '^5.0.1',
      'mime-types': '^2.1.35',
      'silk-wasm': '^3.0.0',
    });

    afterPack.__test.deduplicatePluginAgainstRuntime(pluginDir, runtimeNM);

    const pkg = JSON.parse(readFileSync(join(pluginDir, 'package.json'), 'utf-8'));
    // No leaked entries: the mirror must not advertise dependencies it dropped.
    expect(Object.keys(pkg.dependencies)).toEqual(['silk-wasm']);
  });

  it('keeps node resolution honest for scoped packages', () => {
    const runtimeNM = join(workDir, 'runtime', 'node_modules');
    const pluginDir = join(workDir, 'plugins', 'mixed');
    // Only one package of the scope is shared: the other must stay private.
    seedRuntime(runtimeNM, ['@scope/shared']);
    seedPluginMirror(pluginDir, ['@scope/shared', '@scope/private']);

    const result = afterPack.__test.deduplicatePluginAgainstRuntime(pluginDir, runtimeNM);

    expect(result.dropped).toEqual(['@scope/shared']);
    expect(result.kept).toEqual(['@scope/private']);
    expect(existsSync(join(pluginDir, 'node_modules', '@scope', 'private'))).toBe(true);
    expect(existsSync(join(pluginDir, 'node_modules', '@scope', 'shared'))).toBe(false);
  });

  it('leaves .bin alone and writes no manifest when nothing is shared', () => {
    const runtimeNM = join(workDir, 'runtime', 'node_modules');
    const pluginDir = join(workDir, 'plugins', 'unique');
    seedRuntime(runtimeNM, ['ws']);
    seedPluginMirror(pluginDir, ['totally-unique']);
    mkdirSync(join(pluginDir, 'node_modules', '.bin'), { recursive: true });
    writeFileSync(join(pluginDir, 'node_modules', '.bin', 'tool.cmd'), 'echo hi\n', 'utf-8');

    const result = afterPack.__test.deduplicatePluginAgainstRuntime(pluginDir, runtimeNM);

    expect(result.dropped).toEqual([]);
    expect(readdirSync(join(pluginDir, 'node_modules'))).toContain('.bin');
    // No shared deps => no manifest, so install-time provisioning stays a no-op.
    expect(existsSync(join(pluginDir, 'shared-deps.json'))).toBe(false);
  });

  it('is a no-op when either side is absent', () => {
    const pluginDir = join(workDir, 'plugins', 'ghost');
    seedPluginMirror(pluginDir, ['ws']);

    const result = afterPack.__test.deduplicatePluginAgainstRuntime(pluginDir, join(workDir, 'missing'));

    expect(result.dropped).toEqual([]);
    expect(existsSync(join(pluginDir, 'node_modules', 'ws'))).toBe(true);
  });
});

describe('prunePackageJsonDependencies', () => {
  it('removes names across dependency maps and tolerates a malformed package.json', () => {
    const pluginDir = join(workDir, 'plugins', 'maps');
    mkdirSync(pluginDir, { recursive: true });
    writeFileSync(
      join(pluginDir, 'package.json'),
      `${JSON.stringify({
        dependencies: { ws: '^8.21.0', keep: '^1.0.0' },
        optionalDependencies: { ws: '^8.21.0' },
        peerDependencies: { ws: '^8.0.0' },
      })}\n`,
      'utf-8',
    );

    afterPack.__test.prunePackageJsonDependencies(pluginDir, new Set(['ws']));

    const pkg = JSON.parse(readFileSync(join(pluginDir, 'package.json'), 'utf-8'));
    expect(pkg.dependencies).toEqual({ keep: '^1.0.0' });
    expect(pkg.optionalDependencies).toEqual({});
    expect(pkg.peerDependencies).toEqual({});

    // A broken file must not throw: packaging continues with the mirror as-is.
    writeFileSync(join(pluginDir, 'package.json'), '{ not json', 'utf-8');
    expect(() => afterPack.__test.prunePackageJsonDependencies(pluginDir, new Set(['ws']))).not.toThrow();
  });
});