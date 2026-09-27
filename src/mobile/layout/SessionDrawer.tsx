/**
 * SessionDrawer Component
 * Left-side sheet holding the session list for the mobile shell.
 * Reuses the desktop Sidebar's session bucket / rename / delete logic; action
 * buttons are always visible (no hover-only affordances on touch screens).
 */
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Plus, Trash2, Pencil, Check, X, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { useChatStore } from '@/stores/chat';
import { useAgentsStore } from '@/stores/agents';
import {
  getSessionActivityMs,
  getSessionBucket,
  type SessionBucketKey,
} from '@/components/layout/session-buckets';
import { CHANNEL_NAMES } from '@shared/types/channel';
import { Input } from '@/components/ui/input';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useNewChatAction } from '@/components/layout/use-new-chat-action';
import { SESSION_GRADIENTS, getGradientIndex } from '@/lib/constants';

const INITIAL_NOW_MS = Date.now();
const DEFAULT_EXPANDED_SESSION_BUCKETS: Record<SessionBucketKey, boolean> = {
  today: true,
  withinWeek: true,
  withinMonth: false,
  older: false,
};

function getAgentIdFromSessionKey(sessionKey: string): string {
  if (!sessionKey.startsWith('agent:')) return 'main';
  const [, agentId] = sessionKey.split(':');
  return agentId || 'main';
}

interface SessionDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SessionDrawer({ open, onOpenChange }: SessionDrawerProps) {
  const { t } = useTranslation(['common', 'chat']);
  const navigate = useNavigate();
  const isOnChat = useLocation().pathname === '/';

  const sessions = useChatStore((s) => s.sessions);
  const currentSessionKey = useChatStore((s) => s.currentSessionKey);
  const sessionLabels = useChatStore((s) => s.sessionLabels);
  const sessionLastActivity = useChatStore((s) => s.sessionLastActivity);
  const switchSession = useChatStore((s) => s.switchSession);
  const deleteSession = useChatStore((s) => s.deleteSession);
  const renameSession = useChatStore((s) => s.renameSession);
  const loadHistory = useChatStore((s) => s.loadHistory);
  const handleNewChat = useNewChatAction();

  const agents = useAgentsStore((s) => s.agents);

  const [sessionToDelete, setSessionToDelete] = useState<{ key: string; label: string } | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingSessionKey, setEditingSessionKey] = useState<string | null>(null);
  const [editingLabel, setEditingLabel] = useState('');
  const [nowMs, setNowMs] = useState(INITIAL_NOW_MS);
  const [expandedSessionBuckets, setExpandedSessionBuckets] = useState<Record<SessionBucketKey, boolean>>(
    () => ({ ...DEFAULT_EXPANDED_SESSION_BUCKETS }),
  );

  useEffect(() => {
    const timer = window.setInterval(() => {
      setNowMs(Date.now());
    }, 60 * 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (deleteDialogOpen || !sessionToDelete) return;
    const timer = window.setTimeout(() => setSessionToDelete(null), 160);
    return () => window.clearTimeout(timer);
  }, [deleteDialogOpen, sessionToDelete]);

  const getSessionLabel = (key: string, displayName?: string, label?: string) =>
    sessionLabels[key] ?? label ?? displayName ?? key;

  const handleStartRename = (key: string, currentLabel: string) => {
    setEditingSessionKey(key);
    setEditingLabel(currentLabel);
  };

  const handleRenameSubmit = async () => {
    if (!editingSessionKey || !editingLabel.trim()) {
      setEditingSessionKey(null);
      return;
    }
    try {
      await renameSession(editingSessionKey, editingLabel.trim());
    } catch (err) {
      console.error('Failed to rename session:', err);
    }
    setEditingSessionKey(null);
  };

  const handleRenameCancel = () => {
    setEditingSessionKey(null);
  };

  const handleRenameKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      void handleRenameSubmit();
    } else if (e.key === 'Escape') {
      handleRenameCancel();
    }
  };

  const toggleSessionBucket = (bucketKey: SessionBucketKey) => {
    setExpandedSessionBuckets((current) => ({
      ...current,
      [bucketKey]: !current[bucketKey],
    }));
  };

  const agentNameById = useMemo(
    () => Object.fromEntries((agents ?? []).map((agent) => [agent.id, agent.name])),
    [agents],
  );

  const sessionBuckets: Array<{ key: SessionBucketKey; label: string; sessions: typeof sessions }> = [
    { key: 'today', label: t('chat:historyBuckets.today'), sessions: [] },
    { key: 'withinWeek', label: t('chat:historyBuckets.withinWeek'), sessions: [] },
    { key: 'withinMonth', label: t('chat:historyBuckets.withinMonth'), sessions: [] },
    { key: 'older', label: t('chat:historyBuckets.older'), sessions: [] },
  ];
  const sessionBucketMap = Object.fromEntries(sessionBuckets.map((bucket) => [bucket.key, bucket])) as Record<
    SessionBucketKey,
    (typeof sessionBuckets)[number]
  >;

  for (const { session, activityMs } of sessions
    .map((session) => ({
      session,
      activityMs: getSessionActivityMs(session, sessionLastActivity),
    }))
    .sort((a, b) => b.activityMs - a.activityMs)) {
    const bucketKey = getSessionBucket(activityMs, nowMs);
    sessionBucketMap[bucketKey].sessions.push(session);
  }

  const handleSelectSession = (key: string) => {
    if (currentSessionKey === key) {
      void loadHistory(false);
    } else {
      switchSession(key);
    }
    navigate('/');
    onOpenChange(false);
  };

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent side="left" className="w-[85vw] max-w-sm p-0 flex flex-col gap-0">
          <SheetHeader className="pt-safe shrink-0 border-b border-black/5 dark:border-white/10 px-4 py-3 text-left">
            <SheetTitle className="text-sm">{t('chat:history', '会话')}</SheetTitle>
          </SheetHeader>

          {/* New chat */}
          <div className="shrink-0 px-2 pt-2">
            <button
              type="button"
              data-testid="drawer-new-chat"
              onClick={() => {
                handleNewChat();
                onOpenChange(false);
              }}
              className="flex w-full min-h-[44px] items-center gap-2 rounded-xl px-3 text-sm text-foreground/80 active:bg-black/5 dark:active:bg-white/10 transition-colors"
            >
              <Plus className="h-4 w-4 shrink-0" strokeWidth={2} />
              <span className="flex-1 text-left truncate">{t('common:sidebar.newChat')}</span>
            </button>
          </div>

          {/* Session buckets */}
          <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden overscroll-contain px-2 pb-4 space-y-1">
            {sessionBuckets.map((bucket) => {
              const isBucketExpanded = expandedSessionBuckets[bucket.key] ?? false;
              return (
                <div key={bucket.key} data-testid={`session-bucket-${bucket.key}`} className="pt-2">
                  <button
                    type="button"
                    aria-expanded={isBucketExpanded}
                    onClick={() => toggleSessionBucket(bucket.key)}
                    className={cn(
                      'flex w-full min-h-[36px] items-center gap-1 rounded-md px-2.5 text-left text-[11px] font-bold uppercase tracking-widest',
                      'text-muted-foreground/60 transition-colors active:bg-black/5 dark:active:bg-white/5',
                    )}
                  >
                    <ChevronRight
                      className={cn(
                        'h-3 w-3 shrink-0 transition-transform',
                        isBucketExpanded && 'rotate-90',
                      )}
                    />
                    <span>{bucket.label}</span>
                  </button>
                  {isBucketExpanded && bucket.sessions.map((s) => {
                    const agentId = getAgentIdFromSessionKey(s.key);
                    const agentName = agentNameById[agentId] || agentId;
                    const isEditing = editingSessionKey === s.key;
                    const sessionLabel = getSessionLabel(s.key, s.displayName, s.label);
                    const channelType = s.channel && s.channel !== 'webchat' ? s.channel : null;
                    const channelName = channelType ? CHANNEL_NAMES[channelType as keyof typeof CHANNEL_NAMES] ?? channelType : null;
                    const isCurrent = isOnChat && currentSessionKey === s.key;
                    return (
                      <div key={s.key} className="relative flex items-center">
                        {isEditing ? (
                          <div className="flex w-full items-center gap-1 px-1.5 py-1">
                            <Input
                              autoFocus
                              value={editingLabel}
                              onChange={(e) => setEditingLabel(e.target.value)}
                              onKeyDown={handleRenameKeyDown}
                              onBlur={() => void handleRenameSubmit()}
                              className="h-9 min-w-0 flex-1 text-base"
                              aria-label={t('common:sidebar.renameSessionPlaceholder')}
                            />
                            <button
                              aria-label={t('common:sidebar.saveSessionRename')}
                              onMouseDown={(e) => { e.preventDefault(); void handleRenameSubmit(); }}
                              className="flex h-9 w-9 shrink-0 items-center justify-center rounded text-muted-foreground active:text-foreground"
                            >
                              <Check className="h-4 w-4" />
                            </button>
                            <button
                              aria-label={t('common:sidebar.cancelSessionRename')}
                              onMouseDown={(e) => { e.preventDefault(); handleRenameCancel(); }}
                              className="flex h-9 w-9 shrink-0 items-center justify-center rounded text-muted-foreground active:text-destructive"
                            >
                              <X className="h-4 w-4" />
                            </button>
                          </div>
                        ) : (
                          <>
                            <button
                              data-testid={`drawer-session-${s.key}`}
                              onClick={() => handleSelectSession(s.key)}
                              className={cn(
                                'w-full text-left rounded-2xl px-2.5 py-2 transition-all pr-[4.5rem]',
                                isCurrent
                                  ? 'bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-lg shadow-indigo-200 font-medium'
                                  : 'active:bg-black/5 dark:active:bg-white/5 text-foreground/75',
                              )}
                            >
                              <div className="flex min-w-0 items-center gap-2.5">
                                {/* Gradient avatar */}
                                <div className={cn(
                                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-white text-2xs font-bold',
                                  isCurrent
                                    ? 'ring-2 ring-white/30 bg-white/20'
                                    : `bg-gradient-to-br ${SESSION_GRADIENTS[getGradientIndex(s.key)]}`
                                )}>
                                  {(sessionLabel || agentName || '?')[0].toUpperCase()}
                                </div>
                                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                                  <span className={cn(
                                    'truncate text-sm font-semibold',
                                    isCurrent ? 'text-white' : 'text-foreground/85'
                                  )}>
                                    {sessionLabel || agentName}
                                  </span>
                                  <div className="flex items-center gap-1.5">
                                    <span className={cn(
                                      'shrink-0 rounded-full px-1.5 py-px text-[10px] font-medium',
                                      isCurrent
                                        ? 'bg-white/20 text-white/90'
                                        : 'bg-black/[0.04] text-foreground/60 dark:bg-white/[0.08]'
                                    )}>
                                      {agentName}
                                    </span>
                                    {channelType && channelName && (
                                      <span
                                        title={channelName}
                                        aria-label={channelName}
                                        className={cn(
                                          'shrink-0 truncate rounded-full px-1.5 py-px text-[10px] font-medium',
                                          isCurrent
                                            ? 'bg-white/20 text-white/90'
                                            : 'bg-blue-500/10 text-blue-700 dark:bg-blue-400/10 dark:text-blue-400'
                                        )}
                                      >
                                        {channelName}
                                      </span>
                                    )}
                                  </div>
                                </div>
                              </div>
                            </button>
                            {/* Touch: action buttons always visible */}
                            <div className="absolute right-1 flex items-center gap-0.5">
                              <button
                                aria-label={t('common:sidebar.renameSession')}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleStartRename(s.key, sessionLabel);
                                }}
                                className={cn(
                                  'flex h-8 w-8 items-center justify-center rounded',
                                  isCurrent ? 'text-white/80' : 'text-muted-foreground',
                                )}
                              >
                                <Pencil className="h-4 w-4" />
                              </button>
                              <button
                                data-testid={`drawer-session-delete-${s.key}`}
                                aria-label={t('common:sidebar.deleteSession')}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSessionToDelete({ key: s.key, label: sessionLabel });
                                  setDeleteDialogOpen(true);
                                }}
                                className={cn(
                                  'flex h-8 w-8 items-center justify-center rounded',
                                  isCurrent ? 'text-white/80' : 'text-muted-foreground active:text-destructive',
                                )}
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </div>
                          </>
                        )}
                      </div>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </SheetContent>
      </Sheet>

      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('common:actions.confirm')}
        message={t('common:sidebar.deleteSessionConfirm', { label: sessionToDelete?.label ?? '' })}
        confirmLabel={t('common:actions.delete')}
        cancelLabel={t('common:actions.cancel')}
        variant="destructive"
        onConfirm={async () => {
          const targetSession = sessionToDelete;
          if (!targetSession) return;
          await deleteSession(targetSession.key);
          if (currentSessionKey === targetSession.key) navigate('/');
          setDeleteDialogOpen(false);
        }}
        onCancel={() => setDeleteDialogOpen(false)}
      />
    </>
  );
}
