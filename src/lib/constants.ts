/**
 * Shared design constants for GrandpoemStudio
 * Matching GrandPoem studio-web-frontend design system
 */

/** Session/Agent avatar gradient colors (8 variants) */
export const SESSION_GRADIENTS = [
  "from-indigo-500 to-violet-600",
  "from-blue-500 to-cyan-600",
  "from-violet-500 to-purple-600",
  "from-orange-500 to-rose-500",
  "from-pink-500 to-rose-500",
  "from-emerald-500 to-teal-600",
  "from-rose-500 to-pink-600",
  "from-cyan-500 to-blue-600",
] as const;

/**
 * Generate a stable gradient index from session key.
 * Same session key always gets the same gradient color.
 */
export function getGradientIndex(sessionKey: string): number {
  let hash = 0;
  for (let i = 0; i < sessionKey.length; i++) {
    hash = ((hash << 5) - hash) + sessionKey.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash) % SESSION_GRADIENTS.length;
}

/** Model provider background colors */
export const MODEL_BG_COLORS = [
  "bg-orange-500",
  "bg-emerald-500",
  "bg-blue-500",
  "bg-indigo-500",
  "bg-orange-400",
  "bg-rose-500",
  "bg-violet-500",
  "bg-teal-500",
  "bg-cyan-500",
  "bg-pink-500",
] as const;
