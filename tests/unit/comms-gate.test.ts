import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * Regression guard for the comms replay/compare gate.
 *
 * These scripts previously derived their root with
 *   new URL(import.meta.url).pathname
 * which on Windows yields "/D:/Projects/.../replay.mjs" and percent-encodes
 * non-ASCII path segments. path.resolve() then produced "D:\D:\Projects\...",
 * so the scripts resolved a nonexistent root. Because replay/compare also guard
 * their main() behind an `isEntrypoint` check built from that same broken value,
 * the guard was always false: the scripts exited 0 while doing nothing at all.
 * The gate reported success without ever running — the worst possible failure
 * mode for a regression check.
 *
 * fileURLToPath() is the only correct conversion, so these tests assert the
 * scripts actually do their job when invoked the way package.json invokes them.
 */
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const COMMS_DIR = join(ROOT, 'scripts', 'comms');
const ARTIFACTS = join(ROOT, 'artifacts', 'comms');

function runScript(name: string): { status: number; stdout: string } {
  try {
    const stdout = execFileSync(process.execPath, [join(COMMS_DIR, name)], {
      cwd: ROOT,
      encoding: 'utf8',
      stdio: 'pipe',
    });
    return { status: 0, stdout };
  } catch (error) {
    const err = error as { status?: number; stdout?: string; stderr?: string };
    return { status: err.status ?? 1, stdout: `${err.stdout ?? ''}${err.stderr ?? ''}` };
  }
}

describe('comms gate scripts resolve their own paths', () => {
  it('does not build paths with URL.pathname (Windows-breaking)', () => {
    for (const file of ['replay.mjs', 'compare.mjs', 'baseline.mjs']) {
      const source = readFileSync(join(COMMS_DIR, file), 'utf8');
      expect(source, `${file} must not use URL.pathname for filesystem paths`).not.toContain(
        'import.meta.url).pathname',
      );
      expect(source, `${file} should convert file URLs with fileURLToPath`).toContain('fileURLToPath');
    }
  });

  it('replay actually writes metrics instead of silently exiting 0', () => {
    rmSync(ARTIFACTS, { recursive: true, force: true });

    const { status, stdout } = runScript('replay.mjs');

    expect(status).toBe(0);
    expect(stdout).toContain('Wrote comms replay metrics');

    const metricsFile = join(ARTIFACTS, 'current-metrics.json');
    expect(existsSync(metricsFile), 'replay must produce current-metrics.json').toBe(true);

    const metrics = JSON.parse(readFileSync(metricsFile, 'utf8'));
    const scenarios = Object.keys(metrics.scenarios ?? {});
    expect(scenarios.length).toBeGreaterThan(0);
    // Every dataset fixture must be replayed, not a subset.
    expect(scenarios).toContain('happy-path-chat');
    expect(scenarios).toContain('network-degraded');
  });

  it('replay metrics are deterministic across runs', () => {
    const first = runScript('replay.mjs');
    expect(first.status).toBe(0);
    const a = JSON.parse(readFileSync(join(ARTIFACTS, 'current-metrics.json'), 'utf8'));

    const second = runScript('replay.mjs');
    expect(second.status).toBe(0);
    const b = JSON.parse(readFileSync(join(ARTIFACTS, 'current-metrics.json'), 'utf8'));

    // generated_at is a timestamp; everything else must match.
    expect(a.scenarios).toEqual(b.scenarios);
    expect(a.aggregate).toEqual(b.aggregate);
  });

  it('compare exits non-zero when a required scenario is missing', () => {
    runScript('replay.mjs');

    const metricsFile = join(ARTIFACTS, 'current-metrics.json');
    const original = readFileSync(metricsFile, 'utf8');
    try {
      const metrics = JSON.parse(original);
      delete metrics.scenarios['happy-path-chat'];
      writeFileSync(metricsFile, JSON.stringify(metrics, null, 2));

      const { status } = runScript('compare.mjs');
      expect(status, 'compare must fail when a required scenario is absent').not.toBe(0);
    } finally {
      writeFileSync(metricsFile, original);
    }
  });
});