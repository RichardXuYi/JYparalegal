/**
 * Windows Defender exclusion management ("startup acceleration").
 *
 * The packaged app is ~50k files and unsigned. On a freshly installed machine,
 * the first launch spends most of its time inside Windows Defender: real-time
 * inspection of newly written files, plus a cloud reputation lookup for an
 * executable nobody has seen before. Registering the install tree (and the two
 * runtime data directories) as exclusions removes that cost without weakening
 * protection for anything else on the machine.
 *
 * The actual work is done by resources/cli/win32/set-defender-exclusion.ps1,
 * which elevates itself with a UAC prompt and reports a JSON result. This module
 * only orchestrates that call and remembers the outcome: `Get-MpPreference`
 * itself requires administrator rights, so the status shown in the UI is the
 * result of the last verified run rather than a live query.
 *
 * See docs/startup-performance-plan.md (scheme 1).
 */
import { app, shell } from 'electron';
import { spawn } from 'child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { dirname, join } from 'path';
import type {
  HostSuccess,
  StartupAccelerationErrorCode,
  StartupAccelerationResult,
  StartupAccelerationStatus,
} from '@shared/host-api/contract';
import { logger } from './logger';

const STATE_FILE_NAME = 'startup-acceleration.json';
// Keep SCRIPT_NAME and the target list below in sync with
// resources/cli/win32/set-defender-exclusion.ps1 (which also excludes the two
// process names "GrandPoem Studio.exe" and "openclaw-gateway.exe").
const SCRIPT_NAME = 'set-defender-exclusion.ps1';
const ELEVATION_TIMEOUT_MS = 180_000;

interface PersistedState {
  applied?: boolean;
  lastVerifiedAt?: string;
  blocked?: boolean;
  needsElevation?: boolean;
  installDir?: string;
}

interface ScriptReport {
  ok?: boolean;
  verified?: boolean;
  message?: string;
  tamperProtected?: boolean | null;
  checkedAt?: string;
}

function isDryRun(): boolean {
  return process.env.GRANDPOEM_DEFENDER_DRY_RUN === '1';
}

function isSupported(): boolean {
  return process.platform === 'win32' && app.isPackaged;
}

/** Directory the application is installed in (the .exe's own directory). */
export function resolveInstallDir(): string | null {
  if (process.platform !== 'win32') return null;
  try {
    return dirname(app.getPath('exe'));
  } catch {
    return null;
  }
}

function resolveScriptPath(): string | null {
  const candidates = app.isPackaged
    ? [join(process.resourcesPath, 'cli', SCRIPT_NAME)]
    : [join(process.cwd(), 'resources', 'cli', 'win32', SCRIPT_NAME)];
  return candidates.find((candidate) => existsSync(candidate)) ?? null;
}

/** Paths handed to the exclusion list — mirrors the PowerShell script. */
export function getStartupAccelerationTargets(): string[] {
  const targets: string[] = [];
  const installDir = resolveInstallDir();
  if (installDir) targets.push(installDir);
  try {
    targets.push(join(app.getPath('appData'), 'grandpoem-studio'));
  } catch {
    // ignore
  }
  // The downloaded OpenClaw runtime and the lazy archives live under
  // %LOCALAPPDATA%; without this entry a freshly downloaded runtime would be
  // scanned again on the first Gateway start.
  if (process.platform === 'win32') {
    const localAppData = process.env.LOCALAPPDATA?.trim();
    if (localAppData) {
      targets.push(join(localAppData, 'grandpoem-studio'));
    }
  }
  try {
    targets.push(join(app.getPath('home'), '.openclaw'));
  } catch {
    // ignore
  }
  return targets;
}

function stateFilePath(): string {
  return join(app.getPath('userData'), STATE_FILE_NAME);
}

function readState(): PersistedState {
  try {
    return JSON.parse(readFileSync(stateFilePath(), 'utf-8')) as PersistedState;
  } catch {
    return {};
  }
}

function writeState(state: PersistedState): void {
  try {
    const filePath = stateFilePath();
    mkdirSync(dirname(filePath), { recursive: true });
    writeFileSync(filePath, JSON.stringify(state, null, 2), 'utf-8');
  } catch (error) {
    logger.warn('[defender] Failed to persist startup-acceleration state:', error);
  }
}

function buildStatus(state: PersistedState): StartupAccelerationStatus {
  const supported = isSupported();
  return {
    supported,
    state: state.applied ? 'applied' : 'not-applied',
    targets: supported ? getStartupAccelerationTargets() : [],
    lastVerifiedAt: state.lastVerifiedAt,
    blocked: state.blocked,
    needsElevation: state.needsElevation,
    installDir: resolveInstallDir() ?? undefined,
  };
}

/** Current status, from the last verified run. */
export async function getStartupAccelerationStatus(): Promise<StartupAccelerationStatus> {
  return buildStatus(readState());
}

function failure(
  code: StartupAccelerationErrorCode,
  message: string,
): StartupAccelerationResult {
  return { success: false, error: message, code, status: buildStatus(readState()) };
}

/**
 * Run the helper script. A non-elevated parent waits for the elevated child
 * (the script re-launches itself with `-Verb RunAs`), so the exit code we read
 * here is the real outcome of the operation the user approved in the UAC prompt
 * (or `3` when they declined it).
 */
async function runScript(action: 'add' | 'remove'): Promise<{
  exitCode: number | null;
  report: ScriptReport | null;
  failureReason?: string;
}> {
  const scriptPath = resolveScriptPath();
  if (!scriptPath) {
    return { exitCode: null, report: null, failureReason: `helper script not found (${SCRIPT_NAME})` };
  }

  const installDir = resolveInstallDir();
  if (!installDir) {
    return { exitCode: null, report: null, failureReason: 'cannot resolve the install directory' };
  }

  if (isDryRun()) {
    logger.info(`[defender] dry run: skipping ${action} (GRANDPOEM_DEFENDER_DRY_RUN=1)`);
    return {
      exitCode: 0,
      report: { ok: true, verified: true, message: 'dry-run', checkedAt: new Date().toISOString() },
    };
  }

  const tempDir = mkdtempSync(join(tmpdir(), 'grandpoem-defender-'));
  const resultFile = join(tempDir, 'result.json');

  const args = [
    '-NoProfile',
    '-NonInteractive',
    '-ExecutionPolicy', 'Bypass',
    '-File', scriptPath,
    '-Action', action,
    '-InstallDir', installDir,
    '-ResultFile', resultFile,
  ];

  logger.info(`[defender] Running ${action} (installDir=${installDir})`);

  const exitCode = await new Promise<number | null>((resolve) => {
    let settled = false;
    const finish = (code: number | null): void => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(code);
    };

    const child = spawn('powershell.exe', args, {
      windowsHide: true,
      // The UAC prompt is the user interaction here; no stdio plumbing is needed
      // (and piping would keep a console alive for the elevated child).
      stdio: 'ignore',
      detached: false,
    });

    child.on('error', (error) => {
      logger.warn('[defender] Failed to launch PowerShell:', error);
      finish(null);
    });
    child.on('close', (code) => finish(code));

    const timer = setTimeout(() => {
      logger.warn(`[defender] ${action} timed out after ${ELEVATION_TIMEOUT_MS}ms`);
      try {
        child.kill();
      } catch {
        // ignore
      }
      finish(null);
    }, ELEVATION_TIMEOUT_MS);
  });

  let report: ScriptReport | null;
  try {
    report = JSON.parse(readFileSync(resultFile, 'utf-8')) as ScriptReport;
  } catch {
    report = null;
  }
  rmSync(tempDir, { recursive: true, force: true });

  return { exitCode, report };
}

async function runAction(action: 'add' | 'remove'): Promise<StartupAccelerationResult> {
  if (!isSupported()) {
    return failure(
      'UNSUPPORTED',
      process.platform === 'win32'
        ? 'Startup acceleration is only available in packaged builds'
        : 'Startup acceleration is only available on Windows',
    );
  }

  const { exitCode, report, failureReason } = await runScript(action);
  const previous = readState();
  const now = new Date().toISOString();

  if (failureReason) {
    writeState({ ...previous, blocked: true, needsElevation: false });
    return failure('INTERNAL', failureReason);
  }

  if (exitCode === 0 && report?.verified !== false) {
    const next: PersistedState = {
      applied: action === 'add',
      lastVerifiedAt: report?.checkedAt ?? now,
      blocked: false,
      needsElevation: false,
      installDir: resolveInstallDir() ?? undefined,
    };
    writeState(next);
    logger.info(`[defender] ${action} verified (tamperProtected=${String(report?.tamperProtected ?? 'unknown')})`);
    return { success: true, status: buildStatus(next) };
  }

  if (exitCode === 3) {
    writeState({ ...previous, needsElevation: true, blocked: false });
    return failure('NOT_ELEVATED', 'Administrator approval was declined, so nothing was changed');
  }

  if (exitCode === 4) {
    writeState({ ...previous, blocked: true, needsElevation: false });
    const tamper = report?.tamperProtected ? ' Windows Tamper Protection is enabled.' : '';
    return failure(
      'BLOCKED',
      `Windows did not apply the exclusion.${tamper} Add it manually under Windows Security → Virus & threat protection → Exclusions.`,
    );
  }

  const detail = report?.message ? ` ${report.message}` : '';
  return failure('INTERNAL', `The helper script failed (exit code ${String(exitCode)}).${detail}`);
}

export async function applyStartupAcceleration(): Promise<StartupAccelerationResult> {
  return runAction('add');
}

export async function removeStartupAcceleration(): Promise<StartupAccelerationResult> {
  return runAction('remove');
}

/**
 * Open the Windows Security exclusion page, so a user whose exclusion was
 * blocked (Tamper Protection, group policy) can add it by hand. Main-process
 * `shell.openExternal` is used because the renderer-facing shell route only
 * allows http/https.
 */
export async function openDefenderExclusionSettings(): Promise<HostSuccess> {
  if (process.platform !== 'win32') {
    return { success: false, error: 'Windows only' };
  }
  try {
    await shell.openExternal('windowsdefender://threatsettings');
    return { success: true };
  } catch (error) {
    logger.warn('[defender] Failed to open Windows Security settings:', error);
    return {
      success: false,
      error: 'Could not open Windows Security. Open it manually: Windows Security → Virus & threat protection → Exclusions.',
    };
  }
}
