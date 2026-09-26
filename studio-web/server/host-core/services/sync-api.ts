import type { CompleteHostServiceRegistry } from '../main/ipc/host-contract';
import type { SyncScope } from '@shared/host-api/contract';
import { downloadConfig, getConfigSyncStatus, uploadConfig } from './sync/config-sync-service';
import { isRecord } from './payload-utils';

const VALID_SCOPES: SyncScope[] = ['agents', 'skills', 'preferences'];

type ScopesPayload = {
  scopes?: unknown;
};

/** Filter a raw payload down to the recognised sync scopes. */
function getScopes(payload: unknown): SyncScope[] {
  const raw = isRecord(payload) ? (payload as ScopesPayload).scopes : undefined;
  if (!Array.isArray(raw)) return [];
  return raw.filter((value): value is SyncScope => VALID_SCOPES.includes(value as SyncScope));
}

/** Same as getScopes but returns undefined when nothing was requested (status). */
function getScopesOptional(payload: unknown): SyncScope[] | undefined {
  const scopes = getScopes(payload);
  return scopes.length > 0 ? scopes : undefined;
}

export function createSyncApi(): CompleteHostServiceRegistry['sync'] {
  return {
    status: async (payload) => getConfigSyncStatus(getScopesOptional(payload)),
    upload: async (payload) => uploadConfig(getScopes(payload)),
    download: async (payload) => downloadConfig(getScopes(payload)),
  };
}
