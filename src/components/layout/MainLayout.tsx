/**
 * Main Layout — connected shell (layout-v2 / WorkBuddy-Qoder architecture).
 * The whole window is one saturated blue-purple gradient. TopBar + PlatformTabs
 * + left sidebar sit directly on that gradient as transparent/glass regions;
 * only the stage is a single white rounded card. No white seams between panels.
 */
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { LegalSidebar } from './LegalSidebar';
import { TopBar } from './TopBar';
import { PlatformTabs } from './PlatformTabs';
import { isLegalSection } from './nav-config';
import { SettingsModal } from '@/components/settings/SettingsModal';
import { GatewayStatusBanner } from '@/components/common/GatewayStatusBanner';
import { ChatInput } from '@/pages/Chat/ChatInput';
import { useChatStore } from '@/stores/chat';
import { MAC_SIDEBAR_CHROME_HEIGHT } from '@shared/sidebar-layout';

export function MainLayout() {
  const platform = window.electron?.platform;
  const isMac = platform === 'darwin';
  const location = useLocation();
  const isChatPage = location.pathname === '/';
  const isLegal = isLegalSection(location.pathname);
  const showSidebar = isChatPage || isLegal;

  const sendMessage = useChatStore((s) => s.sendMessage);
  const abortRun = useChatStore((s) => s.abortRun);
  const sending = useChatStore((s) => s.sending);

  return (
    <div
      data-testid="main-layout"
      data-platform={platform}
      className="shell-gradient flex h-screen flex-col overflow-hidden"
    >
      <TopBar />
      <PlatformTabs />
      <GatewayStatusBanner />

      <div className="flex min-h-0 flex-1 overflow-hidden">
        {showSidebar && (
          <div className="flex min-h-0 shrink-0 overflow-hidden bg-transparent">
            {isLegal ? <LegalSidebar /> : <Sidebar />}
          </div>
        )}

        {/* Stage: the single white card floating on the gradient */}
        <div className="m-2 ml-1 flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl bg-surface-modal shadow-shell">
          <main data-testid="main-content" className="relative min-h-0 flex-1 overflow-hidden">
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

          {isChatPage && (
            <div className="shrink-0 border-t border-border/60 px-4 py-3">
              <ChatInput onSend={sendMessage} onStop={abortRun} disabled={false} sending={sending} />
            </div>
          )}
        </div>
      </div>

      <SettingsModal />
    </div>
  );
}
