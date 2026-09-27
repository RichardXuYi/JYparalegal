export type ModelInputModality = 'text' | 'image';

import { MODEL_CONTEXT_TABLE, type ModelContextEntry } from './model-context-table';

/**
 * Safe floor for unknown custom models: high enough to avoid compaction spam.
 * Models not listed in the precise context-window table fall back to this
 * conservative value.
 */
export const DEFAULT_CUSTOM_MODEL_CONTEXT_WINDOW = 200_000;

/**
 * Common context-window tiers offered in the model-settings UI, expressed in
 * tokens: 128k / 200k / 256k / 400k / 512k / 1M / 2M. A model only receives
 * the tiers at or below its own maximum, so big-window models keep smaller
 * tiers available for token saving while a 200k model never gets offered a
 * tier it cannot support.
 */
export const CONTEXT_WINDOW_PRESETS = [
  131_072,
  200_000,
  262_144,
  400_000,
  524_288,
  1_048_576,
  2_097_152,
] as const;

/**
 * Normalize a user-entered model id for exact table lookup: trim, lowercase,
 * and strip any provider prefix (e.g. `MiniMax/MiniMax-M3`, `ZHIPU/GLM-5.2`,
 * `kimi/kimi-k3`).
 */
function normalizeModelId(modelId: string): string {
  const trimmed = modelId.trim().toLowerCase();
  const slashIndex = trimmed.lastIndexOf('/');
  return slashIndex >= 0 ? trimmed.slice(slashIndex + 1).trim() : trimmed;
}

let entryLookup: Map<string, ModelContextEntry> | undefined;

function getEntryLookup(): Map<string, ModelContextEntry> {
  if (!entryLookup) {
    entryLookup = new Map();
    for (const entry of MODEL_CONTEXT_TABLE) {
      entryLookup.set(entry.id, entry);
      for (const alias of entry.aliases ?? []) entryLookup.set(alias, entry);
    }
  }
  return entryLookup;
}

/** Exact (non-regex) lookup of a model's verified context-window entry. */
export function findModelContextEntry(modelId: string): ModelContextEntry | null {
  return getEntryLookup().get(normalizeModelId(modelId)) ?? null;
}

/** Maximum context window for a known model; conservative 200k otherwise. */
export function inferCustomModelContextWindow(modelId: string): number {
  return findModelContextEntry(modelId)?.contextWindow ?? DEFAULT_CUSTOM_MODEL_CONTEXT_WINDOW;
}

/**
 * Mirrors OpenClaw 2026.5.20 custom-provider onboarding inference.
 * Unknown models use the same conservative text-only fallback as non-interactive onboarding.
 */
export function inferCustomModelInputModalities(modelId: string): ModelInputModality[] {
  const normalized = modelId.trim().toLowerCase();
  const supportsImageInput = (
    /\b(?:gpt-4o|gpt-4\.1|gpt-[5-9]|o[134])\b/.test(normalized)
    || /\bclaude-(?:3|4|sonnet|opus|haiku)\b/.test(normalized)
    || /\bgemini\b/.test(normalized)
    || /\b(?:qwen[\w.-]*?-vl|qwen-vl)\b/.test(normalized)
    || /\b(?:vision|llava|pixtral|internvl|mllama|minicpm-v|glm-4v)\b/.test(normalized)
    || /(?:^|[-_/])vl(?:[-_/]|$)/.test(normalized)
  );

  return supportsImageInput ? ['text', 'image'] : ['text'];
}

/**
 * Whether a custom model should be registered with `reasoning: true` so
 * OpenClaw treats it as thinking-capable. As of 2026 essentially all current
 * models support thinking, so the default is `true`; only a small whitelist of
 * legacy non-reasoning models is excluded. The Gateway catalog remains the
 * final arbiter at request time.
 */
const NON_REASONING_MODEL_PATTERNS: RegExp[] = [
  /\bgpt-4o\b/,
  /\bgpt-4\.1(?:-mini|-nano)?\b/,
  /\bgpt-4-turbo\b/,
  /\bgpt-4\b/,
  /\bgpt-3\.5\b/,
];

export function inferCustomModelReasoning(modelId: string): boolean {
  const normalized = modelId.trim().toLowerCase();
  if (NON_REASONING_MODEL_PATTERNS.some((pattern) => pattern.test(normalized))) {
    return false;
  }
  return true;
}

/**
 * Format a token count as a compact human label, e.g. 1048576 -> "1M",
 * 524288 -> "512k", 1500000 -> "1.5M". Values >= 1M keep one decimal place
 * so distinct tiers like 1.5M and 2M never collide on the same label.
 */
export function formatContextWindowLabel(tokens: number): string {
  if (tokens >= 1_000_000) {
    const tenths = Math.floor(tokens / 100_000);
    return `${tenths % 10 === 0 ? String(tenths / 10) : (tenths / 10).toFixed(1)}M`;
  }
  if (tokens % 1_024 === 0) return `${tokens / 1_024}k`;
  if (tokens >= 1_000) return `${Math.round(tokens / 1_000)}k`;
  return String(tokens);
}

/**
 * Return the context-window tiers a model is allowed to pick from: every
 * standard tier at or below its verified maximum, plus the maximum itself
 * (and any extra tiers) when no included tier shares its display label. A
 * maximum like Qwen's 1,000,000 therefore merges into the 1M tier instead of
 * appearing as a duplicate.
 */
export function resolveContextWindowPresets(modelId: string): number[] {
  const entry = findModelContextEntry(modelId);
  const max = entry?.contextWindow ?? DEFAULT_CUSTOM_MODEL_CONTEXT_WINDOW;
  const presets = new Set<number>();
  for (const tier of CONTEXT_WINDOW_PRESETS) {
    if (tier <= max) presets.add(tier);
  }
  for (const extra of entry?.extraPresets ?? []) {
    if (extra <= max) presets.add(extra);
  }
  const maxLabel = formatContextWindowLabel(max);
  const labelTaken = [...presets].some((tier) => formatContextWindowLabel(tier) === maxLabel);
  if (!labelTaken) presets.add(max);
  return [...presets].sort((left, right) => left - right);
}
