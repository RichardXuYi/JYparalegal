import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import {
  SHARED_DEPS_DIR_NAME,
  SHARED_DEPS_FILE_NAME,
  auditAndRepairSharedDeps,
  listProvisionedSharedDeps,
  provisionSharedDeps,
  readSharedDepsManifest,
} from '../../electron/utils/plugin-shared-deps';

/**
 * Install-time half of the forced dedup (docs/startup-performance-plan.md §11):
 * packages dropped from a plugin mirror at build time are copied from the bundled
 * runtime into the shared `~/.openclaw/extensions/node_modules` root, which Node
 * resolves for every installed mirror.
 *
 * `GRANDPOEM_RUNTIME_DIR` is the documented override for getOpenClawDir(), so the
 * "runtime bundle" is a temp directory here.
 */
let workDir: string;
let runtimeDir: string;
let extensionsRoot: string;
const originalRuntimeOverride = process.env.GRANDPOEM_RUNTIME_DIR;

function seedPackage(nodeModulesDir: string, name: string): string {
  const dir = join(nodeModulesDir, ...name.split('/'));
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'index.js'), `module.exports = ${JSON.stringify(name)};\n`, 'utf-8');
  return dir;
}

function writeMirrorManifest(pluginDir: string, sharedDeps: string[]): void {
  mkdirSync(pluginDir, { recursive: true });
  writeFileSync(
    join(pluginDir, SHARED_DEPS_FILE_NAME),
    `${JSON.stringify({ schema: 1, sharedDeps }, null, 2)}\n`,
    'utf-8',
  );
}

beforeEach(() => {
  workDir = mkdtempSync(join(tmpdir(), 'gp-shared-deps-'));
  runtimeDir = join(workDir, 'runtime');
  mkdirSync(join(runtimeDir, 'node_modules'), { recursive: true });
  extensionsRoot = join(workDir, 'extensions');
  mkdirSync(extensionsRoot, { recursive: true });
  process.env.GRANDPOEM_RUNTIME_DIR = runtimeDir;
});

afterEach(() => {
  if (originalRuntimeOverride === undefined) {
    delete process.env.GRANDPOEM_RUNTIME_DIR;
  } else {
    process.env.GRANDPOEM_RUNTIME_DIR = originalRuntimeOverride;
  }
  rmSync(workDir, { recursive: true, force: true });
});

describe('readSharedDepsManifest', () => {
  it('parses the manifest written at packaging time', () => {
    const pluginDir = join(extensionsRoot, 'dingtalk');
    writeMirrorManifest(pluginDir, ['ws', '@scope/pkg']);

    const manifest = readSharedDepsManifest(pluginDir);

    expect(manifest?.schema).toBe(1);
    expect(manifest?.sharedDeps).toEqual(['ws', '@scope/pkg']);
  });

  it('tolerates a UTF-8 BOM (written on Windows) and ignores junk entries', () => {
    const pluginDir = join(extensionsRoot, 'wecom');
    mkdirSync(pluginDir, { recursive: true });
    writeFileSync(
      join(pluginDir, SHARED_DEPS_FILE_NAME),
      `\uFEFF${JSON.stringify({ schema: 1, sharedDeps: ['ws', 42, '', '@scope/pkg'] })}\n`,
      'utf-8',
    );

    expect(readSharedDepsManifest(pluginDir)?.sharedDeps).toEqual(['ws', '@scope/pkg']);
  });

  it('returns null when the mirror carries its own dependencies', () => {
    const pluginDir = join(extensionsRoot, 'discord');
    mkdirSync(pluginDir, { recursive: true });

    expect(readSharedDepsManifest(pluginDir)).toBeNull();
  });

  it('returns null for a malformed manifest instead of throwing', () => {
    const pluginDir = join(extensionsRoot, 'qqbot');
    mkdirSync(pluginDir, { recursive: true });
    writeFileSync(join(pluginDir, SHARED_DEPS_FILE_NAME), '{ not json', 'utf-8');

    expect(readSharedDepsManifest(pluginDir)).toBeNull();
  });
});

describe('provisionSharedDeps', () => {
  it('copies shared packages from the runtime into the shared root', () => {
    const pluginDir = join(extensionsRoot, 'dingtalk');
    writeMirrorManifest(pluginDir, ['ws', 'zod']);
    seedPackage(join(runtimeDir, 'node_modules'), 'ws');
    seedPackage(join(runtimeDir, 'node_modules'), 'zod');

    const result = provisionSharedDeps(pluginDir, extensionsRoot);

    expect(result.copied.sort()).toEqual(['ws', 'zod']);
    expect(result.missing).toEqual([]);
    // Node resolves <extensions>/node_modules for every mirror, so one copy serves all.
    expect(existsSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME, 'ws', 'index.js'))).toBe(true);
    expect(existsSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME, 'zod', 'index.js'))).toBe(true);
  });

  it('places scoped packages under their scope directory', () => {
    const pluginDir = join(extensionsRoot, 'feishu-openclaw-plugin');
    writeMirrorManifest(pluginDir, ['@larksuiteoapi/node-sdk']);
    seedPackage(join(runtimeDir, 'node_modules'), '@larksuiteoapi/node-sdk');

    const result = provisionSharedDeps(pluginDir, extensionsRoot);

    expect(result.copied).toEqual(['@larksuiteoapi/node-sdk']);
    expect(existsSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME, '@larksuiteoapi', 'node-sdk', 'index.js'))).toBe(true);
  });

  it('is idempotent: a second run copies nothing', () => {
    const pluginDir = join(extensionsRoot, 'wecom');
    writeMirrorManifest(pluginDir, ['ws']);
    seedPackage(join(runtimeDir, 'node_modules'), 'ws');

    provisionSharedDeps(pluginDir, extensionsRoot);
    const second = provisionSharedDeps(pluginDir, extensionsRoot);

    expect(second.copied).toEqual([]);
    expect(second.alreadyPresent).toEqual(['ws']);
  });

  it('reports packages the runtime bundle does not actually carry', () => {
    const pluginDir = join(extensionsRoot, 'qqbot');
    writeMirrorManifest(pluginDir, ['ws', 'never-bundled']);
    seedPackage(join(runtimeDir, 'node_modules'), 'ws');

    const result = provisionSharedDeps(pluginDir, extensionsRoot);

    expect(result.copied).toEqual(['ws']);
    expect(result.missing).toEqual(['never-bundled']);
  });

  it('reports every package as missing when the runtime is unavailable', () => {
    const pluginDir = join(extensionsRoot, 'whatsapp');
    writeMirrorManifest(pluginDir, ['ws', 'zod']);
    rmSync(runtimeDir, { recursive: true, force: true });

    const result = provisionSharedDeps(pluginDir, extensionsRoot);

    expect(result.copied).toEqual([]);
    expect(result.missing.sort()).toEqual(['ws', 'zod']);
  });

  it('is a no-op for a mirror with no shared-deps.json', () => {
    const pluginDir = join(extensionsRoot, 'discord');
    mkdirSync(pluginDir, { recursive: true });

    const result = provisionSharedDeps(pluginDir, extensionsRoot);

    expect(result).toEqual({ copied: [], alreadyPresent: [], missing: [] });
    expect(existsSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME))).toBe(false);
  });
});

describe('listProvisionedSharedDeps', () => {
  it('lists provisioned packages, scoped names included, ignoring .bin', () => {
    seedPackage(join(extensionsRoot, SHARED_DEPS_DIR_NAME), 'ws');
    seedPackage(join(extensionsRoot, SHARED_DEPS_DIR_NAME), '@scope/pkg');
    mkdirSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME, '.bin'), { recursive: true });

    expect(listProvisionedSharedDeps(extensionsRoot).sort()).toEqual(['@scope/pkg', 'ws']);
  });

  it('returns an empty list when nothing was provisioned', () => {
    expect(listProvisionedSharedDeps(join(workDir, 'nowhere'))).toEqual([]);
  });
});

describe('auditAndRepairSharedDeps', () => {
  it('repairs a mirror whose shared root was cleared', () => {
    const pluginDir = join(extensionsRoot, 'dingtalk');
    writeMirrorManifest(pluginDir, ['ws']);
    seedPackage(join(runtimeDir, 'node_modules'), 'ws');
    // Simulate a user/AV that removed the shared root after install.
    rmSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME), { recursive: true, force: true });

    const audit = auditAndRepairSharedDeps(extensionsRoot);

    expect(audit.installedMirrorCount).toBe(1);
    expect(audit.provisionedCount).toBe(1);
    expect(audit.broken).toEqual([]);
    expect(existsSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME, 'ws'))).toBe(true);
  });

  it('reports mirrors that declare a package the runtime does not provide', () => {
    const pluginDir = join(extensionsRoot, 'wecom');
    writeMirrorManifest(pluginDir, ['never-bundled']);

    const audit = auditAndRepairSharedDeps(extensionsRoot);

    expect(audit.broken).toEqual([{ pluginId: 'wecom', missing: ['never-bundled'] }]);
  });

  it('ignores mirrors without a manifest and the shared root itself', () => {
    mkdirSync(join(extensionsRoot, 'discord'), { recursive: true });
    seedPackage(join(extensionsRoot, SHARED_DEPS_DIR_NAME), 'ws');
    // A stray shared-deps.json inside the shared root must not be treated as a mirror.
    writeMirrorManifest(join(extensionsRoot, SHARED_DEPS_DIR_NAME), ['ws']);

    const audit = auditAndRepairSharedDeps(extensionsRoot);

    expect(audit.installedMirrorCount).toBe(0);
    expect(audit.broken).toEqual([]);
  });

  it('returns an empty audit for a missing extensions root', () => {
    expect(auditAndRepairSharedDeps(join(workDir, 'nowhere'))).toEqual({
      broken: [],
      installedMirrorCount: 0,
      provisionedCount: 0,
    });
  });
});

describe('build/install contract', () => {
  it('a manifest produced by after-pack is consumable here', () => {
    // Guards the seam: after-pack writes shared-deps.json, this module reads it.
    const pluginDir = join(extensionsRoot, 'dingtalk');
    writeMirrorManifest(pluginDir, ['https-proxy-agent', 'mime-types']);
    seedPackage(join(runtimeDir, 'node_modules'), 'https-proxy-agent');
    seedPackage(join(runtimeDir, 'node_modules'), 'mime-types');

    const manifest = readSharedDepsManifest(pluginDir);
    expect(manifest?.sharedDeps).toHaveLength(2);

    const result = provisionSharedDeps(pluginDir, extensionsRoot);
    expect(result.copied).toHaveLength(2);
    // The mirror itself still carries no copy: the shared root is the only one.
    expect(existsSync(join(pluginDir, 'node_modules', 'https-proxy-agent'))).toBe(false);
    expect(readFileSync(join(extensionsRoot, SHARED_DEPS_DIR_NAME, 'mime-types', 'index.js'), 'utf-8'))
      .toContain('mime-types');
  });
});