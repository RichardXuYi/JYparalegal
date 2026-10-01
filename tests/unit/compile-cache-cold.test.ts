import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { isOpenClawCompileCacheCold } from '../../electron/utils/paths';

describe('isOpenClawCompileCacheCold', () => {
  let dir: string;

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'compile-cache-'));
  });

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true });
  });

  it('returns true when the directory does not exist', () => {
    expect(isOpenClawCompileCacheCold(join(dir, 'missing'))).toBe(true);
  });

  it('returns true when the directory exists but is empty', () => {
    expect(isOpenClawCompileCacheCold(dir)).toBe(true);
  });

  it('returns false when the cache holds entries', () => {
    mkdirSync(join(dir, 'v1'));
    writeFileSync(join(dir, 'v1', 'entry.blob'), 'x');
    expect(isOpenClawCompileCacheCold(dir)).toBe(false);
  });

  it('returns false (assume warm) when the directory cannot be read', () => {
    // A file where a directory is expected makes readdirSync throw.
    const filePath = join(dir, 'not-a-dir');
    writeFileSync(filePath, 'x');
    expect(isOpenClawCompileCacheCold(filePath)).toBe(false);
  });
});
