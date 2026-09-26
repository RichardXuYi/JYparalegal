/**
 * Provider Types & UI Metadata — single source of truth for the frontend.
 *
 * NOTE: Backend provider metadata is being refactored toward the new
 * account-based registry, but the renderer still keeps a local compatibility
 * layer so TypeScript project boundaries remain stable during the migration.
 */

// Single source of truth for provider identifiers lives in the shared host-api
// contract; the renderer re-exports it so existing imports keep working.
import type { ProviderType } from '@shared/host-api/contract';

export type { ProviderType };

export const PROVIDER_TYPES = [
  'anthropic',
  'openai',
  'google',
  'openrouter',
  'ark',
  'moonshot',
  'moonshot-global',
  'siliconflow',
  'deepseek',
  'minimax-portal',
  'minimax-portal-cn',
  'minimax',
  'qwen',
  'zhipu',
  'ollama',
  'custom',
] as const;

export type ProviderProtocol =
  | 'openai-completions'
  | 'openai-responses'
  | 'openai-chatgpt-responses'
  | 'anthropic-messages'
  | 'google-generative-ai'
  | 'github-copilot'
  | 'bedrock-converse-stream'
  | 'ollama'
  | 'azure-openai-responses';

export const BUILTIN_PROVIDER_TYPES = [
  'anthropic',
  'openai',
  'google',
  'openrouter',
  'ark',
  'moonshot',
  'moonshot-global',
  'siliconflow',
  'deepseek',
  'minimax-portal',
  'minimax-portal-cn',
  'minimax',
  'qwen',
  'zhipu',
  'ollama',
] as const;

export const OLLAMA_PLACEHOLDER_API_KEY = 'ollama-local';

export interface ProviderConfig {
  id: string;
  name: string;
  type: ProviderType;
  baseUrl?: string;
  apiProtocol?: ProviderProtocol;
  headers?: Record<string, string>;
  model?: string;
  fallbackModels?: string[];
  fallbackProviderIds?: string[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProviderWithKeyInfo extends ProviderConfig {
  hasKey: boolean;
  keyMasked: string | null;
}

export interface ProviderTypeInfo {
  id: ProviderType;
  name: string;
  icon: string;
  placeholder: string;
  model?: string;
  requiresApiKey: boolean;
  defaultBaseUrl?: string;
  showBaseUrl?: boolean;
  showModelId?: boolean;
  showModelIdInDevModeOnly?: boolean;
  modelIdPlaceholder?: string;
  defaultModelId?: string;
  isOAuth?: boolean;
  supportsApiKey?: boolean;
  apiKeyUrl?: string;
  docsUrl?: string;
  docsUrlZh?: string;
  codePlanPresetBaseUrl?: string;
  codePlanPresetModelId?: string;
  codePlanDocsUrl?: string;
  /**
   * Optional plan presets (e.g. Coding Plan / Token Plan / API). When present
   * with more than one entry, the add dialog renders a plan tab row; selecting
   * a plan prefills `baseUrl` and labels the account. Each configured plan is
   * saved as its own account with an independent runtime provider key.
   */
  planPresets?: Array<{ id: string; labelKey: string; baseUrl: string; apiProtocol?: ProviderProtocol; docsUrl?: string }>;
  /** If true, this provider is not shown in the "Add Provider" dialog. */
  hidden?: boolean;
  /** If true, hide OAuth sign-in controls in the add-provider UI (logic remains enabled). */
  hideOAuthUi?: boolean;
  /** Color theme for the provider icon background */
  colorTheme?: {
    bg: string;
    bgDark?: string;
    icon: string;
  };
}

export type ProviderAuthMode =
  | 'api_key'
  | 'oauth_device'
  | 'oauth_browser'
  | 'local';

export type ProviderVendorCategory =
  | 'official'
  | 'compatible'
  | 'local'
  | 'custom';

export interface ProviderVendorInfo extends ProviderTypeInfo {
  category: ProviderVendorCategory;
  envVar?: string;
  supportedAuthModes: ProviderAuthMode[];
  defaultAuthMode: ProviderAuthMode;
  supportsMultipleAccounts: boolean;
}

export interface ProviderAccount {
  id: string;
  vendorId: ProviderType;
  label: string;
  authMode: ProviderAuthMode;
  baseUrl?: string;
  apiProtocol?: ProviderProtocol;
  headers?: Record<string, string>;
  model?: string;
  fallbackModels?: string[];
  fallbackAccountIds?: string[];
  enabled: boolean;
  isDefault: boolean;
  metadata?: {
    region?: string;
    email?: string;
    resourceUrl?: string;
    customModels?: string[];
  };
  createdAt: string;
  updatedAt: string;
}

import { providerIcons } from '@/assets/providers';

/** All supported provider types with UI metadata */
export const PROVIDER_TYPE_INFO: ProviderTypeInfo[] = [
  {
    id: 'anthropic',
    name: 'Anthropic',
    icon: '🤖',
    placeholder: 'sk-ant-api03-...',
    model: 'Claude',
    requiresApiKey: true,
    showModelId: true,
    defaultModelId: 'claude-opus-4-6',
    modelIdPlaceholder: 'claude-opus-4-6',
    docsUrl: 'https://platform.claude.com/docs/en/api/overview',
    colorTheme: { bg: '#F5E6D3', bgDark: '#3D2E1F', icon: '#CC785C' },
  },
  {
    id: 'openai',
    name: 'OpenAI',
    icon: '💚',
    placeholder: 'sk-proj-...',
    model: 'GPT',
    requiresApiKey: true,
    isOAuth: true,
    supportsApiKey: true,
    defaultModelId: 'gpt-5.5',
    showModelId: true,
    modelIdPlaceholder: 'gpt-5.5',
    apiKeyUrl: 'https://platform.openai.com/api-keys',
    colorTheme: { bg: '#D1FAE5', bgDark: '#064E3B', icon: '#10A37F' },
  },
  {
    id: 'google',
    name: 'Google',
    icon: '🔷',
    placeholder: 'AIza...',
    model: 'Gemini',
    requiresApiKey: true,
    defaultModelId: 'gemini-3.1-pro-preview',
    showModelId: true,
    modelIdPlaceholder: 'gemini-3.1-pro-preview',
    apiKeyUrl: 'https://aistudio.google.com/app/apikey',
    colorTheme: { bg: '#DBEAFE', bgDark: '#1E3A5F', icon: '#4285F4' },
  },
  { id: 'openrouter', name: 'OpenRouter', icon: '🌐', placeholder: 'sk-or-v1-...', model: 'Multi-Model', requiresApiKey: true, showModelId: true, modelIdPlaceholder: 'openai/gpt-5.5', defaultModelId: 'openai/gpt-5.5', docsUrl: 'https://openrouter.ai/models', colorTheme: { bg: '#EDE9FE', bgDark: '#2E1065', icon: '#8B5CF6' } },
  { id: 'minimax-portal-cn', name: 'MiniMax (CN)', icon: '☁️', placeholder: 'sk-...', model: 'MiniMax', requiresApiKey: false, isOAuth: true, supportsApiKey: true, defaultModelId: 'MiniMax-M3', showModelId: true, modelIdPlaceholder: 'MiniMax-M3', apiKeyUrl: 'https://platform.minimaxi.com/', colorTheme: { bg: '#FEF3C7', bgDark: '#451A03', icon: '#F59E0B' } },
  { id: 'moonshot', name: 'Moonshot (CN)', icon: '🌙', placeholder: 'sk-...', model: 'Kimi', requiresApiKey: true, defaultBaseUrl: 'https://api.moonshot.cn/v1', showModelId: true, defaultModelId: 'kimi-k2.6', modelIdPlaceholder: 'kimi-k2.6', docsUrl: 'https://platform.moonshot.cn/', colorTheme: { bg: '#E0E7FF', bgDark: '#1E1B4B', icon: '#6366F1' } },
  { id: 'moonshot-global', name: 'Moonshot (Global)', icon: '🌙', placeholder: 'sk-...', model: 'Kimi', requiresApiKey: true, defaultBaseUrl: 'https://api.moonshot.ai/v1', showModelId: true, defaultModelId: 'kimi-k2.6', modelIdPlaceholder: 'kimi-k2.6', docsUrl: 'https://platform.moonshot.ai/', colorTheme: { bg: '#E0E7FF', bgDark: '#1E1B4B', icon: '#6366F1' } },
  { id: 'siliconflow', name: 'SiliconFlow (CN)', icon: '🌊', placeholder: 'sk-...', model: 'Multi-Model', requiresApiKey: true, defaultBaseUrl: 'https://api.siliconflow.cn/v1', showModelId: true, modelIdPlaceholder: 'deepseek-ai/DeepSeek-V3', defaultModelId: 'deepseek-ai/DeepSeek-V3', docsUrl: 'https://docs.siliconflow.cn/cn/userguide/introduction', colorTheme: { bg: '#CFFAFE', bgDark: '#083344', icon: '#06B6D4' } },
  { id: 'deepseek', name: 'DeepSeek', icon: '🐋', placeholder: 'sk-...', model: 'DeepSeek', requiresApiKey: true, defaultBaseUrl: 'https://api.deepseek.com/v1', showModelId: true, modelIdPlaceholder: 'deepseek-v4-pro', defaultModelId: 'deepseek-v4-pro', apiKeyUrl: 'https://platform.deepseek.com/api_keys', docsUrl: 'https://api-docs.deepseek.com/', docsUrlZh: 'https://api-docs.deepseek.com/zh-cn/', colorTheme: { bg: '#DBEAFE', bgDark: '#172554', icon: '#3B82F6' } },
  { id: 'minimax-portal', name: 'MiniMax (Global)', icon: '☁️', placeholder: 'sk-...', model: 'MiniMax', requiresApiKey: false, isOAuth: true, supportsApiKey: true, defaultModelId: 'MiniMax-M3', showModelId: true, modelIdPlaceholder: 'MiniMax-M3', apiKeyUrl: 'https://platform.minimax.io', colorTheme: { bg: '#FEF3C7', bgDark: '#451A03', icon: '#F59E0B' } },
  { id: 'qwen', name: '通义千问 (Qwen)', icon: '☁️', placeholder: 'sk-...', model: 'Qwen', requiresApiKey: true, defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', showBaseUrl: true, defaultModelId: 'qwen3.7-plus', showModelId: true, modelIdPlaceholder: 'qwen3.7-plus', apiKeyUrl: 'https://bailian.console.aliyun.com/', docsUrl: 'https://help.aliyun.com/zh/model-studio/', docsUrlZh: 'https://help.aliyun.com/zh/model-studio/', planPresets: [{ id: 'api', labelKey: 'aiProviders.dialog.planApi', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' }, { id: 'coding', labelKey: 'aiProviders.dialog.planCoding', baseUrl: 'https://coding.dashscope.aliyuncs.com/v1' }, { id: 'token', labelKey: 'aiProviders.dialog.planToken', baseUrl: 'https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1' }], colorTheme: { bg: '#FFEDD5', bgDark: '#431407', icon: '#F97316' } },
  { id: 'zhipu', name: '智谱 GLM', icon: '🧠', placeholder: 'sk-...', model: 'GLM', requiresApiKey: true, defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4', showBaseUrl: true, defaultModelId: 'glm-4-plus', showModelId: true, modelIdPlaceholder: 'glm-4-plus', apiKeyUrl: 'https://open.bigmodel.cn/', docsUrl: 'https://open.bigmodel.cn/dev/api', docsUrlZh: 'https://open.bigmodel.cn/dev/api', colorTheme: { bg: '#F3E8FF', bgDark: '#2E1065', icon: '#A855F7' } },
  { id: 'ark', name: 'ByteDance Ark', icon: 'A', placeholder: 'your-ark-api-key', model: 'Doubao', requiresApiKey: true, defaultBaseUrl: 'https://ark.cn-beijing.volces.com/api/v3', showBaseUrl: true, showModelId: true, modelIdPlaceholder: 'ep-20260228000000-xxxxx', docsUrl: 'https://www.volcengine.com/', codePlanPresetBaseUrl: 'https://ark.cn-beijing.volces.com/api/coding/v3', codePlanPresetModelId: 'ark-code-latest', codePlanDocsUrl: 'https://www.volcengine.com/docs/82379/1928261?lang=zh', planPresets: [{ id: 'api', labelKey: 'aiProviders.dialog.planApi', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3' }, { id: 'coding', labelKey: 'aiProviders.dialog.planCoding', baseUrl: 'https://ark.cn-beijing.volces.com/api/coding/v3' }], colorTheme: { bg: '#DBEAFE', bgDark: '#1E3A5F', icon: '#2563EB' } },
  { id: 'minimax', name: 'MiniMax', icon: '☁️', placeholder: 'sk-...', model: 'MiniMax', requiresApiKey: true, defaultBaseUrl: 'https://api.minimaxi.com/v1', showBaseUrl: true, defaultModelId: 'MiniMax-M3', showModelId: true, modelIdPlaceholder: 'MiniMax-M3', apiKeyUrl: 'https://platform.minimaxi.com/', planPresets: [{ id: 'token', labelKey: 'aiProviders.dialog.planToken', baseUrl: 'https://api.minimaxi.com/v1' }], colorTheme: { bg: '#FEF3C7', bgDark: '#451A03', icon: '#F59E0B' } },
  { id: 'ollama', name: 'Ollama', icon: '🦙', placeholder: 'Not required', requiresApiKey: false, defaultBaseUrl: 'http://localhost:11434/v1', showBaseUrl: true, showModelId: true, modelIdPlaceholder: 'qwen3:latest', colorTheme: { bg: '#F3F4F6', bgDark: '#1F2937', icon: '#6B7280' } },
  {
    id: 'custom',
    name: 'Custom',
    icon: '⚙️',
    placeholder: 'API key...',
    requiresApiKey: true,
    showBaseUrl: true,
    showModelId: true,
    modelIdPlaceholder: 'your-provider/model-id',
    docsUrl: 'https://icnnp7d0dymg.feishu.cn/wiki/BmiLwGBcEiloZDkdYnGc8RWnn6d#Ee1ldfvKJoVGvfxc32mcILwenth',
    docsUrlZh: 'https://icnnp7d0dymg.feishu.cn/wiki/BmiLwGBcEiloZDkdYnGc8RWnn6d#IWQCdfe5fobGU3xf3UGcgbLynGh',
    colorTheme: { bg: '#F3F4F6', bgDark: '#1F2937', icon: '#6B7280' },
  },
];

/** Get the SVG logo URL for a provider type, falls back to undefined */
export function getProviderIconUrl(type: ProviderType | string): string | undefined {
  return providerIcons[type];
}

/** Get the color theme for a provider type */
export function getProviderColorTheme(type: ProviderType | string): { bg: string; bgDark?: string; icon: string } | undefined {
  return PROVIDER_TYPE_INFO.find((t) => t.id === type)?.colorTheme;
}

/** Whether a provider's logo needs CSS invert in dark mode (all logos are monochrome) */
export function shouldInvertInDark(_type: ProviderType | string): boolean {
  return true;
}

/** Provider list shown in the Setup wizard */
export const SETUP_PROVIDERS = PROVIDER_TYPE_INFO;

/** Get type info by provider type id */
export function getProviderTypeInfo(type: ProviderType): ProviderTypeInfo | undefined {
  return PROVIDER_TYPE_INFO.find((t) => t.id === type);
}

export function getProviderDocsUrl(
  provider: Pick<ProviderTypeInfo, 'docsUrl' | 'docsUrlZh'> | undefined,
  language: string
): string | undefined {
  if (!provider?.docsUrl) {
    return undefined;
  }

  if (language.startsWith('zh') && provider.docsUrlZh) {
    return provider.docsUrlZh;
  }

  return provider.docsUrl;
}

export function shouldShowProviderModelId(
  provider: Pick<ProviderTypeInfo, 'showModelId' | 'showModelIdInDevModeOnly'> | undefined,
  devModeUnlocked: boolean
): boolean {
  if (!provider?.showModelId) return false;
  if (provider.showModelIdInDevModeOnly && !devModeUnlocked) return false;
  return true;
}

/**
 * Parse a model input that may contain multiple model ids separated by commas
 * (half/full-width) or newlines. Trims each token, drops empties and dedups by
 * exact value while preserving input order. The first entry is treated as the
 * primary/default model.
 */
export function parseModelIdList(raw: string): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const token of (raw || '').split(/[，,\n]/)) {
    const trimmed = token.trim();
    if (!trimmed || seen.has(trimmed)) continue;
    seen.add(trimmed);
    result.push(trimmed);
  }
  return result;
}

export function resolveProviderModelForSave(
  provider: Pick<ProviderTypeInfo, 'defaultModelId' | 'showModelId' | 'showModelIdInDevModeOnly'> | undefined,
  modelId: string,
  devModeUnlocked: boolean
): string | undefined {
  if (!shouldShowProviderModelId(provider, devModeUnlocked)) {
    return undefined;
  }

  const normalizedModels = parseModelIdList(modelId);
  return normalizedModels.join(',') || provider?.defaultModelId || undefined;
}

export function normalizeProviderApiKeyInput(apiKey: string): string {
  return apiKey.trim();
}

/** Normalize provider API key before saving; Ollama uses a local placeholder when blank. */
export function resolveProviderApiKeyForSave(type: ProviderType | string, apiKey: string): string | undefined {
  const trimmed = normalizeProviderApiKeyInput(apiKey);
  if (type === 'ollama') {
    return trimmed || OLLAMA_PLACEHOLDER_API_KEY;
  }
  return trimmed || undefined;
}
