/**
 * Precise context-window table for well-known model IDs, verified one by one
 * against official vendor docs (2026-08-18). Replaces the previous regex-based
 * guessing: matching is exact (normalized ID lookup), so every entry listed
 * here carries its officially documented maximum context window.
 *
 * Unknown models fall back to `DEFAULT_CUSTOM_MODEL_CONTEXT_WINDOW` (200k).
 */

export interface ModelContextEntry {
  /** Vendor display name, e.g. '阿里 Qwen' / 'Anthropic'. */
  vendor: string;
  /** Canonical lowercase model id, e.g. 'qwen3.8-max'. */
  id: string;
  /** Common alternative spellings users may type, e.g. 'claude-opus-4-8'. */
  aliases?: string[];
  /** Maximum context window in tokens (official value). */
  contextWindow: number;
  /** Extra tiers offered in the settings dropdown beyond the standard tiers. */
  extraPresets?: number[];
  /** Optional note (e.g. HTTP-only limits). */
  note?: string;
  /** Where the value was verified (official docs). */
  source: string;
}

export const MODEL_CONTEXT_TABLE: ModelContextEntry[] = [
  // ---- 阿里 Qwen（阿里云百炼官方文档 help.aliyun.com/zh/model-studio，逐模型核实） ----
  { vendor: '阿里 Qwen', id: 'qwen3.8-max', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.7-max', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.7-plus', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.7-flash', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.6-plus', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.6-flash', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.5-plus', contextWindow: 1_000_000, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.5-omni-plus', contextWindow: 262_144, note: '最大输入 196,608', source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen3.5-omni-flash', contextWindow: 262_144, source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen-plus', contextWindow: 1_000_000, note: '2025-07-28 起 1M', source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen-max', contextWindow: 32_768, note: '千问 2.5 系列', source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen-turbo', contextWindow: 131_072, note: 'Qwen3 系列 Turbo', source: '百炼官方文档' },
  { vendor: '阿里 Qwen', id: 'qwen-long', contextWindow: 10_000_000, note: 'HTTP 直连仅 1M，超 1M 需文件方式', source: '百炼官方文档' },

  // ---- DeepSeek（官方 API 文档 api-docs.deepseek.com） ----
  { vendor: 'DeepSeek', id: 'deepseek-v4-pro', aliases: ['deepseek-v4-pro-0813'], contextWindow: 1_048_576, source: 'DeepSeek 官方 API 文档' },
  { vendor: 'DeepSeek', id: 'deepseek-v4-flash', aliases: ['deepseek-v4-flash-0731'], contextWindow: 1_048_576, source: 'DeepSeek 官方 API 文档' },
  { vendor: 'DeepSeek', id: 'deepseek-chat', contextWindow: 1_048_576, note: '端点现指向 V4 系列', source: 'DeepSeek 官方 API 文档' },
  { vendor: 'DeepSeek', id: 'deepseek-reasoner', contextWindow: 1_048_576, note: '端点现指向 V4 系列', source: 'DeepSeek 官方 API 文档' },
  { vendor: 'DeepSeek', id: 'deepseek-v3.2', contextWindow: 131_072, source: 'DeepSeek 官方' },

  // ---- 智谱（官方文档 docs.bigmodel.cn） ----
  { vendor: '智谱', id: 'glm-5.3', contextWindow: 1_048_576, note: '最大输出 128K', source: '智谱官方' },
  { vendor: '智谱', id: 'glm-5.2', contextWindow: 1_048_576, note: '最大输出 128K', source: '智谱官方' },
  { vendor: '智谱', id: 'glm-4.7', contextWindow: 204_800, source: '智谱官方文档' },
  { vendor: '智谱', id: 'glm-4.6', contextWindow: 204_800, source: '智谱官方文档' },
  { vendor: '智谱', id: 'glm-4.5', contextWindow: 131_072, source: '智谱官方文档' },
  { vendor: '智谱', id: 'glm-4.5-air', contextWindow: 131_072, source: '智谱官方文档' },

  // ---- MiniMax（官网 minimaxi.com） ----
  { vendor: 'MiniMax', id: 'minimax-m3', contextWindow: 1_048_576, note: '官方保证至少 512K 可用', source: 'MiniMax 官网' },
  { vendor: 'MiniMax', id: 'minimax-m2.7', contextWindow: 204_800, source: 'MiniMax 官网' },
  { vendor: 'MiniMax', id: 'minimax-m2', contextWindow: 204_800, source: 'MiniMax 官网' },

  // ---- OpenAI（官方 openai.com / 平台规格页） ----
  { vendor: 'OpenAI', id: 'gpt-5.6', aliases: ['gpt-5.6-sol'], contextWindow: 1_500_000, note: 'Sol 标称 1.5M，API 逐步开放', source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-5.6-terra', contextWindow: 1_050_000, source: 'OpenAI 平台规格页' },
  { vendor: 'OpenAI', id: 'gpt-5.6-luna', contextWindow: 1_050_000, source: 'OpenAI 平台规格页' },
  { vendor: 'OpenAI', id: 'gpt-5.5', contextWindow: 1_050_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-5.4', contextWindow: 1_048_576, extraPresets: [272_000], note: '标准 272K，超出部分双倍计费', source: 'OpenAI 官方发布页' },
  { vendor: 'OpenAI', id: 'gpt-5.2', contextWindow: 400_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-5.1', contextWindow: 400_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-5', contextWindow: 400_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-4.1', contextWindow: 1_047_576, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-4.1-mini', contextWindow: 1_047_576, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-4.1-nano', contextWindow: 1_047_576, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-4o', contextWindow: 200_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'gpt-4o-mini', contextWindow: 200_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'o1', contextWindow: 200_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'o3', contextWindow: 200_000, source: 'OpenAI 官方' },
  { vendor: 'OpenAI', id: 'o4-mini', contextWindow: 200_000, source: 'OpenAI 官方' },

  // ---- Anthropic（官方 anthropic.com） ----
  { vendor: 'Anthropic', id: 'claude-sonnet-5', contextWindow: 1_048_576, note: '最大输出 128K', source: 'Anthropic 官方公告' },
  { vendor: 'Anthropic', id: 'claude-opus-4.8', aliases: ['claude-opus-4-8'], contextWindow: 1_048_576, note: '最大输出 128K', source: 'Anthropic 官方' },
  { vendor: 'Anthropic', id: 'claude-opus-4.7', aliases: ['claude-opus-4-7'], contextWindow: 1_048_576, source: 'Anthropic 官方文档' },
  { vendor: 'Anthropic', id: 'claude-sonnet-4.7', aliases: ['claude-sonnet-4-7'], contextWindow: 1_048_576, source: 'Anthropic 官方文档' },
  { vendor: 'Anthropic', id: 'claude-opus-4.6', aliases: ['claude-opus-4-6'], contextWindow: 1_048_576, note: '2026-03 全面开放 1M', source: 'Anthropic 公告' },
  { vendor: 'Anthropic', id: 'claude-sonnet-4.6', aliases: ['claude-sonnet-4-6'], contextWindow: 1_048_576, note: '2026-03 全面开放 1M', source: 'Anthropic 公告' },
  { vendor: 'Anthropic', id: 'claude-opus-4.5', aliases: ['claude-opus-4-5'], contextWindow: 200_000, source: 'Anthropic' },
  { vendor: 'Anthropic', id: 'claude-sonnet-4.5', aliases: ['claude-sonnet-4-5'], contextWindow: 200_000, source: 'Anthropic' },
  { vendor: 'Anthropic', id: 'claude-haiku-4.5', aliases: ['claude-haiku-4-5'], contextWindow: 200_000, source: 'Anthropic' },
  { vendor: 'Anthropic', id: 'claude-3-7-sonnet', aliases: ['claude-3-7-sonnet-latest'], contextWindow: 200_000, source: 'Anthropic' },
  { vendor: 'Anthropic', id: 'claude-3-5-sonnet', aliases: ['claude-3-5-sonnet-latest'], contextWindow: 200_000, source: 'Anthropic' },
  { vendor: 'Anthropic', id: 'claude-3-5-haiku', aliases: ['claude-3-5-haiku-latest'], contextWindow: 200_000, source: 'Anthropic' },

  // ---- Google（官方 ai.google.dev / cloud.google.com） ----
  { vendor: 'Google', id: 'gemini-3.5-flash', contextWindow: 1_048_576, note: '2026-07 上线', source: 'Google 官方' },
  { vendor: 'Google', id: 'gemini-3-pro', contextWindow: 1_048_576, source: 'Google 官方' },
  { vendor: 'Google', id: 'gemini-3-flash', contextWindow: 1_048_576, source: 'Google 官方' },
  { vendor: 'Google', id: 'gemini-2.5-pro', contextWindow: 1_048_576, source: 'Google 官方' },
  { vendor: 'Google', id: 'gemini-2.5-flash', contextWindow: 1_048_576, source: 'Google 官方' },
  { vendor: 'Google', id: 'gemini-2.5-flash-lite', contextWindow: 1_048_576, source: 'Google 官方' },

  // ---- xAI（官方文档 docs.x.ai） ----
  { vendor: 'xAI', id: 'grok-4.6', contextWindow: 500_000, note: '2026-08 发布', source: 'xAI 官方文档' },
  { vendor: 'xAI', id: 'grok-4.5', contextWindow: 500_000, source: 'xAI 官方文档' },
  { vendor: 'xAI', id: 'grok-4.1-fast', contextWindow: 2_097_152, source: 'xAI 官方' },
  { vendor: 'xAI', id: 'grok-4-fast', contextWindow: 2_097_152, source: 'xAI 官方' },
  { vendor: 'xAI', id: 'grok-4', contextWindow: 2_097_152, source: 'xAI' },
  { vendor: 'xAI', id: 'grok-4.1', contextWindow: 262_144, source: 'xAI（2025-11-17）' },

  // ---- Moonshot（Kimi 平台官方文档 platform.kimi.com） ----
  { vendor: 'Moonshot', id: 'kimi-k3', contextWindow: 1_048_576, note: '2026-07 发布', source: 'Kimi 平台官方文档' },
  { vendor: 'Moonshot', id: 'kimi-k2.6', contextWindow: 262_144, source: 'Kimi 平台官方文档' },
  { vendor: 'Moonshot', id: 'kimi-k2.5', contextWindow: 262_144, source: 'Kimi 平台官方文档' },
];
