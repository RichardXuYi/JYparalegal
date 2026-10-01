import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import { runOpenClawDoctor, runOpenClawDoctorFix } from '../utils/openclaw-doctor';
import { isRecord } from './payload-utils';

type OpenClawDoctorPayload = {
  mode?: unknown;
};

/**
 * Windows startup acceleration (Windows Defender exclusions) is a desktop-shell
 * concern: it edits the registry and asks Defender to exclude the installed
 * app directory. The web host serves browsers, has no install directory, and
 * cannot touch the user's Defender configuration, so these actions report a
 * well-formed "unsupported" result rather than throwing.
 *
 * Kept structurally identical to studio-frontend/electron/services/app-api.ts so
 * the shared HostApiContract stays satisfied and the renderer's
 * `supported: false` branch works unchanged. See docs/startup-performance-plan.md.
 */
function unsupportedStatus() {
  return {
    supported: false as const,
    state: 'not-applied' as const,
    targets: [] as string[],
  };
}

export function createAppApi(): CompleteHostServiceRegistry['app'] {
  return {
    openClawDoctor: async (payload) => {
      const body = isRecord(payload) ? payload as OpenClawDoctorPayload : {};
      return body.mode === 'fix' ? runOpenClawDoctorFix() : runOpenClawDoctor();
    },
    startupAccelerationStatus: () => unsupportedStatus(),
    applyStartupAcceleration: () => ({
      success: false,
      status: unsupportedStatus(),
      code: 'UNSUPPORTED' as const,
      error: 'Startup acceleration is only available in the Windows desktop app',
    }),
    removeStartupAcceleration: () => ({
      success: false,
      status: unsupportedStatus(),
      code: 'UNSUPPORTED' as const,
      error: 'Startup acceleration is only available in the Windows desktop app',
    }),
    openDefenderSettings: () => ({
      success: false,
      error: 'Windows Security settings are only available in the Windows desktop app',
    }),
  };
}