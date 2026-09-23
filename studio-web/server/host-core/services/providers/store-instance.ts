// Lazy-load electron-store (ESM module) from the main process only.
import { getScopeStoreSuffix } from '../../utils/user-scope';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let providerStore: any = null;
let providerStoreSuffix: string | null = null;

export async function getGrandPoemStudioProviderStore() {
  // Re-open the store whenever the active user scope changes so accounts
  // never see each other's provider secrets.
  const suffix = getScopeStoreSuffix();
  if (!providerStore || providerStoreSuffix !== suffix) {
    const Store = (await import('electron-store')).default;
    providerStore = new Store({
      name: `grandpoem-studio-providers${suffix}`,
      defaults: {
        schemaVersion: 0,
        providers: {} as Record<string, unknown>,
        providerAccounts: {} as Record<string, unknown>,
        apiKeys: {} as Record<string, string>,
        providerSecrets: {} as Record<string, unknown>,
        defaultProvider: null as string | null,
        defaultProviderAccountId: null as string | null,
      },
    });
    providerStoreSuffix = suffix;
  }

  return providerStore;
}
