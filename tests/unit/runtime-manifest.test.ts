import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import {
  RUNTIME_INSTALL_MARKER_NAME,
  getRuntimeVersionDir,
  isRuntimeVersionCompatible,
  isRuntimeVersionInstalled,
  listInstalledRuntimeVersions,
  normalizeRuntimeVersion,
  pruneRuntimeVersions,
  readRuntimeInstallMarker,
  removeRuntimeVersion,
  writeRuntimeInstallMarker,
} from '../../electron/utils/runtime-manifest';

/**
 * Runtime version model backing the "detect at install, download on demand"
 * flow (see docs/startup-performance-plan.md). These helpers are pure
 * filesystem functions with an injectable root, so they are tested against a
 * temp directory rather than the real user runtime folder.
 */
let root: string;

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), 'gp-runtime-'));
});

afterEach(() => {
  rmSync(root, { recursive: true, force: true });
});

function seedRuntime(version: string, options: { withMarker?: boolean; withEntry?: boolean } = {}): string {
  const dir = getRuntimeVersionDir(version, root);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'package.json'), JSON.stringify({ name: 'openclaw', version }), 'utf-8');
  if (options.withEntry !== false) {
    writeFileSync(join(dir, 'openclaw.mjs'), '// entry', 'utf-8');
  }
  if (options.withMarker !== false) {
    writeRuntimeInstallMarker(version, { version, installedAt: '2026-01-01T00:00:00.000Z', source: 'download' }, root);
  }
  return dir;
}

describe('normalizeRuntimeVersion', () => {
  it('trims whitespace and a trailing newline (the NSIS helper writes a text file)', () => {
    expect(normalizeRuntimeVersion(' 2026.9.6\n')).toBe('2026.9.6');
  });

  it('drops a leading v and SemVer build metadata', () => {
    expect(normalizeRuntimeVersion('v2026.9.6')).toBe('2026.9.6');
    expect(normalizeRuntimeVersion('2026.9.6+build.7')).toBe('2026.9.6');
  });

  it('returns null for empty or non-string input', () => {
    expect(normalizeRuntimeVersion('')).toBeNull();
    expect(normalizeRuntimeVersion('   ')).toBeNull();
    expect(normalizeRuntimeVersion(undefined)).toBeNull();
    expect(normalizeRuntimeVersion(null)).toBeNull();
  });
});

describe('isRuntimeVersionCompatible', () => {
  it('matches on the normalized version', () => {
    expect(isRuntimeVersionCompatible('2026.9.6', '2026.9.6')).toBe(true);
    expect(isRuntimeVersionCompatible('v2026.9.6\n', '2026.9.6')).toBe(true);
  });

  it('treats upgrade and downgrade as equally incompatible', () => {
    expect(isRuntimeVersionCompatible('2026.9.7', '2026.9.6')).toBe(false);
    expect(isRuntimeVersionCompatible('2026.9.5', '2026.9.6')).toBe(false);
  });

  it('is never compatible when either side is unknown', () => {
    expect(isRuntimeVersionCompatible(null, '2026.9.6')).toBe(false);
    expect(isRuntimeVersionCompatible('2026.9.6', null)).toBe(false);
  });
});

describe('install markers and installed-version listing', () => {
  it('treats a seeded runtime as installed and reads back its marker', () => {
    seedRuntime('2026.9.6');

    expect(isRuntimeVersionInstalled('2026.9.6', root)).toBe(true);
    const marker = readRuntimeInstallMarker('2026.9.6', root);
    expect(marker?.version).toBe('2026.9.6');
    expect(marker?.source).toBe('download');
  });

  it('does not treat a half-extracted tree as installed', () => {
    seedRuntime('2026.9.6', { withMarker: false });
    expect(isRuntimeVersionInstalled('2026.9.6', root)).toBe(false);

    seedRuntime('2026.9.7', { withEntry: false });
    expect(isRuntimeVersionInstalled('2026.9.7', root)).toBe(false);
  });

  it('ignores dot-directories such as staging and cache folders', () => {
    seedRuntime('2026.9.6');
    mkdirSync(join(root, '.staging-123'), { recursive: true });
    mkdirSync(join(root, '.cache'), { recursive: true });

    expect(listInstalledRuntimeVersions(root)).toEqual(['2026.9.6']);
  });

  it('lists newest first and prunes only beyond the keep window', () => {
    seedRuntime('2026.9.4');
    seedRuntime('2026.9.5');
    seedRuntime('2026.9.6');
    writeRuntimeInstallMarker('2026.9.5', { version: '2026.9.5', installedAt: '2026-02-01T00:00:00.000Z', source: 'download' }, root);
    writeRuntimeInstallMarker('2026.9.6', { version: '2026.9.6', installedAt: '2026-03-01T00:00:00.000Z', source: 'download' }, root);

    expect(listInstalledRuntimeVersions(root)).toEqual(['2026.9.6', '2026.9.5', '2026.9.4']);

    const removed = pruneRuntimeVersions(1, ['2026.9.5'], root);
    expect(removed).toEqual(['2026.9.4']);
    expect(existsSync(getRuntimeVersionDir('2026.9.4', root))).toBe(false);
    expect(isRuntimeVersionInstalled('2026.9.6', root)).toBe(true);
    expect(isRuntimeVersionInstalled('2026.9.5', root)).toBe(true);
  });

  it('removes a version directory on request', () => {
    seedRuntime('2026.9.6');
    removeRuntimeVersion('2026.9.6', root);
    expect(existsSync(getRuntimeVersionDir('2026.9.6', root))).toBe(false);
  });

  it('writes the marker under the normalized version directory', () => {
    writeRuntimeInstallMarker('v2026.9.6', { version: 'v2026.9.6', installedAt: '', source: 'import' }, root);
    expect(existsSync(join(getRuntimeVersionDir('2026.9.6', root), RUNTIME_INSTALL_MARKER_NAME))).toBe(true);
    const marker = readRuntimeInstallMarker('2026.9.6', root);
    expect(marker?.version).toBe('2026.9.6');
    expect(marker?.source).toBe('import');
    // An empty installedAt is filled in rather than written as an empty string.
    expect(marker?.installedAt).not.toBe('');
  });
});
