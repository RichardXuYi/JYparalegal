import { shell } from 'electron';
import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import { createRuntimeService, getRuntimeFolderForReveal, type RuntimeEventSink } from './runtime-service';
import { isRecord } from './payload-utils';

/**
 * Host API surface for OpenClaw runtime provisioning.
 *
 * Thin adapter: payload validation lives here, all behaviour lives in
 * runtime-service.ts (see docs/startup-performance-plan.md).
 */
export function createRuntimeApi(sink: RuntimeEventSink): CompleteHostServiceRegistry['runtime'] {
  const service = createRuntimeService(sink);

  return {
    status: () => service.getStatus(),
    manifest: () => service.getManifest(),
    install: (payload) => {
      const consent = isRecord(payload) && typeof payload.consent === 'boolean'
        ? payload.consent
        : undefined;
      return service.install({ consent });
    },
    cancel: () => service.cancel(),
    rollback: (payload) => service.rollback(
      isRecord(payload) && typeof payload.version === 'string' ? { version: payload.version } : {},
    ),
    importArchive: (payload) => service.importArchive({
      path: isRecord(payload) && typeof payload.path === 'string' ? payload.path : '',
    }),
    revealFolder: async () => {
      const error = await shell.openPath(getRuntimeFolderForReveal());
      return error ? { success: false, error } : { success: true };
    },
  };
}
