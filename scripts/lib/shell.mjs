/**
 * Shared shell bootstrap for the zx scripts.
 *
 * Why this exists:
 *   zx defaults to bash (`useBash()` -> which.sync('bash')). On Windows,
 *   `bash` frequently resolves to `C:\Windows\System32\bash.exe` — the WSL
 *   stub. When no Linux distribution is installed for WSL, that stub exits
 *   immediately with "execvpe(/bin/bash) failed: No such file or directory",
 *   so every `$`-invocation in the bundling scripts fails even though the
 *   machine has a perfectly good Git for Windows bash.
 *
 * Behaviour of `useWorkingBash()`:
 *   1. Probe candidate bash binaries with a trivial command.
 *   2. Prefer Git for Windows' bash; fall back to any bash that actually runs.
 *   3. Point zx at it via `$.shell`, preserving zx's bash quoting.
 *   4. If nothing works on Windows, fail early with an actionable message
 *      instead of a confusing downstream error.
 */

import { existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

/** Candidate Git-for-Windows bash locations, most specific first. */
function gitBashCandidates() {
  const roots = [
    process.env.ProgramFiles,
    process.env['ProgramFiles(x86)'],
    process.env.LOCALAPPDATA && `${process.env.LOCALAPPDATA}\\Programs`,
    process.env.ProgramW6432,
  ].filter(Boolean);

  const out = [];
  for (const root of roots) {
    out.push(`${root}\\Git\\bin\\bash.exe`);
    out.push(`${root}\\Git\\usr\\bin\\bash.exe`);
  }
  return out;
}

/** True when this bash can actually execute a command. */
function bashWorks(candidate) {
  try {
    execFileSync(candidate, ['-c', 'exit 0'], { stdio: 'ignore', timeout: 10_000 });
    return true;
  } catch {
    return false;
  }
}

/**
 * Point zx at a bash that works, and return its path.
 * Safe to call from every zx script; on non-Windows it leaves zx alone.
 */
export function useWorkingBash() {
  if (process.platform !== 'win32') return '';

  const tried = [];
  for (const candidate of gitBashCandidates()) {
    if (!existsSync(candidate)) continue;
    tried.push(candidate);
    if (bashWorks(candidate)) {
      if (globalThis.$) {
        globalThis.$.shell = candidate;
        // zx's bash flavour: POSIX quoting, no PowerShell prelude.
        globalThis.$.prefix = 'set -euo pipefail;';
        globalThis.$.postfix = '';
        globalThis.$.quote = (arg) => `'${String(arg).replace(/'/g, `'\\''`)}'`;
      }
      console.log(`   (shell) using ${candidate}`);
      return candidate;
    }
  }

  // Nothing from the Git locations worked — try whatever `bash` resolves to,
  // unless it is the WSL stub (which we know is broken here).
  const resolved = process.env.DRY_RUN_BASH || '';
  if (resolved && existsSync(resolved) && bashWorks(resolved)) {
    if (globalThis.$) globalThis.$.shell = resolved;
    return resolved;
  }

  throw new Error(
    'No working bash found. Install Git for Windows (which provides bash), '
    + 'or set the SHELL environment variable to a bash executable.\n'
    + `Probed: ${tried.join(', ') || '(none of the standard Git locations exist)'}`,
  );
}