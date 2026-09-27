/**
 * Agent session helpers
 * Pure helpers for attributing chat sessions to agents.
 * Sessions are attributed to agents via the `agent:<agentId>:` session-key prefix.
 */

export function getAgentIdFromSessionKey(sessionKey: string): string {
  if (!sessionKey.startsWith('agent:')) return 'main';
  const [, agentId] = sessionKey.split(':');
  return agentId || 'main';
}
