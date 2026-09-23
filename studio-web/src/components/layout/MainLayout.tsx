/**
 * Main Layout Component
 * Platform-aware application shell.
 * Structure: TopBar (full-width) → Sidebar + MainContent
 */
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { SigningSidebar } from './SigningSidebar';
import { TopBar } from './TopBar';
import { SettingsModal } from '@/components/settings/SettingsModal';
import { PlatformTabs } from './PlatformTabs';
import { ChatInput } from '@/pages/Chat/ChatInput';
import { useChatStore } from '@/stores/chat';
import { MAC_SIDEBAR_CHROME_HEIGHT } from '@shared/sidebar-layout';
import { cn } from '@/lib/utils';

export function MainLayout() {
  const platform = window.electron?.platform;
  const isMac = platform === 'darwin';
  const location = useLocation();
  const isChatPage = location.pathname === '/';
  // V3 IA：总览/模拟法庭/语音/模板/证据/企业管理/比对 全宽无侧栏；签署/详情用 SigningSidebar；Agent 用会话 Sidebar
  const isNoSidebarPage = ['/overview', '/moot', '/voice', '/templates', '/evidence', '/company', '/compare'].some((p) => location.pathname.startsWith(p));
  const isSigningSection = location.pathname.startsWith('/signing');
  
  const sendMessage = useChatStore((s) => s.sendMessage);
  const abortRun = useChatStore((s) => s.abortRun);
  const sending = useChatStore((s) => s.sending);

  return (
    <div
      data-testid="main-layout"
      data-platform={platform}
      className="flex h-screen overflow-hidden bg-background p-1 sm:p-2"
    >
      {/* Outer container with rounded border */}
      <div className="flex flex-col min-h-0 flex-1 rounded-2xl border-[3px] border-purple-300/60 dark:border-purple-500/30 bg-gradient-to-br from-purple-200/60 via-blue-100/40 to-indigo-200/60 dark:from-purple-900/20 dark:via-blue-900/15 dark:to-indigo-900/20 overflow-hidden shadow-xl">
        
        {/* 1. TopBar(含平台导航)——桌面单条顶栏(F1:不再上下两张漂浮卡片) */}
        <div className="mx-2 sm:mx-4 mt-2 sm:mt-4 rounded-xl bg-surface-modal overflow-hidden">
          <TopBar>
            <PlatformTabs />
          </TopBar>
        </div>

        {/* 2. Below: Sidebar + Main content */}
        <div className="flex min-h-0 flex-1 overflow-hidden p-2 sm:p-4 gap-2 sm:gap-4">
          {/* Left sidebar: Agent=会话列表, 签署/总览=SigningSidebar, 模拟法庭/语音=无 */}
          {!isNoSidebarPage && (
          <div className="flex min-h-0 shrink-0 rounded-xl bg-surface-sidebar overflow-hidden">
            {isSigningSection ? <SigningSidebar /> : <Sidebar />}
          </div>
          )}

          {/* Right: main content area - split 2:1 */}
          <div className="flex flex-col min-h-0 flex-1 gap-3">
            {/* Top card: messages - 2/3 */}
            <main
              data-testid="main-content"
              className={cn(
                'relative flex-[2] min-h-0 rounded-xl bg-surface-modal overflow-hidden',
              )}
            >
              {isMac && (
                <div
                  data-testid="mac-main-drag-region"
                  aria-hidden="true"
                  className="drag-region absolute inset-x-0 top-0 z-10"
                  style={{ height: MAC_SIDEBAR_CHROME_HEIGHT }}
                />
              )}
              <Outlet />
            </main>
            
            {/* Bottom card: chat input - auto height */}
            {isChatPage && (
              <div className="shrink-0 rounded-xl bg-surface-modal">
                <ChatInput
                  onSend={sendMessage}
                  onStop={abortRun}
                  disabled={false}
                  sending={sending}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Global settings modal (opened from the top-bar gear icon) */}
      <SettingsModal />
    </div>
  );
}
