/**
 * Chat Toolbar
 * Session selector, new session, refresh, and the workspace browser
 * entry point.  Rendered in the Header when on the Chat page.
 */
import { useMemo, useState } from 'react';
import { RefreshCw, FolderTree, ListTree, FileDiff } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useChatStore } from '@/stores/chat';
import { useAgentsStore } from '@/stores/agents';
import { useArtifactPanel } from '@/stores/artifact-panel';
import { cn } from '@/lib/utils';
import { useTranslation } from 'react-i18next';
import { WORKSPACE_BROWSER_ENABLED } from '@/components/file-preview/workspace-browser-config';

type ChatToolbarProps = {
  questionDirectoryOpen?: boolean;
  questionDirectoryCount?: number;
  changeCount?: number;
  onToggleQuestionDirectory?: () => void;
};

export function ChatToolbar({
  questionDirectoryOpen = false,
  questionDirectoryCount = 0,
  changeCount = 0,
  onToggleQuestionDirectory,
}: ChatToolbarProps = {}) {
  const refresh = useChatStore((s) => s.refresh);
  const loading = useChatStore((s) => s.loading);
  const [refreshing, setRefreshing] = useState(false);
  const currentAgentId = useChatStore((s) => s.currentAgentId);
  const agents = useAgentsStore((s) => s.agents);
  const openBrowser = useArtifactPanel((s) => s.openBrowser);
  const openChanges = useArtifactPanel((s) => s.openChanges);
  const panelOpen = useArtifactPanel((s) => s.open);
  const panelTab = useArtifactPanel((s) => s.tab);
  const closePanel = useArtifactPanel((s) => s.close);
  const { t } = useTranslation('chat');
  const currentAgent = useMemo(
    () => (agents ?? []).find((agent) => agent.id === currentAgentId) ?? null,
    [agents, currentAgentId],
  );
  const browserActive = WORKSPACE_BROWSER_ENABLED && panelOpen && panelTab === 'browser';
  const changesActive = panelOpen && panelTab === 'changes';
  const questionDirectoryAvailable = questionDirectoryCount > 1 && !!onToggleQuestionDirectory;
  const refreshBusy = loading || refreshing;

  const handleRefresh = async (): Promise<void> => {
    if (refreshBusy) return;
    setRefreshing(true);
    try {
      await refresh();
      if (useChatStore.getState().error) {
        toast.error(t('toolbar.refreshFailed'));
      } else {
        toast.success(t('toolbar.refreshDone'));
      }
    } catch (error) {
      toast.error(`${t('toolbar.refreshFailed')}: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <div className="flex items-center gap-2">
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className={cn(
              'relative h-8 w-8 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10',
              changesActive && 'bg-foreground/10 text-foreground',
            )}
            onClick={() => (changesActive ? closePanel() : openChanges())}
            aria-label={t('generatedFiles.title', { count: changeCount })}
          >
            <FileDiff className="h-4 w-4" />
            {changeCount > 0 && (
              <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-indigo-600 px-1 text-[10px] leading-none text-white dark:bg-orange-500">
                {changeCount > 9 ? '9+' : changeCount}
              </span>
            )}
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          <p>{t('generatedFiles.title', { count: changeCount })}</p>
        </TooltipContent>
      </Tooltip>
      {WORKSPACE_BROWSER_ENABLED && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                'h-8 w-8 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10',
                browserActive && 'bg-foreground/10 text-foreground',
              )}
              onClick={() => (browserActive ? closePanel() : openBrowser())}
              disabled={!currentAgent?.workspace}
              aria-label={t('toolbar.workspace')}
            >
              <FolderTree className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>{t('toolbar.workspace')}</p>
          </TooltipContent>
        </Tooltip>
      )}
      <Tooltip>
        <TooltipTrigger asChild>
          {/* Span wrapper keeps the tooltip reachable while the button is
              disabled, so users can learn why the toggle is unavailable. */}
          <span className="inline-flex" tabIndex={questionDirectoryAvailable ? undefined : 0}>
            <Button
              data-testid="chat-question-directory-toggle"
              variant="ghost"
              size="icon"
              className={cn(
                'h-8 w-8 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10',
                questionDirectoryOpen && 'bg-foreground/10 text-foreground',
              )}
              onClick={onToggleQuestionDirectory}
              disabled={!questionDirectoryAvailable}
              aria-label={t('questionDirectory.title')}
            >
              <ListTree className="h-4 w-4" />
            </Button>
          </span>
        </TooltipTrigger>
        <TooltipContent>
          <p>{questionDirectoryAvailable ? t('questionDirectory.title') : t('questionDirectory.disabledHint')}</p>
        </TooltipContent>
      </Tooltip>
      {/* Refresh */}
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
            onClick={() => void handleRefresh()}
            disabled={refreshBusy}
            aria-label={t('toolbar.refresh')}
          >
            <RefreshCw className={cn('h-4 w-4', refreshBusy && 'animate-spin')} />
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          <p>{t('toolbar.refresh')}</p>
        </TooltipContent>
      </Tooltip>
    </div>
  );
}
