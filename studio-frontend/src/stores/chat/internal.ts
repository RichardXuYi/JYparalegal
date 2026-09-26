import { DEFAULT_SESSION_KEY, type ChatState } from './types';
import { readLastSessionKey } from './last-session';
import { createRuntimeActions } from './runtime-actions';
import { createSessionHistoryActions } from './session-history-actions';
import type { ChatGet, ChatSet } from './store-api';

// Restore the last-viewed session (if any) so app startup lands where the
// user last worked instead of always selecting the main-agent session.
const initialSessionKey = readLastSessionKey() ?? DEFAULT_SESSION_KEY;
const initialAgentId = initialSessionKey.startsWith('agent:')
  ? (initialSessionKey.split(':')[1] || 'main')
  : 'main';

export const initialChatState: Pick<
  ChatState,
  | 'messages'
  | 'loading'
  | 'error'
  | 'runError'
  | 'dismissedRunErrors'
  | 'sending'
  | 'activeRunId'
  | 'streamingText'
  | 'streamingMessage'
  | 'streamingTools'
  | 'pendingFinal'
  | 'lastUserMessageAt'
  | 'pendingToolImages'
  | 'sessions'
  | 'currentSessionKey'
  | 'currentAgentId'
  | 'sessionLabels'
  | 'sessionLastActivity'
  | 'thinkingLevel'
  | 'thinkingLevels'
> = {
  messages: [],
  loading: false,
  error: null,
  runError: null,
  dismissedRunErrors: {},

  sending: false,
  activeRunId: null,
  streamingText: '',
  streamingMessage: null,
  streamingTools: [],
  pendingFinal: false,
  lastUserMessageAt: null,
  pendingToolImages: [],

  sessions: [],
  currentSessionKey: initialSessionKey,
  currentAgentId: initialAgentId,
  sessionLabels: {},
  sessionLastActivity: {},

  thinkingLevel: null,
  thinkingLevels: [],
};

export function createChatActions(
  set: ChatSet,
  get: ChatGet,
): Pick<
  ChatState,
  | 'loadSessions'
  | 'switchSession'
  | 'newSession'
  | 'deleteSession'
  | 'renameSession'
  | 'cleanupEmptySession'
  | 'loadHistory'
  | 'sendMessage'
  | 'abortRun'
  | 'handleChatEvent'
  | 'refresh'
  | 'clearError'
> {
  return {
    ...createSessionHistoryActions(set, get),
    ...createRuntimeActions(set, get),
  };
}
