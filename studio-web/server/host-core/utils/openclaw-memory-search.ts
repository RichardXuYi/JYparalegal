/**
 * Memory search default seeding for openclaw.json.
 *
 * OpenClaw enables semantic memory search by default with the `openai`
 * embedding provider, so a user without an OpenAI key gets doctor errors and
 * a broken memory_search tool. GrandPoem Paralegal Studio seeds `memory.search =
 * { enabled: false }` at Gateway prelaunch —but only when the user has no
 * memory search config anywhere (global or per-agent). OpenClaw 2026.9.6 moved
 * the legacy `memorySearch` key to `memory.search`. Existing user config is never modified.
 */

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function entryHasMemorySearch(entry: unknown): boolean {
  if (!isRecord(entry)) return false;
  if (entry.memorySearch !== undefined) return true;
  return isRecord(entry.memory) && entry.memory.search !== undefined;
}

/**
 * True when the user manages memory search themselves.
 * Accepts the 2026.9.6 paths (`memory.search`, `agents.entries.*.memory.search`)
 * and the legacy `memorySearch` keys so a migrated config is not seeded again.
 */
export function hasUserMemorySearchConfig(config: Record<string, unknown>): boolean {
  const rootMemory = isRecord(config.memory) ? config.memory : undefined;
  if (rootMemory && rootMemory.search !== undefined) return true;

  const agents = isRecord(config.agents) ? config.agents : undefined;
  if (!agents) return false;

  const defaults = isRecord(agents.defaults) ? agents.defaults : undefined;
  if (defaults && (defaults.memorySearch !== undefined || entryHasMemorySearch(defaults))) return true;

  const list = Array.isArray(agents.list) ? agents.list : [];
  if (list.some((entry) => entryHasMemorySearch(entry))) return true;

  const entries = isRecord(agents.entries) ? agents.entries : undefined;
  if (!entries) return false;
  return Object.values(entries).some((entry) => entryHasMemorySearch(entry));
}

/**
 * Seed `memory.search = { enabled: false }` when the user has no memory search
 * config at all. Mutates `config` in place and returns true when a change was made.
 */
export function ensureMemorySearchDisabledDefault(config: Record<string, unknown>): boolean {
  if (hasUserMemorySearchConfig(config)) return false;

  const memory = (isRecord(config.memory) ? config.memory : {}) as Record<string, unknown>;
  memory.search = { enabled: false };
  config.memory = memory;
  return true;
}
