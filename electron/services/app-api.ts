import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import { runOpenClawDoctor, runOpenClawDoctorFix } from '../utils/openclaw-doctor';
import {
  applyStartupAcceleration,
  getStartupAccelerationStatus,
  openDefenderExclusionSettings,
  removeStartupAcceleration,
} from '../utils/defender-exclusion';
import { isRecord } from './payload-utils';

type OpenClawDoctorPayload = {
  mode?: unknown;
};

export function createAppApi(): CompleteHostServiceRegistry['app'] {
  return {
    openClawDoctor: async (payload) => {
      const body = isRecord(payload) ? payload as OpenClawDoctorPayload : {};
      return body.mode === 'fix' ? runOpenClawDoctorFix() : runOpenClawDoctor();
    },
    // Windows-only startup acceleration (Windows Defender exclusions).
    // See docs/startup-performance-plan.md (scheme 1).
    startupAccelerationStatus: () => getStartupAccelerationStatus(),
    applyStartupAcceleration: () => applyStartupAcceleration(),
    removeStartupAcceleration: () => removeStartupAcceleration(),
    openDefenderSettings: () => openDefenderExclusionSettings(),
  };
}
