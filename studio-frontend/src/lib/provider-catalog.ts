/**
 * Provider vendor catalog — presentation-layer grouping of the legacy
 * provider types into "vendor group → service offering" so multi-service
 * vendors (MiniMax, Qwen, Ark, Moonshot) show up as a single card in the add
 * dialog. Saving still resolves back to the legacy `vendorId + baseUrl +
 * authMode` shape; host contracts and persistence are untouched.
 */
import {
  PROVIDER_TYPE_INFO,
  type ProviderAuthMode,
  type ProviderProtocol,
  type ProviderType,
  type ProviderTypeInfo,
} from './providers';

export type ProviderServiceKind = 'api' | 'coding_plan' | 'token_plan' | 'oauth_portal';

export type ProviderServiceRegion = 'cn' | 'global';

export interface ProviderServiceOffering {
  /** Unique within its vendor group. */
  id: string;
  kind: ProviderServiceKind;
  region?: ProviderServiceRegion;
  /** Legacy provider type this offering saves as. */
  vendorTypeId: ProviderType;
  /** Base URL prefilled when the offering is selected. */
  baseUrl?: string;
  apiProtocol?: ProviderProtocol;
  authModes: ProviderAuthMode[];
  docsUrl?: string;
  /** i18n key for the offering label (kind label; region shown separately). */
  labelKey: string;
}

export interface ProviderVendorGroup {
  key: string;
  name: string;
  icon: string;
  colorTheme?: ProviderTypeInfo['colorTheme'];
  offerings: ProviderServiceOffering[];
}

const KIND_LABEL_KEYS: Record<ProviderServiceKind, string> = {
  api: 'aiProviders.dialog.planApi',
  coding_plan: 'aiProviders.dialog.planCoding',
  token_plan: 'aiProviders.dialog.planToken',
  oauth_portal: 'aiProviders.catalog.kind.oauthPortal',
};

export const REGION_LABEL_KEYS: Record<ProviderServiceRegion, string> = {
  cn: 'aiProviders.catalog.region.cn',
  global: 'aiProviders.catalog.region.global',
};

function typeInfo(id: ProviderType): ProviderTypeInfo {
  const info = PROVIDER_TYPE_INFO.find((entry) => entry.id === id);
  if (!info) throw new Error(`Unknown provider type: ${id}`);
  return info;
}

function planKindFromPresetId(presetId: string): ProviderServiceKind {
  if (presetId === 'coding') return 'coding_plan';
  if (presetId === 'token') return 'token_plan';
  return 'api';
}

/** Build offerings for a vendor whose planPresets drive the split. */
function offeringsFromPlanPresets(id: ProviderType): ProviderServiceOffering[] {
  const info = typeInfo(id);
  return (info.planPresets ?? []).map((preset) => ({
    id: preset.id,
    kind: planKindFromPresetId(preset.id),
    vendorTypeId: id,
    baseUrl: preset.baseUrl,
    apiProtocol: preset.apiProtocol,
    authModes: ['api_key'],
    docsUrl: preset.docsUrl ?? info.docsUrl,
    labelKey: KIND_LABEL_KEYS[planKindFromPresetId(preset.id)],
  }));
}

/** Single-service vendors map to one plain API offering. */
function singleApiGroup(id: ProviderType): ProviderVendorGroup {
  const info = typeInfo(id);
  return {
    key: id,
    name: info.name,
    icon: info.icon,
    colorTheme: info.colorTheme,
    offerings: [
      {
        id: 'api',
        kind: 'api',
        vendorTypeId: id,
        baseUrl: info.defaultBaseUrl,
        authModes: info.isOAuth ? ['oauth_device', 'api_key'] : ['api_key'],
        docsUrl: info.docsUrl,
        labelKey: KIND_LABEL_KEYS.api,
      },
    ],
  };
}

const minimaxInfo = typeInfo('minimax');
const minimaxPortalInfo = typeInfo('minimax-portal');

export const PROVIDER_VENDOR_CATALOG: ProviderVendorGroup[] = [
  singleApiGroup('anthropic'),
  singleApiGroup('openai'),
  singleApiGroup('google'),
  singleApiGroup('openrouter'),
  {
    key: 'minimax',
    name: 'MiniMax',
    icon: minimaxPortalInfo.icon,
    colorTheme: minimaxPortalInfo.colorTheme,
    offerings: [
      {
        id: 'oauth',
        kind: 'oauth_portal',
        vendorTypeId: 'minimax-portal',
        authModes: ['oauth_device', 'api_key'],
        labelKey: KIND_LABEL_KEYS.oauth_portal,
      },
      {
        id: 'token',
        kind: 'token_plan',
        vendorTypeId: 'minimax',
        baseUrl: minimaxInfo.planPresets?.[0]?.baseUrl ?? minimaxInfo.defaultBaseUrl,
        authModes: ['api_key'],
        labelKey: KIND_LABEL_KEYS.token_plan,
      },
    ],
  },
  {
    key: 'moonshot',
    name: 'Moonshot',
    icon: typeInfo('moonshot').icon,
    colorTheme: typeInfo('moonshot').colorTheme,
    offerings: [
      {
        id: 'api-cn',
        kind: 'api',
        region: 'cn',
        vendorTypeId: 'moonshot',
        baseUrl: typeInfo('moonshot').defaultBaseUrl,
        authModes: ['api_key'],
        docsUrl: typeInfo('moonshot').docsUrl,
        labelKey: KIND_LABEL_KEYS.api,
      },
      {
        id: 'api-global',
        kind: 'api',
        region: 'global',
        vendorTypeId: 'moonshot-global',
        baseUrl: typeInfo('moonshot-global').defaultBaseUrl,
        authModes: ['api_key'],
        docsUrl: typeInfo('moonshot-global').docsUrl,
        labelKey: KIND_LABEL_KEYS.api,
      },
    ],
  },
  singleApiGroup('siliconflow'),
  singleApiGroup('deepseek'),
  {
    key: 'qwen',
    name: typeInfo('qwen').name,
    icon: typeInfo('qwen').icon,
    colorTheme: typeInfo('qwen').colorTheme,
    offerings: offeringsFromPlanPresets('qwen'),
  },
  singleApiGroup('zhipu'),
  {
    key: 'ark',
    name: typeInfo('ark').name,
    icon: typeInfo('ark').icon,
    colorTheme: typeInfo('ark').colorTheme,
    offerings: offeringsFromPlanPresets('ark'),
  },
  singleApiGroup('ollama'),
  singleApiGroup('custom'),
];

export function getVendorGroupByKey(key: string): ProviderVendorGroup | undefined {
  return PROVIDER_VENDOR_CATALOG.find((group) => group.key === key);
}

export function getVendorGroupByVendorId(vendorId: string): ProviderVendorGroup | undefined {
  return PROVIDER_VENDOR_CATALOG.find((group) =>
    group.offerings.some((offering) => offering.vendorTypeId === vendorId),
  );
}

export interface LegacyProviderTarget {
  vendorId: ProviderType;
  baseUrl?: string;
  apiProtocol?: ProviderProtocol;
  defaultAuthMode: ProviderAuthMode;
}

/** Map an offering back to the legacy save shape (vendorId + baseUrl + …). */
export function resolveOfferingToLegacy(offering: ProviderServiceOffering): LegacyProviderTarget {
  return {
    vendorId: offering.vendorTypeId,
    baseUrl: offering.baseUrl,
    apiProtocol: offering.apiProtocol,
    defaultAuthMode: offering.authModes[0] ?? 'api_key',
  };
}

function normalizeBaseUrl(url?: string): string {
  return (url ?? '').trim().replace(/\/+$/, '');
}

/**
 * Reverse-lookup the offering for a saved account by vendorId + baseUrl.
 * When several offerings share the same vendorId (Qwen/Ark plans) the base
 * URL disambiguates; otherwise the vendor's single offering wins.
 */
export function findOfferingForAccount(
  vendorId: string,
  baseUrl?: string,
): ProviderServiceOffering | undefined {
  const group = getVendorGroupByVendorId(vendorId);
  if (!group) return undefined;
  const candidates = group.offerings.filter((offering) => offering.vendorTypeId === vendorId);
  if (candidates.length <= 1) return candidates[0];
  const normalized = normalizeBaseUrl(baseUrl);
  return (
    candidates.find((offering) => normalizeBaseUrl(offering.baseUrl) === normalized)
    ?? candidates[0]
  );
}
