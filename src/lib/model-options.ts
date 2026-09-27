import type { ProviderAccount, ProviderVendorInfo, ProviderWithKeyInfo } from '@/lib/providers';

export interface ConfiguredModelOption {
  modelRef: string;
  label: string;
  runtimeProviderKey: string;
  accountId: string;
}

export interface RuntimeProviderOption {
  runtimeProviderKey: string;
  accountId: string;
  label: string;
  modelIdPlaceholder?: string;
  configuredModelId?: string;
}

export function resolveRuntimeProviderKey(account: ProviderAccount): string {
  if (account.authMode === 'oauth_browser') {
    if (account.vendorId === 'openai') return 'openai';
  }

  // Plan accounts are created with ids shaped as `${vendorId}-[8hex]`, which act
  // directly as unique runtime provider keys so each plan (e.g. Qwen API /
  // Coding / Token) is registered as an independent provider. Legacy UUID ids
  // (multi-dash) and OAuth ids (e.g. `minimax-portal-cn-<uuid>`) do not match
  // this shape, so their behaviour is unchanged.
  if (new RegExp(`^${account.vendorId}-[0-9a-f]{8}$`).test(account.id)) {
    return account.id;
  }

  if (account.vendorId === 'custom' || account.vendorId === 'ollama') {
    const prefix = `${account.vendorId}-`;
    if (account.id.startsWith(prefix)) {
      const tail = account.id.slice(prefix.length);
      if (tail.length === 8 && !tail.includes('-')) {
        return account.id;
      }
    }

    const suffix = account.id.replace(/-/g, '').slice(0, 8);
    return `${account.vendorId}-${suffix}`;
  }

  if (account.vendorId === 'minimax-portal-cn') {
    return 'minimax-portal';
  }

  return account.vendorId;
}

/**
 * Strips the account's runtime provider key prefix from its stored model value
 * (a comma-separated list of model ids). The prefix is removed repeatedly per
 * token so legacy values polluted with stacked prefixes (e.g.
 * `minimax-portal/minimax-portal/MiniMax-M3`) display clean in the editor.
 * Only the exact runtime key is matched, so model ids that merely contain
 * slashes (e.g. `deepseek-ai/DeepSeek-V3`) are left untouched.
 */
export function stripAccountModelPrefixes(account: ProviderAccount): string {
  const model = (account.model || '').trim();
  if (!model) return '';
  const prefix = `${resolveRuntimeProviderKey(account)}/`;
  return model
    .split(/[，,\n]/)
    .map((token) => token.trim())
    .filter(Boolean)
    .map((token) => {
      let current = token;
      while (current.startsWith(prefix)) {
        current = current.slice(prefix.length);
      }
      return current;
    })
    .join(',');
}

export function splitModelRef(modelRef: string | null | undefined): { providerKey: string; modelId: string } | null {
  const value = (modelRef || '').trim();
  if (!value) return null;
  const separatorIndex = value.indexOf('/');
  if (separatorIndex <= 0 || separatorIndex >= value.length - 1) return null;
  return {
    providerKey: value.slice(0, separatorIndex),
    modelId: value.slice(separatorIndex + 1),
  };
}

export function formatModelRefLabel(modelRef: string | null | undefined): string {
  const parsed = splitModelRef(modelRef);
  return parsed?.modelId || (modelRef || '').trim() || 'Model';
}

export function formatProviderDisplayName(
  account: ProviderAccount,
  vendorMap: Map<string, ProviderVendorInfo>,
): string {
  if (account.vendorId === 'custom' || account.vendorId === 'ollama') {
    return account.label.trim() || account.vendorId;
  }

  const vendor = vendorMap.get(account.vendorId);
  if (vendor?.name) return vendor.name;

  // Fallback: strip plan-account hex suffix from label for clean display
  // (e.g. "通义千问 (Qwen) · API" or "qwen-124880ac" -> "通义千问 (Qwen)")
  const label = account.label.trim();
  if (label) {
    return label
      .replace(/\s*·\s*[A-Za-z].*$/, '')
      .replace(/-\s*[0-9a-f]{8}$/, '');
  }
  return account.vendorId;
}

export function formatConfiguredModelLabel(
  modelId: string,
  account: ProviderAccount,
  vendorMap: Map<string, ProviderVendorInfo>,
): string {
  const providerName = formatProviderDisplayName(account, vendorMap);
  return `${modelId} (${providerName})`;
}

export function toModelOptionTestId(label: string): string {
  return label.replace(/[^a-zA-Z0-9_-]+/g, '-');
}

export function hasConfiguredProviderCredentials(
  account: ProviderAccount,
  statusById: Map<string, ProviderWithKeyInfo>,
): boolean {
  if (account.authMode === 'oauth_device' || account.authMode === 'oauth_browser' || account.authMode === 'local') {
    return true;
  }
  return statusById.get(account.id)?.hasKey ?? false;
}

export function buildRuntimeProviderOptions(
  providerAccounts: ProviderAccount[],
  providerStatuses: ProviderWithKeyInfo[],
  providerVendors: ProviderVendorInfo[],
  providerDefaultAccountId: string | null,
): RuntimeProviderOption[] {
  const safeAccounts = Array.isArray(providerAccounts) ? providerAccounts : [];
  const safeStatuses = Array.isArray(providerStatuses) ? providerStatuses : [];
  const safeVendors = Array.isArray(providerVendors) ? providerVendors : [];
  const vendorMap = new Map<string, ProviderVendorInfo>(safeVendors.map((vendor) => [vendor.id, vendor]));
  const statusById = new Map<string, ProviderWithKeyInfo>(safeStatuses.map((status) => [status.id, status]));
  const entries = safeAccounts
    .filter((account) => account.enabled && hasConfiguredProviderCredentials(account, statusById))
    .sort((left, right) => {
      if (left.id === providerDefaultAccountId) return -1;
      if (right.id === providerDefaultAccountId) return 1;
      return right.updatedAt.localeCompare(left.updatedAt);
    });

  const deduped = new Map<string, RuntimeProviderOption>();
  for (const account of entries) {
    const runtimeProviderKey = resolveRuntimeProviderKey(account);
    if (!runtimeProviderKey || deduped.has(runtimeProviderKey)) continue;
    const vendor = vendorMap.get(account.vendorId);
    const label = `${account.label} (${vendor?.name || account.vendorId})`;
    const configuredModelId = (() => {
      if (!account.model) return undefined;
      const primaryModel = account.model.split(/[，,\n]/)[0]?.trim();
      if (!primaryModel) return undefined;
      return primaryModel.startsWith(`${runtimeProviderKey}/`)
        ? primaryModel.slice(runtimeProviderKey.length + 1)
        : primaryModel;
    })();

    deduped.set(runtimeProviderKey, {
      runtimeProviderKey,
      accountId: account.id,
      label,
      modelIdPlaceholder: vendor?.modelIdPlaceholder,
      configuredModelId,
    });
  }

  return [...deduped.values()];
}

export function buildConfiguredModelOptions(
  providerAccounts: ProviderAccount[],
  providerStatuses: ProviderWithKeyInfo[],
  providerVendors: ProviderVendorInfo[],
  providerDefaultAccountId: string | null,
): ConfiguredModelOption[] {
  const safeAccounts = Array.isArray(providerAccounts) ? providerAccounts : [];
  const safeStatuses = Array.isArray(providerStatuses) ? providerStatuses : [];
  const safeVendors = Array.isArray(providerVendors) ? providerVendors : [];
  const vendorMap = new Map<string, ProviderVendorInfo>(safeVendors.map((vendor) => [vendor.id, vendor]));
  const statusById = new Map<string, ProviderWithKeyInfo>(safeStatuses.map((status) => [status.id, status]));
  const entries = safeAccounts
    .filter((account) => {
      const hasModel = Boolean(account.model?.trim())
        || Boolean(account.metadata?.customModels?.some((modelId) => modelId.trim()));
      return account.enabled && hasModel && hasConfiguredProviderCredentials(account, statusById);
    })
    .sort((left, right) => {
      if (left.id === providerDefaultAccountId) return -1;
      if (right.id === providerDefaultAccountId) return 1;
      return right.updatedAt.localeCompare(left.updatedAt);
    });

  const deduped = new Map<string, ConfiguredModelOption>();
  for (const account of entries) {
    const runtimeProviderKey = resolveRuntimeProviderKey(account);
    const modelIds = (() => {
      const configured = (account.metadata?.customModels ?? [])
        .map((modelId) => modelId.trim())
        .filter(Boolean);
      if (configured.length > 0) return configured;
      if (!account.model?.trim()) return [];
      return account.model
        .split(/[，,\n]/)
        .map((modelId) => modelId.trim())
        .filter(Boolean)
        .map((modelId) => (modelId.startsWith(`${runtimeProviderKey}/`)
          ? modelId.slice(runtimeProviderKey.length + 1)
          : modelId));
    })();
    for (const modelId of modelIds) {
      const modelRef = `${runtimeProviderKey}/${modelId}`;
      if (deduped.has(modelRef)) continue;
      deduped.set(modelRef, {
        modelRef,
        label: formatConfiguredModelLabel(modelId, account, vendorMap),
        runtimeProviderKey,
        accountId: account.id,
      });
    }
  }

  return [...deduped.values()];
}

export function isConfiguredModelRefAvailable(
  modelRef: string | null | undefined,
  modelOptions: ConfiguredModelOption[],
): boolean {
  const value = (modelRef || '').trim();
  if (!value) return false;
  return modelOptions.some((option) => option.modelRef === value);
}

export function resolveConfiguredModelRef(
  preferredModelRef: string | null | undefined,
  defaultModelRef: string | null | undefined,
  modelOptions: ConfiguredModelOption[],
): string | null {
  const preferred = (preferredModelRef || '').trim();
  if (preferred && isConfiguredModelRefAvailable(preferred, modelOptions)) {
    return preferred;
  }

  const fallbackDefault = (defaultModelRef || '').trim();
  if (fallbackDefault && isConfiguredModelRefAvailable(fallbackDefault, modelOptions)) {
    return fallbackDefault;
  }

  return modelOptions[0]?.modelRef ?? null;
}
