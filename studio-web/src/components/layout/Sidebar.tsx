/**
 * Sidebar Component
 * Navigation sidebar with menu items.
 * No longer fixed - sits inside the flex layout below the title bar.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  PanelLeftClose,
  PanelLeft,
  Plus,
  Trash2,
  Pencil,
  Check,
  X,
  ChevronRight,
  Search,
  Settings,
  Sparkles,
  Clock,
  Bot,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { rendererExtensionRegistry } from '@/extensions/registry';
import { useSettingsStore } from '@/stores/settings';
import { useAuthStore } from '@/stores/auth';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useChatStore, type ChatSession } from '@/stores/chat';
import { useGatewayStore } from '@/stores/gateway';
import { useAgentsStore } from '@/stores/agents';
import { getSessionActivityMs, getSessionBucket, type SessionBucketKey } from './session-buckets';
import { getAgentIdFromSessionKey } from './agent-session-tree';
import { CHANNEL_NAMES } from '@shared/types/channel';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { hostApi } from '@/lib/host-api';
import { SIDEBAR_COLLAPSED_WIDTH, MAC_SIDEBAR_CHROME_HEIGHT } from '@shared/sidebar-layout';
import { useTranslation } from 'react-i18next';
// logoSvg moved to TopBar
import { useNewChatAction } from './use-new-chat-action';
import { SESSION_GRADIENTS, getGradientIndex } from '@/lib/constants';

interface NavItemProps {
  to: string;
  icon: React.ReactNode;
  label: string;
  badge?: string;
  collapsed?: boolean;
  onClick?: () => void;
  testId?: string;
}

function NavItem({ to, icon, label, badge, collapsed, onClick, testId }: NavItemProps) {
  return (
    <NavLink
      to={to}
      onClick={onClick}
      data-testid={testId}
      className={({ isActive }) =>
        cn(
          'sidebar-nav-text flex items-center gap-2 rounded-xl px-2.5 py-2 transition-all',
          'hover:bg-white/10 text-white/80',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60',
          isActive
            ? 'shell-glass-strong text-white font-medium'
            : '',
          collapsed && 'justify-center px-0'
        )
      }
    >
      <>
        <div className="flex shrink-0 items-center justify-center text-current [&_svg]:size-4">
          {icon}
        </div>
        {!collapsed && (
          <>
            <span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap">{label}</span>
            {badge && (
              <Badge variant="secondary" className="ml-auto shrink-0">
                {badge}
              </Badge>
            )}
          </>
        )}
      </>
    </NavLink>
  );
}

const INITIAL_NOW_MS = Date.now();

function relTime(ms: number, now: number): string {
  const min = Math.floor(Math.max(0, now - ms) / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min}分钟前`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}小时前`;
  const day = Math.floor(hr / 24);
  if (day < 30) return `${day}天前`;
  return `${Math.floor(day / 30)}个月前`;
}
const DEFAULT_EXPANDED_SESSION_BUCKETS: Record<SessionBucketKey, boolean> = {
  today: true,
  withinWeek: true,
  withinMonth: false,
  older: false,
};

interface SessionItemProps {
  session: ChatSession;
  agentName: string;
  sessionLabel: string;
  activityLabel: string;
  isActive: boolean;
  isEditing: boolean;
  editingLabel: string;
  onEditingLabelChange: (value: string) => void;
  onRenameKeyDown: (e: React.KeyboardEvent) => void;
  onRenameSubmit: () => void;
  onRenameCancel: () => void;
  onOpen: () => void;
  onStartRename: () => void;
  onRequestDelete: () => void;
}

function SessionItem({
  session,
  agentName,
  sessionLabel,
  activityLabel,
  isActive,
  isEditing,
  editingLabel,
  onEditingLabelChange,
  onRenameKeyDown,
  onRenameSubmit,
  onRenameCancel,
  onOpen,
  onStartRename,
  onRequestDelete,
}: SessionItemProps) {
  const { t } = useTranslation(['common']);
  const channelType = session.channel && session.channel !== 'webchat' ? session.channel : null;
  const channelName = channelType
    ? CHANNEL_NAMES[channelType as keyof typeof CHANNEL_NAMES] ?? channelType
    : null;
  return (
    <div className="group relative flex items-center">
      {isEditing ? (
        <div className="flex w-full items-center gap-1 px-1.5 py-1">
          <Input
            autoFocus
            value={editingLabel}
            onChange={(e) => onEditingLabelChange(e.target.value)}
            onKeyDown={onRenameKeyDown}
            onBlur={onRenameSubmit}
            className="h-7 min-w-0 flex-1 text-meta"
            aria-label={t('common:sidebar.renameSessionPlaceholder')}
          />
          <button
            aria-label={t('common:sidebar.saveSessionRename')}
            onMouseDown={(e) => { e.preventDefault(); onRenameSubmit(); }}
            className="flex shrink-0 items-center justify-center rounded p-0.5 text-muted-foreground hover:text-foreground"
          >
            <Check className="h-3.5 w-3.5" />
          </button>
          <button
            aria-label={t('common:sidebar.cancelSessionRename')}
            onMouseDown={(e) => { e.preventDefault(); onRenameCancel(); }}
            className="flex shrink-0 items-center justify-center rounded p-0.5 text-muted-foreground hover:text-destructive"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ) : (
        <>
          <button
            data-testid={`sidebar-session-${session.key}`}
            onClick={onOpen}
            onDoubleClick={onStartRename}
            className={cn(
              'w-full text-left rounded-2xl px-2.5 py-2 text-meta transition-all pr-16',
              isActive
                ? 'shell-glass-strong text-white font-medium'
                : 'hover:bg-white/10 text-white/80',
            )}
          >
            <div className="flex min-w-0 items-center gap-2.5">
              {/* Gradient avatar */}
              <div className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-xl text-white text-2xs font-bold',
                isActive
                  ? 'ring-2 ring-white/30 bg-white/20'
                  : `bg-gradient-to-br ${SESSION_GRADIENTS[getGradientIndex(session.key)]}`
              )}>
                {(sessionLabel || agentName || '?')[0].toUpperCase()}
              </div>
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className={cn(
                  'truncate text-xs font-semibold',
                  isActive ? 'text-white' : 'text-white/90'
                )}>
                  {sessionLabel || agentName}
                </span>
                <div className="flex items-center gap-1.5">
                  <span className={cn(
                    'shrink-0 rounded-full px-1.5 py-px text-[10px] font-medium',
                    isActive
                      ? 'bg-white/20 text-white/90'
                      : 'bg-white/10 text-white/65'
                  )}>
                    {agentName}
                  </span>
                  {channelType && channelName && (
                    <span
                      title={channelName}
                      aria-label={channelName}
                      className={cn(
                        'shrink-0 truncate rounded-full px-1.5 py-px text-[10px] font-medium',
                        isActive
                          ? 'bg-white/20 text-white/90'
                          : 'bg-white/10 text-white/70'
                      )}
                    >
                      {channelName}
                    </span>
                  )}
                  <span className={cn(
                    'ml-auto shrink-0 text-[10px]',
                    isActive ? 'text-white/70' : 'text-white/45'
                  )}>
                    {activityLabel}
                  </span>
                </div>
              </div>
            </div>
          </button>
          <div className={cn(
            'absolute right-1 flex items-center gap-0.5 transition-opacity',
            'opacity-0 group-hover:opacity-100',
            '[@media(pointer:coarse)]:opacity-100',
          )}>
            <button
              aria-label={t('common:sidebar.renameSession')}
              onClick={(e) => {
                e.stopPropagation();
                onStartRename();
              }}
              className="flex items-center justify-center rounded p-0.5 text-white/60 hover:text-white hover:bg-white/15"
            >
              <Pencil className="h-3.5 w-3.5" />
            </button>
            <button
              data-testid={`sidebar-session-delete-${session.key}`}
              aria-label={t('common:sidebar.deleteSession')}
              onClick={(e) => {
                e.stopPropagation();
                onRequestDelete();
              }}
              className="flex items-center justify-center rounded p-0.5 text-white/60 hover:text-red-300 hover:bg-white/15"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        </>
      )}
    </div>
  );
}

function SidebarFooter() {
  const { t } = useTranslation(['common']);
  const user = useAuthStore((s) => s.user);
  const openSettings = useSettingsUiStore((s) => s.openSettings);
  const name = user?.username?.trim() || t('sidebar.settings');
  const initial = name.slice(0, 1).toUpperCase();
  const entries = [
    { section: 'skills' as const, icon: Sparkles, label: t('sidebar.skills') },
    { section: 'cron' as const, icon: Clock, label: t('sidebar.cronTasks') },
    { section: 'agents' as const, icon: Bot, label: t('sidebar.agents') },
  ];

  return (
    <div className="shell-card mt-2 shrink-0 rounded-xl p-2">
      <div className="px-2.5 pb-1 pt-1 text-[11px] font-medium tracking-wide text-white/55">
        {t('sidebar.capabilities')}
      </div>
      {entries.map((item) => {
        const Icon = item.icon;
        return (
          <button
            key={item.section}
            type="button"
            onClick={() => openSettings(item.section)}
            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-sm text-white/80 transition-colors hover:bg-white/10 hover:text-white"
          >
            <Icon className="h-4 w-4 shrink-0" strokeWidth={1.75} />
            <span className="truncate">{item.label}</span>
          </button>
        );
      })}
      <div className="mt-1 flex items-center gap-2 rounded-lg px-2 py-1.5">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white text-xs font-semibold text-indigo-700">
          {initial}
        </span>
        <span className="min-w-0 flex-1 truncate text-sm text-white">{name}</span>
        <button
          type="button"
          title={t('sidebar.settings')}
          aria-label={t('sidebar.settings')}
          onClick={() => openSettings()}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-white/70 hover:bg-white/15 hover:text-white"
        >
          <Settings className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

export function Sidebar() {
  const isMac = window.electron?.platform === 'darwin';
  const sidebarCollapsed = useSettingsStore((state) => state.sidebarCollapsed);
  const setSidebarCollapsed = useSettingsStore((state) => state.setSidebarCollapsed);
  const sidebarWidth = useSettingsStore((state) => state.sidebarWidth);
  const setSidebarWidth = useSettingsStore((state) => state.setSidebarWidth);
  const [isResizing, setIsResizing] = useState(false);
  const [query, setQuery] = useState('');
  const stopResizeRef = useRef<(() => void) | null>(null);

  const sessions = useChatStore((s) => s.sessions);
  const currentSessionKey = useChatStore((s) => s.currentSessionKey);
  const sessionLabels = useChatStore((s) => s.sessionLabels);
  const sessionLastActivity = useChatStore((s) => s.sessionLastActivity);
  const switchSession = useChatStore((s) => s.switchSession);
  const deleteSession = useChatStore((s) => s.deleteSession);
  const renameSession = useChatStore((s) => s.renameSession);
  const loadSessions = useChatStore((s) => s.loadSessions);
  const loadHistory = useChatStore((s) => s.loadHistory);
  const handleNewChat = useNewChatAction();

  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayReady = isGatewayRunning && gatewayStatus.gatewayReady !== false;
  // gatewayRestarting moved to TopBar
  const gatewayRuntimeKey = `${gatewayStatus.pid ?? 'none'}:${gatewayStatus.connectedAt ?? 'none'}:${gatewayStatus.port}`;

  const hasLoadedCurrentRuntimeRef = useRef(false);

  useEffect(() => {
    hasLoadedCurrentRuntimeRef.current = false;
  }, [gatewayRuntimeKey]);

  useEffect(() => {
    if (!isGatewayReady) return;
    let cancelled = false;
    (async () => {
      try {
        await loadSessions();
      } catch (error) {
        // loadSessions has its own internal error handling, but guard against
        // any unexpected failure so loadHistory still runs.
        console.warn('[Sidebar] loadSessions failed:', error);
      }
      if (cancelled) return;
      if (hasLoadedCurrentRuntimeRef.current) return;
      hasLoadedCurrentRuntimeRef.current = true;
      try {
        await loadHistory(false);
      } catch (error) {
        // loadHistory already clears its own loading state on failure, but
        // log the error for debugging.
        console.warn('[Sidebar] loadHistory failed:', error);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [gatewayRuntimeKey, isGatewayReady, loadHistory, loadSessions]);
  const agents = useAgentsStore((s) => s.agents);
  const fetchAgents = useAgentsStore((s) => s.fetchAgents);

  useEffect(() => {
    if (!isMac) return;
    void hostApi.window.syncTrafficLightPosition(sidebarCollapsed);
  }, [isMac, sidebarCollapsed]);

  const navigate = useNavigate();
  const isOnChat = useLocation().pathname === '/';

  const getSessionLabel = (key: string, displayName?: string, label?: string) =>
    sessionLabels[key] ?? label ?? displayName ?? key;

  const { t } = useTranslation(['common', 'chat']);
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
    void fetchAgents();
  }, [fetchAgents]);

  useEffect(() => {
    if (deleteDialogOpen || !sessionToDelete) return;
    const timer = window.setTimeout(() => setSessionToDelete(null), 160);
    return () => window.clearTimeout(timer);
  }, [deleteDialogOpen, sessionToDelete]);

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

  const stopResizing = useCallback(() => {
    stopResizeRef.current?.();
    stopResizeRef.current = null;
    setIsResizing(false);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  }, []);

  const handleResizePointerDown = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      if (sidebarCollapsed) return;
      event.preventDefault();
      event.stopPropagation();
      try {
        event.currentTarget.setPointerCapture(event.pointerId);
      } catch {
        // Window listeners below keep dragging reliable even if capture is unavailable.
      }

      const onMove = (moveEvent: PointerEvent) => {
        setSidebarWidth(moveEvent.clientX);
      };
      const onUp = () => stopResizing();

      stopResizeRef.current = () => {
        window.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', onUp);
      };
      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', onUp);
      setIsResizing(true);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    },
    [setSidebarWidth, sidebarCollapsed, stopResizing],
  );

  useEffect(() => stopResizing, [stopResizing]);

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

  const q = query.trim().toLowerCase();
  const visibleSessions = q
    ? sessions.filter((s) =>
        (sessionLabels[s.key] || s.label || s.displayName || '').toLowerCase().includes(q),
      )
    : sessions;

  for (const session of visibleSessions) {
    const bucketKey = getSessionBucket(getSessionActivityMs(session, sessionLastActivity), nowMs);
    sessionBucketMap[bucketKey].sessions.push(session);
  }

  const renderSessionItem = (session: ChatSession) => {
    const agentId = getAgentIdFromSessionKey(session.key);
    const agentName = agentNameById[agentId] || agentId;
    const sessionLabel = getSessionLabel(session.key, session.displayName, session.label);
    return (
      <SessionItem
        key={session.key}
        session={session}
        agentName={agentName}
        sessionLabel={sessionLabel}
        activityLabel={relTime(getSessionActivityMs(session, sessionLastActivity), nowMs)}
        isActive={isOnChat && currentSessionKey === session.key}
        isEditing={editingSessionKey === session.key}
        editingLabel={editingLabel}
        onEditingLabelChange={setEditingLabel}
        onRenameKeyDown={handleRenameKeyDown}
        onRenameSubmit={() => void handleRenameSubmit()}
        onRenameCancel={handleRenameCancel}
        onOpen={() => {
          if (currentSessionKey === session.key) {
            void loadHistory(false);
          } else {
            switchSession(session.key);
          }
          navigate('/');
        }}
        onStartRename={() => handleStartRename(session.key, sessionLabel)}
        onRequestDelete={() => {
          setSessionToDelete({ key: session.key, label: sessionLabel });
          setDeleteDialogOpen(true);
        }}
      />
    );
  };

  const hiddenRoutes = rendererExtensionRegistry.getHiddenRoutes();
  const extraNavItems = rendererExtensionRegistry.getExtraNavItems();

  const coreNavItems: { to: string; icon: React.ReactNode; label: string; testId: string }[] = [];

  const navItems = [
    ...coreNavItems.filter((item) => !hiddenRoutes.has(item.to)),
    ...extraNavItems.map((item) => ({
      to: item.to,
      icon: <item.icon className="h-4 w-4" strokeWidth={2} />,
      label: item.labelI18nKey ? t(item.labelI18nKey) : item.label,
      testId: item.testId,
    })),
  ];

  return (
    <aside
      data-testid="sidebar"
      className={cn(
        'relative flex min-h-0 shrink-0 flex-col overflow-hidden px-2.5 pb-3 pt-1',
        isResizing ? 'transition-none' : 'transition-[width] duration-300',
      )}
      style={{ width: sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : sidebarWidth }}
    >
      {isMac && (
        <div
          aria-hidden="true"
          data-testid="mac-sidebar-chrome"
          className="drag-region shrink-0"
          style={{ height: MAC_SIDEBAR_CHROME_HEIGHT }}
        />
      )}

      {/* Top: collapse toggle only (logo moved to TopBar) */}
      <div
        className={cn(
          'flex shrink-0 items-center p-2 h-8',
          sidebarCollapsed ? 'justify-center' : 'justify-end',
        )}
      >
        <Button
          data-testid="sidebar-collapse-toggle"
          variant="ghost"
          size="icon"
          className={cn(
            'no-drag h-8 w-8 shrink-0 rounded-lg text-white/70',
            'hover:bg-white/15 hover:text-white',
          )}
          onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
        >
          {sidebarCollapsed ? (
            <PanelLeft className="h-[18px] w-[18px]" />
          ) : (
            <PanelLeftClose className="h-[18px] w-[18px]" />
          )}
        </Button>
      </div>

      {/* Navigation */}
      <nav className="flex shrink-0 flex-col gap-1 px-2">
        <button
          type="button"
          data-testid="sidebar-new-chat"
          onClick={handleNewChat}
          className={cn(
            'sidebar-nav-text shell-cta flex items-center gap-2 rounded-lg px-2.5 py-2 font-semibold transition-colors',
            sidebarCollapsed && 'justify-center px-0',
          )}
        >
          <div className="flex shrink-0 items-center justify-center text-current [&_svg]:size-4">
            <Plus className="h-4 w-4" strokeWidth={2} />
          </div>
          {!sidebarCollapsed && <span className="flex-1 text-left overflow-hidden text-ellipsis whitespace-nowrap">{t('sidebar.newChat')}</span>}
        </button>

        {navItems.map((item) => (
          <NavItem
            key={item.to}
            {...item}
            collapsed={sidebarCollapsed}
          />
        ))}
      </nav>

      {/* Glass search over sessions */}
      {!sidebarCollapsed && (
        <div className="shell-glass mx-2 mt-2 flex shrink-0 items-center gap-2 rounded-lg px-2.5 py-1.5">
          <Search className="h-3.5 w-3.5 shrink-0 text-white/60" strokeWidth={1.75} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索会话…"
            className="min-w-0 flex-1 bg-transparent text-meta text-white placeholder:text-white/50 focus:outline-none"
          />
        </div>
      )}

      {/* Session list —below Settings, only when expanded */}
      {!sidebarCollapsed && sessions.length > 0 && (
        <div className="mt-4 flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-2 pb-2 space-y-1">
          {sessionBuckets.map((bucket) => {
            const isBucketExpanded = expandedSessionBuckets[bucket.key] ?? false;
            return (
              <div key={bucket.key} data-testid={`session-bucket-${bucket.key}`} className="pt-2">
                <button
                  type="button"
                  data-testid={`session-bucket-toggle-${bucket.key}`}
                  aria-expanded={isBucketExpanded}
                  onClick={() => toggleSessionBucket(bucket.key)}
                  className={cn(
                    'flex w-full items-center gap-1 rounded-md px-2.5 py-1 text-left text-[11px] font-bold uppercase tracking-widest',
                    'text-white/55 transition-colors',
                    'hover:bg-white/10 hover:text-white/80',
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
                {isBucketExpanded && bucket.sessions.map((s) => renderSessionItem(s))}
              </div>
            );
          })}
        </div>
      )}

      {/* Bottom glass user + capabilities footer */}
      {!sidebarCollapsed && <SidebarFooter />}

      {!sidebarCollapsed && (
        <div
          data-testid="sidebar-resize-handle"
          role="separator"
          aria-orientation="vertical"
          aria-valuemin={220}
          aria-valuemax={420}
          aria-valuenow={sidebarWidth}
          title="Drag to resize sidebar"
          onPointerDown={handleResizePointerDown}
          className="no-drag group absolute inset-y-0 right-0 z-20 w-2 translate-x-1/2 cursor-col-resize select-none"
        >
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-transparent transition-colors group-hover:bg-primary/40"
          />
        </div>
      )}

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
    </aside>
  );
}
